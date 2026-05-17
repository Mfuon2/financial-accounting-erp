package com.qesuite.accounting.assets.service

import com.qesuite.accounting.assets.domain.AssetStatus
import com.qesuite.accounting.assets.domain.DepreciationMethod
import com.qesuite.accounting.assets.domain.FixedAsset
import com.qesuite.accounting.assets.repository.FixedAssetRepository
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

@Service
class DepreciationService(
    private val assetMasterService: AssetMasterService,
    private val journalService: JournalService,
    private val currencyRepository: CurrencyRepository,
    private val assetRepository: FixedAssetRepository
) {

    private val log = LoggerFactory.getLogger(DepreciationService::class.java)

    @Transactional
    fun runDepreciation(entityId: UUID, periodId: UUID, date: LocalDate) {
        val functionalCurrency = currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId)
            .orElseThrow { ValidationException("FUNCTIONAL_CURRENCY_NOT_SET", "Functional currency not set for entity $entityId.") }

        val assets = assetMasterService.getAssets(entityId)
            .filter { it.acquisitionDate <= date && it.status == AssetStatus.ACTIVE }

        assets.forEach { asset ->
            val rawAmount = calculateMonthlyDepreciation(asset)
            // Cap to the remaining depreciable balance so the final run never over-depreciates
            val remaining = asset.acquisitionCost
                .subtract(asset.salvageValue)
                .subtract(asset.accumulatedDepreciation)
                .max(BigDecimal.ZERO)
                .setScale(6, RoundingMode.HALF_EVEN)
            val amount = rawAmount.min(remaining)
            if (amount.signum() > 0) {
                postDepreciationEntry(asset, periodId, date, amount, functionalCurrency.currencyCode)
            }
        }
    }

    private fun calculateMonthlyDepreciation(asset: FixedAsset): BigDecimal {
        // D3 — guard: skip assets with invalid useful life
        if (asset.usefulLifeMonths <= 0) {
            log.warn(
                "depreciation.skip: asset {} has usefulLifeMonths={} — skipping",
                asset.id, asset.usefulLifeMonths
            )
            return BigDecimal.ZERO
        }

        return when (asset.depreciationMethod) {
            DepreciationMethod.STRAIGHT_LINE -> {
                val depreciableAmount = asset.acquisitionCost.subtract(asset.salvageValue)
                    .setScale(6, RoundingMode.HALF_EVEN)
                depreciableAmount.divide(BigDecimal(asset.usefulLifeMonths), 6, RoundingMode.HALF_EVEN)
            }
            DepreciationMethod.DOUBLE_DECLINING_BALANCE -> {
                // Use stored accumulatedDepreciation — account.currentBalance is unreliable here
                // because contra-asset accounts carry a credit (negative) balance, so
                // cost - accumBalance would compute cost + accum (wrong direction).
                val bookValue = asset.acquisitionCost
                    .subtract(asset.accumulatedDepreciation)
                    .setScale(6, RoundingMode.HALF_EVEN)

                val maxAllowable = bookValue.subtract(asset.salvageValue)
                    .setScale(6, RoundingMode.HALF_EVEN)
                if (maxAllowable.signum() <= 0) return BigDecimal.ZERO

                val rate = BigDecimal("2.0").divide(BigDecimal(asset.usefulLifeMonths), 6, RoundingMode.HALF_EVEN)
                val uncapped = bookValue.multiply(rate).setScale(6, RoundingMode.HALF_EVEN)
                uncapped.min(maxAllowable)
            }
        }
    }

    // D5: after posting, check if asset is now fully depreciated and update status
    private fun postDepreciationEntry(
        asset: FixedAsset,
        periodId: UUID,
        date: LocalDate,
        amount: BigDecimal,
        functionalCurrencyCode: String
    ) {
        val command = CreateJournalEntryCommand(
            entityId = asset.entityId,
            periodId = periodId,
            transDate = date,
            description = "Depreciation for ${asset.assetName}",
            sourceType = "FIXED_ASSET",
            sourceId = asset.id,
            lines = listOf(
                CreateJournalLineCommand(
                    accountId = asset.depExpenseAccountId,
                    description = "Depreciation Expense",
                    debitAmount = amount.setScale(6, RoundingMode.HALF_EVEN),
                    creditAmount = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN),
                    currencyCode = functionalCurrencyCode
                ),
                CreateJournalLineCommand(
                    accountId = asset.accumDepAccountId,
                    description = "Accumulated Depreciation",
                    debitAmount = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN),
                    creditAmount = amount.setScale(6, RoundingMode.HALF_EVEN),
                    currencyCode = functionalCurrencyCode
                )
            )
        )
        val entry = journalService.createDraft(command)
        journalService.postEntryAsSystem(entry.id)

        // D5 — update accumulated depreciation on asset entity, then check fully-depreciated
        asset.accumulatedDepreciation = asset.accumulatedDepreciation.add(amount)
            .setScale(6, RoundingMode.HALF_EVEN)

        val depreciableAmount = asset.acquisitionCost.subtract(asset.salvageValue)
            .setScale(6, RoundingMode.HALF_EVEN)

        if (asset.accumulatedDepreciation.compareTo(depreciableAmount) >= 0) {
            asset.status = AssetStatus.FULLY_DEPRECIATED
            log.info("depreciation: asset {} fully depreciated", asset.id)
        }
        assetRepository.save(asset)
    }
}

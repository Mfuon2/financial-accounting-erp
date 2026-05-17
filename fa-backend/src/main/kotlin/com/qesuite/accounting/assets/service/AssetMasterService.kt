package com.qesuite.accounting.assets.service

import com.qesuite.accounting.assets.domain.AssetStatus
import com.qesuite.accounting.assets.domain.DepreciationMethod
import com.qesuite.accounting.assets.domain.FixedAsset
import com.qesuite.accounting.assets.repository.FixedAssetRepository
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.shared.codegen.service.CodeGeneratorService
import com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

@Service
@Transactional
class AssetMasterService(
    private val fixedAssetRepository: FixedAssetRepository,
    private val accountRepository: AccountRepository,
    private val journalService: JournalService,
    private val currencyRepository: com.qesuite.accounting.fx.repository.CurrencyRepository,
    private val codeGeneratorService: CodeGeneratorService,
    private val numberConfigService: EntityNumberConfigService,
) {

    /**
     * §16.1 — Create and persist a new fixed asset.
     * Validates asset code uniqueness within the entity and verifies all three
     * COA account mappings exist before persisting.
     */
    fun createAsset(command: CreateAssetCommand): FixedAsset {
        val cfg = numberConfigService.resolveConfig(command.entityId, "FIXED_ASSET")
        val assetCode = command.assetCode.takeIf { it.isNotBlank() }
            ?: codeGeneratorService.nextUniqueForConfig(command.entityId, cfg) { code ->
                !fixedAssetRepository.existsByEntityIdAndAssetCode(command.entityId, code)
            }
        if (command.assetCode.isNotBlank() && fixedAssetRepository.existsByEntityIdAndAssetCode(command.entityId, assetCode)) {
            throw ConflictException(
                errorCode = "ASSET_CODE_DUPLICATE",
                message = "Asset code '$assetCode' already exists for entity ${command.entityId}.",
                context = mapOf("entityId" to command.entityId.toString(), "assetCode" to assetCode)
            )
        }

        accountRepository.findById(command.costAccountId).orElseThrow {
            ResourceNotFoundException("ACCOUNT_NOT_FOUND", command.costAccountId, "CostAccount")
        }
        accountRepository.findById(command.accumDepAccountId).orElseThrow {
            ResourceNotFoundException("ACCOUNT_NOT_FOUND", command.accumDepAccountId, "AccumDepAccount")
        }
        accountRepository.findById(command.depExpenseAccountId).orElseThrow {
            ResourceNotFoundException("ACCOUNT_NOT_FOUND", command.depExpenseAccountId, "DepExpenseAccount")
        }

        val asset = FixedAsset(
            entityId = command.entityId,
            periodId = command.periodId,
            assetCode = assetCode,
            assetName = command.assetName,
            category = command.category,
            assignedTo = command.assignedTo,
            costAccountId = command.costAccountId,
            accumDepAccountId = command.accumDepAccountId,
            depExpenseAccountId = command.depExpenseAccountId,
            acquisitionDate = command.acquisitionDate,
            acquisitionCost = command.acquisitionCost.setScale(6, RoundingMode.HALF_EVEN),
            salvageValue = command.salvageValue.setScale(6, RoundingMode.HALF_EVEN),
            usefulLifeMonths = command.usefulLifeMonths,
            depreciationMethod = command.depreciationMethod,
            status = AssetStatus.ACTIVE
        )

        return fixedAssetRepository.save(asset)
    }

    /**
     * §16.1 — Retrieve a single asset by primary key.
     * Throws [ResourceNotFoundException] with code ASSET_NOT_FOUND if absent.
     */
    @Transactional(readOnly = true)
    fun findById(id: UUID): FixedAsset =
        fixedAssetRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("ASSET_NOT_FOUND", id, "FixedAsset") }

    /**
     * §16.1 — Paginated list of all assets for a given entity.
     */
    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<FixedAsset> =
        fixedAssetRepository.findByEntityId(entityId, pageable)

    /**
     * §16.1 — Paginated list of assets filtered by entity and status.
     */
    @Transactional(readOnly = true)
    fun findByEntityAndStatus(entityId: UUID, status: AssetStatus, pageable: Pageable): Page<FixedAsset> =
        fixedAssetRepository.findByEntityIdAndStatus(entityId, status, pageable)

    /**
     * §16.1 — Flat list of all assets for a given entity (used by depreciation service).
     */
    @Transactional(readOnly = true)
    fun getAssets(entityId: UUID): List<FixedAsset> =
        fixedAssetRepository.findByEntityId(entityId)

    /**
     * §16.3 — Dispose an active fixed asset.
     *
     * Journal entry posted:
     *   DR  Proceeds account         (proceedsAmount)
     *   DR  Accumulated Depreciation (accumulatedDep — to clear contra-asset)
     *   CR  Asset at Cost            (acquisitionCost — to remove asset)
     *   CR  Gain on Disposal         (gainLoss > 0  — credit income)
     *   DR  Loss on Disposal         (gainLoss < 0  — debit income/loss)
     *
     * The Gain/Loss account is resolved as the first OTHER_INCOME account for the entity.
     */
    fun disposeAsset(
        assetId: UUID,
        periodId: UUID,
        disposalDate: LocalDate,
        proceedsAmount: BigDecimal,
        proceedsAccountId: UUID
    ): FixedAsset {
        val asset = findById(assetId)

        if (asset.status != AssetStatus.ACTIVE) {
            throw BusinessRuleViolationException(
                errorCode = "ASSET_NOT_ACTIVE",
                message = "Asset '${asset.assetCode}' must be ACTIVE to be disposed (current: ${asset.status}).",
                context = mapOf(
                    "assetId" to assetId.toString(),
                    "currentStatus" to asset.status.name
                )
            )
        }

        val functionalCurrency = currencyRepository.findByEntityIdAndIsFunctionalTrue(asset.entityId)
            .orElseThrow {
                ValidationException(
                    errorCode = "FUNCTIONAL_CURRENCY_NOT_SET",
                    message = "Functional currency not set for entity ${asset.entityId}."
                )
            }

        // Validate accounts exist (we only need existence, not their running balance)
        accountRepository.findById(asset.accumDepAccountId).orElseThrow {
            ResourceNotFoundException("ACCOUNT_NOT_FOUND", asset.accumDepAccountId, "AccumDepAccount")
        }
        accountRepository.findById(proceedsAccountId).orElseThrow {
            ResourceNotFoundException("ACCOUNT_NOT_FOUND", proceedsAccountId, "ProceedsAccount")
        }

        // Use the asset-level accumulated depreciation field, not the account running balance.
        // The account balance reflects ALL assets sharing that contra-asset account; using it
        // would over-state the cleared amount for a single-asset disposal.
        val accumulatedDep = asset.accumulatedDepreciation.setScale(6, RoundingMode.HALF_EVEN)
        val acquisitionCost = asset.acquisitionCost.setScale(6, RoundingMode.HALF_EVEN)
        val proceeds = proceedsAmount.setScale(6, RoundingMode.HALF_EVEN)
        val nbv = acquisitionCost.subtract(accumulatedDep).setScale(6, RoundingMode.HALF_EVEN)
        val gainLoss = proceeds.subtract(nbv).setScale(6, RoundingMode.HALF_EVEN)

        val gainLossAccount = accountRepository.findAllByEntityId(asset.entityId)
            .find { it.accountSubtype == AccountSubtype.OTHER_INCOME }
            ?: throw ValidationException(
                errorCode = "MISSING_GAIN_LOSS_ACCOUNT",
                message = "No OTHER_INCOME account found for entity ${asset.entityId}. " +
                        "A Gain/Loss on Disposal account (AccountSubtype.OTHER_INCOME) is required."
            )

        val currencyCode = functionalCurrency.currencyCode
        val lines = mutableListOf<CreateJournalLineCommand>()

        // DR Cash / Proceeds
        lines.add(
            CreateJournalLineCommand(
                accountId = proceedsAccountId,
                description = "Disposal proceeds — ${asset.assetName} (${asset.assetCode})",
                debitAmount = proceeds,
                creditAmount = BigDecimal.ZERO,
                currencyCode = currencyCode
            )
        )

        // DR Accumulated Depreciation (clear contra-asset)
        if (accumulatedDep.compareTo(BigDecimal.ZERO) != 0) {
            lines.add(
                CreateJournalLineCommand(
                    accountId = asset.accumDepAccountId,
                    description = "Clear accumulated depreciation — ${asset.assetName} (${asset.assetCode})",
                    debitAmount = accumulatedDep,
                    creditAmount = BigDecimal.ZERO,
                    currencyCode = currencyCode
                )
            )
        }

        // CR Asset at Cost (remove from books)
        lines.add(
            CreateJournalLineCommand(
                accountId = asset.costAccountId,
                description = "Remove asset at cost — ${asset.assetName} (${asset.assetCode})",
                debitAmount = BigDecimal.ZERO,
                creditAmount = acquisitionCost,
                currencyCode = currencyCode
            )
        )

        // CR Gain on Disposal  OR  DR Loss on Disposal
        if (gainLoss.compareTo(BigDecimal.ZERO) != 0) {
            if (gainLoss > BigDecimal.ZERO) {
                lines.add(
                    CreateJournalLineCommand(
                        accountId = gainLossAccount.id!!,
                        description = "Gain on disposal — ${asset.assetName} (${asset.assetCode})",
                        debitAmount = BigDecimal.ZERO,
                        creditAmount = gainLoss,
                        currencyCode = currencyCode
                    )
                )
            } else {
                lines.add(
                    CreateJournalLineCommand(
                        accountId = gainLossAccount.id!!,
                        description = "Loss on disposal — ${asset.assetName} (${asset.assetCode})",
                        debitAmount = gainLoss.abs(),
                        creditAmount = BigDecimal.ZERO,
                        currencyCode = currencyCode
                    )
                )
            }
        }

        val journalCommand = CreateJournalEntryCommand(
            entityId = asset.entityId,
            periodId = periodId,
            transDate = disposalDate,
            description = "Disposal of fixed asset: ${asset.assetName} (${asset.assetCode})",
            sourceType = "FIXED_ASSET_DISPOSAL",
            sourceId = asset.id,
            lines = lines
        )

        val entry = journalService.createDraft(journalCommand)
        journalService.postEntryAsSystem(entry.id)

        asset.status = AssetStatus.DISPOSED
        return fixedAssetRepository.save(asset)
    }

    /**
     * §16.1 — Update mutable metadata for a fixed asset.
     */
    fun updateAsset(id: UUID, command: UpdateAssetCommand): FixedAsset {
        val asset = findById(id)
        asset.assetName = command.assetName
        asset.salvageValue = command.salvageValue.setScale(6, RoundingMode.HALF_EVEN)
        asset.usefulLifeMonths = command.usefulLifeMonths
        asset.depreciationMethod = command.depreciationMethod
        if (command.category != null) asset.category = command.category
        if (command.assignedTo != null) asset.assignedTo = command.assignedTo
        return fixedAssetRepository.save(asset)
    }

    /**
     * §16.2 — Project the depreciation schedule for the next [months] periods.
     * Read-only; does not consume the sequence.
     */
    @Transactional(readOnly = true)
    fun getDepreciationSchedule(assetId: UUID, months: Int = 12): List<DepreciationScheduleEntry> {
        val asset = findById(assetId)
        val entries = mutableListOf<DepreciationScheduleEntry>()
        val depreciableAmount = asset.acquisitionCost.subtract(asset.salvageValue)
            .setScale(6, RoundingMode.HALF_EVEN)
        var accum = asset.accumulatedDepreciation.setScale(6, RoundingMode.HALF_EVEN)
        val today = java.time.LocalDate.now()

        for (i in 1..months) {
            val remaining = depreciableAmount.subtract(accum)
            if (remaining.signum() <= 0) break
            val openingNbv = asset.acquisitionCost.subtract(accum)
            val monthly = when (asset.depreciationMethod) {
                DepreciationMethod.STRAIGHT_LINE ->
                    if (asset.usefulLifeMonths <= 0) BigDecimal.ZERO
                    else depreciableAmount.divide(BigDecimal(asset.usefulLifeMonths), 6, RoundingMode.HALF_EVEN)
                DepreciationMethod.DOUBLE_DECLINING_BALANCE -> {
                    val rate = BigDecimal("2").divide(BigDecimal(asset.usefulLifeMonths), 6, RoundingMode.HALF_EVEN)
                    val uncapped = openingNbv.multiply(rate).setScale(6, RoundingMode.HALF_EVEN)
                    val maxAllowable = openingNbv.subtract(asset.salvageValue).setScale(6, RoundingMode.HALF_EVEN)
                    if (maxAllowable.signum() <= 0) BigDecimal.ZERO else uncapped.min(maxAllowable)
                }
            }
            val dep = monthly.min(remaining).setScale(6, RoundingMode.HALF_EVEN)
            if (dep.signum() <= 0) break
            accum = accum.add(dep)
            val period = today.plusMonths(i.toLong())
            entries.add(DepreciationScheduleEntry(
                period = "${period.year}-${period.monthValue.toString().padStart(2, '0')}",
                openingNbv = openingNbv,
                depreciation = dep,
                accumulatedDepreciation = accum,
                closingNbv = asset.acquisitionCost.subtract(accum),
            ))
        }
        return entries
    }
}

// ---------------------------------------------------------------------------
// Command DTO
// ---------------------------------------------------------------------------

data class CreateAssetCommand(
    val entityId: UUID,
    val periodId: UUID? = null,
    val assetCode: String = "",
    val assetName: String,
    val category: String? = null,
    val assignedTo: String? = null,
    val costAccountId: UUID,
    val accumDepAccountId: UUID,
    val depExpenseAccountId: UUID,
    val acquisitionDate: LocalDate,
    val acquisitionCost: BigDecimal,
    val salvageValue: BigDecimal = BigDecimal.ZERO,
    val usefulLifeMonths: Int,
    val depreciationMethod: DepreciationMethod = DepreciationMethod.STRAIGHT_LINE,
)

data class UpdateAssetCommand(
    val assetName: String,
    val salvageValue: BigDecimal,
    val usefulLifeMonths: Int,
    val depreciationMethod: DepreciationMethod,
    val category: String? = null,
    val assignedTo: String? = null,
)

data class DepreciationScheduleEntry(
    val period: String,
    val openingNbv: BigDecimal,
    val depreciation: BigDecimal,
    val accumulatedDepreciation: BigDecimal,
    val closingNbv: BigDecimal,
)

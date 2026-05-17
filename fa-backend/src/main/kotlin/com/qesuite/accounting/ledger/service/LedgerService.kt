package com.qesuite.accounting.ledger.service

import com.qesuite.accounting.assets.domain.AssetStatus
import com.qesuite.accounting.assets.domain.DepreciationMethod
import com.qesuite.accounting.assets.repository.FixedAssetRepository
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.ledger.domain.LedgerEntry
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.party.repository.CustomerRepository
import com.qesuite.accounting.party.repository.SupplierRepository
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

@Service
class LedgerService(
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val fixedAssetRepository: FixedAssetRepository,
    private val accountRepository: AccountRepository,
    private val customerRepository: CustomerRepository,
    private val supplierRepository: SupplierRepository,
    private val journalService: JournalService,
    private val currencyRepository: CurrencyRepository
) {

    // -------------------------------------------------------------------------
    // §5.2 — Find a single ledger entry by ID
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun findById(id: UUID): LedgerEntry =
        ledgerEntryRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("LEDGER_ENTRY_NOT_FOUND", id, "Ledger Entry") }

    // -------------------------------------------------------------------------
    // §5.3 — Full chronological ledger for an account
    // -------------------------------------------------------------------------

    /**
     * Returns every ledger entry for [accountId], oldest transaction first.
     * Tie-breaking uses createdAt to preserve insertion order within the same day.
     */
    @Transactional(readOnly = true)
    fun getEntriesByAccount(accountId: UUID): List<LedgerEntry> =
        ledgerEntryRepository.findByAccountIdOrderByTransDateAscCreatedAtAsc(accountId)

    // -------------------------------------------------------------------------
    // §5.4 — T-Account view (split debits / credits over a date range)
    // -------------------------------------------------------------------------

    /**
     * Returns a [TAccountView] populated from database-filtered rows.
     * No client-side filtering — all predicates are pushed into the SQL query.
     */
    @Transactional(readOnly = true)
    fun getTAccount(accountId: UUID, startDate: LocalDate, endDate: LocalDate): TAccountView {
        val entries = ledgerEntryRepository.findByAccountIdAndDateRange(accountId, startDate, endDate)
        return TAccountView(
            accountId = accountId,
            debits  = entries.filter { it.functionalDebit  > BigDecimal.ZERO },
            credits = entries.filter { it.functionalCredit > BigDecimal.ZERO }
        )
    }

    // -------------------------------------------------------------------------
    // §14.3 — AR sub-ledger (customer subsidiary)
    // -------------------------------------------------------------------------

    /**
     * Returns all general-ledger entries posted to the customer's default AR account,
     * chronologically ordered. This constitutes the AR subsidiary ledger for the customer.
     *
     * @param customerId  The customer whose AR sub-ledger is requested.
     * @param entityId    Tenant context — used for ResourceNotFoundException message clarity.
     * @throws ResourceNotFoundException if the customer does not exist.
     * @throws ValidationException if the customer has no default AR account mapped.
     */
    @Transactional(readOnly = true)
    fun getCustomerSubsidiary(customerId: UUID, entityId: UUID): List<LedgerEntry> {
        val customer = customerRepository.findById(customerId)
            .orElseThrow { ResourceNotFoundException("CUSTOMER_NOT_FOUND", customerId, "Customer") }

        val arAccountId = customer.defaultArAccountId
            ?: throw ValidationException(
                errorCode = "CUSTOMER_AR_ACCOUNT_NOT_SET",
                message   = "Customer ${customer.customerCode} has no default AR account configured. " +
                            "Set defaultArAccountId on the customer master record."
            )

        return ledgerEntryRepository.findByAccountIdOrderByTransDateAscCreatedAtAsc(arAccountId)
    }

    // -------------------------------------------------------------------------
    // §14.3 — AP sub-ledger (supplier subsidiary)
    // -------------------------------------------------------------------------

    /**
     * Returns all general-ledger entries posted to the supplier's default AP account,
     * chronologically ordered. This constitutes the AP subsidiary ledger for the supplier.
     *
     * @param supplierId  The supplier whose AP sub-ledger is requested.
     * @param entityId    Tenant context — used for ResourceNotFoundException message clarity.
     * @throws ResourceNotFoundException if the supplier does not exist.
     * @throws ValidationException if the supplier has no default AP account mapped.
     */
    @Transactional(readOnly = true)
    fun getSupplierSubsidiary(supplierId: UUID, entityId: UUID): List<LedgerEntry> {
        val supplier = supplierRepository.findById(supplierId)
            .orElseThrow { ResourceNotFoundException("SUPPLIER_NOT_FOUND", supplierId, "Supplier") }

        val apAccountId = supplier.defaultApAccountId
            ?: throw ValidationException(
                errorCode = "SUPPLIER_AP_ACCOUNT_NOT_SET",
                message   = "Supplier ${supplier.supplierCode} has no default AP account configured. " +
                            "Set defaultApAccountId on the supplier master record."
            )

        return ledgerEntryRepository.findByAccountIdOrderByTransDateAscCreatedAtAsc(apAccountId)
    }

    // -------------------------------------------------------------------------
    // §11.1 — Forward depreciation schedule for a fixed asset
    // -------------------------------------------------------------------------

    /**
     * Generates a forward-looking depreciation schedule from the asset's acquisition date
     * until the net book value reaches salvage value or [usefulLifeMonths] periods elapse.
     *
     * - STRAIGHT_LINE:          monthly charge = (cost − salvage) / usefulLifeMonths
     * - DOUBLE_DECLINING_BALANCE: monthly charge = openingNBV × (2 / usefulLifeMonths),
     *                            capped so closingNBV never falls below salvage value.
     *
     * All amounts are rounded to scale 6 using HALF_EVEN (banker's rounding) per §3.2.
     *
     * @throws ResourceNotFoundException if the asset does not exist.
     */
    @Transactional(readOnly = true)
    fun getAssetSchedule(assetId: UUID): List<DepreciationScheduleEntry> {
        val asset = fixedAssetRepository.findById(assetId)
            .orElseThrow { ResourceNotFoundException("ASSET_NOT_FOUND", assetId, "Fixed Asset") }

        val salvage         = asset.salvageValue.setScale(6, RoundingMode.HALF_EVEN)
        val cost            = asset.acquisitionCost.setScale(6, RoundingMode.HALF_EVEN)
        val totalMonths     = asset.usefulLifeMonths
        val depreciableBase = cost.subtract(salvage)

        // STRAIGHT_LINE: constant monthly charge computed once.
        val slMonthlyCharge: BigDecimal = if (asset.depreciationMethod == DepreciationMethod.STRAIGHT_LINE) {
            depreciableBase.divide(BigDecimal(totalMonths), 6, RoundingMode.HALF_EVEN)
        } else {
            BigDecimal.ZERO
        }

        // DOUBLE_DECLINING_BALANCE: annual rate = 2 / usefulLifeMonths (already monthly).
        val ddbRate: BigDecimal = if (asset.depreciationMethod == DepreciationMethod.DOUBLE_DECLINING_BALANCE) {
            BigDecimal("2.0").divide(BigDecimal(totalMonths), 6, RoundingMode.HALF_EVEN)
        } else {
            BigDecimal.ZERO
        }

        val schedule = mutableListOf<DepreciationScheduleEntry>()
        var openingNbv          = cost
        var accumulatedDep      = BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)
        var periodDate          = asset.acquisitionDate.plusMonths(1).withDayOfMonth(1)  // end of first full month

        for (period in 1..totalMonths) {
            // Stop if already at or below salvage (can happen after prior DDB period).
            if (openingNbv <= salvage) break

            val rawCharge: BigDecimal = when (asset.depreciationMethod) {
                DepreciationMethod.STRAIGHT_LINE           -> slMonthlyCharge
                DepreciationMethod.DOUBLE_DECLINING_BALANCE -> openingNbv.multiply(ddbRate)
                    .setScale(6, RoundingMode.HALF_EVEN)
            }

            // Cap: closing NBV must not drop below salvage.
            val charge      = rawCharge.min(openingNbv.subtract(salvage)).setScale(6, RoundingMode.HALF_EVEN)
            val closingNbv  = openingNbv.subtract(charge).setScale(6, RoundingMode.HALF_EVEN)
            accumulatedDep  = accumulatedDep.add(charge).setScale(6, RoundingMode.HALF_EVEN)

            schedule.add(
                DepreciationScheduleEntry(
                    periodNumber             = period,
                    date                     = periodDate,
                    openingNbv               = openingNbv,
                    depreciationCharge       = charge,
                    closingNbv               = closingNbv,
                    accumulatedDepreciation  = accumulatedDep
                )
            )

            openingNbv  = closingNbv
            periodDate  = periodDate.plusMonths(1)

            // Early exit once fully depreciated.
            if (closingNbv <= salvage) break
        }

        return schedule
    }

    // -------------------------------------------------------------------------
    // §11.2 — Post a single depreciation journal entry for one asset
    // -------------------------------------------------------------------------

    /**
     * Posts one month of depreciation for the specified asset.
     *
     * The method:
     * 1. Validates the asset is ACTIVE.
     * 2. Validates a periodId is set (required to anchor the journal entry to a period).
     * 3. Resolves the entity's functional currency.
     * 4. Calculates the monthly depreciation charge using the same algorithm as
     *    [DepreciationService.runDepreciation] — straight-line or DDB from live book value.
     * 5. Creates and immediately posts a balanced journal entry:
     *      DR depExpenseAccountId
     *      CR accumDepAccountId
     *
     * @throws ResourceNotFoundException if the asset does not exist.
     * @throws ValidationException       if the asset is not ACTIVE or has no periodId.
     */
    @Transactional
    fun postDepreciation(assetId: UUID) {
        val asset = fixedAssetRepository.findById(assetId)
            .orElseThrow { ResourceNotFoundException("ASSET_NOT_FOUND", assetId, "Fixed Asset") }

        if (asset.status != AssetStatus.ACTIVE) {
            throw ValidationException(
                errorCode = "ASSET_NOT_ACTIVE",
                message   = "Cannot post depreciation for asset ${asset.assetCode}: status is ${asset.status}. " +
                            "Only ACTIVE assets may be depreciated."
            )
        }

        val periodId = asset.periodId
            ?: throw ValidationException(
                errorCode = "ASSET_NO_PERIOD",
                message   = "Asset ${asset.assetCode} has no accounting period set (periodId is null). " +
                            "Assign the asset to the current open period before posting depreciation."
            )

        // Resolve functional currency — required for journal line currencyCode.
        val functionalCurrency = currencyRepository.findByEntityIdAndIsFunctionalTrue(asset.entityId)
            .orElseThrow {
                ValidationException(
                    errorCode = "FUNCTIONAL_CURRENCY_NOT_SET",
                    message   = "No functional currency configured for entity ${asset.entityId}."
                )
            }

        // Calculate monthly depreciation amount.
        // STRAIGHT_LINE: (cost − salvage) / usefulLifeMonths
        // DOUBLE_DECLINING_BALANCE: current NBV × (2 / usefulLifeMonths)
        //   NBV is derived from the live account balances so it reflects all prior postings.
        val amount: BigDecimal = when (asset.depreciationMethod) {
            DepreciationMethod.STRAIGHT_LINE -> {
                val depreciableAmount = asset.acquisitionCost.subtract(asset.salvageValue)
                depreciableAmount.divide(BigDecimal(asset.usefulLifeMonths), 6, RoundingMode.HALF_EVEN)
            }
            DepreciationMethod.DOUBLE_DECLINING_BALANCE -> {
                val costAccount     = accountRepository.findById(asset.costAccountId)
                    .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", asset.costAccountId, "Account") }
                val accumDepAccount = accountRepository.findById(asset.accumDepAccountId)
                    .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", asset.accumDepAccountId, "Account") }

                val bookValue = costAccount.currentBalance.subtract(accumDepAccount.currentBalance)
                val rate      = BigDecimal("2.0").divide(BigDecimal(asset.usefulLifeMonths), 6, RoundingMode.HALF_EVEN)
                bookValue.multiply(rate).setScale(6, RoundingMode.HALF_EVEN)
            }
        }

        if (amount <= BigDecimal.ZERO) {
            throw ValidationException(
                errorCode = "DEPRECIATION_AMOUNT_ZERO",
                message   = "Computed depreciation amount for asset ${asset.assetCode} is zero or negative ($amount). " +
                            "The asset may already be fully depreciated."
            )
        }

        // Build and post the balanced journal entry.
        val command = CreateJournalEntryCommand(
            entityId    = asset.entityId,
            periodId    = periodId,
            transDate   = LocalDate.now(),
            description = "Monthly depreciation — ${asset.assetName} (${asset.assetCode})",
            sourceType  = "FIXED_ASSET",
            sourceId    = asset.id,
            lines       = listOf(
                CreateJournalLineCommand(
                    accountId    = asset.depExpenseAccountId,
                    description  = "Depreciation expense — ${asset.assetName}",
                    debitAmount  = amount,
                    currencyCode = functionalCurrency.currencyCode
                ),
                CreateJournalLineCommand(
                    accountId    = asset.accumDepAccountId,
                    description  = "Accumulated depreciation — ${asset.assetName}",
                    creditAmount = amount,
                    currencyCode = functionalCurrency.currencyCode
                )
            )
        )

        val entry = journalService.createDraft(command)
        journalService.postEntryAsSystem(entry.id)
    }
}

// ---------------------------------------------------------------------------
// View models
// ---------------------------------------------------------------------------

data class TAccountView(
    val accountId: UUID,
    val debits: List<LedgerEntry>,
    val credits: List<LedgerEntry>
)

/**
 * §11.1 — One row in a forward-looking asset depreciation schedule.
 *
 * @property periodNumber            1-based period index within the asset's useful life.
 * @property date                    The last day of the depreciation period (month-end).
 * @property openingNbv              Net book value at the start of this period.
 * @property depreciationCharge      The charge recognised in this period (scale 6, HALF_EVEN).
 * @property closingNbv              Net book value after the charge (never below salvage value).
 * @property accumulatedDepreciation Running total of all charges from period 1 to this period.
 */
data class DepreciationScheduleEntry(
    val periodNumber: Int,
    val date: LocalDate,
    val openingNbv: BigDecimal,
    val depreciationCharge: BigDecimal,
    val closingNbv: BigDecimal,
    val accumulatedDepreciation: BigDecimal
)

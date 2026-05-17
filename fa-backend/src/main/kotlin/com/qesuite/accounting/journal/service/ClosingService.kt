package com.qesuite.accounting.journal.service

import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.repository.PeriodRepository
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

/**
 * §8 — Period-End Closing Service
 * Executes the four-step closing process per IFRS standards.
 */
@Service
class ClosingService(
    private val journalService: JournalService,
    private val accountRepository: AccountRepository,
    private val periodService: PeriodService,
    private val periodRepository: PeriodRepository,
    private val currencyRepository: CurrencyRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    /**
     * Returns the live net balance for an account within a period.
     * For CREDIT-normal accounts (Revenue): net = credits − debits (positive = income).
     * For DEBIT-normal accounts (Expense): net = debits − credits (positive = cost).
     * Returns the raw signed net so callers can determine direction for JE lines.
     */
    private fun liveAccountBalance(accountId: UUID, periodStart: LocalDate, periodEnd: LocalDate): BigDecimal {
        val debits  = ledgerEntryRepository.sumFunctionalDebitsRange(accountId, periodStart, periodEnd)  ?: BigDecimal.ZERO
        val credits = ledgerEntryRepository.sumFunctionalCreditsRange(accountId, periodStart, periodEnd) ?: BigDecimal.ZERO
        // Return credits − debits (CREDIT-normal perspective); callers use abs() for expense amounts
        return credits.subtract(debits).setScale(6, RoundingMode.HALF_EVEN)
    }

    /**
     * §8.1 — Full period-end close:
     * 1. Close Revenues → Income Summary
     * 2. Close Expenses → Income Summary
     * 3. Close Income Summary → Retained Earnings
     * 4. Close Dividends → Retained Earnings
     */
    @Transactional
    @Auditable(action = AuditAction.CLOSE, resourceType = "ACCOUNTING_PERIOD")
    fun runClosing(
        entityId: UUID,
        @AuditResourceId periodId: UUID,
        closingDate: LocalDate = LocalDate.now(),
        retainedEarningsAccountId: UUID? = null,
        incomeSummaryAccountId: UUID? = null
    ) {
        val functionalCurrency = currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId)
            .orElseThrow { ValidationException("FUNCTIONAL_CURRENCY_NOT_SET", "Functional currency not set for entity $entityId.") }
        val functionalCurrencyCode = functionalCurrency.currencyCode

        // Load the period so we have the real start/end dates for live ledger queries
        val period = periodRepository.findById(periodId)
            .orElseThrow { ValidationException("PERIOD_NOT_FOUND", "Period $periodId not found.") }

        val accounts = accountRepository.findAllByEntityId(entityId)

        // Use live ledger balances instead of cached account.currentBalance
        // Revenue: CREDIT-normal → liveAccountBalance returns credits−debits (positive = revenue earned)
        val revenueAccounts = accounts.filter { it.accountType == AccountType.REVENUE }
        val revenueLines = revenueAccounts.filter {
            liveAccountBalance(it.id, period.startDate, period.endDate).compareTo(BigDecimal.ZERO) != 0
        }

        // Expense: DEBIT-normal → liveAccountBalance returns credits−debits (negative = expense incurred)
        // Use abs() for the positive expense amount in JE lines
        val expenseAccounts = accounts.filter { it.accountType == AccountType.EXPENSE }
        val expenseLines = expenseAccounts.filter {
            liveAccountBalance(it.id, period.startDate, period.endDate).compareTo(BigDecimal.ZERO) != 0
        }

        // Step 1 — Close Revenues to Income Summary (or Retained Earnings directly)
        if (revenueLines.isNotEmpty()) {
            val targetId = incomeSummaryAccountId
                ?: accounts.find { it.accountSubtype == AccountSubtype.RETAINED_EARNINGS }?.id
                ?: throw ValidationException("MISSING_RETAINED_EARNINGS", "No Retained Earnings account found for entity $entityId")

            val revCommand = CreateJournalEntryCommand(
                entityId = entityId,
                periodId = periodId,
                transDate = closingDate,
                description = "Period-End: Close Revenues",
                sourceType = "CLOSING",
                lines = revenueLines.map {
                    val balance = liveAccountBalance(it.id, period.startDate, period.endDate)
                    // Revenue has CREDIT normal balance; to close, debit each revenue account
                    CreateJournalLineCommand(it.id, "Close ${it.accountName}", debitAmount = balance, currencyCode = it.currencyCode)
                } + CreateJournalLineCommand(
                    targetId, "Total Revenue",
                    creditAmount = revenueLines.sumOf { liveAccountBalance(it.id, period.startDate, period.endDate) }
                        .setScale(6, RoundingMode.HALF_EVEN),
                    currencyCode = functionalCurrencyCode
                )
            )
            journalService.postEntryAsSystem(journalService.createEntry(revCommand).id)
        }

        // Step 2 — Close Expenses to Income Summary
        if (expenseLines.isNotEmpty()) {
            val targetId = incomeSummaryAccountId
                ?: accounts.find { it.accountSubtype == AccountSubtype.RETAINED_EARNINGS }?.id
                ?: throw ValidationException("MISSING_RETAINED_EARNINGS", "No Retained Earnings account found for entity $entityId")

            val expCommand = CreateJournalEntryCommand(
                entityId = entityId,
                periodId = periodId,
                transDate = closingDate,
                description = "Period-End: Close Expenses",
                sourceType = "CLOSING",
                lines = expenseLines.map {
                    val balance = liveAccountBalance(it.id, period.startDate, period.endDate).abs()
                    // Expense has DEBIT normal balance; to close, credit each expense account
                    CreateJournalLineCommand(it.id, "Close ${it.accountName}", creditAmount = balance, currencyCode = it.currencyCode)
                } + CreateJournalLineCommand(
                    targetId, "Total Expenses",
                    debitAmount = expenseLines.sumOf { liveAccountBalance(it.id, period.startDate, period.endDate).abs() }
                        .setScale(6, RoundingMode.HALF_EVEN),
                    currencyCode = functionalCurrencyCode
                )
            )
            journalService.postEntryAsSystem(journalService.createEntry(expCommand).id)
        }

        // Step 3 — If Income Summary account provided, close it to Retained Earnings.
        // FIX B3: auto-discover RE account if retainedEarningsAccountId is null rather than silently skipping.
        if (incomeSummaryAccountId != null) {
            val retainedId = retainedEarningsAccountId
                ?: accounts.find { it.accountSubtype == AccountSubtype.RETAINED_EARNINGS }?.id
                ?: throw ValidationException(
                    "MISSING_RETAINED_EARNINGS",
                    "No Retained Earnings account found and none provided. " +
                    "Provide retainedEarningsAccountId or ensure a RETAINED_EARNINGS account exists."
                )

            val totalRevenueLive = revenueLines.sumOf { liveAccountBalance(it.id, period.startDate, period.endDate) }
                .setScale(6, RoundingMode.HALF_EVEN)
            val totalExpenseLive = expenseLines.sumOf { liveAccountBalance(it.id, period.startDate, period.endDate).abs() }
                .setScale(6, RoundingMode.HALF_EVEN)
            val netIncome = totalRevenueLive.subtract(totalExpenseLive).setScale(6, RoundingMode.HALF_EVEN)

            if (netIncome.compareTo(BigDecimal.ZERO) != 0) {
                val summaryCommand = CreateJournalEntryCommand(
                    entityId = entityId,
                    periodId = periodId,
                    transDate = closingDate,
                    description = "Period-End: Transfer Net Income to Retained Earnings",
                    sourceType = "CLOSING",
                    lines = listOf(
                        CreateJournalLineCommand(
                            incomeSummaryAccountId, "Close Income Summary",
                            debitAmount  = if (netIncome > BigDecimal.ZERO) netIncome else BigDecimal.ZERO,
                            creditAmount = if (netIncome < BigDecimal.ZERO) netIncome.abs() else BigDecimal.ZERO,
                            currencyCode = functionalCurrencyCode
                        ),
                        CreateJournalLineCommand(
                            retainedId, "Transfer to Retained Earnings",
                            debitAmount  = if (netIncome < BigDecimal.ZERO) netIncome.abs() else BigDecimal.ZERO,
                            creditAmount = if (netIncome > BigDecimal.ZERO) netIncome else BigDecimal.ZERO,
                            currencyCode = functionalCurrencyCode
                        )
                    )
                )
                journalService.postEntryAsSystem(journalService.createEntry(summaryCommand).id)
            }
        }

        // Step 4 — Close Dividends to Retained Earnings
        val retainedId = retainedEarningsAccountId
            ?: accounts.find { it.accountSubtype == AccountSubtype.RETAINED_EARNINGS }?.id
        if (retainedId != null) {
            val dividendLines = accounts.filter {
                it.accountSubtype == AccountSubtype.DIVIDENDS_DRAWINGS &&
                liveAccountBalance(it.id, period.startDate, period.endDate).compareTo(BigDecimal.ZERO) != 0
            }
            if (dividendLines.isNotEmpty()) {
                val divCommand = CreateJournalEntryCommand(
                    entityId = entityId,
                    periodId = periodId,
                    transDate = closingDate,
                    description = "Period-End: Close Dividends to Retained Earnings",
                    sourceType = "CLOSING",
                    lines = dividendLines.map {
                        val balance = liveAccountBalance(it.id, period.startDate, period.endDate).abs()
                        CreateJournalLineCommand(it.id, "Close ${it.accountName}", creditAmount = balance, currencyCode = it.currencyCode)
                    } + CreateJournalLineCommand(
                        retainedId, "Transfer Dividends",
                        debitAmount = dividendLines.sumOf { liveAccountBalance(it.id, period.startDate, period.endDate).abs() }
                            .setScale(6, RoundingMode.HALF_EVEN),
                        currencyCode = functionalCurrencyCode
                    )
                )
                journalService.postEntryAsSystem(journalService.createEntry(divCommand).id)
            }
        }

        periodService.closePeriod(periodId)
    }

    /**
     * §8.0 — Dry-run preview: computes what closing entries would be posted without writing anything.
     */
    @Transactional(readOnly = true)
    fun previewClosing(entityId: UUID, periodId: UUID): ClosingPreview {
        val period = periodRepository.findById(periodId)
            .orElseThrow { ValidationException("PERIOD_NOT_FOUND", "Period $periodId not found.") }

        val accounts = accountRepository.findAllByEntityId(entityId)

        val revenueAccounts = accounts.filter { it.accountType == AccountType.REVENUE }
        val expenseAccounts = accounts.filter { it.accountType == AccountType.EXPENSE }
        val dividendAccounts = accounts.filter { it.accountSubtype == AccountSubtype.DIVIDENDS_DRAWINGS }

        val totalRevenue = revenueAccounts.sumOf {
            liveAccountBalance(it.id, period.startDate, period.endDate)
        }.setScale(2, RoundingMode.HALF_EVEN)

        val totalExpenses = expenseAccounts.sumOf {
            liveAccountBalance(it.id, period.startDate, period.endDate).abs()
        }.setScale(2, RoundingMode.HALF_EVEN)

        val netIncome = totalRevenue.subtract(totalExpenses)

        val revenueLines = revenueAccounts
            .filter { liveAccountBalance(it.id, period.startDate, period.endDate).compareTo(BigDecimal.ZERO) != 0 }
            .map { PreviewLine(it.accountCode, it.accountName, liveAccountBalance(it.id, period.startDate, period.endDate), null) }

        val expenseLines = expenseAccounts
            .filter { liveAccountBalance(it.id, period.startDate, period.endDate).compareTo(BigDecimal.ZERO) != 0 }
            .map { PreviewLine(it.accountCode, it.accountName, null, liveAccountBalance(it.id, period.startDate, period.endDate).abs()) }

        val dividendLines = dividendAccounts
            .filter { liveAccountBalance(it.id, period.startDate, period.endDate).compareTo(BigDecimal.ZERO) != 0 }
            .map { PreviewLine(it.accountCode, it.accountName, null, liveAccountBalance(it.id, period.startDate, period.endDate).abs()) }

        return ClosingPreview(
            periodId      = periodId,
            periodCode    = period.periodName,
            totalRevenue  = totalRevenue,
            totalExpenses = totalExpenses,
            netIncome     = netIncome,
            revenueLines  = revenueLines,
            expenseLines  = expenseLines,
            dividendLines = dividendLines
        )
    }

    /**
     * §8.2 — Reopen a closed period with mandatory audit reason.
     */
    @Transactional
    @Auditable(action = AuditAction.REOPEN, resourceType = "ACCOUNTING_PERIOD")
    fun reopenPeriod(@AuditResourceId periodId: UUID, reason: String) {
        periodService.transitionPeriod(periodId, PeriodStatus.REOPENED)
    }
}

data class ClosingPreview(
    val periodId: UUID,
    val periodCode: String,
    val totalRevenue: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
    val revenueLines: List<PreviewLine>,
    val expenseLines: List<PreviewLine>,
    val dividendLines: List<PreviewLine>
)

data class PreviewLine(
    val accountCode: String,
    val accountName: String,
    val debit: BigDecimal?,
    val credit: BigDecimal?
)

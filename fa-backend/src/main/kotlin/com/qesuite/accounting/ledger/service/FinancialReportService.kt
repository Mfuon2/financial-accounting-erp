package com.qesuite.accounting.ledger.service

import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.*

data class FinancialReport(
    val entityId: UUID,
    val reportDate: java.time.LocalDate,
    val sections: List<ReportSection>,
    val totalAssets: BigDecimal = BigDecimal.ZERO,
    val totalLiabilities: BigDecimal = BigDecimal.ZERO,
    val totalEquity: BigDecimal = BigDecimal.ZERO,
    val netIncome: BigDecimal = BigDecimal.ZERO
)

data class ReportSection(
    val name: String,
    val lines: List<ReportLine>,
    val total: BigDecimal
)

data class ReportLine(
    val accountCode: String,
    val accountName: String,
    val balance: BigDecimal
)

@Service
class FinancialReportService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    private val log = LoggerFactory.getLogger(FinancialReportService::class.java)

    /**
     * §8.3 — Balance Sheet (IAS 1)
     *
     * The accounting equation (Assets = Liabilities + Equity) is validated after
     * including current-year net income in equity.  A mismatch during an open period
     * is expected (P&L not yet closed to Retained Earnings), so we log a WARNING
     * rather than throwing.  The strict enforcement at formal period-close is the
     * responsibility of FinancialStatementService.
     */
    @Transactional(readOnly = true)
    fun generateBalanceSheet(entityId: UUID, date: java.time.LocalDate): FinancialReport {
        val accounts = accountRepository.findAllByEntityId(entityId)

        fun pointInTimeBalance(account: com.qesuite.accounting.coa.domain.Account): BigDecimal {
            val debits  = ledgerEntryRepository.sumFunctionalDebits(account.id, date) ?: BigDecimal.ZERO
            val credits = ledgerEntryRepository.sumFunctionalCredits(account.id, date) ?: BigDecimal.ZERO
            return if (account.normalBalance == NormalBalance.CREDIT)
                credits.subtract(debits).setScale(6, RoundingMode.HALF_EVEN)
            else
                debits.subtract(credits).setScale(6, RoundingMode.HALF_EVEN)
        }

        val assets      = accounts.filter { it.accountType == AccountType.ASSET }
        val liabilities = accounts.filter { it.accountType == AccountType.LIABILITY }
        val equity      = accounts.filter { it.accountType == AccountType.EQUITY }

        val totalAssets      = assets.sumOf      { pointInTimeBalance(it) }.setScale(6, RoundingMode.HALF_EVEN)
        val totalLiabilities = liabilities.sumOf { pointInTimeBalance(it) }.setScale(6, RoundingMode.HALF_EVEN)
        val totalEquity      = equity.sumOf      { pointInTimeBalance(it) }.setScale(6, RoundingMode.HALF_EVEN)

        // Include current year net income in equity for equation check
        val fiscalStart     = date.withDayOfYear(1)
        val pnl             = generateProfitAndLoss(entityId, fiscalStart, date)
        val adjustedEquity  = totalEquity.add(pnl.netIncome).setScale(6, RoundingMode.HALF_EVEN)
        val diff = totalAssets.subtract(totalLiabilities.add(adjustedEquity)).abs()
        if (diff.compareTo(BigDecimal("0.000001")) > 0) {
            log.warn("balance.sheet: equation imbalance for entity={} asOf={} diff={}", entityId, date, diff)
        }

        return FinancialReport(
            entityId    = entityId,
            reportDate  = date,
            sections    = listOf(
                ReportSection("Assets",      assets.map      { ReportLine(it.accountCode, it.accountName, pointInTimeBalance(it)) }, totalAssets),
                ReportSection("Liabilities", liabilities.map { ReportLine(it.accountCode, it.accountName, pointInTimeBalance(it)) }, totalLiabilities),
                ReportSection("Equity",      equity.map      { ReportLine(it.accountCode, it.accountName, pointInTimeBalance(it)) }, totalEquity)
            ),
            totalAssets      = totalAssets,
            totalLiabilities = totalLiabilities,
            totalEquity      = totalEquity,
            netIncome        = pnl.netIncome
        )
    }

    /**
     * §8.2 — Profit & Loss (IAS 1)
     */
    @Transactional(readOnly = true)
    fun generateProfitAndLoss(
        entityId: UUID,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): FinancialReport {
        val accounts = accountRepository.findAllByEntityId(entityId)
            .filter { it.accountType == AccountType.REVENUE || it.accountType == AccountType.EXPENSE }

        val revenues = accounts.filter { it.accountType == AccountType.REVENUE }
        val expenses = accounts.filter { it.accountType == AccountType.EXPENSE }

        fun accountNetBalance(account: com.qesuite.accounting.coa.domain.Account): BigDecimal {
            val debits  = ledgerEntryRepository.sumFunctionalDebitsRange(account.id, startDate, endDate)
                ?: BigDecimal.ZERO
            val credits = ledgerEntryRepository.sumFunctionalCreditsRange(account.id, startDate, endDate)
                ?: BigDecimal.ZERO
            return if (account.normalBalance == NormalBalance.CREDIT)
                credits.subtract(debits).setScale(6, RoundingMode.HALF_EVEN)
            else
                debits.subtract(credits).setScale(6, RoundingMode.HALF_EVEN)
        }

        val totalRevenue = revenues.sumOf { accountNetBalance(it) }.setScale(6, RoundingMode.HALF_EVEN)
        val totalExpense = expenses.sumOf { accountNetBalance(it) }.setScale(6, RoundingMode.HALF_EVEN)
        val netIncome    = totalRevenue.subtract(totalExpense).setScale(6, RoundingMode.HALF_EVEN)

        return FinancialReport(
            entityId   = entityId,
            reportDate = endDate,
            sections   = listOf(
                ReportSection("Revenue",  revenues.map { ReportLine(it.accountCode, it.accountName, accountNetBalance(it)) }, totalRevenue),
                ReportSection("Expenses", expenses.map { ReportLine(it.accountCode, it.accountName, accountNetBalance(it)) }, totalExpense)
            ),
            netIncome  = netIncome
        )
    }
}

package com.qesuite.accounting.ledger.service

import com.qesuite.accounting.coa.domain.IfrsCategory
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Service
class FinancialStatementService(
    private val trialBalanceService: TrialBalanceService,
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    @Transactional(readOnly = true)
    fun getBalanceSheet(entityId: UUID, asOfDate: LocalDate? = null): BalanceSheetReport {
        val date = asOfDate ?: LocalDate.now()
        val accounts = accountRepository.findAllByEntityId(entityId)
        
        val totalAssets = accounts.filter { 
            it.ifrsCategory == IfrsCategory.CURRENT_ASSETS || it.ifrsCategory == IfrsCategory.NON_CURRENT_ASSETS 
        }.sumOf { getAccountBalance(it.id, date) }

        val totalLiabilities = accounts.filter { 
            it.ifrsCategory == IfrsCategory.CURRENT_LIABILITIES || it.ifrsCategory == IfrsCategory.NON_CURRENT_LIABILITIES 
        }.sumOf { getAccountBalance(it.id, date) }

        val totalEquity = accounts.filter { 
            it.ifrsCategory == IfrsCategory.EQUITY 
        }.sumOf { getAccountBalance(it.id, date) }

        return BalanceSheetReport(entityId, date, totalAssets, totalLiabilities, totalEquity)
    }

    @Transactional(readOnly = true)
    fun getProfitLoss(entityId: UUID, startDate: LocalDate, endDate: LocalDate): ProfitLossReport {
        val accounts = accountRepository.findAllByEntityId(entityId)
        
        val totalRevenue = accounts.filter { it.ifrsCategory == IfrsCategory.REVENUE }
            .sumOf { getAccountBalanceRange(it.id, startDate, endDate) }

        val totalExpenses = accounts.filter { 
            it.ifrsCategory == IfrsCategory.COST_OF_SALES || 
            it.ifrsCategory == IfrsCategory.OPERATING_EXPENSES ||
            it.ifrsCategory == IfrsCategory.FINANCE_COSTS ||
            it.ifrsCategory == IfrsCategory.TAX_EXPENSE ||
            it.ifrsCategory == IfrsCategory.OTHER_INCOME_EXPENSE
        }.sumOf { getAccountBalanceRange(it.id, startDate, endDate) }

        val netIncome = totalRevenue.subtract(totalExpenses)

        return ProfitLossReport(entityId, startDate, endDate, totalRevenue, totalExpenses, netIncome)
    }

    private fun getAccountBalance(accountId: UUID, asOfDate: LocalDate): BigDecimal {
        val account = accountRepository.findById(accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", accountId, "Account") }
        val debits  = ledgerEntryRepository.sumFunctionalDebits(accountId, asOfDate)  ?: BigDecimal.ZERO
        val credits = ledgerEntryRepository.sumFunctionalCredits(accountId, asOfDate) ?: BigDecimal.ZERO

        return if (account.normalBalance == NormalBalance.DEBIT) {
            debits.subtract(credits)
        } else {
            credits.subtract(debits)
        }
    }

    private fun getAccountBalanceRange(accountId: UUID, start: LocalDate, end: LocalDate): BigDecimal {
        val account = accountRepository.findById(accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", accountId, "Account") }
        val debits  = ledgerEntryRepository.sumFunctionalDebitsRange(accountId, start, end)  ?: BigDecimal.ZERO
        val credits = ledgerEntryRepository.sumFunctionalCreditsRange(accountId, start, end) ?: BigDecimal.ZERO

        return if (account.normalBalance == NormalBalance.DEBIT) {
            debits.subtract(credits)
        } else {
            credits.subtract(debits)
        }
    }
}

data class BalanceSheetReport(
    val entityId: UUID,
    val asOfDate: LocalDate,
    val totalAssets: BigDecimal,
    val totalLiabilities: BigDecimal,
    val totalEquity: BigDecimal
)

data class ProfitLossReport(
    val entityId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalRevenue: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal
)

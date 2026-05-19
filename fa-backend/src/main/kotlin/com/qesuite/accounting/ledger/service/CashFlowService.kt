package com.qesuite.accounting.ledger.service

import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.IfrsCategory
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

data class CashFlowReport(
    val entityId: UUID,
    val periodId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val operatingActivities: BigDecimal,
    val investingActivities: BigDecimal,
    val financingActivities: BigDecimal,
    val netChangeInCash: BigDecimal,
    val openingCash: BigDecimal,
    val closingCash: BigDecimal
)

@Service
class CashFlowService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val financialReportService: FinancialReportService
) {

    private val log = LoggerFactory.getLogger(CashFlowService::class.java)

    /**
     * §8.4 — Statement of Cash Flows (IAS 7 Indirect Method)
     *
     * Operating Activities  = Net Income + Non-cash add-backs + Working-capital movements
     * Investing Activities  = Net movement in Non-Current Asset accounts (IAS 7 §16)
     * Financing Activities  = Net movement in Non-Current Liability + Equity accounts (IAS 7 §17)
     */
    @Transactional(readOnly = true)
    fun generateIndirectCashFlow(
        entityId: UUID,
        periodId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): CashFlowReport {

        val accounts = accountRepository.findAllByEntityId(entityId)

        // ─────────────────────────────────────────────────────────────
        // 1. OPERATING ACTIVITIES (IAS 7 §20–§28, indirect method)
        // ─────────────────────────────────────────────────────────────

        // 1a. Start with net income for the period
        val pnlReport = financialReportService.generateProfitAndLoss(entityId, startDate, endDate)
        val netIncome = pnlReport.netIncome.setScale(6, RoundingMode.HALF_EVEN)

        // 1b. Add back non-cash charges: Depreciation (EXPENSE, normal-balance = DEBIT)
        //     Current period net movement = debits − credits within [startDate, endDate]
        val depreciationAccounts = accounts.filter {
            it.accountSubtype == AccountSubtype.DEPRECIATION ||
            it.accountSubtype == AccountSubtype.AMORTISATION
        }
        val depreciation = depreciationAccounts
            .sumOf { account ->
                val debits = ledgerEntryRepository
                    .sumFunctionalDebitsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                val credits = ledgerEntryRepository
                    .sumFunctionalCreditsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                // Expense accounts have DEBIT normal balance; net movement = debits - credits
                val movement = if (account.normalBalance == NormalBalance.DEBIT)
                    debits.subtract(credits)
                else
                    credits.subtract(debits)
                movement.setScale(6, RoundingMode.HALF_EVEN)
            }
            .setScale(6, RoundingMode.HALF_EVEN)

        // 1c. Working-capital adjustments (IAS 7 §20)
        //     Receivables (ASSET, DEBIT normal): increase = cash outflow → subtract
        //     Payables (LIABILITY, CREDIT normal): increase = cash inflow → add
        //     We use the period net movement in each account as the proxy for the change.
        val receivableMovement = accounts
            .filter { it.accountSubtype == AccountSubtype.CURRENT_RECEIVABLE }
            .sumOf { account ->
                val debits = ledgerEntryRepository
                    .sumFunctionalDebitsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                val credits = ledgerEntryRepository
                    .sumFunctionalCreditsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                // Net increase in receivables (debit-normal): debits - credits
                debits.subtract(credits).setScale(6, RoundingMode.HALF_EVEN)
            }
            .setScale(6, RoundingMode.HALF_EVEN)

        val payableMovement = accounts
            .filter { it.accountSubtype == AccountSubtype.CURRENT_PAYABLE }
            .sumOf { account ->
                val debits = ledgerEntryRepository
                    .sumFunctionalDebitsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                val credits = ledgerEntryRepository
                    .sumFunctionalCreditsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                // Net increase in payables (credit-normal): credits - debits
                credits.subtract(debits).setScale(6, RoundingMode.HALF_EVEN)
            }
            .setScale(6, RoundingMode.HALF_EVEN)

        // Operating cash flow = Net Income + Depreciation/Amortisation
        //                       − Increase in Receivables + Increase in Payables
        val operatingActivities = netIncome
            .add(depreciation)
            .subtract(receivableMovement)
            .add(payableMovement)
            .setScale(6, RoundingMode.HALF_EVEN)

        log.debug(
            "[CashFlow] entity={} period={} netIncome={} depreciation={} receivableMove={} payableMove={} operating={}",
            entityId, periodId, netIncome, depreciation, receivableMovement, payableMovement, operatingActivities
        )

        // ─────────────────────────────────────────────────────────────
        // 2. INVESTING ACTIVITIES (IAS 7 §16)
        //    Capital expenditure = net movement in NON_CURRENT_ASSETS accounts.
        //    An increase in non-current assets is a cash outflow (negate).
        // ─────────────────────────────────────────────────────────────
        // Exclude accumulated depreciation (contra-asset) accounts — they represent non-cash write-downs,
        // not capital expenditure cash outflows (IAS 7 §16).
        val nonCurrentAssets = accounts.filter {
            it.ifrsCategory == IfrsCategory.NON_CURRENT_ASSETS &&
            it.accountSubtype != AccountSubtype.ACCUMULATED_DEPRECIATION
        }
        val nonCurrentAssetMovement = nonCurrentAssets
            .sumOf { account ->
                val debits = ledgerEntryRepository
                    .sumFunctionalDebitsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                val credits = ledgerEntryRepository
                    .sumFunctionalCreditsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                // Asset accounts are DEBIT normal: net movement = debits - credits
                val movement = if (account.normalBalance == NormalBalance.DEBIT)
                    debits.subtract(credits)
                else
                    credits.subtract(debits)
                movement.setScale(6, RoundingMode.HALF_EVEN)
            }
            .setScale(6, RoundingMode.HALF_EVEN)

        // Net increase in non-current assets = cash outflow → negate
        val investingActivities = nonCurrentAssetMovement.negate().setScale(6, RoundingMode.HALF_EVEN)

        log.debug(
            "[CashFlow] entity={} period={} nonCurrentAssetMovement={} investing={}",
            entityId, periodId, nonCurrentAssetMovement, investingActivities
        )

        // ─────────────────────────────────────────────────────────────
        // 3. FINANCING ACTIVITIES (IAS 7 §17)
        //    Proceeds from / repayments of borrowings: NON_CURRENT_LIABILITIES
        //    Equity issuance / dividends: EQUITY
        //    Net inflow = increase in liabilities or equity (credit-normal).
        // ─────────────────────────────────────────────────────────────
        val financingAccounts = accounts.filter {
            it.ifrsCategory == IfrsCategory.NON_CURRENT_LIABILITIES ||
            it.ifrsCategory == IfrsCategory.EQUITY
        }
        val financingActivities = financingAccounts
            .sumOf { account ->
                val debits = ledgerEntryRepository
                    .sumFunctionalDebitsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                val credits = ledgerEntryRepository
                    .sumFunctionalCreditsRange(account.id, startDate, endDate)
                    ?: BigDecimal.ZERO
                // LIABILITY/EQUITY accounts are CREDIT normal: net movement = credits - debits
                val movement = if (account.normalBalance == NormalBalance.CREDIT)
                    credits.subtract(debits)
                else
                    debits.subtract(credits)
                movement.setScale(6, RoundingMode.HALF_EVEN)
            }
            .setScale(6, RoundingMode.HALF_EVEN)

        log.debug(
            "[CashFlow] entity={} period={} financing={}",
            entityId, periodId, financingActivities
        )

        // ─────────────────────────────────────────────────────────────
        // 4. NET CHANGE IN CASH & OPENING / CLOSING BALANCES
        // ─────────────────────────────────────────────────────────────
        val netChangeInCash = operatingActivities
            .add(investingActivities)
            .add(financingActivities)
            .setScale(6, RoundingMode.HALF_EVEN)

        // Closing cash = current balance held in CASH_AND_EQUIVALENTS accounts
        val cashAccounts = accounts.filter { it.accountSubtype == AccountSubtype.CASH_AND_EQUIVALENTS }
        val closingCash = cashAccounts
            .sumOf { it.currentBalance }
            .setScale(6, RoundingMode.HALF_EVEN)

        // Opening cash (proxy) = closing − net change this period
        val openingCash = closingCash.subtract(netChangeInCash).setScale(6, RoundingMode.HALF_EVEN)

        log.debug(
            "[CashFlow] entity={} period={} netChange={} openingCash={} closingCash={}",
            entityId, periodId, netChangeInCash, openingCash, closingCash
        )

        return CashFlowReport(
            entityId = entityId,
            periodId = periodId,
            startDate = startDate,
            endDate = endDate,
            operatingActivities = operatingActivities,
            investingActivities = investingActivities,
            financingActivities = financingActivities,
            netChangeInCash = netChangeInCash,
            openingCash = openingCash,
            closingCash = closingCash
        )
    }
}

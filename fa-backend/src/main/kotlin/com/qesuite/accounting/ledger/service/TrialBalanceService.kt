package com.qesuite.accounting.ledger.service

import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Service
class TrialBalanceService(
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) {

    @Transactional(readOnly = true)
    fun generateTrialBalance(entityId: UUID, asOfDate: LocalDate? = null): TrialBalanceReport {
        val date = asOfDate ?: LocalDate.now()
        val accounts = accountRepository.findAllByEntityId(entityId)

        val accountById = accounts.associateBy { it.id }
        fun depth(id: UUID, visited: MutableSet<UUID> = mutableSetOf()): Int {
            if (id in visited) return 0
            visited.add(id)
            val acc = accountById[id] ?: return 0
            val parentId = acc.parentAccountId ?: return 0
            return 1 + depth(parentId, visited)
        }

        var rawTotalDebits = BigDecimal.ZERO
        var rawTotalCredits = BigDecimal.ZERO

        val rows = accounts.map { account ->
            val debits  = ledgerEntryRepository.sumFunctionalDebits(account.id, date)  ?: BigDecimal.ZERO
            val credits = ledgerEntryRepository.sumFunctionalCredits(account.id, date) ?: BigDecimal.ZERO

            rawTotalDebits  = rawTotalDebits.add(debits)
            rawTotalCredits = rawTotalCredits.add(credits)

            val netBalance = if (account.normalBalance == NormalBalance.DEBIT) {
                debits.subtract(credits)
            } else {
                credits.subtract(debits)
            }

            // Accounts with abnormal balances (opposite to their normal side) appear on the
            // opposite column. This keeps the displayed TB totals balanced while clearly
            // flagging the anomaly to the reviewer.
            val debitBalance = when {
                account.normalBalance == NormalBalance.DEBIT  && netBalance > BigDecimal.ZERO -> netBalance
                account.normalBalance == NormalBalance.CREDIT && netBalance < BigDecimal.ZERO -> netBalance.negate()
                else -> BigDecimal.ZERO
            }
            val creditBalance = when {
                account.normalBalance == NormalBalance.CREDIT && netBalance > BigDecimal.ZERO -> netBalance
                account.normalBalance == NormalBalance.DEBIT  && netBalance < BigDecimal.ZERO -> netBalance.negate()
                else -> BigDecimal.ZERO
            }

            TrialBalanceRow(
                accountCode   = account.accountCode,
                accountName   = account.accountName,
                isHeader      = account.isHeader,
                depth         = depth(account.id),
                debitBalance  = debitBalance,
                creditBalance = creditBalance
            )
        }

        if (rawTotalDebits.compareTo(rawTotalCredits) != 0) {
            throw ValidationException(
                "TRIAL_BALANCE_FAILURE",
                "Trial balance mismatch: Raw debits ($rawTotalDebits) != Raw credits ($rawTotalCredits)"
            )
        }

        val totalDebits  = rows.sumOf { it.debitBalance }
        val totalCredits = rows.sumOf { it.creditBalance }

        return TrialBalanceReport(
            entityId     = entityId,
            asOfDate     = date,
            rows         = rows,
            totalDebits  = totalDebits,
            totalCredits = totalCredits
        )
    }

    @Transactional(readOnly = true)
    fun generateComparative(
        entityId: UUID,
        asOfDate: LocalDate,
        compareAsOfDate: LocalDate
    ): ComparativeTrialBalanceReport {
        val current = generateTrialBalance(entityId, asOfDate)
        val prior   = generateTrialBalance(entityId, compareAsOfDate)

        val priorByCode = prior.rows.associateBy { it.accountCode }
        val rows = current.rows.map { row ->
            val p = priorByCode[row.accountCode]
            ComparativeRow(
                accountCode    = row.accountCode,
                accountName    = row.accountName,
                isHeader       = row.isHeader,
                depth          = row.depth,
                currentDebit   = row.debitBalance,
                currentCredit  = row.creditBalance,
                priorDebit     = p?.debitBalance  ?: BigDecimal.ZERO,
                priorCredit    = p?.creditBalance ?: BigDecimal.ZERO,
                movementDebit  = row.debitBalance.subtract(p?.debitBalance  ?: BigDecimal.ZERO),
                movementCredit = row.creditBalance.subtract(p?.creditBalance ?: BigDecimal.ZERO)
            )
        }

        return ComparativeTrialBalanceReport(
            entityId             = entityId,
            asOfDate             = asOfDate,
            compareAsOfDate      = compareAsOfDate,
            rows                 = rows,
            currentTotalDebits   = current.totalDebits,
            currentTotalCredits  = current.totalCredits,
            priorTotalDebits     = prior.totalDebits,
            priorTotalCredits    = prior.totalCredits
        )
    }
}

data class TrialBalanceReport(
    val entityId: UUID,
    val asOfDate: LocalDate,
    val rows: List<TrialBalanceRow>,
    val totalDebits: BigDecimal,
    val totalCredits: BigDecimal
)

data class TrialBalanceRow(
    val accountCode: String,
    val accountName: String,
    val isHeader: Boolean,
    val depth: Int,
    val debitBalance: BigDecimal,
    val creditBalance: BigDecimal
)

data class ComparativeTrialBalanceReport(
    val entityId: UUID,
    val asOfDate: LocalDate,
    val compareAsOfDate: LocalDate,
    val rows: List<ComparativeRow>,
    val currentTotalDebits: BigDecimal,
    val currentTotalCredits: BigDecimal,
    val priorTotalDebits: BigDecimal,
    val priorTotalCredits: BigDecimal
)

data class ComparativeRow(
    val accountCode: String,
    val accountName: String,
    val isHeader: Boolean,
    val depth: Int,
    val currentDebit: BigDecimal,
    val currentCredit: BigDecimal,
    val priorDebit: BigDecimal,
    val priorCredit: BigDecimal,
    val movementDebit: BigDecimal,
    val movementCredit: BigDecimal
)

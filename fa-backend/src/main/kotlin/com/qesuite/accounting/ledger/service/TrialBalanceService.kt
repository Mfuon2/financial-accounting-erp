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

            TrialBalanceRow(
                accountCode   = account.accountCode,
                accountName   = account.accountName,
                debitBalance  = if (account.normalBalance == NormalBalance.DEBIT  && netBalance > BigDecimal.ZERO) netBalance else BigDecimal.ZERO,
                creditBalance = if (account.normalBalance == NormalBalance.CREDIT && netBalance > BigDecimal.ZERO) netBalance else BigDecimal.ZERO
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
    val currentDebit: BigDecimal,
    val currentCredit: BigDecimal,
    val priorDebit: BigDecimal,
    val priorCredit: BigDecimal,
    val movementDebit: BigDecimal,
    val movementCredit: BigDecimal
)

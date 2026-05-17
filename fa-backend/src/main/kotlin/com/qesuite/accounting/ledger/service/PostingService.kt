package com.qesuite.accounting.ledger.service

import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.ledger.domain.LedgerEntry
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * §5.1 — Posting Engine
 */
@Service
class PostingService(
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val accountRepository: AccountRepository
) {

    @Transactional
    fun postJournalEntry(entry: JournalEntry) {
        entry.lines.forEach { line ->
            val account = accountRepository.findById(line.accountId)
                .orElseThrow { ValidationException("ACCOUNT_NOT_FOUND", "Account ${line.accountId} not found.") }

            // §5.2 — Calculate running balance
            val newRunningBalance = calculateNewBalance(
                account.currentBalance,
                line.functionalDebit,
                line.functionalCredit,
                account.normalBalance
            )

            // §13.2 — Update Original Currency Balance
            val newOriginalBalance = calculateNewBalance(
                account.originalCurrencyBalance,
                line.debitAmount,
                line.creditAmount,
                account.normalBalance
            )

            val ledgerEntry = LedgerEntry(
                entityId = entry.entityId,
                accountId = account.id,
                journalEntryLineId = line.id,
                transDate = entry.transDate,
                functionalDebit = line.functionalDebit,
                functionalCredit = line.functionalCredit,
                runningBalance = newRunningBalance
            )

            ledgerEntryRepository.save(ledgerEntry)

            // Update Account totals
            account.totalDebits = account.totalDebits.add(line.functionalDebit)
                .setScale(6, RoundingMode.HALF_EVEN)
            account.totalCredits = account.totalCredits.add(line.functionalCredit)
                .setScale(6, RoundingMode.HALF_EVEN)
            account.currentBalance = newRunningBalance.setScale(6, RoundingMode.HALF_EVEN)
            account.originalCurrencyBalance = newOriginalBalance.setScale(6, RoundingMode.HALF_EVEN)

            accountRepository.save(account)
        }
    }

    private fun calculateNewBalance(
        currentBalance: BigDecimal,
        debit: BigDecimal,
        credit: BigDecimal,
        normalBalance: NormalBalance
    ): BigDecimal {
        val result = if (normalBalance == NormalBalance.DEBIT) {
            currentBalance.add(debit).subtract(credit)
        } else {
            currentBalance.add(credit).subtract(debit)
        }
        return result.setScale(6, RoundingMode.HALF_EVEN)
    }
}

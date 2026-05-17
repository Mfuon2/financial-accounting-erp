package com.qesuite.accounting.journal.service

import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Component

/**
 * §4.2 — Double-Entry Validator
 */
@Component
class DoubleEntryValidator {

    fun validate(entry: JournalEntry) {
        if (entry.lines.size < 2) {
            throw ValidationException("INSUFFICIENT_JOURNAL_LINES", "A journal entry must have at least 2 lines.")
        }

        val totalDebits = entry.lines.sumOf { it.debitAmount }
        val totalCredits = entry.lines.sumOf { it.creditAmount }

        // Transaction Currency Balance Check
        if (totalDebits.compareTo(totalCredits) != 0) {
            throw ValidationException(
                "BALANCE_MISMATCH",
                "Total debits ($totalDebits) do not equal total credits ($totalCredits)."
            )
        }

        val functionalDebits = entry.lines.sumOf { it.functionalDebit }
        val functionalCredits = entry.lines.sumOf { it.functionalCredit }

        // Functional Currency Balance Check
        if (functionalDebits.compareTo(functionalCredits) != 0) {
            throw ValidationException(
                "FUNCTIONAL_CURRENCY_BALANCE_MISMATCH",
                "Functional debits ($functionalDebits) do not equal functional credits ($functionalCredits)."
            )
        }
        
        // Ensure no line has both debit and credit, and each line carries a positive amount
        entry.lines.forEach { line ->
            // FIX: use signum() — scale-independent zero check
            val hasDebit  = line.debitAmount.signum() > 0
            val hasCredit = line.creditAmount.signum() > 0

            if (!hasDebit && !hasCredit) {
                throw ValidationException(
                    "INVALID_DEBIT_CREDIT_LINE",
                    "Line for account ${line.accountId}: both debit and credit are zero. " +
                    "Each line must carry a positive debit or credit amount."
                )
            }
            if (hasDebit && hasCredit) {
                throw ValidationException(
                    "INVALID_DEBIT_CREDIT_LINE",
                    "Line for account ${line.accountId}: a line cannot have both a debit and a credit amount."
                )
            }
            if (line.debitAmount.signum() < 0 || line.creditAmount.signum() < 0) {
                throw ValidationException(
                    "INVALID_DEBIT_CREDIT_LINE",
                    "Line for account ${line.accountId}: negative amounts are not permitted on journal lines."
                )
            }
        }
    }
}

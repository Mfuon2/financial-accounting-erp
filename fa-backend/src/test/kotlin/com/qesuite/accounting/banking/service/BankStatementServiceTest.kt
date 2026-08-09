package com.qesuite.accounting.banking.service

import com.qesuite.accounting.banking.domain.BankStatementImport
import com.qesuite.accounting.banking.domain.BankStatementLine
import com.qesuite.accounting.banking.domain.MatchType
import com.qesuite.accounting.banking.domain.ReconciliationStatus
import com.qesuite.accounting.banking.dto.CreateBankStatementImportCommand
import com.qesuite.accounting.banking.dto.BankStatementLineCommand
import com.qesuite.accounting.banking.repository.BankLineMatchRepository
import com.qesuite.accounting.banking.repository.BankStatementImportRepository
import com.qesuite.accounting.banking.repository.BankStatementLineRepository
import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.domain.LedgerEntry
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class BankStatementServiceTest {

    private val bankStatementImportRepository = mockk<BankStatementImportRepository>()
    private val bankStatementLineRepository = mockk<BankStatementLineRepository>()
    private val bankLineMatchRepository = mockk<BankLineMatchRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val ledgerEntryRepository = mockk<LedgerEntryRepository>()
    private val service = BankStatementService(
        bankStatementImportRepository, bankStatementLineRepository, bankLineMatchRepository,
        accountRepository, ledgerEntryRepository,
    )

    private val entityId = UUID.randomUUID()
    private val otherEntityId = UUID.randomUUID()

    private fun bankAccount(
        entityId: UUID = this.entityId,
        isHeader: Boolean = false,
        accountType: AccountType = AccountType.ASSET,
        accountSubtype: AccountSubtype = AccountSubtype.CASH_AND_EQUIVALENTS,
    ) = Account(
        entityId = entityId,
        accountCode = "1000",
        accountName = "Operating Bank Account",
        accountType = accountType,
        accountSubtype = accountSubtype,
        normalBalance = NormalBalance.DEBIT,
        isTemporary = false,
        isHeader = isHeader,
    )

    private fun ledgerEntry(accountId: UUID, entityId: UUID = this.entityId, debit: BigDecimal = BigDecimal.ZERO, credit: BigDecimal = BigDecimal.ZERO, transDate: LocalDate = LocalDate.of(2026, 3, 14)) =
        LedgerEntry(
            entityId = entityId,
            accountId = accountId,
            journalEntryLineId = UUID.randomUUID(),
            transDate = transDate,
            functionalDebit = debit,
            functionalCredit = credit,
            runningBalance = BigDecimal.ZERO,
        )

    private fun statementLine(import: BankStatementImport, amount: BigDecimal, transDate: LocalDate = LocalDate.of(2026, 3, 14), status: ReconciliationStatus = ReconciliationStatus.UNMATCHED): BankStatementLine {
        val line = BankStatementLine(transDate = transDate, description = "Test line", amount = amount, status = status)
        import.addLine(line)
        return line
    }

    // ── importStatement ──────────────────────────────────────────────────────────

    @Test
    fun `importStatement saves a header and its lines against a valid bank account`() {
        val acct = bankAccount()
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every {
            bankStatementImportRepository.existsByEntityIdAndAccountIdAndStatementDateAndClosingBalance(any(), any(), any(), any())
        } returns false
        val saved = slot<BankStatementImport>()
        every { bankStatementImportRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.importStatement(
            CreateBankStatementImportCommand(
                entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31),
                openingBalance = BigDecimal("100000"), closingBalance = BigDecimal("145000"), notes = null,
                lines = listOf(
                    BankStatementLineCommand(LocalDate.of(2026, 3, 5), "Deposit", BigDecimal("50000"), "REF1"),
                    BankStatementLineCommand(LocalDate.of(2026, 3, 20), "Bank fee", BigDecimal("-5000"), null),
                ),
            )
        )

        assertEquals(2, result.lines.size)
        assertEquals(ReconciliationStatus.UNMATCHED, result.lines[0].status)
    }

    @Test
    fun `importStatement rejects a header account`() {
        val header = bankAccount(isHeader = true)
        every { accountRepository.findById(header.id) } returns Optional.of(header)

        assertThrows<BusinessRuleViolationException> {
            service.importStatement(minimalCommand(header.id))
        }
    }

    @Test
    fun `importStatement rejects an account from a different entity`() {
        val foreign = bankAccount(entityId = otherEntityId)
        every { accountRepository.findById(foreign.id) } returns Optional.of(foreign)

        assertThrows<ValidationException> {
            service.importStatement(minimalCommand(foreign.id))
        }
    }

    @Test
    fun `importStatement rejects a non-cash-equivalents account`() {
        val receivable = bankAccount(accountSubtype = AccountSubtype.CURRENT_RECEIVABLE)
        every { accountRepository.findById(receivable.id) } returns Optional.of(receivable)

        assertThrows<BusinessRuleViolationException> {
            service.importStatement(minimalCommand(receivable.id))
        }
    }

    @Test
    fun `importStatement rejects a duplicate re-import of the same statement`() {
        val acct = bankAccount()
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every {
            bankStatementImportRepository.existsByEntityIdAndAccountIdAndStatementDateAndClosingBalance(any(), any(), any(), any())
        } returns true

        assertThrows<BusinessRuleViolationException> {
            service.importStatement(minimalCommand(acct.id))
        }
    }

    private fun minimalCommand(accountId: UUID) = CreateBankStatementImportCommand(
        entityId = entityId, accountId = accountId, statementDate = LocalDate.of(2026, 3, 31),
        openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO, notes = null,
        lines = listOf(BankStatementLineCommand(LocalDate.of(2026, 3, 1), "x", BigDecimal("1"), null)),
    )

    // ── match (manual) ───────────────────────────────────────────────────────────

    @Test
    fun `match links a line to a ledger entry with the same signed amount and sets MATCHED`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"))
        val entry = ledgerEntry(acct.id, debit = BigDecimal("50000.000000"))
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { ledgerEntryRepository.findAllById(setOf(entry.id)) } returns listOf(entry)
        every { bankLineMatchRepository.existsByLedgerEntryId(entry.id) } returns false
        every { bankStatementLineRepository.save(line) } returns line

        val result = service.match(line.id, listOf(entry.id), null)

        assertEquals(ReconciliationStatus.MATCHED, result.status)
        assertEquals(1, result.matches.size)
        assertEquals(MatchType.MANUAL, result.matches[0].matchType)
    }

    @Test
    fun `match rejects when the selected ledger entries do not sum to the line amount`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"))
        val entry = ledgerEntry(acct.id, debit = BigDecimal("49999.000000"))
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { ledgerEntryRepository.findAllById(setOf(entry.id)) } returns listOf(entry)
        every { bankLineMatchRepository.existsByLedgerEntryId(entry.id) } returns false

        assertThrows<BusinessRuleViolationException> { service.match(line.id, listOf(entry.id), null) }
    }

    @Test
    fun `match rejects a line that is not UNMATCHED`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000"), status = ReconciliationStatus.MATCHED)
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)

        assertThrows<BusinessRuleViolationException> { service.match(line.id, listOf(UUID.randomUUID()), null) }
    }

    @Test
    fun `match rejects a ledger entry belonging to a different account`() {
        val acct = bankAccount()
        val otherAccountId = UUID.randomUUID()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"))
        val entry = ledgerEntry(otherAccountId, debit = BigDecimal("50000.000000"))
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { ledgerEntryRepository.findAllById(setOf(entry.id)) } returns listOf(entry)

        assertThrows<ValidationException> { service.match(line.id, listOf(entry.id), null) }
    }

    @Test
    fun `match rejects a ledger entry already matched to another line`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"))
        val entry = ledgerEntry(acct.id, debit = BigDecimal("50000.000000"))
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { ledgerEntryRepository.findAllById(setOf(entry.id)) } returns listOf(entry)
        every { bankLineMatchRepository.existsByLedgerEntryId(entry.id) } returns true

        assertThrows<BusinessRuleViolationException> { service.match(line.id, listOf(entry.id), null) }
    }

    @Test
    fun `match rejects an unknown ledger entry with a 404`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"))
        val unknownId = UUID.randomUUID()
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { ledgerEntryRepository.findAllById(setOf(unknownId)) } returns emptyList()

        assertThrows<ResourceNotFoundException> { service.match(line.id, listOf(unknownId), null) }
    }

    // ── autoMatch ────────────────────────────────────────────────────────────────

    @Test
    fun `autoMatch commits when exactly one candidate exists`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"), transDate = LocalDate.of(2026, 3, 14))
        val candidate = ledgerEntry(acct.id, debit = BigDecimal("50000.000000"), transDate = LocalDate.of(2026, 3, 15))
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { bankLineMatchRepository.findMatchedLedgerEntryIdsByAccountId(acct.id) } returns emptyList()
        every { ledgerEntryRepository.findByAccountIdAndDateRange(acct.id, LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 17)) } returns listOf(candidate)
        every { ledgerEntryRepository.findAllById(setOf(candidate.id)) } returns listOf(candidate)
        every { bankLineMatchRepository.existsByLedgerEntryId(candidate.id) } returns false
        every { bankStatementLineRepository.save(line) } returns line

        val result = service.autoMatch(line.id, null)

        assertEquals(ReconciliationStatus.MATCHED, result.status)
        assertEquals(MatchType.AUTO, result.matches[0].matchType)
    }

    @Test
    fun `autoMatch refuses to guess when there are zero candidates`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"), transDate = LocalDate.of(2026, 3, 14))
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { bankLineMatchRepository.findMatchedLedgerEntryIdsByAccountId(acct.id) } returns emptyList()
        every { ledgerEntryRepository.findByAccountIdAndDateRange(acct.id, LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 17)) } returns emptyList()

        val ex = assertThrows<BusinessRuleViolationException> { service.autoMatch(line.id, null) }
        assertEquals("NO_AUTO_MATCH_CANDIDATE", ex.errorCode)
    }

    @Test
    fun `autoMatch refuses to guess when there are multiple candidates`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000.000000"), transDate = LocalDate.of(2026, 3, 14))
        val c1 = ledgerEntry(acct.id, debit = BigDecimal("50000.000000"), transDate = LocalDate.of(2026, 3, 13))
        val c2 = ledgerEntry(acct.id, debit = BigDecimal("50000.000000"), transDate = LocalDate.of(2026, 3, 16))
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { bankLineMatchRepository.findMatchedLedgerEntryIdsByAccountId(acct.id) } returns emptyList()
        every { ledgerEntryRepository.findByAccountIdAndDateRange(acct.id, LocalDate.of(2026, 3, 11), LocalDate.of(2026, 3, 17)) } returns listOf(c1, c2)

        val ex = assertThrows<BusinessRuleViolationException> { service.autoMatch(line.id, null) }
        assertEquals("AMBIGUOUS_AUTO_MATCH", ex.errorCode)
    }

    // ── unmatch / ignore / unignore ──────────────────────────────────────────────

    @Test
    fun `unmatch clears matches and reverts status to UNMATCHED`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000"), status = ReconciliationStatus.MATCHED)
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { bankStatementLineRepository.save(line) } returns line

        val result = service.unmatch(line.id)

        assertEquals(ReconciliationStatus.UNMATCHED, result.status)
        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun `unmatch rejects a line that is not MATCHED`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("50000"), status = ReconciliationStatus.UNMATCHED)
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)

        assertThrows<BusinessRuleViolationException> { service.unmatch(line.id) }
    }

    @Test
    fun `ignore requires UNMATCHED and records the reason`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("-500"))
        val userId = UUID.randomUUID()
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { bankStatementLineRepository.save(line) } returns line

        val result = service.ignore(line.id, "Bank error, corrected next month", userId)

        assertEquals(ReconciliationStatus.IGNORED, result.status)
        assertEquals("Bank error, corrected next month", result.ignoreReason)
        assertEquals(userId, result.ignoredBy)
    }

    @Test
    fun `unignore reverts an IGNORED line back to UNMATCHED and clears the reason`() {
        val acct = bankAccount()
        val import = BankStatementImport(entityId = entityId, accountId = acct.id, statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO)
        val line = statementLine(import, BigDecimal("-500"), status = ReconciliationStatus.IGNORED)
        line.ignoreReason = "temp"
        every { bankStatementLineRepository.findByIdWithImport(line.id) } returns Optional.of(line)
        every { bankStatementLineRepository.save(line) } returns line

        val result = service.unignore(line.id)

        assertEquals(ReconciliationStatus.UNMATCHED, result.status)
        assertEquals(null, result.ignoreReason)
    }

    // ── reconciliationSummary (the core tie-out proof) ──────────────────────────

    @Test
    fun `reconciliationSummary ties out when GL and statement outstanding items exactly offset`() {
        // Realistic scenario: a $40,000 deposit is on both the GL and the statement (matched); a
        // $1,000 deposit-in-transit is booked in the GL but hasn't cleared the bank yet (GL-only
        // outstanding); a $1,000 bank fee is on the statement but not yet booked in the GL
        // (bank-only outstanding). GL balance = 40000 + 1000 = 41000. Bank closing balance
        // includes the matched deposit and the fee but not the in-transit deposit = 39000.
        // adjustedBookBalance = 41000 + (-1000) = 40000; adjustedBankBalance = 39000 + 1000 = 40000 — ties.
        val acct = bankAccount()
        val statementDate = LocalDate.of(2026, 3, 31)
        val import = BankStatementImport(
            entityId = entityId, accountId = acct.id, statementDate = statementDate,
            openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal("39000.000000"),
        )
        val matchedEntry = ledgerEntry(acct.id, debit = BigDecimal("40000.000000"), transDate = LocalDate.of(2026, 3, 10))
        val matchedLine = statementLine(import, BigDecimal("40000.000000"), status = ReconciliationStatus.MATCHED)
        matchedLine.addMatch(com.qesuite.accounting.banking.domain.BankLineMatch(ledgerEntryId = matchedEntry.id, matchType = MatchType.MANUAL))
        // Unmatched bank line: a bank fee not yet recorded in the GL.
        statementLine(import, BigDecimal("-1000.000000"), status = ReconciliationStatus.UNMATCHED)
        // Outstanding GL entry: a deposit in transit, recorded in the books, not yet cleared by the bank.
        val outstandingGlEntry = ledgerEntry(acct.id, debit = BigDecimal("1000.000000"), transDate = LocalDate.of(2026, 3, 25))

        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { ledgerEntryRepository.sumFunctionalDebits(acct.id, statementDate) } returns BigDecimal("41000.000000")
        every { ledgerEntryRepository.sumFunctionalCredits(acct.id, statementDate) } returns BigDecimal.ZERO
        every { bankLineMatchRepository.findMatchedLedgerEntryIdsByAccountId(acct.id) } returns listOf(matchedEntry.id)
        every { ledgerEntryRepository.findByAccountIdAndDateRange(acct.id, LocalDate.of(1970, 1, 1), statementDate) } returns listOf(matchedEntry, outstandingGlEntry)
        every { ledgerEntryRepository.findAllById(listOf(matchedEntry.id)) } returns listOf(matchedEntry)
        every { bankStatementImportRepository.findById(import.id) } returns Optional.of(import)

        val report = service.reconciliationSummary(import.id)

        assertEquals(BigDecimal("41000.000000"), report.glBalance)
        assertEquals(BigDecimal("1000.000000"), report.glOutstandingTotal)
        assertEquals(BigDecimal("-1000.000000"), report.bankOutstandingTotal)
        assertEquals(BigDecimal("40000.000000"), report.adjustedBookBalance)
        assertEquals(BigDecimal("40000.000000"), report.adjustedBankBalance)
        assertTrue(report.tiesOut, "expected tie-out, difference was ${report.difference}")
    }

    @Test
    fun `reconciliationSummary does not tie out when there is a genuine unexplained gap`() {
        val acct = bankAccount()
        val statementDate = LocalDate.of(2026, 3, 31)
        val import = BankStatementImport(
            entityId = entityId, accountId = acct.id, statementDate = statementDate,
            openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal("100000.000000"),
        )
        // An ignored line with no GL counterpart at all — real cash effect, nothing to offset it.
        statementLine(import, BigDecimal("-2500.000000"), status = ReconciliationStatus.IGNORED)

        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { ledgerEntryRepository.sumFunctionalDebits(acct.id, statementDate) } returns BigDecimal("100000.000000")
        every { ledgerEntryRepository.sumFunctionalCredits(acct.id, statementDate) } returns BigDecimal.ZERO
        every { bankLineMatchRepository.findMatchedLedgerEntryIdsByAccountId(acct.id) } returns emptyList()
        every { ledgerEntryRepository.findByAccountIdAndDateRange(acct.id, LocalDate.of(1970, 1, 1), statementDate) } returns emptyList()
        every { ledgerEntryRepository.findAllById(emptyList<UUID>()) } returns emptyList()
        every { bankStatementImportRepository.findById(import.id) } returns Optional.of(import)

        val report = service.reconciliationSummary(import.id)

        // glBalance = 100000, bankOutstandingTotal = -2500 -> adjustedBookBalance = 97500
        // glOutstandingTotal = 0 -> adjustedBankBalance = closingBalance(100000) + 0 = 100000
        assertFalse(report.tiesOut)
        assertEquals(BigDecimal("-2500.000000"), report.difference)
    }
}

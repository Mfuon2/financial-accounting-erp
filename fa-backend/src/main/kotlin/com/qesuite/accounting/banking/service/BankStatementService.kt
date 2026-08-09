package com.qesuite.accounting.banking.service

import com.qesuite.accounting.banking.domain.BankLineMatch
import com.qesuite.accounting.banking.domain.BankStatementImport
import com.qesuite.accounting.banking.domain.BankStatementLine
import com.qesuite.accounting.banking.domain.MatchType
import com.qesuite.accounting.banking.domain.ReconciliationStatus
import com.qesuite.accounting.banking.dto.BankLineMatchResponse
import com.qesuite.accounting.banking.dto.BankReconciliationSummaryResponse
import com.qesuite.accounting.banking.dto.BankStatementImportResponse
import com.qesuite.accounting.banking.dto.BankStatementLineResponse
import com.qesuite.accounting.banking.dto.CreateBankStatementImportCommand
import com.qesuite.accounting.banking.dto.MatchCandidateResponse
import com.qesuite.accounting.banking.dto.OutstandingLedgerEntryResponse
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
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Cash & Bank Management module service.
 * workplan.md Phase 1 item 2.
 *
 * Owns bank statement import, matching a statement line against existing GL activity (manual or
 * a simple auto-match suggestion — same-amount + a small date tolerance, not a fuzzy/ML matcher),
 * and the reconciliation tie-out report. Never posts a journal entry: reconciliation here is
 * purely a comparison of the statement against ledger activity that already exists, matching
 * BudgetService's "never posts, compares at read time" philosophy exactly.
 *
 * Money discipline: every monetary calculation is `BigDecimal`, rounded `HALF_EVEN` to scale 6,
 * matching `BudgetService`/`InvoiceService`'s convention.
 */
@Service
@Transactional
class BankStatementService(
    private val bankStatementImportRepository: BankStatementImportRepository,
    private val bankStatementLineRepository: BankStatementLineRepository,
    private val bankLineMatchRepository: BankLineMatchRepository,
    private val accountRepository: AccountRepository,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {

    private companion object {
        const val MONEY_SCALE = 6
        val ROUND: RoundingMode = RoundingMode.HALF_EVEN

        /**
         * Auto-match date tolerance window, in days either side of the bank line's transaction
         * date. Deliberately a small fixed constant, not configurable, for this first cut — a
         * simple, honest suggestion rule, not a fuzzy/ML matcher. Track "make configurable per
         * entity" as future work if real usage shows 3 days is too narrow/wide.
         */
        const val AUTO_MATCH_DATE_TOLERANCE_DAYS = 3L

        /** "From the beginning of time" lower bound for an unbounded ledger-entry date range query. */
        val EPOCH: LocalDate = LocalDate.of(1970, 1, 1)
    }

    // ── Import ───────────────────────────────────────────────────────────────────

    /**
     * Import a bank statement (header + lines) in one request. Validates [command.accountId] is
     * a real, non-header, Cash & Equivalents asset account belonging to [command.entityId] —
     * the exact validation shape as `BudgetService.validateLine()`, tightened to specifically a
     * bank/cash account rather than any monetary asset. Rejects an exact re-import of the same
     * statement (same account/date/closing balance) as a defensive de-dupe, not a substitute for
     * a real CSV-import de-duplication pipeline (future work).
     */
    @Auditable(action = AuditAction.CREATE, resourceType = "BANK_STATEMENT_IMPORT")
    fun importStatement(command: CreateBankStatementImportCommand): BankStatementImport {
        val account = validateBankAccount(command.entityId, command.accountId)

        if (bankStatementImportRepository.existsByEntityIdAndAccountIdAndStatementDateAndClosingBalance(
                command.entityId, command.accountId, command.statementDate,
                command.closingBalance.setScale(MONEY_SCALE, ROUND),
            )
        ) {
            throw BusinessRuleViolationException(
                errorCode = "DUPLICATE_STATEMENT_IMPORT",
                message = "A statement for account ${account.accountCode} dated ${command.statementDate} " +
                    "with closing balance ${command.closingBalance} has already been imported.",
                context = mapOf("account_id" to command.accountId, "statement_date" to command.statementDate.toString()),
            )
        }

        val import = BankStatementImport(
            entityId = command.entityId,
            accountId = command.accountId,
            statementDate = command.statementDate,
            openingBalance = command.openingBalance.setScale(MONEY_SCALE, ROUND),
            closingBalance = command.closingBalance.setScale(MONEY_SCALE, ROUND),
            notes = command.notes,
        )
        command.lines.forEach { line ->
            import.addLine(
                BankStatementLine(
                    transDate = line.transactionDate,
                    description = line.description,
                    amount = line.amount.setScale(MONEY_SCALE, ROUND),
                    reference = line.reference,
                )
            )
        }
        return bankStatementImportRepository.save(import)
    }

    @Transactional(readOnly = true)
    fun findImportById(id: UUID): BankStatementImport = bankStatementImportRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("BANK_STATEMENT_IMPORT_NOT_FOUND", id, "BankStatementImport") }

    /**
     * Fetch-joins the parent import (see `BankStatementLineRepository.findByIdWithImport`'s
     * KDoc) so the returned line's `entityId`/`accountId`/`bankStatementImportId` are safe to
     * read from the controller layer even after this read-only transaction has closed
     * (`spring.jpa.open-in-view: false` — a plain lazy `@ManyToOne` would otherwise throw
     * `LazyInitializationException` the moment the controller does the IDOR ownership check).
     */
    @Transactional(readOnly = true)
    fun findLineById(id: UUID): BankStatementLine = bankStatementLineRepository.findByIdWithImport(id)
        .orElseThrow { ResourceNotFoundException("BANK_STATEMENT_LINE_NOT_FOUND", id, "BankStatementLine") }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<BankStatementImport> =
        bankStatementImportRepository.findByEntityId(entityId, pageable)

    // ── Matching ─────────────────────────────────────────────────────────────────

    /**
     * Manually link [lineId] to [ledgerEntryIds]. Requires the line to currently be `UNMATCHED`
     * (unmatch first to re-match — no partial/incremental match editing in this first cut).
     * Every ledger entry must belong to the same account and entity as the bank line, must not
     * already be matched to a different line, and the selected entries' signed amounts
     * ([signedAmount]) must sum exactly to the bank line's amount — an honest, mechanically
     * checked match, not an approximate one.
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "BANK_STATEMENT_LINE")
    fun match(@AuditResourceId lineId: UUID, ledgerEntryIds: List<UUID>, matchedBy: UUID?): BankStatementLine {
        val line = findLineById(lineId)
        val entries = resolveAndValidateEntries(line, ledgerEntryIds)
        return performMatch(line, entries, MatchType.MANUAL, matchedBy)
    }

    /**
     * One-click auto-match: finds ledger entries for [lineId]'s account with the exact same
     * signed amount, dated within [AUTO_MATCH_DATE_TOLERANCE_DAYS] days of the line's transaction
     * date, not already matched elsewhere. Commits the match only when there is **exactly one**
     * such candidate — zero or multiple candidates are refused with a clear error rather than
     * guessing, matching the "simple, honest suggestion, not a fuzzy ML system" scope decision.
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "BANK_STATEMENT_LINE")
    fun autoMatch(@AuditResourceId lineId: UUID, matchedBy: UUID?): BankStatementLine {
        val line = findLineById(lineId)
        requireStatus(line, ReconciliationStatus.UNMATCHED, "auto-matched")
        val candidates = findCandidates(line)
        when {
            candidates.isEmpty() -> throw BusinessRuleViolationException(
                errorCode = "NO_AUTO_MATCH_CANDIDATE",
                message = "No ledger entry within ${AUTO_MATCH_DATE_TOLERANCE_DAYS} days matches this line's amount exactly. Match manually.",
                context = mapOf("bank_statement_line_id" to lineId),
            )
            candidates.size > 1 -> throw BusinessRuleViolationException(
                errorCode = "AMBIGUOUS_AUTO_MATCH",
                message = "${candidates.size} ledger entries match this line's amount within the date window — match manually to disambiguate.",
                context = mapOf("bank_statement_line_id" to lineId, "candidate_count" to candidates.size),
            )
            else -> return performMatch(line, listOf(candidates.single()), MatchType.AUTO, matchedBy)
        }
    }

    /** Read-only auto-match candidates for [lineId] — see [autoMatch] for the same underlying rule. Does not commit anything. */
    @Transactional(readOnly = true)
    fun suggestions(lineId: UUID): List<MatchCandidateResponse> {
        val line = findLineById(lineId)
        val account = accountRepository.findById(line.accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", line.accountId, "Account") }
        return findCandidates(line).map { entry ->
            MatchCandidateResponse(
                ledgerEntryId = entry.id,
                transDate = entry.transDate,
                functionalDebit = entry.functionalDebit,
                functionalCredit = entry.functionalCredit,
                signedAmount = signedAmount(account, entry),
            )
        }
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "BANK_STATEMENT_LINE")
    fun unmatch(@AuditResourceId lineId: UUID): BankStatementLine {
        val line = findLineById(lineId)
        requireStatus(line, ReconciliationStatus.MATCHED, "unmatched")
        line.clearMatches()
        line.status = ReconciliationStatus.UNMATCHED
        line.modifiedAt = Instant.now()
        return bankStatementLineRepository.save(line)
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "BANK_STATEMENT_LINE")
    fun ignore(@AuditResourceId lineId: UUID, reason: String, ignoredBy: UUID?): BankStatementLine {
        val line = findLineById(lineId)
        requireStatus(line, ReconciliationStatus.UNMATCHED, "ignored")
        line.status = ReconciliationStatus.IGNORED
        line.ignoreReason = reason
        line.ignoredAt = Instant.now()
        line.ignoredBy = ignoredBy
        line.modifiedAt = Instant.now()
        return bankStatementLineRepository.save(line)
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "BANK_STATEMENT_LINE")
    fun unignore(@AuditResourceId lineId: UUID): BankStatementLine {
        val line = findLineById(lineId)
        requireStatus(line, ReconciliationStatus.IGNORED, "un-ignored")
        line.status = ReconciliationStatus.UNMATCHED
        line.ignoreReason = null
        line.ignoredAt = null
        line.ignoredBy = null
        line.modifiedAt = Instant.now()
        return bankStatementLineRepository.save(line)
    }

    private fun requireStatus(line: BankStatementLine, required: ReconciliationStatus, actionPastTense: String) {
        if (line.status != required) {
            throw BusinessRuleViolationException(
                errorCode = "LINE_NOT_${required.name}",
                message = "A bank statement line can only be $actionPastTense from status $required (current status: ${line.status}).",
                context = mapOf("bank_statement_line_id" to line.id, "current_status" to line.status.name),
            )
        }
    }

    private fun resolveAndValidateEntries(line: BankStatementLine, ledgerEntryIds: List<UUID>): List<LedgerEntry> {
        requireStatus(line, ReconciliationStatus.UNMATCHED, "matched")
        val account = accountRepository.findById(line.accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", line.accountId, "Account") }

        val uniqueIds = ledgerEntryIds.toSet()
        val entries = ledgerEntryRepository.findAllById(uniqueIds)
        if (entries.size != uniqueIds.size) {
            val found = entries.map { it.id }.toSet()
            throw ResourceNotFoundException("LEDGER_ENTRY_NOT_FOUND", (uniqueIds - found).first(), "LedgerEntry")
        }
        entries.forEach { entry ->
            if (entry.entityId != line.entityId || entry.accountId != line.accountId) {
                throw ValidationException(
                    errorCode = "LEDGER_ENTRY_WRONG_ACCOUNT",
                    message = "Ledger entry ${entry.id} does not belong to account ${line.accountId} for this entity.",
                    httpStatus = 403,
                )
            }
            if (bankLineMatchRepository.existsByLedgerEntryId(entry.id)) {
                throw BusinessRuleViolationException(
                    errorCode = "LEDGER_ENTRY_ALREADY_MATCHED",
                    message = "Ledger entry ${entry.id} is already matched to another bank statement line.",
                    context = mapOf("ledger_entry_id" to entry.id),
                )
            }
        }

        val sum = entries.sumOf { signedAmount(account, it) }.setScale(MONEY_SCALE, ROUND)
        val target = line.amount.setScale(MONEY_SCALE, ROUND)
        if (sum.compareTo(target) != 0) {
            throw BusinessRuleViolationException(
                errorCode = "AMOUNT_MISMATCH",
                message = "Selected ledger entries sum to $sum but the bank line amount is $target.",
                context = mapOf("bank_statement_line_id" to line.id, "selected_sum" to sum, "line_amount" to target),
            )
        }
        return entries
    }

    private fun performMatch(line: BankStatementLine, entries: List<LedgerEntry>, matchType: MatchType, matchedBy: UUID?): BankStatementLine {
        entries.forEach { entry ->
            line.addMatch(BankLineMatch(ledgerEntryId = entry.id, matchType = matchType, matchedBy = matchedBy))
        }
        line.status = ReconciliationStatus.MATCHED
        line.modifiedAt = Instant.now()
        return bankStatementLineRepository.save(line)
    }

    private fun findCandidates(line: BankStatementLine): List<LedgerEntry> {
        val account = accountRepository.findById(line.accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", line.accountId, "Account") }
        val alreadyMatched = bankLineMatchRepository.findMatchedLedgerEntryIdsByAccountId(line.accountId).toSet()
        val windowStart = line.transDate.minusDays(AUTO_MATCH_DATE_TOLERANCE_DAYS)
        val windowEnd = line.transDate.plusDays(AUTO_MATCH_DATE_TOLERANCE_DAYS)
        val target = line.amount.setScale(MONEY_SCALE, ROUND)
        return ledgerEntryRepository.findByAccountIdAndDateRange(line.accountId, windowStart, windowEnd)
            .filter { it.id !in alreadyMatched }
            .filter { signedAmount(account, it).compareTo(target) == 0 }
    }

    /**
     * A ledger entry's amount, signed the same way [BankStatementLine.amount] is signed (positive
     * = increases the account balance) — `functionalDebit - functionalCredit` for a DEBIT-normal
     * account, flipped for a CREDIT-normal one. Every bank/cash account this module accepts
     * ([validateBankAccount] restricts to `CASH_AND_EQUIVALENTS`) is DEBIT-normal in practice, but
     * the general form matches `BudgetService.actualForAccountInPeriod`'s precedent rather than
     * hardcoding the DEBIT branch.
     */
    private fun signedAmount(account: Account, entry: LedgerEntry): BigDecimal =
        if (account.normalBalance == NormalBalance.DEBIT) {
            entry.functionalDebit.subtract(entry.functionalCredit)
        } else {
            entry.functionalCredit.subtract(entry.functionalDebit)
        }

    // ── Responses ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun toResponse(import: BankStatementImport): BankStatementImportResponse {
        val account = accountRepository.findById(import.accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", import.accountId, "Account") }
        val entriesById = ledgerEntryRepository
            .findAllById(import.lines.flatMap { line -> line.matches.map { it.ledgerEntryId } })
            .associateBy { it.id }

        return BankStatementImportResponse(
            id = import.id,
            entityId = import.entityId,
            accountId = import.accountId,
            accountCode = account.accountCode,
            accountName = account.accountName,
            statementDate = import.statementDate,
            openingBalance = import.openingBalance,
            closingBalance = import.closingBalance,
            notes = import.notes,
            version = import.version,
            lines = import.lines.map { toLineResponse(it, entriesById) },
        )
    }

    private fun toLineResponse(line: BankStatementLine, entriesById: Map<UUID, LedgerEntry>): BankStatementLineResponse =
        BankStatementLineResponse(
            id = line.id,
            transDate = line.transDate,
            description = line.description,
            amount = line.amount,
            reference = line.reference,
            status = line.status,
            ignoreReason = line.ignoreReason,
            matches = line.matches.map { match ->
                val entry = entriesById[match.ledgerEntryId]
                BankLineMatchResponse(
                    id = match.id,
                    ledgerEntryId = match.ledgerEntryId,
                    matchType = match.matchType,
                    matchedAt = match.matchedAt,
                    ledgerEntryTransDate = entry?.transDate,
                    ledgerEntryDebit = entry?.functionalDebit,
                    ledgerEntryCredit = entry?.functionalCredit,
                )
            },
        )

    /**
     * The reconciliation tie-out report — the core accounting deliverable of this module.
     *
     * `adjustedBookBalance = glBalance + bankOutstandingTotal` (project the book balance forward
     * by what the bank has recorded that the books haven't yet — e.g. bank fees, interest).
     * `adjustedBankBalance = closingBalance + glOutstandingTotal` (project the bank balance
     * forward by what the books have recorded that the bank hasn't yet cleared — e.g. deposits in
     * transit, outstanding cheques). A correct reconciliation has the two equal ([tiesOut]); if
     * they're not, [difference] is the real, un-explained gap — no adjustment is posted here (see
     * module handover notes for why that's deliberately out of scope for this first cut).
     */
    @Transactional(readOnly = true)
    fun reconciliationSummary(importId: UUID): BankReconciliationSummaryResponse {
        val import = findImportById(importId)
        val account = accountRepository.findById(import.accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", import.accountId, "Account") }

        val glDebits = ledgerEntryRepository.sumFunctionalDebits(import.accountId, import.statementDate) ?: BigDecimal.ZERO
        val glCredits = ledgerEntryRepository.sumFunctionalCredits(import.accountId, import.statementDate) ?: BigDecimal.ZERO
        val glBalance = (
            if (account.normalBalance == NormalBalance.DEBIT) glDebits.subtract(glCredits) else glCredits.subtract(glDebits)
        ).setScale(MONEY_SCALE, ROUND)

        val matchedLedgerEntryIds = bankLineMatchRepository.findMatchedLedgerEntryIdsByAccountId(import.accountId).toSet()
        val outstandingGlEntries = ledgerEntryRepository
            .findByAccountIdAndDateRange(import.accountId, EPOCH, import.statementDate)
            .filter { it.id !in matchedLedgerEntryIds }
        val glOutstandingTotal = outstandingGlEntries.sumOf { signedAmount(account, it) }.setScale(MONEY_SCALE, ROUND)

        val outstandingBankLines = import.lines.filter { it.status != ReconciliationStatus.MATCHED }
        val bankOutstandingTotal = outstandingBankLines.sumOf { it.amount }.setScale(MONEY_SCALE, ROUND)

        val adjustedBookBalance = glBalance.add(bankOutstandingTotal).setScale(MONEY_SCALE, ROUND)
        val adjustedBankBalance = import.closingBalance.add(glOutstandingTotal).setScale(MONEY_SCALE, ROUND)
        val difference = adjustedBookBalance.subtract(adjustedBankBalance).setScale(MONEY_SCALE, ROUND)

        val statementLinesTotal = import.lines.sumOf { it.amount }.setScale(MONEY_SCALE, ROUND)
        val expectedClosing = import.openingBalance.add(statementLinesTotal).setScale(MONEY_SCALE, ROUND)

        val entriesById = ledgerEntryRepository
            .findAllById(import.lines.flatMap { line -> line.matches.map { it.ledgerEntryId } })
            .associateBy { it.id }

        return BankReconciliationSummaryResponse(
            importId = import.id,
            accountId = account.id,
            accountCode = account.accountCode,
            accountName = account.accountName,
            statementDate = import.statementDate,
            openingBalance = import.openingBalance,
            closingBalance = import.closingBalance,
            glBalance = glBalance,
            matchedCount = import.lines.count { it.status == ReconciliationStatus.MATCHED },
            unmatchedCount = import.lines.count { it.status == ReconciliationStatus.UNMATCHED },
            ignoredCount = import.lines.count { it.status == ReconciliationStatus.IGNORED },
            statementLinesTotal = statementLinesTotal,
            statementLinesTieToClosingBalance = expectedClosing.compareTo(import.closingBalance.setScale(MONEY_SCALE, ROUND)) == 0,
            outstandingLedgerEntries = outstandingGlEntries.map { entry ->
                OutstandingLedgerEntryResponse(
                    ledgerEntryId = entry.id,
                    transDate = entry.transDate,
                    functionalDebit = entry.functionalDebit,
                    functionalCredit = entry.functionalCredit,
                    signedAmount = signedAmount(account, entry),
                )
            },
            glOutstandingTotal = glOutstandingTotal,
            bankOutstandingTotal = bankOutstandingTotal,
            adjustedBookBalance = adjustedBookBalance,
            adjustedBankBalance = adjustedBankBalance,
            difference = difference,
            tiesOut = difference.compareTo(BigDecimal.ZERO) == 0,
            lines = import.lines.map { toLineResponse(it, entriesById) },
        )
    }

    // ── Validation ───────────────────────────────────────────────────────────────

    /**
     * A bank/cash account must exist, belong to [entityId], not be a header account (IAS 1 §29 —
     * same rule journal posting and `BudgetService.validateLine()` enforce), and specifically be
     * a `CASH_AND_EQUIVALENTS` asset account — narrower than Budgeting's "any non-header account"
     * because reconciliation is only meaningful against an actual bank/cash GL account, not any
     * monetary account (e.g. a receivable).
     */
    private fun validateBankAccount(entityId: UUID, accountId: UUID): Account {
        val account = accountRepository.findById(accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", accountId, "Account") }
        if (account.entityId != entityId) {
            throw ValidationException(
                errorCode = "FORBIDDEN",
                message = "Account $accountId does not belong to entity $entityId.",
                httpStatus = 403,
            )
        }
        if (account.isHeader) {
            throw BusinessRuleViolationException(
                errorCode = "HEADER_ACCOUNT_NOT_RECONCILABLE",
                message = "Account ${account.accountCode} (${account.accountName}) is a header account and cannot be reconciled directly.",
                context = mapOf("account_id" to account.id),
            )
        }
        if (account.accountType != AccountType.ASSET || account.accountSubtype != AccountSubtype.CASH_AND_EQUIVALENTS) {
            throw BusinessRuleViolationException(
                errorCode = "NOT_A_BANK_ACCOUNT",
                message = "Account ${account.accountCode} (${account.accountName}) is not a Cash & Equivalents asset account and cannot be used for bank reconciliation.",
                context = mapOf("account_id" to account.id, "account_type" to account.accountType.name, "account_subtype" to account.accountSubtype.name),
            )
        }
        return account
    }
}

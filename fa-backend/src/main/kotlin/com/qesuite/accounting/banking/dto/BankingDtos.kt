package com.qesuite.accounting.banking.dto

import com.qesuite.accounting.banking.domain.MatchType
import com.qesuite.accounting.banking.domain.ReconciliationStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ── Import ──────────────────────────────────────────────────────────────────────

@Schema(description = "A single transaction line within a bank statement import request. " +
    "amount is signed: positive = deposit/credit to the bank, negative = withdrawal/debit.")
data class BankStatementLineCommand(
    @field:NotNull(message = "transactionDate is required")
    val transactionDate: LocalDate,

    @field:NotBlank(message = "description is required")
    val description: String,

    @field:NotNull(message = "amount is required")
    val amount: BigDecimal,

    val reference: String? = null,
)

@Schema(description = "Import a bank statement (header) and its transaction lines in one request. " +
    "A real CSV/OFX parsing pipeline is future work — this is a simple JSON array import, same " +
    "scope decision as Budgeting's deferred CSV import.")
data class CreateBankStatementImportCommand(
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,

    @field:NotNull(message = "accountId is required")
    @Schema(description = "Must be a non-header, Cash & Equivalents asset account belonging to entityId")
    val accountId: UUID,

    @field:NotNull(message = "statementDate is required")
    val statementDate: LocalDate,

    @field:NotNull(message = "openingBalance is required")
    val openingBalance: BigDecimal,

    @field:NotNull(message = "closingBalance is required")
    val closingBalance: BigDecimal,

    val notes: String? = null,

    @field:NotEmpty(message = "A statement import must have at least one line")
    @field:Valid
    val lines: List<BankStatementLineCommand>,
)

// ── Matching ────────────────────────────────────────────────────────────────────

@Schema(description = "Manually link a bank statement line to one or more ledger entries. The " +
    "selected ledger entries' signed amounts must sum exactly to the bank line's amount.")
data class MatchLineCommand(
    @field:NotEmpty(message = "At least one ledgerEntryId is required")
    val ledgerEntryIds: List<UUID>,
)

@Schema(description = "Set a bank line aside without matching it — still counted in the " +
    "reconciliation tie-out (see ReconciliationStatus KDoc), just no longer nagging for action.")
data class IgnoreLineCommand(
    @field:NotBlank(message = "A non-blank reason is required to ignore a bank statement line")
    val reason: String,
)

// ── Responses ───────────────────────────────────────────────────────────────────

@Schema(description = "A ledger entry matched to a bank statement line, enriched for display")
data class BankLineMatchResponse(
    val id: UUID,
    val ledgerEntryId: UUID,
    val matchType: MatchType,
    val matchedAt: Instant,
    val ledgerEntryTransDate: LocalDate?,
    val ledgerEntryDebit: BigDecimal?,
    val ledgerEntryCredit: BigDecimal?,
)

@Schema(description = "A bank statement line with its current status and any matches")
data class BankStatementLineResponse(
    val id: UUID,
    val transDate: LocalDate,
    val description: String,
    val amount: BigDecimal,
    val reference: String?,
    val status: ReconciliationStatus,
    val ignoreReason: String?,
    val matches: List<BankLineMatchResponse>,
)

@Schema(description = "An imported bank statement with all its lines")
data class BankStatementImportResponse(
    val id: UUID,
    val entityId: UUID,
    val accountId: UUID,
    val accountCode: String,
    val accountName: String,
    val statementDate: LocalDate,
    val openingBalance: BigDecimal,
    val closingBalance: BigDecimal,
    val notes: String?,
    val version: Long,
    val lines: List<BankStatementLineResponse>,
)

@Schema(description = "A candidate ledger entry for auto-matching a bank line — same signed " +
    "amount, transaction date within the tolerance window. Read-only; does not commit a match.")
data class MatchCandidateResponse(
    val ledgerEntryId: UUID,
    val transDate: LocalDate,
    val functionalDebit: BigDecimal,
    val functionalCredit: BigDecimal,
    val signedAmount: BigDecimal,
)

@Schema(description = "A GL ledger entry for the reconciled account that has no matching bank " +
    "statement line yet — recorded in the books, not yet reflected on the bank statement " +
    "(e.g. a deposit in transit or an outstanding cheque).")
data class OutstandingLedgerEntryResponse(
    val ledgerEntryId: UUID,
    val transDate: LocalDate,
    val functionalDebit: BigDecimal,
    val functionalCredit: BigDecimal,
    val signedAmount: BigDecimal,
)

@Schema(description = "The reconciliation summary/report for one bank statement import — the " +
    "mechanical proof that the statement and the GL actually reconcile. " +
    "adjustedBookBalance = glBalance + bankOutstandingTotal; " +
    "adjustedBankBalance = closingBalance + glOutstandingTotal; " +
    "the two must be equal (tiesOut) for the account to be considered reconciled for this statement.")
data class BankReconciliationSummaryResponse(
    val importId: UUID,
    val accountId: UUID,
    val accountCode: String,
    val accountName: String,
    val statementDate: LocalDate,
    val openingBalance: BigDecimal,
    val closingBalance: BigDecimal,

    @Schema(description = "GL balance for the account as of statementDate, from all posted ledger activity to date")
    val glBalance: BigDecimal,

    val matchedCount: Int,
    val unmatchedCount: Int,
    val ignoredCount: Int,

    @Schema(description = "Statement lines total (all lines, any status) — informational cross-check against closingBalance")
    val statementLinesTotal: BigDecimal,
    @Schema(description = "True when openingBalance + statementLinesTotal == closingBalance — flags a data-entry problem in the imported lines, not blocking")
    val statementLinesTieToClosingBalance: Boolean,

    val outstandingLedgerEntries: List<OutstandingLedgerEntryResponse>,
    @Schema(description = "Signed sum of outstandingLedgerEntries — GL activity not yet on the bank statement")
    val glOutstandingTotal: BigDecimal,
    @Schema(description = "Signed sum of bank lines with no match (UNMATCHED or IGNORED) — on the statement, not yet recorded in the GL")
    val bankOutstandingTotal: BigDecimal,

    val adjustedBookBalance: BigDecimal,
    val adjustedBankBalance: BigDecimal,
    @Schema(description = "adjustedBookBalance - adjustedBankBalance; zero means the reconciliation ties out")
    val difference: BigDecimal,
    val tiesOut: Boolean,

    val lines: List<BankStatementLineResponse>,
)

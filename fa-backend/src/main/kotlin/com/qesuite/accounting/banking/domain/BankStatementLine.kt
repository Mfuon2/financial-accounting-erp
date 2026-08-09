package com.qesuite.accounting.banking.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A single imported bank statement transaction line within a [BankStatementImport].
 *
 * `amount` sign convention (documented once, here, as the single source of truth — every other
 * file in this module defers to this comment rather than restating it): **positive = deposit/
 * credit to the bank (increases the bank balance); negative = withdrawal/debit from the bank
 * (decreases the bank balance)** — exactly how a real bank statement's single "amount" column
 * reads, and deliberately the same sign as `functionalDebit - functionalCredit` on a matched
 * [com.qesuite.accounting.ledger.domain.LedgerEntry] for a DEBIT-normal cash/bank account (see
 * [com.qesuite.accounting.banking.service.BankStatementService]'s `signedAmount` helper), so a
 * bank line and its matched ledger entries compare directly with no sign-flip.
 *
 * [status] starts `UNMATCHED` and moves to `MATCHED` (via [matches] becoming non-empty) or
 * `IGNORED` (a manual "set this aside, don't keep prompting me" action — see
 * [ReconciliationStatus] KDoc for why an ignored line still counts toward the reconciliation
 * tie-out).
 */
@Entity
@Table(name = "bank_statement_lines")
class BankStatementLine(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_statement_import_id", nullable = false)
    var bankStatementImport: BankStatementImport? = null,

    @Column(name = "trans_date", nullable = false)
    @Schema(example = "2026-03-14")
    val transDate: LocalDate,

    @Column(nullable = false, length = 500)
    @Schema(example = "MPESA TRANSFER - INV-2044")
    val description: String,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "45000.000000", description = "Signed: positive = deposit, negative = withdrawal")
    val amount: BigDecimal,

    @Column(length = 100)
    @Schema(example = "FT2607412233")
    val reference: String? = null,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var status: ReconciliationStatus = ReconciliationStatus.UNMATCHED,

    @Column(name = "ignore_reason", nullable = true, columnDefinition = "TEXT")
    var ignoreReason: String? = null,

    @Column(name = "ignored_at", nullable = true)
    var ignoredAt: Instant? = null,

    @Column(name = "ignored_by", nullable = true)
    var ignoredBy: UUID? = null,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var modifiedAt: Instant = Instant.now(),
) {
    /** Zero, one, or more ledger entries this line has been matched against. */
    @OneToMany(mappedBy = "bankStatementLine", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JsonIgnoreProperties("bankStatementLine")
    val matches: MutableList<BankLineMatch> = mutableListOf()

    fun addMatch(match: BankLineMatch) {
        matches.add(match)
        match.bankStatementLine = this
    }

    fun clearMatches() {
        matches.clear()
    }

    /**
     * The GL account this line reconciles against, read off the parent import. Never null for a
     * persisted line (the FK is `NOT NULL`) — the nullable type only exists for JPA's no-op
     * default constructor. Throws (rather than `!!`, banned by CLAUDE.md §4) if this invariant is
     * ever violated, which would indicate a genuine data-integrity bug, not a normal error path.
     */
    val accountId: UUID
        get() = parentImport().accountId

    /** The owning entity, read off the parent import — see [accountId] KDoc for the null-safety note. */
    val entityId: UUID
        get() = parentImport().entityId

    /** The parent [BankStatementImport]'s id — see [accountId] KDoc for the null-safety note. */
    val bankStatementImportId: UUID
        get() = parentImport().id

    private fun parentImport(): BankStatementImport =
        bankStatementImport ?: error("BankStatementLine $id has no parent BankStatementImport — data integrity error")
}

/**
 * Reconciliation lifecycle for a single [BankStatementLine].
 *
 * `IGNORED` is deliberately **not** excluded from the reconciliation tie-out math in
 * [com.qesuite.accounting.banking.service.BankStatementService.reconciliationSummary] — it only
 * means "a human has reviewed this and it isn't going to be matched right now," not "this has no
 * real cash effect." If an ignored line genuinely has no GL counterpart, the reconciliation
 * correctly will not tie out until an adjusting entry is posted (left as documented future work,
 * see module handover notes — this module deliberately does not post one itself).
 */
enum class ReconciliationStatus {
    UNMATCHED,
    MATCHED,
    IGNORED;

    fun canTransitionTo(next: ReconciliationStatus): Boolean = when (this) {
        UNMATCHED -> next == MATCHED || next == IGNORED
        MATCHED -> next == UNMATCHED
        IGNORED -> next == UNMATCHED
    }
}

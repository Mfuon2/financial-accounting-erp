package com.qesuite.accounting.banking.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Links one [BankStatementLine] to one existing [com.qesuite.accounting.ledger.domain.LedgerEntry].
 * A bank line may have several of these (e.g. one deposit covering two invoices' receipts); a
 * given `ledgerEntryId` may appear in at most one match across the whole entity — enforced both
 * here (`uq_bank_line_match_ledger_entry`) and by
 * [com.qesuite.accounting.banking.service.BankStatementService] before it ever reaches the DB.
 *
 * Deliberately a plain `class`, **not** a `data class`: this codebase found a real bug in
 * `Budget`'s `BudgetLine` (and `InvoiceLine`) from exactly this shape — a `data class` with a
 * lazy `@ManyToOne` back-reference in its primary constructor gets a compiler-generated
 * `equals`/`hashCode`/`toString` that touches every constructor property, including the lazy one,
 * and calling any of them on a managed-but-uninitialized Hibernate proxy outside an active
 * session throws `LazyInitializationException` (a log line, a `Set`/`contains()` check, a test
 * assertion on a detached instance — all trigger it). Identity here is by [id] alone, matching
 * how every other entity in this codebase is compared.
 */
@Entity
@Table(name = "bank_line_matches")
class BankLineMatch(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_statement_line_id", nullable = false)
    var bankStatementLine: BankStatementLine? = null,

    @Column(name = "ledger_entry_id", nullable = false)
    @Schema(description = "FK to the matched ledger_entries row")
    val ledgerEntryId: UUID,

    @Column(name = "match_type", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    val matchType: MatchType,

    @Column(name = "matched_at", nullable = false)
    val matchedAt: Instant = Instant.now(),

    @Column(name = "matched_by", nullable = true)
    val matchedBy: UUID? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BankLineMatch) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "BankLineMatch(id=$id, ledgerEntryId=$ledgerEntryId, matchType=$matchType, matchedAt=$matchedAt, matchedBy=$matchedBy)"
}

/** Whether a match was suggested-and-confirmed automatically or picked explicitly by a user. */
enum class MatchType {
    AUTO,
    MANUAL,
}

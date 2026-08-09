package com.qesuite.accounting.expenses.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A single expense item within an [ExpenseClaim] — e.g. "Taxi to client site, 2026-08-01,
 * KES 3,500". `accountId` must reference a non-header EXPENSE-type account belonging to the same
 * `entityId` as the parent claim (validated in `ExpenseClaimService`, not at the JPA layer,
 * matching `BudgetLine`'s precedent).
 *
 * `receiptReference` is a plain string/URL field only — a real file-upload/OCR receipt pipeline
 * is explicitly out of scope for this first cut (see handover notes); it is free-form so a caller
 * can pass a MinIO object key, an external URL, or a human-typed reference like "Receipt #4821"
 * without this module depending on the storage abstraction.
 *
 * Deliberately a plain `class`, NOT a `data class` — a lazy `@ManyToOne` back-reference
 * (`claim`) sits in the constructor, and Kotlin's compiler-generated `equals`/`hashCode`/
 * `toString` for a data class touch every constructor property, including that one. Calling any
 * of them on a managed-but-uninitialized Hibernate proxy outside an active session (a log line, a
 * `Set`/`contains()` check, a test assertion on a detached instance) throws
 * `LazyInitializationException`. This is a real bug this codebase already hit and fixed in
 * `BudgetLine`/`InvoiceLine` (see MEMORY.md) — id-based `equals`/`hashCode` and a `claim`-free
 * `toString` avoid it here from the start, which matters more for this class than most because it
 * participates in the reimbursement journal-posting path (`ExpenseClaimService.approve`).
 */
@Entity
@Table(name = "expense_claim_lines")
class ExpenseClaimLine(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    var claim: ExpenseClaim? = null,

    @Column(name = "account_id", nullable = false)
    @Schema(description = "FK to a non-header EXPENSE-type GL account")
    val accountId: UUID,

    @Column(nullable = false, length = 500)
    @Schema(example = "Taxi to client site", description = "What the expense was for")
    val description: String,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "3500.000000", description = "Amount incurred for this line")
    val amount: BigDecimal,

    @Column(name = "date_incurred", nullable = false)
    @Schema(example = "2026-08-01", description = "Date the expense was actually incurred")
    val dateIncurred: LocalDate,

    @Column(name = "receipt_reference", nullable = true, length = 500)
    @Schema(description = "Optional plain string/URL pointer to a receipt — no OCR/upload pipeline in this first cut")
    val receiptReference: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "modified_at", nullable = false)
    var modifiedAt: Instant = Instant.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExpenseClaimLine) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "ExpenseClaimLine(id=$id, accountId=$accountId, description='$description', amount=$amount, dateIncurred=$dateIncurred)"
}

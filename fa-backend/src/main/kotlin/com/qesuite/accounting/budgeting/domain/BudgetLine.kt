package com.qesuite.accounting.budgeting.domain

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
import java.util.UUID

/**
 * A single (account, period, amount) budgeted line within a [Budget] — e.g. "Office Supplies
 * (account X), March 2026 (period Y), budgeted at 50,000". `accountId` must reference a
 * non-header account (IAS 1 §29 — the same rule journal posting enforces, since a header account
 * has no meaningful standalone actual balance to compare a budget against) belonging to the same
 * `entityId` as the parent budget; `periodId` must likewise belong to that entity — both validated
 * in `BudgetService`, not at the JPA layer.
 *
 * Deliberately NOT a `data class`: `budget` is a lazy `@ManyToOne` back-reference, and a
 * Kotlin-generated `equals`/`hashCode`/`toString` would touch it — calling any of those on a
 * managed-but-uninitialized proxy outside an active Hibernate session (a log line, a `Set`/
 * `contains()` check, a test assertion on a detached instance) throws
 * `LazyInitializationException`. JPA entities should use id-based equality anyway (field-based
 * equality on a mutable entity breaks hash-based collection contracts once a field changes after
 * insertion), so that's what's implemented below instead of relying on the compiler default.
 */
@Entity
@Table(name = "budget_lines")
class BudgetLine(
    @Id
    @Column(name = "id", nullable = false)
    val id: UUID = UUID.randomUUID(),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    var budget: Budget? = null,

    @Column(nullable = false)
    @Schema(description = "FK to a non-header GL account")
    val accountId: UUID,

    @Column(nullable = false)
    @Schema(description = "FK to the accounting period this line's amount applies to")
    val periodId: UUID,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "50000.000000", description = "Budgeted amount for this account in this period")
    val amount: BigDecimal,

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var modifiedAt: Instant = Instant.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BudgetLine) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String =
        "BudgetLine(id=$id, accountId=$accountId, periodId=$periodId, amount=$amount)"
}

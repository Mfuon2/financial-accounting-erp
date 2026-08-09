package com.qesuite.accounting.budgeting.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Budget Master Entity.
 *
 * A budget is a named plan spanning one or more accounting [com.qesuite.accounting.ap.domain.Period]s
 * (e.g. a 12-month operating budget), broken into per-account, per-period [BudgetLine]s — never a
 * single header amount. This is why `periodId` (inherited from [BaseFinancialEntity]) is left `null`
 * on the header: a budget doesn't belong to one period, its lines each do.
 *
 * Never posts a journal entry and never touches the ledger — it is a planning artifact compared
 * against actual ledger activity by [com.qesuite.accounting.budgeting.service.BudgetService]'s
 * variance report, not a transactional document. This is also why it does not require an
 * `@RequireIdempotencyKey` on creation, unlike Invoice/Bill/Payment/JournalEntry (CLAUDE.md §4):
 * creating a budget has no double-posting risk to guard against, same reasoning as Category.
 */
@Entity
@Table(name = "budgets")
class Budget(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,

    @Column(nullable = false, length = 200)
    @Schema(example = "FY2026 Operating Budget", description = "Human-readable budget name")
    var name: String,

    @Column(nullable = false, length = 20)
    @Schema(example = "DRAFT", description = "Current status in the budget lifecycle")
    @Enumerated(EnumType.STRING)
    var status: BudgetStatus = BudgetStatus.DRAFT,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "1200000.000000", description = "Sum of all budget line amounts — always derived from lines, never client-supplied")
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = true, columnDefinition = "TEXT")
    @Schema(example = "Approved by the board 2026-01-15", description = "Free-text notes")
    var notes: String? = null,

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = null) {

    /** Budget lines (child items) — one row per (account, period, amount) triplet. */
    @OneToMany(mappedBy = "budget", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JsonIgnoreProperties("budget")
    val lines: MutableList<BudgetLine> = mutableListOf()

    /** Optimistic locking. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    fun addLine(line: BudgetLine) {
        lines.add(line)
        line.budget = this
    }

    fun clearLines() {
        lines.clear()
    }
}

/**
 * Budget lifecycle. Deliberately simpler than Invoice/Bill — a budget has no payment sub-lifecycle.
 * Only a `DRAFT` budget's lines/name/notes may be edited; `APPROVED` is a frozen plan (corrections
 * are void-and-recreate, matching the immutability-after-approval philosophy used everywhere else
 * in this codebase — CLAUDE.md's "corrections are always reversals, never edits").
 */
enum class BudgetStatus {
    DRAFT,
    APPROVED,
    VOID;

    fun canTransitionTo(next: BudgetStatus): Boolean = when (this) {
        DRAFT -> next == APPROVED || next == VOID
        APPROVED -> next == VOID
        VOID -> false // terminal
    }
}

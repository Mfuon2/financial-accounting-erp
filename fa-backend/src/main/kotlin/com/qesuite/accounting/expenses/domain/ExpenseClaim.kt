package com.qesuite.accounting.expenses.domain

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
import java.time.LocalDate
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Expense Management (T&E), workplan.md Phase 1
 * item 3.
 *
 * An expense claim is an employee's request for reimbursement of out-of-pocket expenses,
 * broken into per-item [ExpenseClaimLine]s (each pointing at the expense GL account it should be
 * debited to). `periodId` (inherited from [BaseFinancialEntity]) is left `null` until [approve]
 * resolves it from `claimDate` — mirrors `Bill`'s pattern
 * ([com.qesuite.accounting.payables.service.BillService.approveBill]) rather than
 * `Invoice`'s (which requires the caller to supply `periodId` up front), because a claim's period
 * is unambiguous from its single `claimDate` and there is no reason to burden the submission form
 * with a period picker.
 *
 * Unlike [com.qesuite.accounting.budgeting.domain.Budget], an approved claim DOES post a real
 * journal entry (the reimbursement liability) — see
 * [com.qesuite.accounting.expenses.service.ExpenseClaimService.approve] — so, per CLAUDE.md §11/§17,
 * that posting path is held to the same debits-equal-credits test discipline as Invoice/Bill.
 *
 * Lifecycle: DRAFT → SUBMITTED → APPROVED → REIMBURSED, or SUBMITTED → REJECTED. A REJECTED claim
 * may be reopened straight back to DRAFT for correction and resubmission (see
 * [ExpenseClaimStatus.canTransitionTo]) rather than being cloned into a new row — simpler, and
 * safe specifically because a REJECTED claim never posted anything, so there is no immutable
 * ledger history to preserve by forcing a fresh document (contrast with `Invoice`, where a posted
 * document must be corrected via a credit note, never edited in place).
 */
@Entity
@Table(name = "expense_claims")
class ExpenseClaim(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,

    @Column(name = "employee_id", nullable = false)
    @Schema(description = "FK to the users.User claiming reimbursement")
    var employeeId: UUID,

    @Column(name = "claim_date", nullable = false)
    @Schema(example = "2026-08-01", description = "Date the claim was raised; also drives which accounting period the reimbursement journal posts into")
    var claimDate: LocalDate,

    @Column(nullable = false, length = 20)
    @Schema(example = "DRAFT", description = "Current status in the expense-claim lifecycle")
    @Enumerated(EnumType.STRING)
    var status: ExpenseClaimStatus = ExpenseClaimStatus.DRAFT,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "24500.000000", description = "Sum of all claim line amounts — always derived from lines, never client-supplied")
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = true, columnDefinition = "TEXT")
    @Schema(example = "March client site visits", description = "Free-text notes")
    var notes: String? = null,

    @Column(name = "journal_entry_id", nullable = true)
    @Schema(description = "The reimbursement journal entry posted on approval; null until APPROVED")
    var journalEntryId: UUID? = null,

    @Column(name = "rejection_reason", nullable = true, columnDefinition = "TEXT")
    @Schema(description = "Reason recorded when a SUBMITTED claim is REJECTED")
    var rejectionReason: String? = null,

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = null) {

    /** Expense claim lines (child items) — one row per expense item. */
    @OneToMany(mappedBy = "claim", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JsonIgnoreProperties("claim")
    val lines: MutableList<ExpenseClaimLine> = mutableListOf()

    /** Optimistic locking. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    fun addLine(line: ExpenseClaimLine) {
        lines.add(line)
        line.claim = this
    }

    fun clearLines() {
        lines.clear()
    }
}

/**
 * Expense claim lifecycle. `APPROVED` is transitional in practice — [ExpenseClaimService.approve]
 * posts the reimbursement journal and advances the claim straight to `REIMBURSED` within the same
 * transaction, exactly mirroring `InvoiceService.approve`'s auto-progression from `APPROVED` to
 * `SENT`. Both statuses are still modeled explicitly (rather than collapsing them into one) so a
 * future step — e.g. a controller distinguishing "accounting sign-off happened" from "the
 * liability is posted" — has somewhere to hook in without a migration.
 *
 * `REIMBURSED` here means the reimbursement liability has been posted to the GL (DR expense
 * accounts / CR Employee Reimbursements Payable) — it does NOT mean cash has left the bank. Actual
 * disbursement (marking the liability paid via a bank transfer) is out of scope for this module —
 * see the handover notes for why (shared territory with Payments / Cash & Bank Management).
 */
enum class ExpenseClaimStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    REIMBURSED,
    REJECTED;

    fun canTransitionTo(next: ExpenseClaimStatus): Boolean = when (this) {
        DRAFT -> next == SUBMITTED
        SUBMITTED -> next == APPROVED || next == REJECTED
        APPROVED -> next == REIMBURSED
        REJECTED -> next == DRAFT // reopen for correction + resubmission
        REIMBURSED -> false // terminal
    }
}

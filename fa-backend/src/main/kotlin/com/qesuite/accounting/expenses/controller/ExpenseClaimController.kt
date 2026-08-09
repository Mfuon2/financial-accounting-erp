package com.qesuite.accounting.expenses.controller

import com.qesuite.accounting.expenses.domain.ExpenseClaimStatus
import com.qesuite.accounting.expenses.dto.CreateExpenseClaimCommand
import com.qesuite.accounting.expenses.dto.ExpenseClaimResponse
import com.qesuite.accounting.expenses.dto.RejectExpenseClaimCommand
import com.qesuite.accounting.expenses.dto.UpdateExpenseClaimCommand
import com.qesuite.accounting.expenses.service.ExpenseClaimService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Expense Management (T&E) REST endpoints,
 * workplan.md Phase 1 item 3.
 *
 * Every id-scoped endpoint resolves the resource first and checks
 * [SecurityUtils.requireOwnEntity] against the resource's OWN `entityId` before acting on it —
 * never against a client-supplied `entityId`, and never skipped for a "read-only" action, per this
 * codebase's standing IDOR lesson (see MEMORY.md Known Issues — the codebase-wide IDOR sweep, and
 * the `GlobalApprovalController` gap its own re-verification later found).
 *
 * Role gates use [RoleSets]: list/read = `BROAD_READ` (includes DATA_ENTRY — any employee needs to
 * see their own claims, matching `JournalController.getAll`/`.getById`'s "everyone needs it to do
 * their job" precedent, unlike Budgeting's `ACCOUNTING_READ` which deliberately excludes
 * DATA_ENTRY for managerial planning data); create/update/submit/reopen = `PREPARER`
 * (DATA_ENTRY-includable — filing your own expense claim is exactly the draft-level
 * create/update/submit action `PREPARER` models, matching `JournalController.create`/`.submit`);
 * approve/reject = `APPROVER` (matches `JournalController.approve`/`BillController.approveBill` —
 * the same segregation-of-duties sign-off tier, on top of which [ExpenseClaimService.approve]
 * additionally rejects the specific case of an approver approving their own claim).
 *
 * No `@RequireIdempotencyKey` on create: this mirrors this codebase's actual (not aspirational)
 * precedent — none of `InvoiceController.create`, `BillController.createBill`, or
 * `PaymentController`'s equivalent apply it today despite CLAUDE.md §4 naming invoices/bills/
 * payments/journal entries explicitly; a double-submit of a DRAFT claim is low-severity (an extra
 * draft row, not a double-posting) and the real double-posting risk — clicking Approve twice — is
 * already closed by the DRAFT/SUBMITTED state-machine guard in [ExpenseClaimService.approve],
 * exactly as it is for `Invoice.approve`/`BillService.approveBill`. Flagged, not silently ignored:
 * see this module's handover notes for the pre-existing, codebase-wide gap this leaves open.
 */
@Tag(name = "Expense Claims", description = "Employee expense claims, approval routing, and reimbursement posting — Project.md Domain 1")
@RestController
@RequestMapping("/api/v1/expense-claims")
class ExpenseClaimController(
    private val expenseClaimService: ExpenseClaimService,
) {

    @Operation(summary = "List expense claims for an entity (paginated), optionally filtered by status or employee")
    @GetMapping
    @PreAuthorize(RoleSets.BROAD_READ)
    fun list(
        @RequestParam @Parameter(description = "Entity (tenant) ID", required = true) entityId: UUID,
        @RequestParam(required = false) status: ExpenseClaimStatus?,
        @RequestParam(required = false) employeeId: UUID?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<ApiResponse<PagedResponse<ExpenseClaimResponse>>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = expenseClaimService.findByEntity(entityId, status, employeeId, pageable)
        return ResponseEntity.ok(ApiResponse.success(page.toPagedResponse { expenseClaimService.toResponse(it) }))
    }

    @Operation(summary = "Get an expense claim by ID, with its lines")
    @GetMapping("/{id}")
    @PreAuthorize(RoleSets.BROAD_READ)
    fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<ExpenseClaimResponse>> {
        val claim = expenseClaimService.findById(id)
        SecurityUtils.requireOwnEntity(claim.entityId)
        return ResponseEntity.ok(ApiResponse.success(expenseClaimService.toResponse(claim)))
    }

    @Operation(summary = "Create a new expense claim in DRAFT status")
    @PostMapping
    @PreAuthorize(RoleSets.PREPARER)
    fun create(@Valid @RequestBody command: CreateExpenseClaimCommand): ResponseEntity<ApiResponse<ExpenseClaimResponse>> {
        SecurityUtils.requireOwnEntity(command.entityId)
        val claim = expenseClaimService.createDraft(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(expenseClaimService.toResponse(claim)))
    }

    @Operation(summary = "Update a DRAFT expense claim's claim date, notes, and/or lines")
    @PutMapping("/{id}")
    @PreAuthorize(RoleSets.PREPARER)
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody command: UpdateExpenseClaimCommand,
    ): ResponseEntity<ApiResponse<ExpenseClaimResponse>> {
        SecurityUtils.requireOwnEntity(expenseClaimService.findById(id).entityId)
        val claim = expenseClaimService.update(id, command)
        return ResponseEntity.ok(ApiResponse.success(expenseClaimService.toResponse(claim)))
    }

    @Operation(summary = "Submit a DRAFT expense claim for approval")
    @PostMapping("/{id}/submit")
    @PreAuthorize(RoleSets.PREPARER)
    fun submit(@PathVariable id: UUID): ResponseEntity<ApiResponse<ExpenseClaimResponse>> {
        SecurityUtils.requireOwnEntity(expenseClaimService.findById(id).entityId)
        val claim = expenseClaimService.submit(id)
        return ResponseEntity.ok(ApiResponse.success(expenseClaimService.toResponse(claim)))
    }

    @Operation(
        summary = "Approve a SUBMITTED expense claim",
        description = "Posts the reimbursement journal entry (DR expense accounts / CR Employee Reimbursements " +
            "Payable) and advances the claim to REIMBURSED in the same transaction. The approving user may not " +
            "be the claim's own employee (segregation of duties).",
    )
    @PostMapping("/{id}/approve")
    @PreAuthorize(RoleSets.APPROVER)
    fun approve(@PathVariable id: UUID): ResponseEntity<ApiResponse<ExpenseClaimResponse>> {
        SecurityUtils.requireOwnEntity(expenseClaimService.findById(id).entityId)
        val claim = expenseClaimService.approve(id, SecurityUtils.currentUser().userId)
        return ResponseEntity.ok(ApiResponse.success(expenseClaimService.toResponse(claim)))
    }

    @Operation(summary = "Reject a SUBMITTED expense claim")
    @PostMapping("/{id}/reject")
    @PreAuthorize(RoleSets.APPROVER)
    fun reject(
        @PathVariable id: UUID,
        @Valid @RequestBody command: RejectExpenseClaimCommand,
    ): ResponseEntity<ApiResponse<ExpenseClaimResponse>> {
        SecurityUtils.requireOwnEntity(expenseClaimService.findById(id).entityId)
        val claim = expenseClaimService.reject(id, command.reason)
        return ResponseEntity.ok(ApiResponse.success(expenseClaimService.toResponse(claim)))
    }

    @Operation(summary = "Reopen a REJECTED expense claim back to DRAFT for correction and resubmission")
    @PostMapping("/{id}/reopen")
    @PreAuthorize(RoleSets.PREPARER)
    fun reopen(@PathVariable id: UUID): ResponseEntity<ApiResponse<ExpenseClaimResponse>> {
        SecurityUtils.requireOwnEntity(expenseClaimService.findById(id).entityId)
        val claim = expenseClaimService.reopen(id)
        return ResponseEntity.ok(ApiResponse.success(expenseClaimService.toResponse(claim)))
    }
}

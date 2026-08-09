package com.qesuite.accounting.budgeting.controller

import com.qesuite.accounting.budgeting.dto.BudgetResponse
import com.qesuite.accounting.budgeting.dto.BudgetVarianceReportResponse
import com.qesuite.accounting.budgeting.dto.CreateBudgetCommand
import com.qesuite.accounting.budgeting.dto.UpdateBudgetCommand
import com.qesuite.accounting.budgeting.dto.VoidBudgetCommand
import com.qesuite.accounting.budgeting.service.BudgetService
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
 * Project.md Domain 1 (Financial Operations) — Budgeting module REST endpoints.
 *
 * Every id-scoped endpoint resolves the resource first and checks
 * [SecurityUtils.requireOwnEntity] against the resource's OWN `entityId` before acting on it —
 * never against a client-supplied `entityId`, and never skipped for a "read-only" action. This
 * codebase has twice found real IDOR gaps from an endpoint that skipped this check (see
 * MEMORY.md's Known Issues — the codebase-wide IDOR sweep, and the `GlobalApprovalController` gap
 * found during its own re-verification), so every endpoint below is checked against that lesson
 * explicitly, not assumed safe by analogy to a sibling controller.
 *
 * Role gates use [RoleSets]: read/variance-report = `ACCOUNTING_READ` (excludes DATA_ENTRY —
 * budgets are managerial planning/reporting data, not routine data entry, matching
 * `TrialBalanceController`'s precedent for a formal report); create/update =
 * `ACCOUNTING_OP` (matches `BillController.recordPayment`'s precedent for a financial-judgment
 * action that isn't itself the final sign-off); approve/void = `APPROVER` (matches
 * `JournalController.approve`/`BillController.approveBill` — a segregation-of-duties sign-off).
 *
 * No `@RequireIdempotencyKey` on create — a budget never posts to the ledger, so there is no
 * double-posting risk to guard against (same reasoning as `CategoryController`), unlike
 * Invoice/Bill/Payment/JournalEntry creation.
 */
@Tag(name = "Budgeting", description = "Budget planning and budget-vs-actual variance reporting — Project.md Domain 1")
@RestController
@RequestMapping("/api/v1/budgets")
class BudgetController(
    private val budgetService: BudgetService,
) {

    @Operation(summary = "List budgets for an entity (paginated)")
    @GetMapping
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun list(
        @RequestParam @Parameter(description = "Entity (tenant) ID", required = true) entityId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<ApiResponse<PagedResponse<BudgetResponse>>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = budgetService.findByEntity(entityId, pageable)
        return ResponseEntity.ok(ApiResponse.success(page.toPagedResponse { budgetService.toResponse(it) }))
    }

    @Operation(summary = "Get a budget by ID, with its lines")
    @GetMapping("/{id}")
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<BudgetResponse>> {
        val budget = budgetService.findById(id)
        SecurityUtils.requireOwnEntity(budget.entityId)
        return ResponseEntity.ok(ApiResponse.success(budgetService.toResponse(budget)))
    }

    @Operation(summary = "Create a new budget in DRAFT status")
    @PostMapping
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun create(@Valid @RequestBody command: CreateBudgetCommand): ResponseEntity<ApiResponse<BudgetResponse>> {
        SecurityUtils.requireOwnEntity(command.entityId)
        val budget = budgetService.createDraft(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(budgetService.toResponse(budget)))
    }

    @Operation(summary = "Update a DRAFT budget's name, notes, and/or lines")
    @PutMapping("/{id}")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody command: UpdateBudgetCommand,
    ): ResponseEntity<ApiResponse<BudgetResponse>> {
        SecurityUtils.requireOwnEntity(budgetService.findById(id).entityId)
        val budget = budgetService.update(id, command)
        return ResponseEntity.ok(ApiResponse.success(budgetService.toResponse(budget)))
    }

    @Operation(summary = "Approve a DRAFT budget")
    @PostMapping("/{id}/approve")
    @PreAuthorize(RoleSets.APPROVER)
    fun approve(@PathVariable id: UUID): ResponseEntity<ApiResponse<BudgetResponse>> {
        SecurityUtils.requireOwnEntity(budgetService.findById(id).entityId)
        val budget = budgetService.approve(id)
        return ResponseEntity.ok(ApiResponse.success(budgetService.toResponse(budget)))
    }

    @Operation(summary = "Void a DRAFT or APPROVED budget")
    @PostMapping("/{id}/void")
    @PreAuthorize(RoleSets.APPROVER)
    fun void(
        @PathVariable id: UUID,
        @Valid @RequestBody command: VoidBudgetCommand,
    ): ResponseEntity<ApiResponse<BudgetResponse>> {
        SecurityUtils.requireOwnEntity(budgetService.findById(id).entityId)
        val budget = budgetService.void(id, command.reason, SecurityUtils.currentUser().userId)
        return ResponseEntity.ok(ApiResponse.success(budgetService.toResponse(budget)))
    }

    @Operation(
        summary = "Budget-vs-actual variance report",
        description = "For every line, compares the budgeted amount against actual ledger activity " +
            "for that account over that line's period date range."
    )
    @GetMapping("/{id}/variance")
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun variance(@PathVariable id: UUID): ResponseEntity<ApiResponse<BudgetVarianceReportResponse>> {
        SecurityUtils.requireOwnEntity(budgetService.findById(id).entityId)
        return ResponseEntity.ok(ApiResponse.success(budgetService.varianceReport(id)))
    }
}

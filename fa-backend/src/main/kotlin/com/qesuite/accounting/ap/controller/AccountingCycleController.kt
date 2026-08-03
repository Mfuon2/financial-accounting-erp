package com.qesuite.accounting.ap.controller

import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.AccountingCycleResult
import com.qesuite.accounting.ap.service.AccountingCycleService
import com.qesuite.accounting.ap.service.ClosingParams
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

// ──────────────────────────────────────────────────────────────────────────────
// §10.2 — Request / Response DTOs
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Request body for [AccountingCycleController.runFullCycle].
 *
 * @property entityId  Legal entity (tenant) UUID.
 * @property periodId  Accounting period to close; must currently be OPEN or ADJUSTING.
 * @property params    Optional closing parameters; all fields have safe defaults.
 */
data class RunCycleRequest(
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,
    @field:NotNull(message = "periodId is required")
    val periodId: UUID,
    val params: ClosingParams = ClosingParams()
)

/**
 * Request body for [AccountingCycleController.transitionPeriod].
 *
 * @property entityId   Legal entity owning the period.
 * @property periodId   Period to transition.
 * @property newStatus  Target [PeriodStatus].
 */
data class TransitionPeriodRequest(
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,
    @field:NotNull(message = "periodId is required")
    val periodId: UUID,
    @field:NotNull(message = "newStatus is required")
    val newStatus: PeriodStatus
)

// ──────────────────────────────────────────────────────────────────────────────
// §10.2 — Controller
// ──────────────────────────────────────────────────────────────────────────────

/**
 * REST API for the 9-step IFRS accounting cycle orchestration.
 *
 * Exposes three endpoints:
 * - `POST /run`           — Execute the full 9-step cycle for a period.
 * - `POST /transition`    — Manually advance a period's status (ad-hoc / corrections).
 * - `GET  /validate-step` — Dry-run validation of a proposed status transition.
 *
 * All exceptions are handled by the global `GlobalExceptionHandler`; this controller
 * only surfaces success paths.
 */
@RestController
@RequestMapping("/api/v1/accounting-cycle")
@Tag(
    name = "Module 9: Accounting Cycle",
    description = """
Orchestrates the complete 9-step IFRS accounting cycle for a given entity and period.

**9-Step Cycle Overview:**
1. Journalize transactions (continuous — via journal endpoints)
2. Post to ledger (continuous — via journal endpoints)
3. Generate unadjusted trial balance
4. Record and post adjusting entries (accruals, deferrals, prepayment amortisation)
5. Generate adjusted trial balance
6. Prepare Income Statement / Profit & Loss
7. Prepare Balance Sheet
8. Post closing entries (revenues, expenses, dividends → Retained Earnings)
9. Generate post-closing trial balance

**Period State Machine:** The cycle drives the period through OPEN → ADJUSTING → CLOSING → CLOSED.
"""
)
class AccountingCycleController(
    private val accountingCycleService: AccountingCycleService
) {

    /**
     * Runs the full 9-step accounting cycle for the specified entity and period.
     *
     * The period must be OPEN (or already ADJUSTING if a partial run was previously
     * initiated). On success the period ends in CLOSED state and the response contains
     * three trial balance snapshots (unadjusted, adjusted, post-closing).
     *
     * Returns HTTP 200 with [AccountingCycleResult] wrapped in [ApiResponse].
     */
    @PostMapping("/run")
    @Operation(
        summary = "Run full 9-step accounting cycle",
        description = "Executes Steps 3–9 of the IFRS accounting cycle for the given period. " +
                "The period must be OPEN or ADJUSTING. On completion the period is CLOSED."
    )
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
    fun runFullCycle(
        @Valid @RequestBody request: RunCycleRequest
    ): ApiResponse<AccountingCycleResult> {
        SecurityUtils.requireOwnEntity(request.entityId)
        val result = accountingCycleService.runFullCycle(
            entityId = request.entityId,
            periodId = request.periodId,
            params = request.params
        )
        return ApiResponse.success(result)
    }

    /**
     * Manually transitions an accounting period to a new status.
     *
     * Validates pre-conditions before saving. Illegal transitions (e.g. OPEN → CLOSED)
     * are rejected by [AccountingCycleService.validateStep] with HTTP 422.
     */
    @PostMapping("/transition")
    @Operation(
        summary = "Manually transition a period status",
        description = "Advances the period to the requested status after validating " +
                "the transition is permitted by the state machine."
    )
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
    fun transitionPeriod(
        @Valid @RequestBody request: TransitionPeriodRequest
    ): ApiResponse<Unit> {
        SecurityUtils.requireOwnEntity(request.entityId)
        accountingCycleService.transitionPeriod(
            entityId = request.entityId,
            periodId = request.periodId,
            newStatus = request.newStatus
        )
        return ApiResponse.success(Unit)
    }

    /**
     * Validates that the requested status transition is permitted without executing it.
     *
     * Returns HTTP 200 with empty data if valid; throws a [com.qesuite.accounting.shared.exceptions.ValidationException]
     * (HTTP 422) if the transition is illegal. Useful for pre-flight checks in UI workflows.
     */
    @GetMapping("/validate-step")
    @Operation(
        summary = "Validate a proposed period-status transition (dry run)",
        description = "Checks all pre-conditions for the given transition without persisting any changes. " +
                "Returns 200 OK if the transition is valid, or 422 Unprocessable Entity if not."
    )
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun validateStep(
        @RequestParam entityId: UUID,
        @RequestParam periodId: UUID,
        @RequestParam targetStatus: PeriodStatus
    ): ApiResponse<Unit> {
        SecurityUtils.requireOwnEntity(entityId)
        accountingCycleService.validateStep(entityId, periodId, targetStatus)
        return ApiResponse.success(Unit)
    }
}

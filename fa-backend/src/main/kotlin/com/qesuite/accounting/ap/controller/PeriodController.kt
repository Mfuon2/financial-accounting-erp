package com.qesuite.accounting.ap.controller

import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/periods")
@Tag(
    name = "Module 9: Period Management",
    description = """
Manages the fiscal calendar lifecycle for each legal entity (tenant).

**Accounting Period State Machine:**
```
FUTURE → OPEN → ADJUSTING → CLOSING → CLOSED → REOPENED
```

- **FUTURE**: Not yet active. No postings permitted.
- **OPEN**: Active period. Journal entries and postings are allowed.
- **ADJUSTING**: Period-end adjusting entries in progress (accruals, deferrals).
- **CLOSING**: Closing entries are being generated. No new business transactions.
- **CLOSED**: Immutable. All entries are finalized.
- **REOPENED**: Previously closed; audit-triggered reopen for corrections only.

The `generateFiscalYear` endpoint bootstraps all 12 calendar months at once — every
period starts as **FUTURE**; none is auto-opened. Callers must explicitly transition
the period they want to start using to **OPEN** via the transition endpoint. Transitions
are validated — illegal jumps (e.g., OPEN → CLOSED) will return a `422 Unprocessable Entity`,
and only one period per entity may be `OPEN` at a time (attempting to open a second one
returns `422 Unprocessable Entity` with error code `PERIOD_ALREADY_OPEN`).

**Dependency:** Periods are required before any journal entries can be created 
(the `periodId` foreign key on `JournalEntry` must reference a valid open period).
"""
)
class PeriodController(private val periodService: PeriodService) {

    // ─────────────────────────────────────────────────────────────────────────
    // GET endpoints
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(
        summary = "List all accounting periods for an entity",
        description = "Returns all 12-month periods generated for the entity, sorted by start date ascending. " +
            "Optionally filter by status."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Periods returned"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or invalid entityId")
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun listPeriods(
        @RequestParam
        @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        entityId: UUID,

        @RequestParam(required = false)
        @Parameter(description = "Optional status filter", schema = Schema(implementation = PeriodStatus::class))
        status: PeriodStatus? = null
    ): ApiResponse<List<Period>> {
        SecurityUtils.requireOwnEntity(entityId)
        val periods = periodService.findAllByEntity(entityId)
        val filtered = if (status != null) periods.filter { it.status == status } else periods
        return ApiResponse.success(filtered)
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single accounting period by ID")
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Period found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Period not found")
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun getPeriod(
        @PathVariable
        @Parameter(description = "UUID of the accounting period", example = "660e8400-e29b-41d4-a716-446655440001")
        id: UUID
    ): ApiResponse<Period> {
        val period = periodService.findById(id)
        SecurityUtils.requireOwnEntity(period.entityId)
        return ApiResponse.success(period)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST endpoints
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/generate-fiscal-year")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Generate a full fiscal year",
        description = """
Atomically creates 12 accounting periods for the specified calendar year, scoped to
the given `entityId` (tenant).

**All 12 periods are initialised as `FUTURE`** — none is auto-opened, including
January. This is intentional: generating a (possibly historical) fiscal year must
never silently switch the entity's active working period. After generation, the
caller must explicitly transition the period they want to start using to `OPEN` via
`POST /{id}/transition`. No existing periods are overwritten — calling this endpoint
for a year that already exists returns `409 FISCAL_YEAR_ALREADY_EXISTS`.

Use this as the first setup step after onboarding a new legal entity.

**Request body:**
```json
{ "entityId": "550e8400-e29b-41d4-a716-446655440000", "fiscalYear": 2026 }
```
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Fiscal year created"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid entity ID or year"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Fiscal year already exists")
    )
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
    fun generateFiscalYear(
        @Valid @RequestBody request: GenerateFiscalYearRequest
    ): ApiResponse<String> {
        SecurityUtils.requireOwnEntity(request.entityId)
        periodService.generateFiscalYear(request.entityId, request.fiscalYear)
        return ApiResponse.success("Fiscal year ${request.fiscalYear} generated successfully.")
    }

    @PostMapping("/{id}/transition")
    @Operation(
        summary = "Transition a period to the next valid state",
        description = """
Advances (or rolls back) an accounting period through its state machine.  
The system validates that the requested transition is legal before applying it.

**Valid transitions:**
| From | To |
|---|---|
| FUTURE | OPEN |
| OPEN | ADJUSTING |
| ADJUSTING | CLOSING, OPEN |
| CLOSING | CLOSED, ADJUSTING |
| CLOSED | REOPENED |
| REOPENED | ADJUSTING, CLOSING, CLOSED |

Attempting an illegal transition returns `422 Unprocessable Entity` with
error code `INVALID_STATE_TRANSITION`.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transition applied"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Period not found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Illegal state transition")
    )
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
    fun transitionPeriod(
        @PathVariable
        @Parameter(description = "UUID of the accounting period to transition", example = "660e8400-e29b-41d4-a716-446655440001")
        id: UUID,

        @RequestParam
        @Parameter(description = "Target status to transition to", schema = Schema(implementation = PeriodStatus::class))
        nextStatus: PeriodStatus
    ): ApiResponse<String> {
        SecurityUtils.requireOwnEntity(periodService.findById(id).entityId)
        periodService.transitionPeriod(id, nextStatus)
        return ApiResponse.success("Period status transitioned to $nextStatus")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Request DTOs
// ─────────────────────────────────────────────────────────────────────────────

@Schema(description = "Request body for generating a full fiscal year")
data class GenerateFiscalYearRequest(
    @field:NotNull
    @Schema(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000")
    val entityId: UUID,

    @field:NotNull
    @field:Min(2000)
    @field:Max(2100)
    @Schema(description = "4-digit calendar year", example = "2026")
    val fiscalYear: Int
)

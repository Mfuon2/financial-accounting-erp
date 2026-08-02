package com.qesuite.accounting.journal.controller

import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.journal.service.ClosingService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1/closing")
@Tag(
    name = "Module 8: Period-End Closing",
    description = """
The period-end closing process is the final step of the accounting cycle. It zeros out
all temporary accounts (Revenue and Expense) and transfers the net income (or loss) to
Retained Earnings — resetting the P&L accounts for the new period.

**The four-step closing process (in order):**

**Step 1 — Close Revenue Accounts**
All revenue account balances are transferred to the Income Summary account (or directly
to Retained Earnings if no Income Summary account is configured).
```
DR  Service Revenue          50,000
  CR  Income Summary                50,000
```

**Step 2 — Close Expense Accounts**
All expense account balances are transferred to the Income Summary account.
```
DR  Income Summary           35,000
  CR  Operating Expenses           35,000
```

**Step 3 — Close Income Summary to Retained Earnings**
The net balance of the Income Summary (net income = $15,000 in this example) is
transferred to Retained Earnings.
```
DR  Income Summary           15,000
  CR  Retained Earnings            15,000
```

**Step 4 — Close Dividends/Drawings to Retained Earnings**
Any dividend or drawing accounts are zeroed out against Retained Earnings.
```
DR  Retained Earnings        5,000
  CR  Dividends Paid               5,000
```

**After closing:**
- All temporary account balances are zero
- Retained Earnings reflects the cumulative net income
- The period status is transitioned to **CLOSED**
- No further postings are permitted in the closed period

**Reopening:** If an error is discovered after closing, the period can be reopened
via `POST /reopen`, placing it in **REOPENED** status and allowing correcting entries
before re-closing. A mandatory audit reason is required for every reopen.

**IFRS context:** IAS 1 requires that closing entries be made after financial 
statements are prepared — the `closePeriod` operation validates that the period
is in CLOSING status, which requires the financial statement workflow to be completed.
"""
)
class ClosingController(
    private val closingService: ClosingService,
    private val periodService: PeriodService,
) {

    @GetMapping("/preview")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Preview closing entries without posting",
        description = "Computes the journal entries that would be created by the closing process for the given period, without actually posting anything. Use this to verify before running the actual close."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Closing preview — no changes made"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Period not found")
    )
    fun previewClosing(
        @RequestParam @Parameter(description = "Tenant/entity UUID") entityId: UUID,
        @RequestParam @Parameter(description = "Period UUID to preview") periodId: UUID
    ): ApiResponse<com.qesuite.accounting.journal.service.ClosingPreview> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(closingService.previewClosing(entityId, periodId))
    }

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Execute the four-step period-end closing process",
        description = """
Runs the complete closing entry sequence for the period, atomically creating and 
posting all required journal entries.

**Sequence of journal entries created:**
1. `DR Revenue accounts / CR Income Summary` (if `incomeSummaryAccountId` provided)
2. `DR Income Summary / CR Expense accounts` (if `incomeSummaryAccountId` provided)
3. `DR/CR Income Summary / CR/DR Retained Earnings` (net income transfer)
4. `DR Retained Earnings / CR Dividends` (dividend accounts, if any)

Only accounts with non-zero balances are included in the closing entries.

**Income Summary:** If `incomeSummaryAccountId` is not provided, revenues and expenses
are closed directly to Retained Earnings in a single step (simplified close).

**Retained Earnings:** If `retainedEarningsAccountId` is not provided, the system will
attempt to locate the account with subtype `RETAINED_EARNINGS` automatically.

**After successful execution:**
- The period status is transitioned to **CLOSED**
- All temporary accounts have zero balances
- The operation is logged in the `AuditLog` with action `CLOSE`

**Required role:** CONTROLLER_CFO or SYSTEM_ADMIN.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Closing entries posted and period closed"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Retained Earnings account not found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Period is not in CLOSING status or closing has already been run")
    )
    fun runClosing(
        @RequestParam @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000") entityId: UUID,
        @RequestParam @Parameter(description = "Period UUID to close", example = "660e8400-e29b-41d4-a716-446655440001") periodId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Closing date for journal entries (defaults to today)", example = "2026-03-31") closingDate: LocalDate?,
        @RequestParam(required = false)
        @Parameter(description = "UUID of the Retained Earnings account. Auto-detected from account subtype if omitted.") retainedEarningsAccountId: UUID?,
        @RequestParam(required = false)
        @Parameter(description = "UUID of the Income Summary account. If omitted, revenues/expenses close directly to Retained Earnings.") incomeSummaryAccountId: UUID?
    ): ApiResponse<Unit> {
        SecurityUtils.requireOwnEntity(entityId)
        closingService.runClosing(
            entityId = entityId,
            periodId = periodId,
            closingDate = closingDate ?: LocalDate.now(),
            retainedEarningsAccountId = retainedEarningsAccountId,
            incomeSummaryAccountId = incomeSummaryAccountId
        )
        return ApiResponse.success(Unit)
    }

    @PostMapping("/reopen")
    @PreAuthorize("hasAnyRole('CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Reopen a closed accounting period",
        description = """
Transitions a **CLOSED** period back to **REOPENED** status, allowing correcting 
journal entries to be posted.

**When to use:**
- A posting error was discovered after period close
- An auditor has requested a correction to a prior period
- A late supplier invoice needs to be recorded in the correct period

**Restrictions:**
- Only periods in `CLOSED` status can be reopened
- The period will be placed in `REOPENED` status (not `OPEN` — this distinguishes
  normal operations from audit corrections)
- A mandatory `reason` must be supplied — this is recorded in the `AuditLog` and
  cannot be empty or blank
- After corrections are made, the period must be re-closed via `POST /run`

**Required role:** CONTROLLER_CFO or SYSTEM_ADMIN. AUDITOR role may trigger this
with additional approval controls depending on configuration.

**Audit impact:** The reopen event, the user who performed it, the reason, and the
timestamp are all permanently recorded in the `AuditLog` table (immutable).
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Period reopened successfully"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Period not found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Period is not in CLOSED status or reason is blank")
    )
    fun reopenPeriod(
        @RequestParam @Parameter(description = "Period UUID to reopen", example = "660e8400-e29b-41d4-a716-446655440001") periodId: UUID,
        @RequestParam @Parameter(description = "Mandatory audit reason for the reopen (recorded permanently)", example = "Late supplier invoice Q1-2026 received after close date") reason: String
    ): ApiResponse<Unit> {
        // No entityId param on this endpoint — resolve the period's owning entity first.
        SecurityUtils.requireOwnEntity(periodService.findById(periodId).entityId)
        closingService.reopenPeriod(periodId, reason)
        return ApiResponse.success(Unit)
    }
}

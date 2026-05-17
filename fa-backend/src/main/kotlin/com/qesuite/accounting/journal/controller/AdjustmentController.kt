package com.qesuite.accounting.journal.controller

import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.journal.service.AdjustmentService
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.shared.exceptions.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/adjustments")
@Tag(
    name = "Module 6: Adjusting Entries",
    description = """
Adjusting entries are period-end journal entries that correct the timing of revenue and
expense recognition in accordance with the **accrual basis of accounting** (IAS 1 §27).
They are recorded after the trial balance is prepared and before financial statements
are generated.

**The four categories of adjusting entries:**

**1. Accruals (Expense Accruals)**
Expenses incurred but not yet paid or recorded. Example: salaries payable at month-end.
```
DR  Salaries Expense        5,000
  CR  Accrued Liabilities       5,000
```

**2. Accruals (Revenue Accruals)**
Revenue earned but not yet invoiced or received. Example: services rendered on Dec 31
but invoiced in January.
```
DR  Accrued Revenue         3,000
  CR  Service Revenue           3,000
```

**3. Deferrals (Prepaid Expenses)**
Cash paid in advance for future expenses. The prepaid asset is amortised monthly.
Example: 12-month insurance premium paid upfront.
```
DR  Insurance Expense       1,000
  CR  Prepaid Insurance         1,000
```

**4. Deferrals (Unearned Revenue)**
Cash received in advance for services not yet delivered. Revenue is recognised
as performance obligations are satisfied (IFRS 15).
```
DR  Deferred Revenue        2,000
  CR  Service Revenue           2,000
```

**Automation:** The `amortizePrepayments` and `recognizeUnearnedRevenue` endpoints
run batch processes across all eligible accounts for the entity, creating individual
journal entries for each item automatically.

**Workflow position:** Adjusting entries must be completed before calling 
`GET /trial-balance` for the adjusted position and before generating financial statements.
"""
)
class AdjustmentController(private val adjustmentService: AdjustmentService) {

    @PostMapping("/accruals")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Record a manual accrual entry",
        description = """
Creates an accrual-type adjusting journal entry for an expense or revenue item that
has been incurred/earned but not yet recorded in the books.

**Common accrual scenarios:**
- Accrued wages (salaries earned but payroll not run until next period)
- Accrued interest on a loan for the current period
- Revenue earned under a service contract not yet invoiced

The entry is created through the standard Journal Entry Engine (DRAFT → approval
workflow applies). The `sourceType` field is automatically set to `ACCRUAL`.

**Reversibility:** Accrual entries should be reversed at the start of the next
period using `POST /journal-entries/{id}/reverse` to prevent double-counting.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Accrual entry created in DRAFT status"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid command structure or missing fields")
    )
    fun recordAccrual(
        @Valid @RequestBody command: CreateJournalEntryCommand
    ): ApiResponse<JournalEntry> {
        return ApiResponse.success(adjustmentService.recordAccrual(command))
    }

    @PostMapping("/deferrals")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Record a manual deferral entry",
        description = """
Creates a deferral-type adjusting journal entry for cash that has been paid or received
in advance, where the related expense or revenue must be deferred to a future period.

**Common deferral scenarios:**
- Monthly amortisation of an annual insurance premium (Prepaid → Expense)
- Recognition of a monthly portion of annual subscription revenue (Deferred Revenue → Revenue)
- Amortisation of loan origination fees over the loan term

The entry is created through the standard Journal Entry Engine. `sourceType` is 
automatically set to `DEFERRAL`.

**Note:** For automated batch amortisation of all prepayments in a period, use
`POST /prepayments/amortize` instead.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Deferral entry created in DRAFT status"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid command structure")
    )
    fun recordDeferral(
        @Valid @RequestBody command: CreateJournalEntryCommand
    ): ApiResponse<JournalEntry> {
        return ApiResponse.success(adjustmentService.recordDeferral(command))
    }

    @PostMapping("/prepayments/amortize")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Run automated amortisation for all prepaid expenses",
        description = """
Scans all accounts with subtype `CURRENT_PREPAID` for the entity and automatically
generates monthly amortisation journal entries for each prepaid item, posting the
proportional expense to the appropriate expense account.

**Process:**
1. Identify all accounts with remaining prepaid balances for the entity
2. Calculate the monthly amortisation amount per item
3. Create and post journal entries: `DR Expense / CR Prepaid Asset`
4. Return a summary of all entries created

This is called as part of the automated period-end adjusting entries batch, typically
triggered after the trial balance has been reviewed and before financial statements
are generated.

**Idempotency warning:** Calling this endpoint twice in the same period will 
double-count the amortisation. Ensure it is called exactly once per period.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Amortisation entries created"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Period is not in OPEN or ADJUSTING status")
    )
    fun amortizePrepayments(
        @RequestParam @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000") entityId: UUID,
        @RequestParam @Parameter(description = "Period UUID for which to run amortisation", example = "660e8400-e29b-41d4-a716-446655440001") periodId: UUID
    ): ApiResponse<Unit> {
        adjustmentService.amortizePrepayments(entityId, periodId)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/unearned/recognize")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Recognise unearned revenue for the current period",
        description = """
Scans all accounts with subtype `CURRENT_DEFERRED_REVENUE` for the entity and 
automatically generates revenue recognition journal entries per the performance
obligation schedule (IFRS 15).

**Process:**
1. Identify all deferred revenue accounts with balances for the entity
2. Calculate the portion of revenue earned in this period
3. Create and post journal entries: `DR Deferred Revenue / CR Revenue`
4. Return a summary of recognised amounts

**IFRS 15 context:** Revenue must be recognised as performance obligations are
satisfied — not simply when cash is received. This endpoint enforces that timing
by moving only the earned portion from the deferred liability to revenue each period.

**Idempotency warning:** Like amortisation, this should be called exactly once 
per period within the adjusting entries workflow.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Revenue recognition entries created"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Period is not in OPEN or ADJUSTING status")
    )
    fun recognizeUnearnedRevenue(
        @RequestParam @Parameter(description = "Tenant/company UUID") entityId: UUID,
        @RequestParam @Parameter(description = "Period UUID for which to run recognition") periodId: UUID
    ): ApiResponse<Unit> {
        adjustmentService.recognizeUnearnedRevenue(entityId, periodId)
        return ApiResponse.success(Unit)
    }
}

package com.qesuite.accounting.ledger.controller

import com.qesuite.accounting.ledger.service.TrialBalanceReport
import com.qesuite.accounting.ledger.service.TrialBalanceService
import com.qesuite.accounting.shared.exceptions.ApiResponse
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
@RequestMapping("/api/v1/trial-balance")
@Tag(
    name = "Module 5: Trial Balance Engine",
    description = """
The Trial Balance is the first formal report in the period-end reporting cycle, providing
a structured listing of all account balances used to verify that total debits equal
total credits across the General Ledger.

**How it works:**
The engine aggregates `LedgerEntry` records directly (not account cached balances) using
point-in-time queries — `SUM(functionalDebit)` and `SUM(functionalCredit)` per account
up to the `asOfDate`. This guarantees consistency with the raw posting data.

**Double-Entry Invariant Verification:**
The system enforces: `SUM(all functionalDebit movements) == SUM(all functionalCredit movements)`
at the raw ledger level. If this invariant is violated, the endpoint throws a 
`TRIAL_BALANCE_FAILURE` error — indicating a data integrity problem in the posting pipeline.

**Presentation columns:**
Each account's net balance is presented in the conventional column:
- **Debit-normal accounts** (Assets, Expenses): net positive balance shown in the **Debit** column
- **Credit-normal accounts** (Liabilities, Equity, Revenue): net positive balance shown in the **Credit** column

**In the accounting cycle:** The Trial Balance must be generated and verified **before**
adjusting entries (Module 6) and **before** financial statements (Module 7) are prepared.
It serves as the control checkpoint confirming the General Ledger is in balance.

**IFRS context:** While not required to be disclosed externally under IFRS, the trial
balance is a mandatory internal control report in every period-end close process.
"""
)
class TrialBalanceController(private val trialBalanceService: TrialBalanceService) {

    @GetMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Generate the Trial Balance as of a given date",
        description = """
Produces the full Trial Balance report for the entity, aggregated from posted 
`LedgerEntry` records up to (and including) `asOfDate`.

**Response structure:**
```json
{
  "entityId": "...",
  "asOfDate": "2026-03-31",
  "rows": [
    { "accountCode": "1000", "accountName": "Cash", "debitBalance": 50000.00, "creditBalance": 0 },
    { "accountCode": "4000", "accountName": "Sales Revenue", "debitBalance": 0, "creditBalance": 50000.00 }
  ],
  "totalDebits": 50000.00,
  "totalCredits": 50000.00
}
```

If `totalDebits != totalCredits`, a `422` error is returned with code `TRIAL_BALANCE_FAILURE`
rather than returning a misleading unbalanced report.

**Pre-adjusting trial balance:** Call this before `POST /adjustments/*` to establish
the unadjusted position.
**Post-adjusting trial balance:** Call again after adjusting entries to verify the
adjusted position before generating financial statements.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Balanced trial balance report"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Trial balance is out of balance — indicates a posting pipeline integrity error")
    )
    fun getTrialBalance(
        @RequestParam @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000") entityId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Point-in-time date for balance aggregation (ISO 8601). Defaults to today.", example = "2026-03-31") asOfDate: LocalDate?
    ): ApiResponse<TrialBalanceReport> {
        return ApiResponse.success(trialBalanceService.generateTrialBalance(entityId, asOfDate))
    }

    @GetMapping("/comparative")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Generate a comparative Trial Balance across two dates",
        description = "Returns a side-by-side balance comparison for `asOfDate` vs `compareAsOfDate`, with a `movement` column per account."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Comparative trial balance report"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Trial balance out of balance at either date")
    )
    fun getComparative(
        @RequestParam @Parameter(description = "Tenant/company UUID") entityId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Current period end date", example = "2026-02-28") asOfDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Prior period end date for comparison", example = "2026-01-31") compareAsOfDate: LocalDate
    ): ApiResponse<com.qesuite.accounting.ledger.service.ComparativeTrialBalanceReport> {
        return ApiResponse.success(trialBalanceService.generateComparative(entityId, asOfDate, compareAsOfDate))
    }
}

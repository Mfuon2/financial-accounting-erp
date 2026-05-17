package com.qesuite.accounting.ledger.controller

import com.qesuite.accounting.ledger.domain.LedgerEntry
import com.qesuite.accounting.ledger.service.LedgerService
import com.qesuite.accounting.ledger.service.TAccountView
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
@RequestMapping("/api/v1/ledger")
@Tag(
    name = "Module 4: General Ledger & Posting Engine",
    description = """
The General Ledger (GL) is the permanent, immutable record of all financial movements.
Every posted `JournalEntry` produces one `LedgerEntry` row per line — these rows are
**insert-only** and cannot be modified or deleted.

**LedgerEntry fields:**
- `accountId` — The account this movement belongs to
- `journalEntryLineId` — Traceability back to the originating journal line
- `transDate` — The accounting date of the movement
- `functionalDebit` / `functionalCredit` — Amounts in functional currency
- `runningBalance` — Cumulative account balance after this entry (maintained for performance)

**T-Account View:**
The `/t-account` endpoint returns the classic T-account presentation: all debit-side
movements on the left and all credit-side movements on the right for a given date range.
Used by accountants to trace account activity for reconciliation purposes.

**Subsidiary Ledgers:**
The GL maintains subsidiary (sub-ledger) views for:
- **Accounts Receivable (AR)** — Customer-level breakdown of trade debtors
- **Accounts Payable (AP)** — Supplier-level breakdown of trade creditors
- **Fixed Assets** — Per-asset depreciation schedules

**Read-Only Surface:** All GL endpoints are read-only. The only write operation here
is `POST /assets/{assetId}/depreciate`, which triggers the depreciation journal
through the Journal Entry Engine (maintaining the full posting pipeline).
"""
)
class LedgerController(private val ledgerService: LedgerService) {

    @GetMapping("/accounts/{accountId}/entries")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Retrieve all ledger entries for an account",
        description = """
Returns every `LedgerEntry` ever posted against the given account, in chronological order.

Each entry includes the `functionalDebit`, `functionalCredit`, and cumulative 
`runningBalance` at the time of posting. The `journalEntryLineId` provides a direct
link back to the originating journal line for full audit traceability.

**Note:** This is the raw, unfiltered view. For point-in-time balance queries, use
`GET /coa/accounts/{id}/balance?asOfDate=...` instead.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of ledger entries (chronological)"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    )
    fun getEntriesByAccount(
        @PathVariable @Parameter(description = "Account UUID to retrieve ledger entries for", example = "770e8400-e29b-41d4-a716-446655440002") accountId: UUID
    ): ApiResponse<List<LedgerEntry>> {
        return ApiResponse.success(ledgerService.getEntriesByAccount(accountId))
    }

    @GetMapping("/accounts/{accountId}/t-account")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Retrieve T-account view for a date range",
        description = """
Returns the classic accountant's T-account presentation for the specified account and
date range, with all debit-side movements and credit-side movements separated.

**Response structure:**
```json
{
  "accountId": "...",
  "debits": [ { "transDate": "2026-01-15", "functionalDebit": 1000.00, ... } ],
  "credits": [ { "transDate": "2026-01-31", "functionalCredit": 500.00, ... } ]
}
```

**Use cases:**
- Accountant reconciliation of a specific account for a period
- Auditor trace of activity between two dates
- Debugging unexpected balances in financial statements

**Date range:** Both `startDate` and `endDate` are inclusive. Format: `YYYY-MM-DD`.
"""
    )
    fun getTAccount(
        @PathVariable @Parameter(description = "Account UUID", example = "770e8400-e29b-41d4-a716-446655440002") accountId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Start date of the range (inclusive, ISO 8601)", example = "2026-01-01") startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "End date of the range (inclusive, ISO 8601)", example = "2026-03-31") endDate: LocalDate
    ): ApiResponse<TAccountView> {
        return ApiResponse.success(ledgerService.getTAccount(accountId, startDate, endDate))
    }

    @GetMapping("/entries/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Retrieve a single ledger entry by ID",
        description = "Returns a specific `LedgerEntry` record. Use the `journalEntryLineId` to trace back to the originating journal line."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ledger entry found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Ledger entry not found")
    )
    fun getById(
        @PathVariable @Parameter(description = "Ledger entry UUID") id: UUID
    ): ApiResponse<LedgerEntry> {
        return ApiResponse.success(ledgerService.findById(id))
    }

    @GetMapping("/subsidiary/customers/{customerId}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Get full AR subsidiary ledger history for a customer",
        description = """
Returns all `LedgerEntry` records associated with a specific customer, filtered from
the Accounts Receivable control account's movements.

**Accounts Receivable sub-ledger:** Provides a customer-level breakdown of outstanding
invoices, partial payments, and credit notes — allowing the AR team to reconcile the
control account total against individual customer balances.

**Reconciliation rule:** The sum of all customer sub-ledger balances must equal the
balance on the AR control account in the Chart of Accounts.
"""
    )
    fun getCustomerSubsidiary(
        @PathVariable @Parameter(description = "Customer UUID (the external customer identifier)", example = "aa0e8400-e29b-41d4-a716-446655440005") customerId: UUID,
        @RequestParam @Parameter(description = "Tenant/company UUID", required = true) entityId: UUID
    ): ApiResponse<List<LedgerEntry>> {
        return ApiResponse.success(ledgerService.getCustomerSubsidiary(customerId, entityId))
    }

    @GetMapping("/subsidiary/suppliers/{supplierId}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Get full AP subsidiary ledger history for a supplier",
        description = """
Returns all `LedgerEntry` records associated with a specific supplier, filtered from
the Accounts Payable control account's movements.

**Accounts Payable sub-ledger:** Provides a supplier-level breakdown of outstanding
purchase invoices, payments, and debit notes — allowing the AP team to reconcile the
control account against individual supplier aging reports.

**Reconciliation rule:** The sum of all supplier sub-ledger balances must equal the
balance on the AP control account in the Chart of Accounts.
"""
    )
    fun getSupplierSubsidiary(
        @PathVariable @Parameter(description = "Supplier UUID (the external supplier identifier)", example = "bb0e8400-e29b-41d4-a716-446655440006") supplierId: UUID,
        @RequestParam @Parameter(description = "Tenant/company UUID", required = true) entityId: UUID
    ): ApiResponse<List<LedgerEntry>> {
        return ApiResponse.success(ledgerService.getSupplierSubsidiary(supplierId, entityId))
    }

    @GetMapping("/assets/{assetId}/depreciation-schedule")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Retrieve the full depreciation schedule for a fixed asset",
        description = """
Returns the complete depreciation schedule for a specific fixed asset, showing the
planned and posted depreciation charges across the asset's useful life.

**Schedule content:**
- Depreciation method (straight-line, declining balance)
- Monthly depreciation amount
- Accumulated depreciation to date
- Net book value (cost less accumulated depreciation) at each period

Used by the Finance team to verify asset registers comply with IAS 16 (Property,
Plant and Equipment).
"""
    )
    fun getAssetSchedule(
        @PathVariable @Parameter(description = "Fixed asset UUID", example = "cc0e8400-e29b-41d4-a716-446655440007") assetId: UUID
    ): ApiResponse<List<Any>> {
        return ApiResponse.success(ledgerService.getAssetSchedule(assetId))
    }

    @PostMapping("/assets/{assetId}/depreciate")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Post the period depreciation charge for a fixed asset",
        description = """
Triggers the automated depreciation calculation for the specified asset and posts
the resulting journal entry through the standard Journal Entry Engine (creating a
DRAFT entry, validating, and posting it to the GL).

**Journal created (example — straight-line on a $12,000 asset, 12-month life):**
```
DR  Depreciation Expense (P&L)    1,000.00
    CR  Accumulated Depreciation (Balance Sheet)    1,000.00
```

This endpoint should be called once per period per asset as part of the period-end
adjusting entries workflow (Module 6). The `AdjustmentController` batch endpoint
calls this internally for all assets.

**Required role:** ACCOUNTANT or above.
"""
    )
    fun postDepreciation(
        @PathVariable @Parameter(description = "Fixed asset UUID to depreciate") assetId: UUID
    ): ApiResponse<Unit> {
        ledgerService.postDepreciation(assetId)
        return ApiResponse.success(Unit)
    }
}

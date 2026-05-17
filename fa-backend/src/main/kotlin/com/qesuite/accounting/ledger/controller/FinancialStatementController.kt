package com.qesuite.accounting.ledger.controller

import com.qesuite.accounting.ledger.service.BalanceSheetReport
import com.qesuite.accounting.ledger.service.CashFlowReport
import com.qesuite.accounting.ledger.service.CashFlowService
import com.qesuite.accounting.ledger.service.FinancialStatementService
import com.qesuite.accounting.ledger.service.ProfitLossReport
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.pdf.PdfReportService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1/statements")
@Tag(
    name = "Module 7: Financial Statements",
    description = """
Generates IFRS-compliant primary financial statements from the General Ledger data.
Financial statements are the final output of the accounting cycle and are produced
**after** adjusting entries have been posted and the trial balance is in balance.

**Statements produced:**

**1. Statement of Financial Position (Balance Sheet) — IAS 1**
Reports the entity's financial position at a point in time:
- **Assets** = Non-Current Assets + Current Assets
- **Liabilities** = Non-Current Liabilities + Current Liabilities  
- **Equity** = Share Capital + Retained Earnings + Current Year Profit
- **Fundamental equation:** Total Assets = Total Liabilities + Total Equity

**2. Statement of Profit or Loss (P&L / Income Statement) — IAS 1**
Reports financial performance over a date range:
- **Revenue** — All income earned in the period
- **Expenses** — Cost of Sales + Operating Expenses + Finance Costs + Tax
- **Net Income** = Total Revenue − Total Expenses

**3. Statement of Cash Flows — IAS 7 (Indirect Method)**
Reports cash generated and used over a date range, classified as:
- **Operating** — Net income adjusted for non-cash items and working-capital movements
- **Investing** — Capital expenditure and proceeds from asset disposals
- **Financing** — Proceeds from / repayments of borrowings; equity issuance / dividends

**Classification engine:** Balances are classified using the `ifrsCategory` field 
on each `Account` (IAS 1 taxonomy: CURRENT_ASSETS, NON_CURRENT_ASSETS, REVENUE, 
OPERATING_EXPENSES, etc.) — no heuristics or account-code conventions are used.

**Point-in-time accuracy:** All balances are computed by aggregating raw `LedgerEntry`
records using `SUM(functionalDebit)` and `SUM(functionalCredit)` queries with date 
filters — ensuring statements reflect exactly what was posted, not cached values.

**Pre-requisites before generating statements:**
1. `GET /trial-balance` must return a balanced report
2. All adjusting entries (Module 6) must be posted
3. The period must be in `CLOSING` status
"""
)
class FinancialStatementController(
    private val financialStatementService: FinancialStatementService,
    private val cashFlowService: CashFlowService,
    private val pdfReportService: PdfReportService,
) {

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Generate the Statement of Financial Position (Balance Sheet)",
        description = """
Produces the Balance Sheet as of `asOfDate` by aggregating all posted `LedgerEntry`
records up to that date, classified by `ifrsCategory`.

**Sections:**
- **Assets** — Accounts with `ifrsCategory` in `[CURRENT_ASSETS, NON_CURRENT_ASSETS]`
- **Liabilities** — Accounts with `ifrsCategory` in `[CURRENT_LIABILITIES, NON_CURRENT_LIABILITIES]`
- **Equity** — Accounts with `ifrsCategory` = `EQUITY` (includes current year retained earnings)

**Accounting equation validation:**
The service logs a warning if `Total Assets ≠ Total Liabilities + Total Equity`, 
which indicates missing equity (e.g., current year P&L not yet closed to Retained Earnings).

**When to generate:** After all adjusting entries are posted and the period is in
CLOSING status. If generated during an open period, it represents a management
accounts view (unaudited interim position).
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Balance sheet generated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Entity not found")
    )
    fun getBalanceSheet(
        @RequestParam
        @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        entityId: UUID,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(
            description = "Reporting date for the balance sheet (ISO 8601). Defaults to today.",
            example = "2026-03-31"
        )
        asOfDate: LocalDate?
    ): ApiResponse<BalanceSheetReport> {
        return ApiResponse.success(financialStatementService.getBalanceSheet(entityId, asOfDate))
    }

    @GetMapping("/profit-loss")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Generate the Statement of Profit or Loss (Income Statement)",
        description = """
Produces the P&L statement for the specified date range by aggregating posted 
`LedgerEntry` movements for Revenue and Expense accounts within that period.

**Sections:**
- **Revenue** — Accounts with `ifrsCategory` = `REVENUE`
- **Cost of Sales** — Accounts with `ifrsCategory` = `COST_OF_SALES`
- **Operating Expenses** — Accounts with `ifrsCategory` = `OPERATING_EXPENSES`
- **Finance Costs** — Accounts with `ifrsCategory` = `FINANCE_COSTS`
- **Tax Expense** — Accounts with `ifrsCategory` = `TAX_EXPENSE`
- **Other Income/Expense** — Accounts with `ifrsCategory` = `OTHER_INCOME_EXPENSE`

**Net Income calculation:**
`Net Income = Total Revenue − (Cost of Sales + Operating Expenses + Finance Costs + Tax Expense + Other)`

**Date range:** Both `startDate` and `endDate` are inclusive. For a full month, 
use the first and last day of the month (e.g., `2026-01-01` to `2026-01-31`).
For a full year, use the fiscal year start and end dates.

**Comparatives:** For year-on-year comparative reporting, call this endpoint twice
with the current and prior year date ranges.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "P&L statement generated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range (startDate must be before endDate)")
    )
    fun getProfitLoss(
        @RequestParam
        @Parameter(description = "Tenant/company UUID")
        entityId: UUID,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Period start date (inclusive, ISO 8601)", example = "2026-01-01")
        startDate: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Period end date (inclusive, ISO 8601)", example = "2026-03-31")
        endDate: LocalDate
    ): ApiResponse<ProfitLossReport> {
        return ApiResponse.success(financialStatementService.getProfitLoss(entityId, startDate, endDate))
    }

    @GetMapping("/cash-flow")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Generate the Statement of Cash Flows (IAS 7 Indirect Method)",
        description = """
Produces the Statement of Cash Flows for the specified date range using the
**indirect method** as required by IAS 7.

**Operating Activities (IAS 7 §20–§28):**
1. Start with net income for the period (from P&L)
2. Add back non-cash charges: Depreciation (`DEPRECIATION`) and Amortisation (`AMORTISATION`)
3. Adjust for working-capital movements:
   - Increase in receivables (`CURRENT_RECEIVABLE`) → cash outflow (subtracted)
   - Increase in payables (`CURRENT_PAYABLE`) → cash inflow (added)

**Investing Activities (IAS 7 §16):**
- Net movement in `NON_CURRENT_ASSETS` accounts.
  An increase in non-current assets represents a capital expenditure cash outflow (negated).

**Financing Activities (IAS 7 §17):**
- Net movement in `NON_CURRENT_LIABILITIES` accounts (borrowings proceeds / repayments)
- Net movement in `EQUITY` accounts (share issuance proceeds / dividends paid)

**Cash balances:**
- `closingCash` = current balance of all `CASH_AND_EQUIVALENTS` accounts
- `openingCash` = `closingCash − netChangeInCash` (period-movement proxy)
- `netChangeInCash` = Operating + Investing + Financing

All monetary amounts are scaled to 6 decimal places (HALF_EVEN rounding).
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cash flow statement generated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid date range or missing parameters"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Entity or period not found")
    )
    fun getCashFlow(
        @RequestParam
        @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        entityId: UUID,
        @RequestParam
        @Parameter(description = "Accounting period UUID", example = "660e8400-e29b-41d4-a716-446655440001")
        periodId: UUID,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Period start date (inclusive, ISO 8601)", example = "2026-01-01")
        startDate: LocalDate,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Period end date (inclusive, ISO 8601)", example = "2026-03-31")
        endDate: LocalDate
    ): ApiResponse<CashFlowReport> {
        return ApiResponse.success(
            cashFlowService.generateIndirectCashFlow(entityId, periodId, startDate, endDate)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PDF export endpoints
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/balance-sheet/pdf", produces = ["application/pdf"])
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(summary = "Export Balance Sheet as PDF")
    fun getBalanceSheetPdf(
        @RequestParam entityId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) asOfDate: LocalDate?
    ): ResponseEntity<ByteArray> {
        val report = financialStatementService.getBalanceSheet(entityId, asOfDate)
        val pdf = pdfReportService.balanceSheet(report)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"balance-sheet.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }

    @GetMapping("/profit-loss/pdf", produces = ["application/pdf"])
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(summary = "Export Profit & Loss as PDF")
    fun getProfitLossPdf(
        @RequestParam entityId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ResponseEntity<ByteArray> {
        val report = financialStatementService.getProfitLoss(entityId, startDate, endDate)
        val pdf = pdfReportService.profitLoss(report)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"profit-loss.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }

    @GetMapping("/cash-flow/pdf", produces = ["application/pdf"])
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(summary = "Export Cash Flow Statement as PDF")
    fun getCashFlowPdf(
        @RequestParam entityId: UUID,
        @RequestParam periodId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate
    ): ResponseEntity<ByteArray> {
        val report = cashFlowService.generateIndirectCashFlow(entityId, periodId, startDate, endDate)
        val pdf = pdfReportService.cashFlow(report)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cash-flow.pdf\"")
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
    }
}

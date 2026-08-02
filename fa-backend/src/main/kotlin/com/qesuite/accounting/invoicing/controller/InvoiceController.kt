package com.qesuite.accounting.invoicing.controller

import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.dto.ApplyInvoicePaymentCommand
import com.qesuite.accounting.invoicing.dto.ArAgeingResponse
import com.qesuite.accounting.invoicing.dto.CreateCreditNoteCommand
import com.qesuite.accounting.invoicing.dto.CreateInvoiceCommand
import com.qesuite.accounting.invoicing.dto.VoidInvoiceCommand
import com.qesuite.accounting.invoicing.service.InvoiceService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(
    name = "Module 14: Invoicing",
    description = """
Full invoice lifecycle management per IFRS 15 (Revenue from Contracts with Customers).

**Lifecycle:**
```
DRAFT → APPROVED → SENT → PARTIALLY_PAID → PAID
                              ↓                ↓
                           VOID          CREDIT_NOTE
```

Revenue recognition (POINT_IN_TIME vs OVER_TIME) is applied per line at approval time.
"""
)
class InvoiceController(
    private val invoiceService: InvoiceService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new invoice in DRAFT status",
        description = "Creates a new invoice with lines. No journal entry is posted until the invoice is approved."
    )
    fun createDraft(
        @Valid @RequestBody command: CreateInvoiceCommand
    ): ApiResponse<Invoice> {
        SecurityUtils.requireOwnEntity(command.entityId)
        return ApiResponse.success(invoiceService.createDraft(command))
    }

    @GetMapping
    @Operation(
        summary = "List invoices with optional filters",
        description = "Returns a paginated list of invoices filtered by entityId and optional customerId, status, or date range."
    )
    fun list(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID,
        @RequestParam(required = false) @Parameter(description = "Filter by customer UUID") customerId: UUID?,
        @RequestParam(required = false) @Parameter(description = "Filter by invoice status") status: InvoiceStatus?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Start of issue-date range (ISO 8601)") fromDate: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "End of issue-date range (ISO 8601)") toDate: LocalDate?,
        @PageableDefault(size = 50) pageable: Pageable
    ): ApiResponse<PagedResponse<Invoice>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = invoiceService.findByEntity(
            entityId = entityId,
            customerId = customerId,
            status = status,
            fromDate = fromDate,
            toDate = toDate,
            pageable = pageable
        )
        return ApiResponse.success(page.toPagedResponse { it })
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve an invoice by ID", description = "Returns the full invoice including all lines.")
    fun findById(
        @PathVariable @Parameter(description = "Invoice UUID") id: UUID
    ): ApiResponse<Invoice> {
        val invoice = invoiceService.findById(id)
        SecurityUtils.requireOwnEntity(invoice.entityId)
        return ApiResponse.success(invoice)
    }

    @PostMapping("/{id}/approve")
    @Operation(
        summary = "Approve a DRAFT invoice and post the AR journal entry",
        description = "Validates credit limit, posts the AR journal entry, and transitions the invoice DRAFT → APPROVED → SENT."
    )
    fun approve(
        @PathVariable @Parameter(description = "Invoice UUID") id: UUID
    ): ApiResponse<Invoice> {
        SecurityUtils.requireOwnEntity(invoiceService.findById(id).entityId)
        return ApiResponse.success(invoiceService.approve(id))
    }

    @PostMapping("/{id}/void")
    @Operation(
        summary = "Void a DRAFT invoice",
        description = "Voids a DRAFT invoice. Posted invoices must use a credit note instead."
    )
    fun void(
        @PathVariable @Parameter(description = "Invoice UUID") id: UUID,
        @Valid @RequestBody command: VoidInvoiceCommand
    ): ApiResponse<Invoice> {
        SecurityUtils.requireOwnEntity(invoiceService.findById(id).entityId)
        return ApiResponse.success(invoiceService.void(id, command.reason))
    }

    @PostMapping("/{id}/credit-note")
    @Operation(
        summary = "Issue a credit note against a posted invoice",
        description = "Creates a credit note with negative amounts and posts a reversing journal entry against the original AR posting."
    )
    fun createCreditNote(
        @PathVariable @Parameter(description = "Original invoice UUID") id: UUID,
        @Valid @RequestBody command: CreateCreditNoteCommand
    ): ApiResponse<Invoice> {
        SecurityUtils.requireOwnEntity(invoiceService.findById(id).entityId)
        return ApiResponse.success(invoiceService.createCreditNote(id, command))
    }

    @PostMapping("/{id}/payment")
    @Operation(
        summary = "Apply a payment to an invoice",
        description = "Updates paidAmount and outstandingAmount. Transitions to PARTIALLY_PAID or PAID as appropriate."
    )
    fun applyPayment(
        @PathVariable @Parameter(description = "Invoice UUID") id: UUID,
        @Valid @RequestBody command: ApplyInvoicePaymentCommand
    ): ApiResponse<Invoice> {
        SecurityUtils.requireOwnEntity(invoiceService.findById(id).entityId)
        return ApiResponse.success(invoiceService.applyPayment(id, command.paymentAmount))
    }

    @GetMapping("/ar-ageing")
    @Operation(
        summary = "Accounts Receivable ageing report",
        description = "Buckets outstanding invoices into 0–30 / 31–60 / 61–90 / 90+ days past due as of the given date."
    )
    fun arAgeing(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Report as-of date (ISO 8601)", required = true) asOfDate: LocalDate
    ): ApiResponse<ArAgeingResponse> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(invoiceService.arAgeing(entityId, asOfDate))
    }
}

package com.qesuite.accounting.invoicing.dto

import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.domain.PerformanceObligationType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

// ========== INVOICE COMMANDS ==========

/**
 * §14.1 — Create Invoice Command
 */
data class CreateInvoiceCommand(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "Entity ID is required")
    val entityId: UUID,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440099")
    @NotNull(message = "Period ID is required")
    val periodId: UUID,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440001")
    @NotNull(message = "Customer ID is required")
    val customerId: UUID,

    @Schema(example = "2024-05-08")
    @NotNull(message = "Issue date is required")
    val issueDate: LocalDate,

    @Schema(example = "2024-06-07")
    @NotNull(message = "Due date is required")
    val dueDate: LocalDate,

    @Schema(example = "KES")
    @NotBlank(message = "Currency code is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must be 3-letter ISO 4217 code")
    val currencyCode: String,

    @Schema(example = "1.000000")
    @NotNull(message = "Exchange rate is required")
    @Positive(message = "Exchange rate must be positive")
    val exchangeRate: BigDecimal,

    @Schema(example = "200.000000")
    val discountAmount: BigDecimal = BigDecimal.ZERO,

    @Schema(example = "Standard terms NET 30")
    val notes: String? = null,

    @Schema(description = "Invoice line items")
    @NotEmpty(message = "At least one line item is required")
    @Valid
    val lines: List<CreateInvoiceLineCommand>
)

/**
 * §14.1 — Create Invoice Line Command
 */
data class CreateInvoiceLineCommand(
    @Schema(example = "1", description = "Line item sequence number")
    val lineNumber: Int = 0,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440020")
    @NotNull(message = "Account ID is required")
    val accountId: UUID,

    @Schema(example = "Widget A - Premium")
    @NotBlank(message = "Description is required")
    val description: String,

    @Schema(example = "10.000000")
    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    val quantity: BigDecimal,

    @Schema(example = "100.000000")
    @NotNull(message = "Unit price is required")
    @Positive(message = "Unit price must be positive")
    val unitPrice: BigDecimal,

    @Schema(example = "10", description = "Line-level discount percentage (0–100)")
    val discountPercent: BigDecimal = BigDecimal.ZERO,

    @Schema(description = "Tax rate ID — use this OR taxCodeId, not both. Direct reference to a specific TaxRate row.")
    val taxRateId: UUID? = null,

    @Schema(description = "Tax code ID — the system resolves the effective rate for the invoice issue date automatically.")
    val taxCodeId: UUID? = null,

    @Schema(example = "POINT_IN_TIME")
    val recognitionType: PerformanceObligationType? = null
)

// ========== INVOICE RESPONSES ==========

/**
 * §14.1 — Invoice Response
 */
data class InvoiceResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440001")
    val id: UUID,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val entityId: UUID,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440099")
    val periodId: UUID?,

    @Schema(example = "INV-2024-00001")
    val invoiceNumber: String,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440001")
    val customerId: UUID,

    @Schema(example = "2024-05-08")
    val issueDate: LocalDate,

    @Schema(example = "2024-06-07")
    val dueDate: LocalDate,

    @Schema(example = "KES")
    val currencyCode: String,

    @Schema(example = "1.000000")
    val exchangeRate: BigDecimal,

    @Schema(example = "4500.000000")
    val subtotal: BigDecimal,

    @Schema(example = "450.000000")
    val taxAmount: BigDecimal,

    @Schema(example = "200.000000")
    val discountAmount: BigDecimal,

    @Schema(example = "4750.000000")
    val totalAmount: BigDecimal,

    @Schema(example = "0.000000")
    val paidAmount: BigDecimal,

    @Schema(example = "4750.000000")
    val outstandingAmount: BigDecimal,

    @Schema(example = "DRAFT")
    val status: InvoiceStatus,

    @Schema(description = "Invoice notes")
    val notes: String? = null,

    @Schema(description = "Posted journal entry ID")
    val journalEntryId: UUID? = null,

    @Schema(description = "Invoice line items")
    val lines: List<InvoiceLineResponse> = emptyList()
)

/**
 * §14.1 — Invoice Line Response
 */
data class InvoiceLineResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440010")
    val id: UUID,

    @Schema(example = "1")
    val lineNumber: Int,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440020")
    val accountId: UUID,

    @Schema(example = "Widget A - Premium")
    val description: String,

    @Schema(example = "10.000000")
    val quantity: BigDecimal,

    @Schema(example = "100.000000")
    val unitPrice: BigDecimal,

    @Schema(example = "1000.000000")
    val lineSubtotal: BigDecimal,

    @Schema(example = "100.000000")
    val lineTax: BigDecimal,

    @Schema(example = "1100.000000")
    val lineTotal: BigDecimal,

    @Schema(example = "POINT_IN_TIME")
    val recognitionType: PerformanceObligationType? = null
)

// ========== AGEING REPORT ==========

/**
 * §14 — AR Ageing Report Response
 */
data class ArAgeingResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val entityId: UUID,

    @Schema(example = "2024-05-08")
    val asOfDate: LocalDate,

    @Schema(description = "Current (0-30 days overdue)")
    val current: AgingBucketResponse,

    @Schema(description = "31-60 days overdue")
    val thirtyOneToSixty: AgingBucketResponse,

    @Schema(description = "61-90 days overdue")
    val sixtyOneToNinety: AgingBucketResponse,

    @Schema(description = "Over 90 days overdue")
    val ninetyPlus: AgingBucketResponse,

    @Schema(description = "Total outstanding across all buckets")
    val totalOutstanding: BigDecimal
)

/**
 * §14 — Ageing Bucket (single time bracket)
 */
data class AgingBucketResponse(
    @Schema(description = "Number of invoices in this bracket")
    val invoiceCount: Int,

    @Schema(description = "Total outstanding amount in this bracket")
    val totalAmount: BigDecimal
)

// §7.3 — `PagedResponse` and `Page.toPagedResponse` are now in
// `com.qesuite.accounting.shared.dto`. Import from there.

/**
 * §14.2 — Credit-note creation command. Reduces an existing posted invoice's AR balance.
 */
data class CreateCreditNoteCommand(
    @Schema(example = "1500.000000")
    @NotNull(message = "Credit note amount is required")
    @Positive(message = "Credit note amount must be positive (the system applies the negative sign)")
    val creditNoteAmount: BigDecimal,

    @Schema(example = "Customer return — defective goods")
    @NotBlank(message = "A reason is required for credit notes")
    val reason: String,
)

/**
 * §14.2 — Apply-payment command. Used by M14 (Payments) when settling an invoice.
 */
data class ApplyInvoicePaymentCommand(
    @Schema(example = "5000.000000")
    @NotNull(message = "Payment amount is required")
    @Positive(message = "Payment amount must be positive")
    val paymentAmount: BigDecimal,
)

/**
 * §14.2 — Void command (only valid on DRAFT invoices).
 */
data class VoidInvoiceCommand(
    @Schema(example = "Customer cancelled order before delivery")
    @NotBlank(message = "Void reason is required")
    val reason: String,
)

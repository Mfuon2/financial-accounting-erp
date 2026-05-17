package com.qesuite.accounting.invoicing.domain

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * §14.1 — Invoice Line Item Entity
 * Represents a line item on an invoice. Multiple lines aggregate to the invoice total.
 * Each line maps to a revenue account and optionally a tax rate.
 */
@Entity
@Table(name = "invoice_lines")
data class InvoiceLine(
    @Id
    @Column(name = "id", nullable = false)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440010")
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    var invoice: Invoice? = null,

    @Column(nullable = false)
    @Schema(example = "1", description = "Line item sequence number")
    val lineNumber: Int,

    @Column(nullable = false)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440020", description = "FK to revenue account or deferred revenue account")
    val accountId: UUID,

    @Column(nullable = false, length = 500)
    @Schema(example = "Widget A - Premium", description = "Line item description")
    val description: String,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "10.000000", description = "Quantity ordered")
    val quantity: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "100.000000", description = "Unit price")
    val unitPrice: BigDecimal,

    @Column(nullable = true)
    @Schema(description = "FK to tax rate master (optional)")
    val taxRateId: UUID? = null,

    @Column(nullable = true, length = 50)
    @Schema(example = "POINT_IN_TIME", description = "IFRS 15 performance obligation type")
    @Enumerated(EnumType.STRING)
    val recognitionType: PerformanceObligationType? = null,

    @Column(nullable = true, precision = 20, scale = 6)
    @Schema(example = "1000.000000", description = "Amount already recognized (for OVER_TIME obligations)")
    val recognizedAmount: BigDecimal? = null,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "1000.000000", description = "Subtotal (quantity × unit price)")
    val lineSubtotal: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "100.000000", description = "Tax on this line item")
    val lineTax: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "1100.000000", description = "Total line amount (subtotal + tax)")
    val lineTotal: BigDecimal,

    @Column(nullable = false)
    @Schema(description = "When this line was created")
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    @Schema(description = "When this line was last modified")
    var modifiedAt: Instant = Instant.now()
)

/**
 * §14.1 — IFRS 15 Performance Obligation Type
 * Distinguishes between point-in-time (goods delivered) and over-time (services rendered) recognition.
 */
enum class PerformanceObligationType {
    POINT_IN_TIME,  // Revenue recognized on delivery
    OVER_TIME       // Revenue recognized as service is performed
}

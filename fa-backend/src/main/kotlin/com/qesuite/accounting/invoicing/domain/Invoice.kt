package com.qesuite.accounting.invoicing.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * §14.1, §14.2 — Invoice Master Entity
 * Represents a sales invoice in the revenue cycle. Links to customer, accounts receivable, and journal entry.
 */
@Entity
@Table(name = "invoices")
class Invoice(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,
    periodId: UUID,

    @Column(nullable = false, length = 50)
    @Schema(example = "INV-2024-00001", description = "Unique invoice number per entity")
    val invoiceNumber: String,

    @Column(nullable = false)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440001", description = "FK to customer")
    val customerId: UUID,

    @Column(nullable = false)
    @Schema(example = "2024-05-08", description = "Invoice issue/transaction date")
    val issueDate: LocalDate,

    @Column(nullable = false)
    @Schema(example = "2024-06-07", description = "Invoice due date")
    val dueDate: LocalDate,

    @Column(nullable = false, length = 3)
    @Schema(example = "KES", description = "ISO 4217 currency code")
    val currencyCode: String,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "1.000000", description = "Exchange rate to functional currency")
    val exchangeRate: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "4500.000000", description = "Subtotal before tax and discount")
    var subtotal: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "450.000000", description = "Total tax amount")
    var taxAmount: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "200.000000", description = "Total discount amount")
    var discountAmount: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "4750.000000", description = "Total invoice amount (subtotal + tax - discount)")
    var totalAmount: BigDecimal,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "0.000000", description = "Amount already paid")
    var paidAmount: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "4750.000000", description = "Remaining outstanding amount")
    var outstandingAmount: BigDecimal,

    @Column(nullable = false, length = 50)
    @Schema(example = "DRAFT", description = "Current status in invoice lifecycle")
    @Enumerated(EnumType.STRING)
    var status: InvoiceStatus = InvoiceStatus.DRAFT,

    @Column(nullable = true, columnDefinition = "TEXT")
    @Schema(example = "Standard terms NET 30", description = "Invoice notes or memo")
    val notes: String? = null,

    @Column(nullable = true)
    @Schema(description = "FK to posted journal entry (set when approved)")
    var journalEntryId: UUID? = null

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = periodId) {

    /**
     * §5.1 — Invoice lines (child items)
     */
    @OneToMany(mappedBy = "invoice", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JsonIgnoreProperties("invoice")
    val lines: MutableList<InvoiceLine> = mutableListOf()

    /**
     * §5.1 — Optimistic locking
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    /**
     * Add a line to the invoice.
     */
    fun addLine(line: InvoiceLine) {
        lines.add(line)
        line.invoice = this
    }

    /**
     * Clear all lines (for updates).
     */
    fun clearLines() {
        lines.clear()
    }
}

/**
 * §14.2 — Invoice Status Enum (State Machine)
 */
enum class InvoiceStatus {
    DRAFT,
    APPROVED,
    SENT,
    PARTIALLY_PAID,
    PAID,
    VOID,
    CREDIT_NOTE;

    /**
     * §5.1 — State machine: define valid transitions
     */
    fun canTransitionTo(next: InvoiceStatus): Boolean = when (this) {
        DRAFT -> next == APPROVED || next == VOID
        APPROVED -> next == SENT || next == PARTIALLY_PAID || next == PAID
        SENT -> next == PARTIALLY_PAID || next == PAID || next == VOID
        PARTIALLY_PAID -> next == PAID || next == PARTIALLY_PAID
        PAID -> false // terminal
        VOID -> false // terminal
        CREDIT_NOTE -> false // terminal
    }
}

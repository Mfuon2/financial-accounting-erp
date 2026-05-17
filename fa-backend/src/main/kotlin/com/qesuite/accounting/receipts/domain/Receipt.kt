package com.qesuite.accounting.receipts.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * §15 — Receipt Entity
 *
 * A receipt is issued to a customer after a payment has been matched to a posted
 * journal entry. The lifecycle runs: PENDING → POSTED → ISSUED → VOID (terminal).
 *
 * A receipt cannot exist without a corresponding posted journal entry
 * (journalEntryId is REQUIRED and validated at creation time by the service).
 */
@Entity
@Table(name = "receipts")
class Receipt(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,
    periodId: UUID? = null,

    /**
     * Human-readable receipt number, unique per entity.
     * Pattern: RCT-{YYYY}-{entityCode4}-{seqPadded6}
     */
    @Column(name = "receipt_number", nullable = false, length = 50)
    @Schema(example = "RCT-2026-QESU-000001", description = "Unique receipt number per entity")
    val receiptNumber: String,

    /**
     * FK to payments.id — immutable after creation.
     * One payment → at most one receipt (UNIQUE constraint on the column).
     */
    @Column(name = "payment_id", nullable = false, updatable = false)
    @Schema(description = "FK to payments.id — immutable")
    val paymentId: UUID,

    /**
     * Optional FK to invoices.id — null for suspense/unallocated payments.
     */
    @Column(name = "invoice_id", nullable = true)
    @Schema(description = "FK to invoices.id — null for unallocated payments")
    val invoiceId: UUID? = null,

    /**
     * FK to the paying customer.
     */
    @Column(name = "customer_id", nullable = false)
    @Schema(description = "FK to customers.id")
    val customerId: UUID,

    /**
     * Date the payment was received.
     */
    @Column(name = "receipt_date", nullable = false)
    @Schema(example = "2026-05-09", description = "Date payment was received")
    val receiptDate: LocalDate,

    /**
     * Amount on the receipt — mirrors payment.paymentAmount.
     */
    @Column(name = "receipt_amount", nullable = false, precision = 20, scale = 6)
    @Schema(example = "5000.000000", description = "Amount received")
    val receiptAmount: BigDecimal,

    /**
     * ISO 4217 currency code.
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    @Schema(example = "KES")
    val currencyCode: String,

    /**
     * FK to the posted journal entry that backs this receipt.
     * REQUIRED — a receipt cannot exist without a posted JE.
     */
    @Column(name = "journal_entry_id", nullable = false)
    @Schema(description = "FK to journal_entries.id — must be POSTED")
    val journalEntryId: UUID,

    /**
     * Current position in the receipt lifecycle.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Schema(example = "POSTED", description = "Receipt lifecycle status")
    var status: ReceiptStatus = ReceiptStatus.PENDING,

    /**
     * Optional email address to deliver the receipt to the customer.
     */
    @Column(name = "delivery_email", nullable = true, length = 255)
    @Schema(example = "accounts@customer.com", description = "Email for receipt delivery")
    var deliveryEmail: String? = null,

    /**
     * Optional phone number (e.g., for SMS delivery).
     */
    @Column(name = "delivery_phone", nullable = true, length = 20)
    @Schema(example = "+254712345678", description = "Phone for SMS receipt delivery")
    var deliveryPhone: String? = null,

    /**
     * Timestamp when the receipt was sent to the customer.
     * Null until status transitions to ISSUED.
     */
    @Column(name = "issued_at", nullable = true)
    @Schema(description = "Timestamp when receipt was delivered to customer")
    var issuedAt: Instant? = null,

    /**
     * Free-text notes or memo.
     */
    @Column(name = "notes", nullable = true, columnDefinition = "TEXT")
    @Schema(description = "Optional notes or memo")
    var notes: String? = null

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = periodId) {

    /**
     * Optimistic lock version — prevents concurrent modifications.
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
}

package com.qesuite.accounting.payments.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * §14 (Module 14) — Payment master entity.
 *
 * Represents a receipt of funds from a customer. Supports:
 *  - Allocated payments: invoiceId is set, status progresses to MATCHED → APPROVED → POSTED.
 *  - Suspense/unallocated payments: invoiceId is null until matched by an operator.
 *  - M-Pesa STK push callbacks (paymentMethod = MPESA, mpesaReceiptNumber set).
 *
 * Money discipline (Rule 01): all amounts are BigDecimal DECIMAL(20,6), rounded HALF_EVEN.
 */
@Entity
@Table(name = "payments")
class Payment(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,
    periodId: UUID? = null,

    /**
     * Human-readable payment reference, unique per entity.
     * Pattern: PAY-{YYYY}-{entityCode4}-{seqPadded6}
     */
    @Column(name = "payment_number", nullable = false, length = 50)
    @Schema(example = "PAY-2026-QESU-000001")
    val paymentNumber: String,

    /**
     * Optional link to an invoice. Null for suspense/unallocated payments.
     */
    @Column(name = "invoice_id", nullable = true)
    @Schema(description = "FK to invoices.id — null until matched")
    var invoiceId: UUID? = null,

    /**
     * FK to the paying customer. Null for M-Pesa suspense payments.
     */
    @Column(name = "customer_id", nullable = false)
    @Schema(description = "FK to customers.id")
    val customerId: UUID,

    /**
     * How the payment was received.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    val paymentMethod: PaymentMethod,

    /**
     * Amount received in the payment currency.
     */
    @Column(name = "payment_amount", nullable = false, precision = 20, scale = 6)
    @Schema(example = "5000.000000")
    val paymentAmount: BigDecimal,

    /**
     * ISO 4217 code for the payment currency.
     */
    @Column(name = "currency_code", nullable = false, length = 3)
    @Schema(example = "KES")
    val currencyCode: String,

    /**
     * Exchange rate to functional currency at time of payment.
     */
    @Column(name = "exchange_rate", nullable = false, precision = 20, scale = 6)
    @Schema(example = "1.000000")
    val exchangeRate: BigDecimal,

    /**
     * paymentAmount * exchangeRate — amount in the entity's functional currency.
     */
    @Column(name = "functional_amount", nullable = false, precision = 20, scale = 6)
    @Schema(example = "5000.000000")
    val functionalAmount: BigDecimal,

    /**
     * Current position in the payment lifecycle.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: PaymentStatus = PaymentStatus.PENDING,

    /**
     * External reference: M-Pesa TransID, bank reference number, cheque number, etc.
     * Must be unique per entity when present.
     */
    @Column(name = "transaction_reference", nullable = true, length = 100)
    @Schema(example = "QHN7XXXXXXX")
    var transactionReference: String? = null,

    /**
     * FK to the journal entry created when status transitions to POSTED.
     */
    @Column(name = "journal_entry_id", nullable = true)
    @Schema(description = "Set when payment is posted to the ledger")
    var journalEntryId: UUID? = null,

    /**
     * M-Pesa STK callback result code (0 = success).
     */
    @Column(name = "mpesa_result_code", nullable = true, length = 10)
    var mpesaResultCode: String? = null,

    /**
     * M-Pesa receipt / transaction ID from callback metadata.
     */
    @Column(name = "mpesa_receipt_number", nullable = true, length = 50)
    var mpesaReceiptNumber: String? = null,

    /**
     * The date the payment was received (value date).
     */
    @Column(name = "payment_date", nullable = false)
    val paymentDate: LocalDate,

    /**
     * Free-text memo or operator notes.
     */
    @Column(name = "notes", nullable = true, columnDefinition = "TEXT")
    var notes: String? = null,

    /**
     * D9 — The amount of this payment that was matched and applied to an invoice.
     * Null until matchToInvoice() is called.  Used in postPayment() instead of
     * paymentAmount so that partial matches are correctly applied to invoice balances.
     */
    @Column(name = "matched_amount", precision = 20, scale = 6)
    @Schema(description = "Amount matched/applied to the linked invoice — set by matchToInvoice()")
    var matchedAmount: BigDecimal? = null

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = periodId) {

    /**
     * Optimistic lock version — prevents concurrent modifications.
     */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
}

package com.qesuite.accounting.receipts.service

import com.qesuite.accounting.email.EmailService
import com.qesuite.accounting.journal.domain.JournalEntryStatus
import com.qesuite.accounting.journal.repository.JournalEntryRepository
import com.qesuite.accounting.payments.domain.PaymentStatus
import com.qesuite.accounting.payments.repository.PaymentRepository
import com.qesuite.accounting.receipts.domain.Receipt
import com.qesuite.accounting.receipts.domain.ReceiptStatus
import com.qesuite.accounting.receipts.repository.ReceiptRepository
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.Year
import java.util.UUID

/**
 * §15 — Receipt Service
 *
 * Owns the full receipt lifecycle:
 *   generateReceipt → POSTED
 *   issueReceipt    → ISSUED  (triggers customer delivery in production)
 *   voidReceipt     → VOID    (soft-delete)
 */
@Service
class ReceiptService(
    private val receiptRepository: ReceiptRepository,
    private val paymentRepository: PaymentRepository,
    private val journalEntryRepository: JournalEntryRepository,
    private val emailService: EmailService,
) {

    /**
     * §15 — Generate a receipt for a posted payment.
     *
     * Pre-conditions:
     *  1. Payment must be in POSTED status.
     *  2. payment.journalEntryId must be non-null AND the referenced JE must be POSTED.
     *  3. No receipt may already exist for this paymentId (UNIQUE constraint).
     *
     * On success the receipt is persisted in POSTED status (it is immediately backed
     * by a posted JE, so PENDING → POSTED in one step) and returned.
     */
    @Transactional
    fun generateReceipt(
        paymentId: UUID,
        entityId: UUID,
        periodId: UUID?,
        deliveryEmail: String? = null,
        deliveryPhone: String? = null,
        notes: String? = null,
    ): Receipt {
        // 1. Load payment — must be POSTED.
        val payment = paymentRepository.findById(paymentId)
            .orElseThrow { ResourceNotFoundException("PAYMENT_NOT_FOUND", paymentId, "Payment") }

        if (payment.status != PaymentStatus.POSTED) {
            throw BusinessRuleViolationException(
                errorCode = "PAYMENT_NOT_POSTED",
                message = "Receipt can only be generated for a POSTED payment (current: ${payment.status}).",
                context = mapOf("payment_id" to paymentId, "payment_status" to payment.status.name),
            )
        }

        // 2. Verify the journal entry exists and is POSTED.
        val jeId = payment.journalEntryId
            ?: throw BusinessRuleViolationException(
                errorCode = "JOURNAL_ENTRY_NOT_POSTED",
                message = "Receipt cannot be generated until journal entry is POSTED.",
                context = mapOf("payment_id" to paymentId),
            )

        val journalEntry = journalEntryRepository.findById(jeId)
            .orElseThrow { ResourceNotFoundException("JOURNAL_ENTRY_NOT_FOUND", jeId, "JournalEntry") }

        if (journalEntry.status != JournalEntryStatus.POSTED) {
            throw BusinessRuleViolationException(
                errorCode = "JOURNAL_ENTRY_NOT_POSTED",
                message = "Receipt cannot be generated until journal entry is POSTED (current: ${journalEntry.status}).",
                context = mapOf("payment_id" to paymentId, "journal_entry_id" to jeId, "je_status" to journalEntry.status.name),
            )
        }

        // 3. Guard against duplicate receipts for the same payment.
        if (receiptRepository.findByPaymentId(paymentId).isPresent) {
            throw ConflictException(
                errorCode = "DUPLICATE_RECEIPT",
                message = "A receipt already exists for payment $paymentId.",
                context = mapOf("payment_id" to paymentId),
            )
        }

        // 4. Generate a unique receipt number: RCT-{YYYY}-{entityCode4}-{seqPadded6}-{collision4}
        val year = Year.now().value
        val entityCode = entityId.toString().replace("-", "").take(4).uppercase()
        val seq = receiptRepository.countByEntityId(entityId) + 1L
        val collision = java.util.UUID.randomUUID().toString().replace("-","").take(4).uppercase()
        val receiptNumber = "RCT-$year-$entityCode-${seq.toString().padStart(6, '0')}-$collision"

        // 5. Create and persist the receipt in POSTED status.
        val receipt = Receipt(
            entityId = entityId,
            periodId = periodId,
            receiptNumber = receiptNumber,
            paymentId = paymentId,
            invoiceId = payment.invoiceId,
            customerId = payment.customerId,
            receiptDate = payment.paymentDate,
            receiptAmount = payment.paymentAmount,
            currencyCode = payment.currencyCode,
            journalEntryId = jeId,
            status = ReceiptStatus.POSTED,
            deliveryEmail = deliveryEmail,
            deliveryPhone = deliveryPhone,
            notes = notes,
        )

        return receiptRepository.save(receipt)
    }

    /**
     * §15 — Issue a POSTED receipt to the customer.
     *
     * Transitions: POSTED → ISSUED. Sets issuedAt timestamp.
     * In production this would trigger email/SMS delivery.
     */
    @Transactional
    fun issueReceipt(receiptId: UUID): Receipt {
        val receipt = findById(receiptId)

        if (!receipt.status.canTransitionTo(ReceiptStatus.ISSUED)) {
            throw ValidationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot issue receipt in status ${receipt.status}. Receipt must be POSTED to be issued.",
                context = mapOf("receipt_id" to receiptId, "current_status" to receipt.status.name),
            )
        }

        receipt.status = ReceiptStatus.ISSUED
        receipt.issuedAt = Instant.now()
        val saved = receiptRepository.save(receipt)

        // Fire-and-forget delivery email when the receipt has a delivery address
        if (!saved.deliveryEmail.isNullOrBlank()) {
            val amtFmt = "${saved.currencyCode} ${"%.2f".format(saved.receiptAmount)}"
            emailService.sendReceiptDelivery(saved.deliveryEmail!!, saved.receiptNumber, amtFmt, "QeSuite Entity")
        }

        return saved
    }

    /**
     * §15 — Void a POSTED or ISSUED receipt.
     *
     * Transitions: POSTED → VOID or ISSUED → VOID.
     * Soft-deletes the receipt (isActive = false).
     */
    @Transactional
    fun voidReceipt(receiptId: UUID, reason: String): Receipt {
        val receipt = findById(receiptId)

        if (!receipt.status.canTransitionTo(ReceiptStatus.VOID)) {
            throw ValidationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot void receipt in status ${receipt.status}. Receipt must be POSTED or ISSUED to be voided.",
                context = mapOf("receipt_id" to receiptId, "current_status" to receipt.status.name),
            )
        }

        receipt.status = ReceiptStatus.VOID
        receipt.isActive = false
        receipt.deactivatedAt = Instant.now()
        receipt.deactivationReason = reason
        return receiptRepository.save(receipt)
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun findById(id: UUID): Receipt = receiptRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("RECEIPT_NOT_FOUND", id, "Receipt") }

    @Transactional(readOnly = true)
    fun findByPayment(paymentId: UUID): Receipt = receiptRepository.findByPaymentId(paymentId)
        .orElseThrow { ResourceNotFoundException("RECEIPT_NOT_FOUND", paymentId, "Receipt") }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<Receipt> =
        receiptRepository.findByEntityId(entityId, pageable)

    @Transactional(readOnly = true)
    fun findByCustomer(entityId: UUID, customerId: UUID, pageable: Pageable): Page<Receipt> =
        receiptRepository.findByEntityIdAndCustomerId(entityId, customerId, pageable)
}

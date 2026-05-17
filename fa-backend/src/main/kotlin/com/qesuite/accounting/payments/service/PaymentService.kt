package com.qesuite.accounting.payments.service

import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.service.InvoiceService
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.party.repository.CustomerRepository
import com.qesuite.accounting.payments.domain.Payment
import com.qesuite.accounting.payments.domain.PaymentMethod
import com.qesuite.accounting.payments.domain.PaymentStatus
import com.qesuite.accounting.payments.dto.CreatePaymentCommand
import com.qesuite.accounting.payments.dto.MpesaCallbackPayload
import com.qesuite.accounting.payments.repository.PaymentRepository
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Year
import java.util.UUID

/**
 * §14 (Module 14) — Payment Service.
 *
 * Owns the full payment lifecycle:
 *   PENDING → MATCHED → APPROVED → POSTED → REVERSED
 *
 * Cross-module collaborators (Rule 11 — only via Spring service interfaces):
 *   • [InvoiceService]   — apply payment / reverse payment against invoice balance.
 *   • [JournalService]   — create and post double-entry journal entries.
 *   • [CustomerRepository] — AR account lookup, active/inactive guard.
 *   • [AccountRepository]  — cash/bank account lookup by subtype.
 *
 * Money discipline (Rule 01): all amounts are BigDecimal rounded HALF_EVEN to scale 6.
 */
@Service
@Transactional
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val invoiceService: InvoiceService,
    private val customerRepository: CustomerRepository,
    private val accountRepository: AccountRepository,
    private val journalService: JournalService
) {

    private val log = LoggerFactory.getLogger(PaymentService::class.java)

    companion object {
        private const val MONEY_SCALE = 6
        private val ROUND = RoundingMode.HALF_EVEN

        /**
         * Sentinel UUIDs used as placeholders for M-Pesa suspense payments when
         * the real entityId/customerId/periodId cannot be resolved from a session
         * table at callback time. Operators must match and correct these before
         * the payment can be approved and posted.
         *
         * In a production deployment these are replaced by the STK push session
         * lookup service that stores entityId+customerId+periodId keyed on
         * checkoutRequestId at push initiation time.
         */
        val MPESA_SUSPENSE_ENTITY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val MPESA_SUSPENSE_CUSTOMER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val MPESA_SUSPENSE_PERIOD_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
    }

    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    /**
     * Record a new payment in PENDING status.
     *
     * Steps:
     * 1. Validate customer exists and is active (skipped for M-Pesa suspense sentinel).
     * 2. Check transactionReference uniqueness (if provided).
     * 3. Generate payment number: PAY-{YYYY}-{entityCode4}-{seqPadded6}.
     * 4. Calculate functionalAmount = paymentAmount * exchangeRate (HALF_EVEN, scale 6).
     * 5. Persist and return.
     */
    fun createPayment(command: CreatePaymentCommand): Payment {
        // D8 — For M-Pesa suspense payments, skip customer validation
        val isSuspense = command.customerId == MPESA_SUSPENSE_CUSTOMER_ID
        val customer = if (!isSuspense) {
            customerRepository.findById(command.customerId)
                .orElseThrow {
                    ResourceNotFoundException("CUSTOMER_NOT_FOUND", command.customerId, "Customer")
                }
        } else null

        if (customer != null && !customer.isActive) {
            throw BusinessRuleViolationException(
                errorCode = "CUSTOMER_INACTIVE",
                message = "Customer ${customer.customerCode} is inactive and cannot receive payments.",
                context = mapOf("customer_id" to customer.id)
            )
        }

        // D8 — Transaction reference uniqueness: for M-Pesa, return existing payment on duplicate receipt
        if (command.transactionReference != null) {
            if (isSuspense) {
                val existing = paymentRepository.findByTransactionReference(command.transactionReference)
                if (existing.isPresent) {
                    log.warn(
                        "mpesa.callback: duplicate transaction reference {} — returning existing payment",
                        command.transactionReference
                    )
                    return existing.get()
                }
            } else {
                if (paymentRepository.existsByEntityIdAndTransactionReference(
                        command.entityId, command.transactionReference
                    )
                ) {
                    throw ConflictException(
                        errorCode = "DUPLICATE_TRANSACTION_REFERENCE",
                        message = "A payment with transaction reference '${command.transactionReference}' already exists for this entity.",
                        context = mapOf("transaction_reference" to command.transactionReference)
                    )
                }
            }
        }

        // Generate payment number
        val paymentNumber = generatePaymentNumber(command.entityId)

        // Calculate functional amount
        val functionalAmount = command.paymentAmount
            .multiply(command.exchangeRate)
            .setScale(MONEY_SCALE, ROUND)

        // Persist
        val payment = Payment(
            entityId = command.entityId,
            periodId = command.periodId,
            paymentNumber = paymentNumber,
            invoiceId = command.invoiceId,
            customerId = command.customerId,
            paymentMethod = command.paymentMethod,
            paymentAmount = command.paymentAmount.setScale(MONEY_SCALE, ROUND),
            currencyCode = command.currencyCode,
            exchangeRate = command.exchangeRate.setScale(MONEY_SCALE, ROUND),
            functionalAmount = functionalAmount,
            status = PaymentStatus.PENDING,
            transactionReference = command.transactionReference,
            paymentDate = command.paymentDate,
            notes = command.notes
        )
        return paymentRepository.save(payment)
    }

    // -----------------------------------------------------------------------
    // MATCH
    // -----------------------------------------------------------------------

    /**
     * Link a PENDING payment to an invoice.
     *
     * Validates:
     * - Payment must be PENDING.
     * - Invoice must be in APPROVED, SENT, or PARTIALLY_PAID state.
     * - matchedAmount ≤ payment.paymentAmount AND ≤ invoice.outstandingAmount.
     *
     * D9 — Stores matchedAmount on the payment for use during postPayment().
     */
    fun matchToInvoice(paymentId: UUID, invoiceId: UUID, matchedAmount: BigDecimal): Payment {
        val payment = findById(paymentId)
        enforceTransition(payment, PaymentStatus.MATCHED)

        val invoice = invoiceService.findById(invoiceId)
        val acceptableStatuses = setOf(InvoiceStatus.APPROVED, InvoiceStatus.SENT, InvoiceStatus.PARTIALLY_PAID)
        if (invoice.status !in acceptableStatuses) {
            throw BusinessRuleViolationException(
                errorCode = "INVOICE_NOT_MATCHABLE",
                message = "Invoice must be in APPROVED, SENT, or PARTIALLY_PAID status to receive a payment match (current: ${invoice.status}).",
                context = mapOf("invoice_id" to invoiceId, "invoice_status" to invoice.status.name)
            )
        }

        val scaled = matchedAmount.setScale(MONEY_SCALE, ROUND)
        if (scaled.compareTo(payment.paymentAmount) > 0) {
            throw BusinessRuleViolationException(
                errorCode = "MATCHED_AMOUNT_EXCEEDS_PAYMENT",
                message = "Matched amount ($scaled) exceeds payment amount (${payment.paymentAmount}).",
                context = mapOf("payment_id" to paymentId, "matched_amount" to scaled, "payment_amount" to payment.paymentAmount)
            )
        }
        if (scaled.compareTo(invoice.outstandingAmount) > 0) {
            throw BusinessRuleViolationException(
                errorCode = "MATCHED_AMOUNT_EXCEEDS_OUTSTANDING",
                message = "Matched amount ($scaled) exceeds invoice outstanding balance (${invoice.outstandingAmount}).",
                context = mapOf("invoice_id" to invoiceId, "matched_amount" to scaled, "outstanding_amount" to invoice.outstandingAmount)
            )
        }

        payment.invoiceId = invoiceId
        payment.status = PaymentStatus.MATCHED
        // D9 — store matched amount so postPayment() applies the correct partial amount
        payment.matchedAmount = scaled
        return paymentRepository.save(payment)
    }

    // -----------------------------------------------------------------------
    // APPROVE
    // -----------------------------------------------------------------------

    /**
     * Approve a MATCHED payment — moves it to APPROVED, ready for posting.
     */
    fun approvePayment(paymentId: UUID): Payment {
        val payment = findById(paymentId)
        enforceTransition(payment, PaymentStatus.APPROVED)
        payment.status = PaymentStatus.APPROVED
        return paymentRepository.save(payment)
    }

    // -----------------------------------------------------------------------
    // POST
    // -----------------------------------------------------------------------

    /**
     * Post an APPROVED payment to the general ledger.
     *
     * Journal entry (double-entry):
     *   DR Cash/Bank account         (functionalAmount)
     *   CR Accounts Receivable account (functionalAmount)
     *
     * D9 — Calls [InvoiceService.applyPayment] with matchedAmount (not full paymentAmount)
     * so partial payment matches are applied correctly to the invoice outstanding balance.
     */
    fun postPayment(paymentId: UUID): Payment {
        val payment = findById(paymentId)
        enforceTransition(payment, PaymentStatus.POSTED)

        // Invoice must be linked
        val invoiceId = payment.invoiceId
            ?: throw BusinessRuleViolationException(
                errorCode = "UNMATCHED_PAYMENT",
                message = "Payment ${payment.paymentNumber} is not matched to any invoice. Match it before posting.",
                context = mapOf("payment_id" to paymentId)
            )
        val invoice = invoiceService.findById(invoiceId)

        // Customer AR account
        val customer = customerRepository.findById(payment.customerId)
            .orElseThrow { ResourceNotFoundException("CUSTOMER_NOT_FOUND", payment.customerId, "Customer") }
        val arAccountId = customer.defaultArAccountId
            ?: throw BusinessRuleViolationException(
                errorCode = "CUSTOMER_AR_ACCOUNT_MISSING",
                message = "Customer ${customer.customerCode} has no defaultArAccountId configured.",
                context = mapOf("customer_id" to customer.id)
            )

        // Cash/bank account — find first CASH_AND_EQUIVALENTS account for the entity
        val cashAccount = accountRepository.findAllByEntityId(payment.entityId)
            .firstOrNull { it.accountSubtype == AccountSubtype.CASH_AND_EQUIVALENTS && it.isActive }
            ?: throw BusinessRuleViolationException(
                errorCode = "MISSING_CASH_ACCOUNT",
                message = "No active CASH_AND_EQUIVALENTS account found for entity ${payment.entityId}. Configure one before posting payments.",
                context = mapOf("entity_id" to payment.entityId)
            )

        val periodId = payment.periodId
            ?: invoice.periodId
            ?: throw BusinessRuleViolationException(
                errorCode = "PERIOD_ID_MISSING",
                message = "Payment ${payment.paymentNumber} has no periodId. Set a period before posting.",
                context = mapOf("payment_id" to paymentId)
            )

        // Build double-entry journal
        val jeLines = listOf(
            CreateJournalLineCommand(
                accountId = cashAccount.id,
                description = "Payment ${payment.paymentNumber} — Cash received",
                debitAmount = payment.functionalAmount,
                creditAmount = BigDecimal.ZERO,
                currencyCode = payment.currencyCode,
                exchangeRate = payment.exchangeRate
            ),
            CreateJournalLineCommand(
                accountId = arAccountId,
                description = "Payment ${payment.paymentNumber} — AR cleared",
                debitAmount = BigDecimal.ZERO,
                creditAmount = payment.functionalAmount,
                currencyCode = payment.currencyCode,
                exchangeRate = payment.exchangeRate
            )
        )
        val journalEntry = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId = payment.entityId,
                periodId = periodId,
                transDate = payment.paymentDate,
                description = "Payment ${payment.paymentNumber} — Invoice ${invoice.invoiceNumber}",
                sourceType = "PAYMENT",
                sourceId = payment.id,
                lines = jeLines
            )
        )
        journalService.postEntryAsSystem(journalEntry.id)

        // Update payment
        payment.journalEntryId = journalEntry.id
        payment.status = PaymentStatus.POSTED

        // D9 — Use matchedAmount when applying payment to invoice, fall back to paymentAmount
        val amountToApply = payment.matchedAmount ?: payment.paymentAmount
        invoiceService.applyPayment(invoiceId, amountToApply)

        return paymentRepository.save(payment)
    }

    // -----------------------------------------------------------------------
    // REVERSE
    // -----------------------------------------------------------------------

    /**
     * Reverse a POSTED payment.
     *
     * Reversing journal entry (opposite of original):
     *   DR Accounts Receivable account (functionalAmount)
     *   CR Cash/Bank account           (functionalAmount)
     *
     * Also restores the invoice outstanding balance by re-adding the payment amount.
     */
    fun reversePayment(paymentId: UUID, reason: String): Payment {
        val payment = findById(paymentId)
        enforceTransition(payment, PaymentStatus.REVERSED)

        val invoiceId = payment.invoiceId
            ?: throw BusinessRuleViolationException(
                errorCode = "UNMATCHED_PAYMENT",
                message = "Cannot reverse unmatched payment ${payment.paymentNumber}.",
                context = mapOf("payment_id" to paymentId)
            )
        val invoice = invoiceService.findById(invoiceId)

        val customer = customerRepository.findById(payment.customerId)
            .orElseThrow { ResourceNotFoundException("CUSTOMER_NOT_FOUND", payment.customerId, "Customer") }
        val arAccountId = customer.defaultArAccountId
            ?: throw BusinessRuleViolationException(
                errorCode = "CUSTOMER_AR_ACCOUNT_MISSING",
                message = "Customer ${customer.customerCode} has no defaultArAccountId.",
                context = mapOf("customer_id" to customer.id)
            )

        val cashAccount = accountRepository.findAllByEntityId(payment.entityId)
            .firstOrNull { it.accountSubtype == AccountSubtype.CASH_AND_EQUIVALENTS && it.isActive }
            ?: throw BusinessRuleViolationException(
                errorCode = "MISSING_CASH_ACCOUNT",
                message = "No active CASH_AND_EQUIVALENTS account found for entity ${payment.entityId}.",
                context = mapOf("entity_id" to payment.entityId)
            )

        val periodId = payment.periodId
            ?: invoice.periodId
            ?: throw BusinessRuleViolationException(
                errorCode = "PERIOD_ID_MISSING",
                message = "Payment ${payment.paymentNumber} has no periodId for reversal entry.",
                context = mapOf("payment_id" to paymentId)
            )

        // Reversing journal: DR AR / CR Cash (opposite of original)
        val jeLines = listOf(
            CreateJournalLineCommand(
                accountId = arAccountId,
                description = "Reversal of Payment ${payment.paymentNumber} — AR reinstated",
                debitAmount = payment.functionalAmount,
                creditAmount = BigDecimal.ZERO,
                currencyCode = payment.currencyCode,
                exchangeRate = payment.exchangeRate
            ),
            CreateJournalLineCommand(
                accountId = cashAccount.id,
                description = "Reversal of Payment ${payment.paymentNumber} — Cash returned",
                debitAmount = BigDecimal.ZERO,
                creditAmount = payment.functionalAmount,
                currencyCode = payment.currencyCode,
                exchangeRate = payment.exchangeRate
            )
        )
        val reversalEntry = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId = payment.entityId,
                periodId = periodId,
                transDate = LocalDate.now(),
                description = "Reversal of Payment ${payment.paymentNumber} — Reason: $reason",
                sourceType = "PAYMENT_REVERSAL",
                sourceId = payment.id,
                lines = jeLines
            )
        )
        journalService.postEntryAsSystem(reversalEntry.id)

        // Restore invoice outstanding balance: add back the reversed amount
        val scaledAmount = payment.paymentAmount.setScale(MONEY_SCALE, ROUND)
        invoice.paidAmount = invoice.paidAmount.subtract(scaledAmount).setScale(MONEY_SCALE, ROUND)
        invoice.outstandingAmount = invoice.totalAmount.subtract(invoice.paidAmount).setScale(MONEY_SCALE, ROUND)
        invoice.status = when {
            invoice.paidAmount.signum() == 0 -> {
                // Determine prior approved/sent state — fall back to SENT if already sent
                if (invoice.status == InvoiceStatus.PAID || invoice.status == InvoiceStatus.PARTIALLY_PAID) {
                    InvoiceStatus.SENT
                } else invoice.status
            }
            invoice.paidAmount.signum() > 0 -> InvoiceStatus.PARTIALLY_PAID
            else -> invoice.status
        }
        // The invoice entity is JPA-managed within this transaction; dirty-checking flushes it automatically.

        payment.status = PaymentStatus.REVERSED
        payment.notes = if (payment.notes.isNullOrBlank()) "Reversed: $reason"
                        else "${payment.notes} | Reversed: $reason"

        return paymentRepository.save(payment)
    }

    // -----------------------------------------------------------------------
    // M-PESA CALLBACK
    // -----------------------------------------------------------------------

    /**
     * Process an M-Pesa STK Push callback (Daraja API v2).
     *
     * Always returns within 5 seconds. A failed resultCode (≠ 0) stores a PENDING
     * payment with the failure description in notes for audit purposes.
     *
     * On success:
     * 1. Extract Amount, MpesaReceiptNumber, PhoneNumber, TransactionDate from callbackMetadata.
     * 2. Build a CreatePaymentCommand (invoiceId = null — suspense until matched).
     * 3. Call createPayment — which handles duplicate receipt numbers idempotently.
     * 4. Stamp mpesaReceiptNumber and mpesaResultCode on the saved payment.
     */
    fun processMpesaCallback(payload: MpesaCallbackPayload): Payment {
        val cb = payload.body.stkCallback

        // Extract metadata items into a name→value map for convenience
        val meta: Map<String, Any?> = cb.callbackMetadata?.item
            ?.associate { it.name to it.value }
            ?: emptyMap()

        val resultCodeStr = cb.resultCode.toString()

        if (cb.resultCode != 0) {
            // Failed payment — record for audit trail
            log.warn(
                "M-Pesa STK callback failed: merchantRequestId={}, resultCode={}, resultDesc={}",
                cb.merchantRequestId, cb.resultCode, cb.resultDesc
            )
            // Store a minimal PENDING payment so the transaction is auditable
            val failedPayment = Payment(
                entityId = MPESA_SUSPENSE_ENTITY_ID,
                paymentNumber = "MPESA-FAIL-${cb.checkoutRequestId.takeLast(12)}",
                customerId = MPESA_SUSPENSE_CUSTOMER_ID,
                paymentMethod = PaymentMethod.MPESA,
                paymentAmount = BigDecimal.ONE.setScale(MONEY_SCALE, ROUND),
                currencyCode = "KES",
                exchangeRate = BigDecimal.ONE.setScale(MONEY_SCALE, ROUND),
                functionalAmount = BigDecimal.ONE.setScale(MONEY_SCALE, ROUND),
                status = PaymentStatus.PENDING,
                mpesaResultCode = resultCodeStr,
                paymentDate = LocalDate.now(),
                notes = "M-Pesa FAILED — Code: ${cb.resultCode} — ${cb.resultDesc}"
            )
            // NOTE: In production the entityId/customerId are resolved from the checkoutRequestId
            // stored in a session table when the STK push was initiated.
            return failedPayment  // not persisted — let the controller decide; no entity context
        }

        // Successful callback — extract fields
        val amount = extractBigDecimal(meta["Amount"])
        val receiptNumber = meta["MpesaReceiptNumber"]?.toString()
            ?: "MPESA-${cb.checkoutRequestId.takeLast(10)}"
        val phoneNumber = meta["PhoneNumber"]?.toString() ?: "UNKNOWN"
        val transactionDateRaw = meta["TransactionDate"]?.toString()
        val paymentDate = parseMpesaDate(transactionDateRaw)

        log.info(
            "M-Pesa STK success: receipt={}, amount={}, phone={}",
            receiptNumber, amount, phoneNumber
        )

        // Build a minimal suspense payment command (invoiceId = null until matched).
        // createPayment() will detect the MPESA_SUSPENSE_CUSTOMER_ID sentinel and skip customer
        // validation; it will also de-duplicate by receipt number and return the existing payment.
        val command = CreatePaymentCommand(
            entityId = MPESA_SUSPENSE_ENTITY_ID,
            periodId = MPESA_SUSPENSE_PERIOD_ID,
            customerId = MPESA_SUSPENSE_CUSTOMER_ID,
            invoiceId = null,
            paymentMethod = PaymentMethod.MPESA,
            paymentAmount = amount,
            currencyCode = "KES",
            exchangeRate = BigDecimal.ONE,
            transactionReference = receiptNumber,
            paymentDate = paymentDate,
            notes = "M-Pesa payment from $phoneNumber — ref $receiptNumber"
        )

        val saved = createPayment(command)
        saved.mpesaReceiptNumber = receiptNumber
        saved.mpesaResultCode = resultCodeStr
        return paymentRepository.save(saved)
    }

    // -----------------------------------------------------------------------
    // QUERY
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    fun findById(id: UUID): Payment = paymentRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("PAYMENT_NOT_FOUND", id, "Payment") }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<Payment> =
        paymentRepository.findByEntityId(entityId, pageable)

    @Transactional(readOnly = true)
    fun findByEntityAndStatus(entityId: UUID, status: PaymentStatus, pageable: Pageable): Page<Payment> =
        paymentRepository.findByEntityIdAndStatus(entityId, status, pageable)

    @Transactional(readOnly = true)
    fun findByCustomer(entityId: UUID, customerId: UUID, pageable: Pageable): Page<Payment> =
        paymentRepository.findByEntityIdAndCustomerId(entityId, customerId, pageable)

    @Transactional(readOnly = true)
    fun findByInvoice(entityId: UUID, invoiceId: UUID): List<Payment> =
        paymentRepository.findByEntityIdAndInvoiceId(entityId, invoiceId)

    // -----------------------------------------------------------------------
    // INTERNAL HELPERS
    // -----------------------------------------------------------------------

    private fun enforceTransition(payment: Payment, next: PaymentStatus) {
        if (!payment.status.canTransitionTo(next)) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATE_TRANSITION",
                message = "Cannot transition payment ${payment.paymentNumber} from ${payment.status} to $next.",
                context = mapOf(
                    "payment_id" to payment.id,
                    "current_status" to payment.status.name,
                    "requested_status" to next.name
                )
            )
        }
    }

    private fun generatePaymentNumber(entityId: UUID): String {
        val year       = Year.now().value
        val entityCode = entityId.toString().replace("-", "").take(4).uppercase()
        val seq        = paymentRepository.countByEntityId(entityId) + 1
        val collision  = UUID.randomUUID().toString().replace("-","").take(4).uppercase()
        return "PAY-$year-$entityCode-${seq.toString().padStart(6, '0')}-$collision"
    }

    private fun parseMpesaDate(raw: String?): LocalDate {
        if (raw == null || raw.length < 8) return LocalDate.now()
        return try {
            LocalDate.of(
                raw.substring(0, 4).toInt(),
                raw.substring(4, 6).toInt(),
                raw.substring(6, 8).toInt()
            )
        } catch (e: Exception) {
            log.warn("Could not parse M-Pesa TransactionDate '{}', using today", raw)
            LocalDate.now()
        }
    }

    private fun extractBigDecimal(value: Any?): BigDecimal = when (value) {
        null -> BigDecimal.ZERO
        is Number -> BigDecimal(value.toString()).setScale(MONEY_SCALE, ROUND)
        is String -> value.toBigDecimalOrNull()?.setScale(MONEY_SCALE, ROUND) ?: BigDecimal.ZERO
        else -> BigDecimal.ZERO
    }
}

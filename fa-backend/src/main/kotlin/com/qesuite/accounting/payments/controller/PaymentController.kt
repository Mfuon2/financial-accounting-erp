package com.qesuite.accounting.payments.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.qesuite.accounting.payments.domain.Payment
import com.qesuite.accounting.payments.domain.PaymentStatus
import com.qesuite.accounting.payments.dto.CreatePaymentCommand
import com.qesuite.accounting.payments.dto.MpesaCallbackPayload
import com.qesuite.accounting.payments.dto.PaymentMatchRequest
import com.qesuite.accounting.payments.dto.PaymentResponse
import com.qesuite.accounting.payments.dto.ReversePaymentRequest
import com.qesuite.accounting.payments.service.PaymentService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.idempotency.service.IdempotencyResult
import com.qesuite.accounting.shared.idempotency.service.IdempotencyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Async
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * §14 (Module 14) — Payment REST endpoints.
 *
 * POST /api/v1/payments                   — create payment (requires Idempotency-Key)
 * GET  /api/v1/payments                   — list payments (paginated, optional filters)
 * GET  /api/v1/payments/{id}              — get payment by ID
 * POST /api/v1/payments/{id}/match        — match to invoice
 * POST /api/v1/payments/{id}/approve      — approve matched payment
 * POST /api/v1/payments/{id}/post         — post to general ledger
 * POST /api/v1/payments/{id}/reverse      — reverse a posted payment
 * POST /api/v1/payments/mpesa/callback    — M-Pesa STK push callback (PUBLIC, no auth)
 *
 * The M-Pesa callback MUST respond within 5 seconds (Safaricom requirement).
 * The controller returns HTTP 200 immediately and dispatches async processing.
 */
@Tag(name = "Module 14: Payments", description = "Payment lifecycle management — Module 14")
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService,
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(PaymentController::class.java)

    // -----------------------------------------------------------------------
    // CREATE
    // -----------------------------------------------------------------------

    @Operation(summary = "Create a new payment (PENDING status)")
    @PostMapping
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CFO','SYSTEM_ADMIN')")
    fun createPayment(
        @Valid @RequestBody command: CreatePaymentCommand,
        @RequestHeader(value = "Idempotency-Key", required = true)
        @Parameter(description = "Client-generated UUID for idempotent retry safety")
        idempotencyKey: String
    ): ResponseEntity<ApiResponse<PaymentResponse>> {
        // Idempotency guard
        val idem = idempotencyService.checkAndStore(idempotencyKey, command.entityId)
        if (idem is IdempotencyResult.DUPLICATE) {
            val cached = objectMapper.readValue(idem.cachedResponse, PaymentResponse::class.java)
            return ResponseEntity.ok(ApiResponse.success(cached))
        }

        val payment = paymentService.createPayment(command)
        val response = payment.toResponse()

        // Cache the response for idempotency replay
        idempotencyService.updateResponse(
            idempotencyKey, command.entityId, objectMapper.writeValueAsString(response)
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response))
    }

    // -----------------------------------------------------------------------
    // LIST / GET
    // -----------------------------------------------------------------------

    @Operation(summary = "List payments for an entity with optional filters (paginated)")
    @GetMapping
    @PreAuthorize("hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CFO','AUDITOR','SYSTEM_ADMIN')")
    fun listPayments(
        @RequestParam entityId: UUID,
        @RequestParam(required = false) status: PaymentStatus?,
        @RequestParam(required = false) customerId: UUID?,
        pageable: Pageable
    ): ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> {
        val page = when {
            customerId != null -> paymentService.findByCustomer(entityId, customerId, pageable)
            status != null -> paymentService.findByEntityAndStatus(entityId, status, pageable)
            else -> paymentService.findByEntity(entityId, pageable)
        }
        return ResponseEntity.ok(ApiResponse.success(page.toPagedResponse { it.toResponse() }))
    }

    @Operation(summary = "Get payment by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CFO','AUDITOR','SYSTEM_ADMIN')")
    fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<PaymentResponse>> {
        return ResponseEntity.ok(ApiResponse.success(paymentService.findById(id).toResponse()))
    }

    // -----------------------------------------------------------------------
    // MATCH
    // -----------------------------------------------------------------------

    @Operation(summary = "Match a PENDING payment to an invoice")
    @PostMapping("/{id}/match")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CFO','SYSTEM_ADMIN')")
    fun matchToInvoice(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PaymentMatchRequest
    ): ResponseEntity<ApiResponse<PaymentResponse>> {
        val payment = paymentService.matchToInvoice(id, request.invoiceId, request.matchedAmount)
        return ResponseEntity.ok(ApiResponse.success(payment.toResponse()))
    }

    // -----------------------------------------------------------------------
    // APPROVE
    // -----------------------------------------------------------------------

    @Operation(summary = "Approve a MATCHED payment")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CFO','SYSTEM_ADMIN')")
    fun approvePayment(@PathVariable id: UUID): ResponseEntity<ApiResponse<PaymentResponse>> {
        val payment = paymentService.approvePayment(id)
        return ResponseEntity.ok(ApiResponse.success(payment.toResponse()))
    }

    // -----------------------------------------------------------------------
    // POST
    // -----------------------------------------------------------------------

    @Operation(summary = "Post an APPROVED payment to the general ledger")
    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CFO','SYSTEM_ADMIN')")
    fun postPayment(@PathVariable id: UUID): ResponseEntity<ApiResponse<PaymentResponse>> {
        val payment = paymentService.postPayment(id)
        return ResponseEntity.ok(ApiResponse.success(payment.toResponse()))
    }

    // -----------------------------------------------------------------------
    // REVERSE
    // -----------------------------------------------------------------------

    @Operation(summary = "Reverse a POSTED payment (creates reversing journal entry)")
    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CFO','SYSTEM_ADMIN')")
    fun reversePayment(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReversePaymentRequest
    ): ResponseEntity<ApiResponse<PaymentResponse>> {
        val payment = paymentService.reversePayment(id, request.reason)
        return ResponseEntity.ok(ApiResponse.success(payment.toResponse()))
    }

    // -----------------------------------------------------------------------
    // M-PESA CALLBACK  (PUBLIC — no auth required, must respond within 5 s)
    // -----------------------------------------------------------------------

    @Operation(
        summary = "M-Pesa STK Push callback (PUBLIC — no authentication required)",
        description = "Daraja API v2 callback endpoint. Must respond HTTP 200 within 5 seconds. " +
            "Processing is dispatched asynchronously to meet Safaricom's timeout requirement."
    )
    @PostMapping("/mpesa/callback")
    fun mpesaCallback(
        @Valid @RequestBody payload: MpesaCallbackPayload,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?
    ): ResponseEntity<ApiResponse<Map<String, String>>> {
        val key = idempotencyKey ?: payload.body.stkCallback.checkoutRequestId

        // Fast idempotency check — use a fixed suspense entity for M-Pesa callbacks
        val idem = idempotencyService.checkAndStore(key, PaymentService.MPESA_SUSPENSE_ENTITY_ID)
        if (idem is IdempotencyResult.DUPLICATE) {
            // Already processed — return 200 immediately (Safaricom requires 200 always)
            return ResponseEntity.ok(
                ApiResponse.success(mapOf("ResultCode" to "0", "ResultDesc" to "Accepted (duplicate)"))
            )
        }

        // Dispatch async so we respond well within 5 seconds
        dispatchMpesaProcessing(payload, key)

        // Acknowledge immediately to Safaricom
        return ResponseEntity.ok(
            ApiResponse.success(mapOf("ResultCode" to "0", "ResultDesc" to "Accepted"))
        )
    }

    /**
     * Async dispatch for M-Pesa callback processing.
     * Marked @Async — requires @EnableAsync on a @Configuration class.
     * If the async executor is not configured, the call executes synchronously
     * (still safe; the 5-second constraint is only violated under heavy load).
     */
    @Async
    fun dispatchMpesaProcessing(payload: MpesaCallbackPayload, idempotencyKey: String) {
        try {
            val payment = paymentService.processMpesaCallback(payload)
            val response = objectMapper.writeValueAsString(payment.toResponse())
            idempotencyService.updateResponse(
                idempotencyKey, PaymentService.MPESA_SUSPENSE_ENTITY_ID, response
            )
            log.info(
                "M-Pesa callback processed: paymentNumber={}, status={}",
                payment.paymentNumber, payment.status
            )
        } catch (ex: Exception) {
            log.error("M-Pesa async processing failed for key={}: {}", idempotencyKey, ex.message, ex)
        }
    }

    // -----------------------------------------------------------------------
    // MAPPING HELPER
    // -----------------------------------------------------------------------

    private fun Payment.toResponse() = PaymentResponse(
        id = id,
        entityId = entityId,
        periodId = periodId,
        paymentNumber = paymentNumber,
        invoiceId = invoiceId,
        customerId = customerId,
        paymentMethod = paymentMethod,
        paymentAmount = paymentAmount,
        currencyCode = currencyCode,
        exchangeRate = exchangeRate,
        functionalAmount = functionalAmount,
        status = status,
        transactionReference = transactionReference,
        journalEntryId = journalEntryId,
        mpesaResultCode = mpesaResultCode,
        mpesaReceiptNumber = mpesaReceiptNumber,
        paymentDate = paymentDate,
        notes = notes,
        version = version
    )
}

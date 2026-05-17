package com.qesuite.accounting.payments.dto

import com.qesuite.accounting.payments.domain.PaymentMethod
import com.qesuite.accounting.payments.domain.PaymentStatus
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// ---------------------------------------------------------------------------
// Command: create a new payment
// ---------------------------------------------------------------------------

data class CreatePaymentCommand(
    @field:NotNull val entityId: UUID,
    @field:NotNull val periodId: UUID,
    @field:NotNull val customerId: UUID,
    val invoiceId: UUID?,
    @field:NotNull val paymentMethod: PaymentMethod,
    @field:NotNull
    @field:DecimalMin(value = "0.000001", message = "paymentAmount must be > 0")
    val paymentAmount: BigDecimal,
    @field:NotBlank @field:Size(min = 3, max = 3) val currencyCode: String,
    val exchangeRate: BigDecimal = BigDecimal.ONE,
    val transactionReference: String?,
    @field:NotNull val paymentDate: LocalDate,
    val notes: String?
)

// ---------------------------------------------------------------------------
// Request: match a payment to an invoice
// ---------------------------------------------------------------------------

data class PaymentMatchRequest(
    @field:NotNull val invoiceId: UUID,
    @field:NotNull
    @field:DecimalMin(value = "0.000001", message = "matchedAmount must be > 0")
    val matchedAmount: BigDecimal
)

// ---------------------------------------------------------------------------
// Request: reverse a posted payment
// ---------------------------------------------------------------------------

data class ReversePaymentRequest(
    @field:NotBlank val reason: String
)

// ---------------------------------------------------------------------------
// M-Pesa STK Push callback payload (Daraja API v2 format)
// ---------------------------------------------------------------------------

data class MpesaCallbackPayload(
    val body: MpesaCallbackBody
)

data class MpesaCallbackBody(
    val stkCallback: MpesaStkCallback
)

data class MpesaStkCallback(
    val merchantRequestId: String,
    val checkoutRequestId: String,
    val resultCode: Int,
    val resultDesc: String,
    val callbackMetadata: MpesaCallbackMetadata?
)

data class MpesaCallbackMetadata(
    val item: List<MpesaMetadataItem>
)

data class MpesaMetadataItem(
    val name: String,
    val value: Any?
)

// ---------------------------------------------------------------------------
// Response DTO (read-safe projection)
// ---------------------------------------------------------------------------

data class PaymentResponse(
    val id: UUID,
    val entityId: UUID,
    val periodId: UUID?,
    val paymentNumber: String,
    val invoiceId: UUID?,
    val customerId: UUID,
    val paymentMethod: PaymentMethod,
    val paymentAmount: BigDecimal,
    val currencyCode: String,
    val exchangeRate: BigDecimal,
    val functionalAmount: BigDecimal,
    val status: PaymentStatus,
    val transactionReference: String?,
    val journalEntryId: UUID?,
    val mpesaResultCode: String?,
    val mpesaReceiptNumber: String?,
    val paymentDate: LocalDate,
    val notes: String?,
    val version: Long
)

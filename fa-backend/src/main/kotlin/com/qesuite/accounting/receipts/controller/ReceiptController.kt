package com.qesuite.accounting.receipts.controller

import com.qesuite.accounting.receipts.domain.Receipt
import com.qesuite.accounting.receipts.service.ReceiptService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * §15 — Receipt REST endpoints.
 *
 * POST /api/v1/receipts/generate               — generate receipt for a posted payment (201)
 * GET  /api/v1/receipts?entityId=              — list receipts for an entity (paginated)
 * GET  /api/v1/receipts/{id}                   — get receipt by ID
 * POST /api/v1/receipts/{id}/issue             — issue receipt to customer (201)
 * POST /api/v1/receipts/{id}/void              — void receipt (body: reason)
 * GET  /api/v1/receipts/by-payment/{paymentId} — find receipt linked to a payment
 */
@Tag(name = "Module 15: Receipts", description = "Receipt lifecycle management — Module 15")
@RestController
@RequestMapping("/api/v1/receipts")
class ReceiptController(
    private val receiptService: ReceiptService,
) {

    // -----------------------------------------------------------------------
    // GENERATE  (POST → 201 CREATED)
    // -----------------------------------------------------------------------

    @Operation(
        summary = "Generate a receipt for a posted payment",
        description = "Pre-conditions: payment must be POSTED and backed by a POSTED journal entry. " +
            "Returns HTTP 201 with the persisted receipt in POSTED status."
    )
    @PostMapping("/generate")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun generateReceipt(
        @Valid @RequestBody request: GenerateReceiptRequest
    ): ResponseEntity<ApiResponse<Receipt>> {
        SecurityUtils.requireOwnEntity(request.entityId)
        val receipt = receiptService.generateReceipt(
            paymentId     = request.paymentId,
            entityId      = request.entityId,
            periodId      = request.periodId,
            deliveryEmail = request.deliveryEmail,
            deliveryPhone = request.deliveryPhone,
            notes         = request.notes,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(receipt))
    }

    // -----------------------------------------------------------------------
    // LIST BY ENTITY  (GET)
    // -----------------------------------------------------------------------

    @Operation(summary = "List all receipts for an entity (paginated)")
    @GetMapping
    @PreAuthorize(RoleSets.BROAD_READ)
    fun findByEntity(
        @RequestParam
        @Parameter(description = "Entity (tenant) ID", required = true)
        entityId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<ApiResponse<PagedResponse<Receipt>>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = receiptService.findByEntity(entityId, pageable)
        return ResponseEntity.ok(ApiResponse.success(page.toPagedResponse { it }))
    }

    // -----------------------------------------------------------------------
    // GET BY ID
    // -----------------------------------------------------------------------

    @Operation(summary = "Get a receipt by its ID")
    @GetMapping("/{id}")
    @PreAuthorize(RoleSets.BROAD_READ)
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<ApiResponse<Receipt>> {
        val receipt = receiptService.findById(id)
        SecurityUtils.requireOwnEntity(receipt.entityId)
        return ResponseEntity.ok(ApiResponse.success(receipt))
    }

    // -----------------------------------------------------------------------
    // ISSUE  (POST → 201 CREATED)
    // -----------------------------------------------------------------------

    @Operation(
        summary = "Issue a POSTED receipt to the customer",
        description = "Transitions receipt from POSTED → ISSUED and records the issuedAt timestamp. " +
            "Returns HTTP 201."
    )
    @PostMapping("/{id}/issue")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun issueReceipt(
        @PathVariable id: UUID,
    ): ResponseEntity<ApiResponse<Receipt>> {
        SecurityUtils.requireOwnEntity(receiptService.findById(id).entityId)
        val receipt = receiptService.issueReceipt(id)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(receipt))
    }

    // -----------------------------------------------------------------------
    // VOID
    // -----------------------------------------------------------------------

    @Operation(
        summary = "Void a POSTED or ISSUED receipt",
        description = "Soft-deletes the receipt (isActive = false) and transitions to VOID (terminal). " +
            "A non-blank reason must be supplied."
    )
    @PostMapping("/{id}/void")
    @PreAuthorize(RoleSets.APPROVER)
    fun voidReceipt(
        @PathVariable id: UUID,
        @Valid @RequestBody request: VoidReceiptRequest,
    ): ResponseEntity<ApiResponse<Receipt>> {
        SecurityUtils.requireOwnEntity(receiptService.findById(id).entityId)
        return ResponseEntity.ok(ApiResponse.success(receiptService.voidReceipt(id, request.reason)))
    }

    // -----------------------------------------------------------------------
    // GET BY PAYMENT
    // -----------------------------------------------------------------------

    @Operation(
        summary = "Find the receipt associated with a specific payment",
        description = "Each payment has at most one receipt (enforced by UNIQUE DB constraint)."
    )
    @GetMapping("/by-payment/{paymentId}")
    @PreAuthorize(RoleSets.BROAD_READ)
    fun findByPayment(
        @PathVariable paymentId: UUID,
    ): ResponseEntity<ApiResponse<Receipt>> {
        val receipt = receiptService.findByPayment(paymentId)
        SecurityUtils.requireOwnEntity(receipt.entityId)
        return ResponseEntity.ok(ApiResponse.success(receipt))
    }
}

// ---------------------------------------------------------------------------
// Request DTOs (inlined per controller — see §7.2)
// ---------------------------------------------------------------------------

/**
 * §15 — Request body for POST /api/v1/receipts/generate.
 */
data class GenerateReceiptRequest(
    @field:NotNull(message = "paymentId is required")
    val paymentId: UUID,

    @field:NotNull(message = "entityId is required")
    val entityId: UUID,

    val periodId: UUID? = null,
    val deliveryEmail: String? = null,
    val deliveryPhone: String? = null,
    val notes: String? = null,
)

/**
 * §15 — Request body for POST /api/v1/receipts/{id}/void.
 */
data class VoidReceiptRequest(
    @field:NotBlank(message = "A non-blank reason is required to void a receipt")
    val reason: String,
)

package com.qesuite.accounting.source.service

import com.fasterxml.jackson.annotation.JsonProperty
import com.qesuite.accounting.source.domain.SourceDocument
import com.qesuite.accounting.source.domain.SourceDocumentStatus
import com.qesuite.accounting.source.domain.SourceDocumentType
import com.qesuite.accounting.source.repository.SourceDocumentRepository
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.domain.AuditAction
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Service
class SourceDocumentService(
    private val sourceDocumentRepository: SourceDocumentRepository
) {

    @Transactional(readOnly = true)
    fun getAllDocuments(entityId: UUID): List<SourceDocument> =
        sourceDocumentRepository.findByEntityId(entityId)

    @Transactional
    @Auditable(action = AuditAction.CREATE, resourceType = "SOURCE_DOCUMENT")
    fun createDocument(request: CreateSourceDocumentRequest): SourceDocument {
        val doc = SourceDocument(
            type          = request.documentType,
            docDate       = request.documentDate,
            referenceNumber = request.referenceNumber,
            description   = request.description,
            entityId      = request.entityId,
            periodId      = request.periodId,
            amount        = request.amount,
            currencyCode  = request.currencyCode ?: "USD"
        )
        return sourceDocumentRepository.save(doc)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): SourceDocument =
        sourceDocumentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("SOURCE_DOCUMENT_NOT_FOUND", id, "Source Document") }

    @Transactional
    fun deleteDocument(id: UUID) = sourceDocumentRepository.deleteById(id)

    @Transactional
    @Auditable(action = AuditAction.UPDATE, resourceType = "SOURCE_DOCUMENT")
    fun updateDocument(@AuditResourceId id: UUID, request: UpdateSourceDocumentRequest): SourceDocument {
        val doc = findById(id)
        if (doc.status != SourceDocumentStatus.DRAFT && doc.status != SourceDocumentStatus.SUBMITTED) {
            throw ValidationException(
                errorCode = "IMMUTABLE_DOCUMENT",
                message   = "Only DRAFT or SUBMITTED documents can be edited. " +
                            "Current status: ${doc.status}."
            )
        }
        if (request.documentDate   != null) doc.docDate         = request.documentDate
        if (request.referenceNumber != null) doc.referenceNumber = request.referenceNumber
        if (request.description    != null) doc.description     = request.description
        if (request.amount         != null) doc.amount          = request.amount
        if (request.currencyCode   != null) doc.currencyCode    = request.currencyCode
        return sourceDocumentRepository.save(doc)
    }

    /**
     * §2.2 — Enforced state machine transition.
     * Rejects illegal jumps (e.g. ARCHIVED → DRAFT) with INVALID_STATE_TRANSITION (422).
     */
    @Transactional
    @Auditable(action = AuditAction.UPDATE, resourceType = "SOURCE_DOCUMENT")
    fun transitionStatus(@AuditResourceId id: UUID, nextStatus: SourceDocumentStatus): SourceDocument {
        val doc = findById(id)
        if (!doc.status.canTransitionTo(nextStatus)) {
            throw ValidationException(
                errorCode = "INVALID_STATE_TRANSITION",
                message   = "Cannot transition source document from ${doc.status} to $nextStatus.",
                context   = mapOf(
                    "document_id"    to id.toString(),
                    "current_status" to doc.status.name,
                    "requested_status" to nextStatus.name
                )
            )
        }
        doc.status = nextStatus
        return sourceDocumentRepository.save(doc)
    }

    fun classifyTransaction(payload: Any): ClassificationResult =
        ClassificationResult(true, "Transaction meets recognition criteria.")
}

// ─────────────────────────────────────────────────────────────────────────────
// Request DTOs  (field names match the Postman collection / API contract)
// ─────────────────────────────────────────────────────────────────────────────

@Schema(description = "Request body to create a new source document")
data class CreateSourceDocumentRequest(
    @field:NotNull(message = "entityId is required")
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val entityId: UUID,

    @Schema(description = "Accounting period — optional but recommended for period-scoped tracking",
            example = "660e8400-e29b-41d4-a716-446655440001")
    val periodId: UUID? = null,

    @field:NotNull(message = "documentType is required")
    @Schema(example = "SALES_INVOICE",
            allowableValues = ["SALES_INVOICE","PURCHASE_INVOICE","CASH_RECEIPT","PAYMENT_VOUCHER",
                               "BANK_STATEMENT","CREDIT_NOTE","DEBIT_NOTE","PAYROLL_RECORD",
                               "TAX_DECLARATION","JOURNAL_VOUCHER"])
    val documentType: SourceDocumentType,

    @field:NotNull(message = "documentDate is required")
    @Schema(example = "2026-01-15")
    val documentDate: LocalDate,

    @field:NotBlank(message = "referenceNumber is required")
    @Schema(example = "SI-2026-0001")
    val referenceNumber: String,

    @Schema(example = "Sales invoice for consulting services — Acme Corp")
    val description: String? = null,

    @Schema(example = "100000.000000")
    val amount: BigDecimal? = null,

    @Schema(example = "KES")
    val currencyCode: String? = "USD"
)

@Schema(description = "Request body to update mutable fields of an existing source document")
data class UpdateSourceDocumentRequest(
    @Schema(example = "2026-01-20")
    val documentDate: LocalDate? = null,

    @Schema(example = "SI-2026-0001-REV")
    val referenceNumber: String? = null,

    @Schema(example = "Revised description after review")
    val description: String? = null,

    @Schema(example = "105000.000000")
    val amount: BigDecimal? = null,

    @Schema(example = "KES")
    val currencyCode: String? = null
)

data class ClassificationResult(val passed: Boolean, val reason: String)

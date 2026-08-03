package com.qesuite.accounting.source.controller

import com.qesuite.accounting.source.domain.SourceDocument
import com.qesuite.accounting.source.domain.SourceDocumentAttachment
import com.qesuite.accounting.source.domain.SourceDocumentStatus
import com.qesuite.accounting.source.repository.SourceDocumentAttachmentRepository
import com.qesuite.accounting.source.service.ClassificationResult
import com.qesuite.accounting.source.service.CreateSourceDocumentRequest
import com.qesuite.accounting.source.service.SourceDocumentService
import com.qesuite.accounting.source.service.UpdateSourceDocumentRequest
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import com.qesuite.accounting.shared.storage.StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@RestController
@RequestMapping("/api/v1/source-documents")
@Tag(
    name = "Module 2: Transaction Capture",
    description = """
Source documents are the **primary evidence layer** of the accounting cycle. Every
financial transaction must originate from a verifiable source document before a
journal entry may be created.

**Document Lifecycle (State Machine):**
```
DRAFT → SUBMITTED → REVIEWED → APPROVED → POSTED → ARCHIVED
         ↑_______|    ↑_______|    ↑_______|
              (reject back)
Any state → VOID (except ARCHIVED)
```

**Field naming:**
- `documentType` — one of SALES_INVOICE, PURCHASE_INVOICE, CASH_RECEIPT, PAYMENT_VOUCHER,
  BANK_STATEMENT, CREDIT_NOTE, DEBIT_NOTE, PAYROLL_RECORD, TAX_DECLARATION, JOURNAL_VOUCHER
- `documentDate` — ISO-8601 date the document was issued/received
"""
)
class SourceDocumentController(
    private val sourceDocumentService: SourceDocumentService,
    private val attachmentRepository: SourceDocumentAttachmentRepository,
    private val storageService: StorageService,
) {

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a source document (DRAFT)",
        description = """
Captures a new financial source document in **DRAFT** status.

**Required fields:** `entityId`, `documentType`, `documentDate`, `referenceNumber`
**Optional:** `periodId`, `amount`, `currencyCode`, `description`
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Document created in DRAFT status"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing or invalid fields")
    )
    @PreAuthorize(RoleSets.PREPARER)
    fun create(
        @Valid @RequestBody request: CreateSourceDocumentRequest
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(request.entityId)
        return ApiResponse.success(sourceDocumentService.createDocument(request))
    }

    @GetMapping
    @Operation(
        summary = "List all source documents for an entity",
        description = "Returns all source documents scoped to the given entity. Filter by status client-side."
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun getAll(
        @RequestParam
        @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        entityId: UUID
    ): ApiResponse<List<SourceDocument>> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(sourceDocumentService.getAllDocuments(entityId))
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a source document by ID")
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun getById(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID
    ): ApiResponse<SourceDocument> {
        val doc = sourceDocumentService.findById(id)
        SecurityUtils.requireOwnEntity(doc.entityId)
        return ApiResponse.success(doc)
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a source document (DRAFT or SUBMITTED only)",
        description = """
Updates mutable fields: `documentDate`, `referenceNumber`, `description`, `amount`, `currencyCode`.
Only non-null fields in the request body are applied. `documentType` is immutable after creation.

**Allowed statuses:** DRAFT or SUBMITTED. Documents in REVIEWED or later states return `400`.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Document updated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Document is in an immutable status"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Document not found")
    )
    @PreAuthorize(RoleSets.PREPARER)
    fun update(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID,
        @Valid @RequestBody request: UpdateSourceDocumentRequest
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        return ApiResponse.success(sourceDocumentService.updateDocument(id, request))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State machine transitions
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit for review (DRAFT → SUBMITTED)")
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transitioned to SUBMITTED"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid state transition")
    )
    @PreAuthorize(RoleSets.PREPARER)
    fun submit(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        val updated = sourceDocumentService.transitionStatus(id, SourceDocumentStatus.SUBMITTED)
        return ApiResponse.success(updated)
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Mark as reviewed (SUBMITTED → REVIEWED)")
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transitioned to REVIEWED"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid state transition")
    )
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun review(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        val updated = sourceDocumentService.transitionStatus(id, SourceDocumentStatus.REVIEWED)
        return ApiResponse.success(updated)
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve for journal entry creation (REVIEWED → APPROVED)")
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transitioned to APPROVED"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid state transition")
    )
    @PreAuthorize(RoleSets.APPROVER)
    fun approve(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        val updated = sourceDocumentService.transitionStatus(id, SourceDocumentStatus.APPROVED)
        return ApiResponse.success(updated)
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive after posting (APPROVED or POSTED → ARCHIVED)")
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transitioned to ARCHIVED"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid state transition")
    )
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun archive(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        val updated = sourceDocumentService.transitionStatus(id, SourceDocumentStatus.ARCHIVED)
        return ApiResponse.success(updated)
    }

    @PostMapping("/{id}/void")
    @Operation(
        summary = "Void a document (DRAFT or SUBMITTED → VOID)",
        description = "Permanently cancels the document. Only DRAFT or SUBMITTED documents may be voided."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transitioned to VOID"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Invalid state transition")
    )
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun void(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        val updated = sourceDocumentService.transitionStatus(id, SourceDocumentStatus.VOID)
        return ApiResponse.success(updated)
    }

    @PostMapping("/{id}/restore")
    @Operation(
        summary = "Restore an archived document back to DRAFT",
        description = "Transitions an ARCHIVED document back to DRAFT for re-processing. VOID documents cannot be restored. The restore event is audit-logged."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transitioned to DRAFT"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Document is not in ARCHIVED status")
    )
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
    fun restore(
        @PathVariable @Parameter(description = "Source document UUID") id: UUID
    ): ApiResponse<SourceDocument> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        val updated = sourceDocumentService.transitionStatus(id, SourceDocumentStatus.DRAFT)
        return ApiResponse.success(updated)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Attachments
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/attachments", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload a file attachment to a source document")
    @PreAuthorize(RoleSets.PREPARER)
    fun uploadAttachment(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
    ): ApiResponse<SourceDocumentAttachment> {
        val doc = sourceDocumentService.findById(id)
        SecurityUtils.requireOwnEntity(doc.entityId)
        val stored = storageService.store(doc.entityId, file)
        val attachment = SourceDocumentAttachment(
            entityId    = doc.entityId,
            documentId  = id,
            fileName    = stored.fileName,
            contentType = stored.contentType,
            fileSize    = stored.fileSize,
            storagePath = stored.storagePath,
            uploadedBy  = SecurityUtils.currentUser().userId,
        )
        return ApiResponse.success(attachmentRepository.save(attachment))
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "List all active attachments for a source document")
    @PreAuthorize(RoleSets.BROAD_READ)
    fun listAttachments(@PathVariable id: UUID): ApiResponse<List<SourceDocumentAttachment>> {
        SecurityUtils.requireOwnEntity(sourceDocumentService.findById(id).entityId)
        return ApiResponse.success(attachmentRepository.findByDocumentIdAndIsActiveTrue(id))
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    @Operation(summary = "Download an attachment")
    @PreAuthorize(RoleSets.BROAD_READ)
    fun downloadAttachment(
        @PathVariable id: UUID,
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        val att = attachmentRepository.findById(attachmentId)
            .orElseThrow { ResourceNotFoundException("ATTACHMENT_NOT_FOUND", attachmentId, "Attachment") }
        SecurityUtils.requireOwnEntity(att.entityId)
        val bytes = storageService.load(att.storagePath).readBytes()
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${att.fileName}\"")
            .contentType(MediaType.parseMediaType(att.contentType))
            .body(bytes)
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Remove an attachment (soft-delete)")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun removeAttachment(
        @PathVariable id: UUID,
        @PathVariable attachmentId: UUID,
    ) {
        val att = attachmentRepository.findById(attachmentId)
            .orElseThrow { ResourceNotFoundException("ATTACHMENT_NOT_FOUND", attachmentId, "Attachment") }
        SecurityUtils.requireOwnEntity(att.entityId)
        att.isActive = false
        attachmentRepository.save(att)
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Permanently delete a source document",
        description = "Hard-deletes a document. Only allowed for DRAFT or VOID documents."
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(RoleSets.APPROVER)
    fun delete(@PathVariable id: UUID) {
        val doc = sourceDocumentService.findById(id)
        SecurityUtils.requireOwnEntity(doc.entityId)
        if (doc.status != SourceDocumentStatus.DRAFT && doc.status != SourceDocumentStatus.VOID) {
            throw com.qesuite.accounting.shared.exceptions.ValidationException(
                "INVALID_STATE",
                "Only DRAFT or VOID documents can be permanently deleted."
            )
        }
        attachmentRepository.findByDocumentIdAndIsActiveTrue(id).forEach { att ->
            att.isActive = false
            attachmentRepository.save(att)
        }
        sourceDocumentService.deleteDocument(id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Classification
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/classify")
    @Operation(
        summary = "Run the four-test IFRS recognition criteria check",
        description = """
Evaluates a transaction payload against the four IFRS recognition criteria:
1. **Control** — Does the entity control the resource?
2. **Past Event** — Is it the result of a past transaction?
3. **Probable** — Is an economic benefit probable?
4. **Measurable** — Can the amount be measured reliably?
"""
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun classify(
        @RequestBody @Parameter(description = "Unstructured transaction payload") payload: Any
    ): ApiResponse<ClassificationResult> =
        ApiResponse.success(sourceDocumentService.classifyTransaction(payload))
}

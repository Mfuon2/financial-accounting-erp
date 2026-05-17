package com.qesuite.accounting.source.repository

import com.qesuite.accounting.source.domain.SourceDocumentAttachment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SourceDocumentAttachmentRepository : JpaRepository<SourceDocumentAttachment, UUID> {
    fun findByDocumentIdAndIsActiveTrue(documentId: UUID): List<SourceDocumentAttachment>
}

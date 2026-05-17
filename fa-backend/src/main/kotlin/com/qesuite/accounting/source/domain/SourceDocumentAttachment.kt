package com.qesuite.accounting.source.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "source_document_attachments")
class SourceDocumentAttachment(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false, updatable = false)
    val entityId: UUID,

    @Column(name = "document_id", nullable = false, updatable = false)
    val documentId: UUID,

    @Column(name = "file_name", nullable = false, length = 255)
    val fileName: String,

    @Column(name = "content_type", nullable = false, length = 100)
    val contentType: String,

    @Column(name = "file_size", nullable = false)
    val fileSize: Long,

    @Column(name = "storage_path", nullable = false, length = 500)
    val storagePath: String,

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    val uploadedBy: UUID,

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    val uploadedAt: Instant = Instant.now(),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
)

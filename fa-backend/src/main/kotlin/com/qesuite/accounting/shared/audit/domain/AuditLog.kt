package com.qesuite.accounting.shared.audit.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import jakarta.persistence.*
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Entity
@Table(name = "audit_logs")
@Schema(description = "Forensic-grade, INSERT-only audit log of critical financial events (§11)")
class AuditLog(
    entityId: UUID,

    @Column(name = "user_id", nullable = false)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "The user who initiated the action")
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    @Schema(example = "POST")
    val action: AuditAction,

    @Column(name = "resource_type", nullable = false)
    @Schema(example = "JOURNAL_ENTRY")
    val resourceType: String,

    @Column(name = "resource_id", nullable = false)
    @Schema(example = "660e8400-e29b-41d4-a716-446655440001")
    val resourceId: UUID,

    @Column(name = "payload_before", columnDefinition = "TEXT")
    val payloadBefore: String? = null,

    @Column(name = "payload_after", columnDefinition = "TEXT")
    val payloadAfter: String? = null,

    @Column(name = "client_ip")
    @Schema(example = "192.168.1.1")
    val clientIp: String? = null

) : BaseFinancialEntity(entityId = entityId)

enum class AuditAction {
    CREATE, UPDATE, DELETE, POST, REVERSE, APPROVE, REJECT, CLOSE, REOPEN, EXPORT, TAX_ADJUSTMENT
}

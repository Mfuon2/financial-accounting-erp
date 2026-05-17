package com.qesuite.accounting.shared.domain

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * §3.3 — Mandatory Audit Columns (Every Entity)
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseFinancialEntity(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Primary internal identifier")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false, updatable = false)
    @Schema(example = "e10e8400-e29b-41d4-a716-446655440099", description = "Tenant/Company identifier for data isolation")
    val entityId: UUID, // tenant isolation

    @Column(name = "period_id", nullable = true, updatable = true)
    @Schema(example = "p10e8400-e29b-41d4-a716-446655440111", description = "Current active accounting period reference")
    var periodId: UUID? = null // accounting period reference
) {
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @CreatedBy
    @Column(name = "created_by", nullable = true, updatable = false)
    var createdBy: UUID? = null // user ID (set by auditing; nullable — system ops may have no principal)

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    var modifiedAt: Instant = Instant.now()

    @LastModifiedBy
    @Column(name = "modified_by", nullable = true)
    var modifiedBy: UUID? = null // user ID (set by auditing; nullable — system ops may have no principal)

    /**
     * §9.3 — Data Retention (Soft-delete only)
     */
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "deactivated_at")
    var deactivatedAt: Instant? = null

    @Column(name = "deactivated_by")
    var deactivatedBy: UUID? = null

    @Column(name = "deactivation_reason")
    var deactivationReason: String? = null
}

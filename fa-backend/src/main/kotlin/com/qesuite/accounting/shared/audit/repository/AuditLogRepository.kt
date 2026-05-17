package com.qesuite.accounting.shared.audit.repository

import com.qesuite.accounting.shared.audit.domain.AuditLog
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    fun findAllByResourceIdOrderByCreatedAtDesc(resourceId: UUID): List<AuditLog>
    fun findAllByEntityIdOrderByCreatedAtDesc(entityId: UUID): List<AuditLog>
    fun findTop10ByEntityIdOrderByCreatedAtDesc(entityId: UUID): List<AuditLog>
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<AuditLog>
}

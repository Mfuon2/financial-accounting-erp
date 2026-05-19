package com.qesuite.accounting.shared.audit.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.audit.domain.AuditLog
import com.qesuite.accounting.shared.audit.repository.AuditLogRepository
import com.qesuite.accounting.users.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class AuditLogView(
    val id: UUID,
    val createdAt: Instant,
    val userId: UUID,
    val actorName: String,
    val action: AuditAction,
    val resourceType: String,
    val resourceId: UUID,
    val payloadBefore: String?,
    val payloadAfter: String?,
    val clientIp: String?
)

@Service
class AuditService(
    private val auditLogRepository: AuditLogRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper
) {

    /**
     * §11.4 — Retrieve paginated audit logs with resolved actor names.
     */
    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<AuditLogView> {
        val logs = auditLogRepository.findByEntityId(entityId, pageable)
        val userIds = logs.content.mapTo(mutableSetOf()) { it.userId }
        val usersById = userRepository.findAllById(userIds).associateBy { it.id }
        return logs.map { log ->
            AuditLogView(
                id           = log.id,
                createdAt    = log.createdAt,
                userId       = log.userId,
                actorName    = usersById[log.userId]?.fullName ?: log.userId.toString().take(8) + "…",
                action       = log.action,
                resourceType = log.resourceType,
                resourceId   = log.resourceId,
                payloadBefore = log.payloadBefore,
                payloadAfter  = log.payloadAfter,
                clientIp      = log.clientIp
            )
        }
    }

    /**
     * Persists a forensic event on the dedicated audit thread pool so the main
     * request thread is never blocked by audit I/O. Each invocation opens its own
     * transaction (no active transaction on a fresh async thread), so a rollback in
     * the caller never prevents the audit record from being saved.
     *
     * CallerRunsPolicy in AsyncConfig ensures we fall back to synchronous execution
     * rather than silently dropping events when the pool is saturated.
     */
    @Async("auditExecutor")
    @Transactional
    fun log(
        entityId: UUID,
        userId: UUID,
        action: AuditAction,
        resourceType: String,
        resourceId: UUID,
        payloadBefore: Any? = null,
        payloadAfter: Any? = null,
        clientIp: String? = null
    ) {
        val log = AuditLog(
            entityId = entityId,
            userId = userId,
            action = action,
            resourceType = resourceType,
            resourceId = resourceId,
            payloadBefore = payloadBefore?.let { objectMapper.writeValueAsString(it) },
            payloadAfter = payloadAfter?.let { objectMapper.writeValueAsString(it) },
            clientIp = clientIp
        )
        // Async thread has no SecurityContextHolder — bypass JPA auditing by setting
        // createdBy/modifiedBy explicitly so the NOT NULL DB constraint is satisfied.
        log.createdBy = userId
        log.modifiedBy = userId
        auditLogRepository.save(log)
    }
}

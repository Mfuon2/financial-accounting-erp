package com.qesuite.accounting.shared.idempotency.repository

import com.qesuite.accounting.shared.idempotency.domain.IdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * §7.2 — Idempotency Key Repository
 * Data access for idempotency keys.
 */
@Repository
interface IdempotencyKeyRepository : JpaRepository<IdempotencyKey, UUID> {
    /**
     * Find an idempotency key by its value and entity.
     * §3.2 — UNIQUE(idempotency_key, entity_id) constraint enforced at DB level.
     */
    fun findByIdempotencyKeyAndEntityId(idempotencyKey: String, entityId: UUID): IdempotencyKey?
}

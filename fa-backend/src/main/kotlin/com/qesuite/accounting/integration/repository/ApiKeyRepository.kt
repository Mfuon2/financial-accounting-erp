package com.qesuite.accounting.integration.repository

import com.qesuite.accounting.integration.domain.ApiKey
import com.qesuite.accounting.integration.domain.ApiKeyStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, UUID> {
    fun findByKeyId(keyId: String): Optional<ApiKey>
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<ApiKey>
    fun findByEntityIdAndStatus(entityId: UUID, status: ApiKeyStatus, pageable: Pageable): Page<ApiKey>
    fun existsByEntityIdAndName(entityId: UUID, name: String): Boolean
    fun countByEntityId(entityId: UUID): Long
}

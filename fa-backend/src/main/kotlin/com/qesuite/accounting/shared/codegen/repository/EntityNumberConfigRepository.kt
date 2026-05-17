package com.qesuite.accounting.shared.codegen.repository

import com.qesuite.accounting.shared.codegen.domain.EntityNumberConfig
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EntityNumberConfigRepository : JpaRepository<EntityNumberConfig, UUID> {
    fun findByEntityIdAndModuleKey(entityId: UUID, moduleKey: String): EntityNumberConfig?
    fun findByEntityId(entityId: UUID): List<EntityNumberConfig>
}

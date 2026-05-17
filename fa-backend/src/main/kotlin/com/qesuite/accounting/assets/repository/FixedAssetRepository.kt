package com.qesuite.accounting.assets.repository

import com.qesuite.accounting.assets.domain.AssetStatus
import com.qesuite.accounting.assets.domain.FixedAsset
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface FixedAssetRepository : JpaRepository<FixedAsset, UUID> {
    fun findByEntityId(entityId: UUID): List<FixedAsset>
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<FixedAsset>
    fun findByEntityIdAndStatus(entityId: UUID, status: AssetStatus, pageable: Pageable): Page<FixedAsset>
    fun existsByEntityIdAndAssetCode(entityId: UUID, assetCode: String): Boolean
    fun findByEntityIdAndAssetCode(entityId: UUID, assetCode: String): Optional<FixedAsset>
}

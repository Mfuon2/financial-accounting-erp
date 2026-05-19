package com.qesuite.accounting.party.repository

import com.qesuite.accounting.party.domain.Supplier
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * §14.3 — Supplier Repository
 * Data access layer for Supplier master data.
 */
@Repository
interface SupplierRepository : JpaRepository<Supplier, UUID> {
    /**
     * Find a supplier by entity and supplier code (unique constraint).
     */
    fun findByEntityIdAndSupplierCode(entityId: UUID, supplierCode: String): Supplier?

    /**
     * Find all active suppliers for an entity.
     * Filters by entityId only — period context does not restrict the supplier master.
     */
    @Query("SELECT s FROM Supplier s WHERE s.entityId = :entityId AND s.isActive = true")
    fun findByEntityIdAndIsActiveTrue(entityId: UUID, pageable: Pageable): Page<Supplier>

    /**
     * Find all suppliers for an entity (including inactive).
     * Filters by entityId only — independent of fiscal year or period context.
     */
    @Query("SELECT s FROM Supplier s WHERE s.entityId = :entityId")
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<Supplier>

    /**
     * Check if a supplier code exists for an entity.
     */
    fun existsByEntityIdAndSupplierCode(entityId: UUID, supplierCode: String): Boolean
}

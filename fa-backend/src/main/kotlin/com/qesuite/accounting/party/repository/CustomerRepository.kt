package com.qesuite.accounting.party.repository

import com.qesuite.accounting.party.domain.Customer
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * §14.3 — Customer Repository
 * Data access layer for Customer master data.
 */
@Repository
interface CustomerRepository : JpaRepository<Customer, UUID> {
    /**
     * Find a customer by entity and customer code (unique constraint).
     */
    fun findByEntityIdAndCustomerCode(entityId: UUID, customerCode: String): Customer?

    /**
     * Find all active customers for an entity.
     * Filters by entityId only — period context does not restrict the customer master.
     */
    @Query("SELECT c FROM Customer c WHERE c.entityId = :entityId AND c.isActive = true")
    fun findByEntityIdAndIsActiveTrue(entityId: UUID, pageable: Pageable): Page<Customer>

    /**
     * Find all customers for an entity (including inactive).
     * Filters by entityId only — independent of fiscal year or period context.
     */
    @Query("SELECT c FROM Customer c WHERE c.entityId = :entityId")
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<Customer>

    /**
     * Check if a customer code exists for an entity.
     */
    fun existsByEntityIdAndCustomerCode(entityId: UUID, customerCode: String): Boolean
}

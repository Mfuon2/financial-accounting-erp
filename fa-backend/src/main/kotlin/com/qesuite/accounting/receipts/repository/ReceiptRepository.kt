package com.qesuite.accounting.receipts.repository

import com.qesuite.accounting.receipts.domain.Receipt
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

/**
 * §15 — Receipt Repository
 */
@Repository
interface ReceiptRepository : JpaRepository<Receipt, UUID> {

    /**
     * Paginated listing of all receipts for an entity (tenant isolation).
     */
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<Receipt>

    /**
     * Look up the single receipt linked to a payment (enforced UNIQUE by DB).
     */
    fun findByPaymentId(paymentId: UUID): Optional<Receipt>

    /**
     * Paginated listing of receipts for a specific customer within an entity.
     */
    fun findByEntityIdAndCustomerId(entityId: UUID, customerId: UUID, pageable: Pageable): Page<Receipt>

    /**
     * Duplicate-receipt guard: check by the UNIQUE (entity_id, receipt_number) constraint.
     */
    fun existsByEntityIdAndReceiptNumber(entityId: UUID, receiptNumber: String): Boolean

    /**
     * Count receipts per entity — used by the receipt-number sequence generator.
     */
    fun countByEntityId(entityId: UUID): Long
}

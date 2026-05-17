package com.qesuite.accounting.payables.repository

import com.qesuite.accounting.payables.domain.Bill
import com.qesuite.accounting.payables.domain.BillStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

interface BillRepository : JpaRepository<Bill, UUID> {
    fun findByEntityIdAndIsActiveTrueOrderByBillDateDesc(entityId: UUID, pageable: Pageable): Page<Bill>
    fun findByEntityIdAndStatusAndIsActiveTrueOrderByBillDateDesc(entityId: UUID, status: BillStatus, pageable: Pageable): Page<Bill>
    fun existsByEntityIdAndBillNumber(entityId: UUID, billNumber: String): Boolean
    fun countByEntityId(entityId: UUID): Long

    @Query("""
        SELECT b FROM Bill b
        WHERE b.entityId = :entityId
          AND b.isActive = true
          AND b.status IN ('APPROVED', 'PARTIALLY_PAID')
          AND b.dueDate < :today
        ORDER BY b.dueDate ASC
    """)
    fun findOverdue(entityId: UUID, today: LocalDate): List<Bill>

    @Query("""
        SELECT b FROM Bill b
        WHERE b.entityId = :entityId
          AND b.isActive = true
          AND b.status IN ('APPROVED', 'PARTIALLY_PAID')
        ORDER BY b.dueDate ASC
    """)
    fun findOutstanding(entityId: UUID): List<Bill>

    @Query("""
        SELECT b FROM Bill b
        WHERE b.entityId = :entityId
          AND b.isActive = true
          AND b.isDebitNote = false
          AND b.status NOT IN ('VOID')
          AND b.supplierName = :supplierName
          AND b.billDate = :billDate
          AND b.totalAmount BETWEEN :minAmount AND :maxAmount
    """)
    fun findPotentialDuplicates(
        entityId: UUID,
        supplierName: String,
        billDate: LocalDate,
        minAmount: BigDecimal,
        maxAmount: BigDecimal,
    ): List<Bill>

    fun findAllByIdIn(ids: List<UUID>): List<Bill>
}

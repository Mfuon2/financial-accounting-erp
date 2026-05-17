package com.qesuite.accounting.payments.repository

import com.qesuite.accounting.payments.domain.Payment
import com.qesuite.accounting.payments.domain.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import java.time.LocalDate

@Repository
interface PaymentRepository : JpaRepository<Payment, UUID> {
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<Payment>
    fun findByEntityIdAndInvoiceId(entityId: UUID, invoiceId: UUID): List<Payment>
    fun findByEntityIdAndStatus(entityId: UUID, status: PaymentStatus, pageable: Pageable): Page<Payment>
    fun findByEntityIdAndCustomerId(entityId: UUID, customerId: UUID, pageable: Pageable): Page<Payment>
    fun existsByEntityIdAndPaymentNumber(entityId: UUID, paymentNumber: String): Boolean
    fun existsByEntityIdAndTransactionReference(entityId: UUID, transactionReference: String): Boolean

    // D8 — used to de-duplicate M-Pesa callbacks by receipt number
    fun findByTransactionReference(transactionReference: String): Optional<Payment>

    @Query("""
        SELECT COALESCE(SUM(p.paymentAmount), 0)
        FROM Payment p
        WHERE p.entityId = :entityId
          AND p.invoiceId = :invoiceId
          AND p.status = 'POSTED'
    """)
    fun sumPostedByInvoice(@Param("entityId") entityId: UUID, @Param("invoiceId") invoiceId: UUID): BigDecimal

    fun countByEntityId(entityId: UUID): Long

    /** Posted payments in a date range — used by the dashboard cash sparkline. */
    @Query("""
        SELECT p FROM Payment p
        WHERE p.entityId = :entityId
          AND p.paymentDate >= :startDate
          AND p.paymentDate <= :endDate
          AND p.status = 'POSTED'
    """)
    fun findPostedInRange(
        @Param("entityId") entityId: UUID,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<Payment>
}

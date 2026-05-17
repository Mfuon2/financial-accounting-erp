package com.qesuite.accounting.invoicing.repository

import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.UUID

/**
 * §14.1 — Invoice Repository
 * Data access layer for invoices with filtering and pagination support.
 */
@Repository
interface InvoiceRepository : JpaRepository<Invoice, UUID> {

    /**
     * Find invoice by number (unique per entity).
     */
    fun findByEntityIdAndInvoiceNumber(entityId: UUID, invoiceNumber: String): Invoice?

    /**
     * Check if invoice number exists (unique constraint).
     */
    fun existsByEntityIdAndInvoiceNumber(entityId: UUID, invoiceNumber: String): Boolean

    /**
     * Find invoices for a customer.
     */
    fun findByEntityIdAndCustomerId(entityId: UUID, customerId: UUID, pageable: Pageable): Page<Invoice>

    /**
     * Find invoices by status.
     */
    fun findByEntityIdAndStatus(entityId: UUID, status: InvoiceStatus, pageable: Pageable): Page<Invoice>

    /**
     * Find invoices within a date range (issued between start and end).
     */
    @Query(
        """
        SELECT i FROM Invoice i
        WHERE i.entityId = :entityId AND i.issueDate BETWEEN :startDate AND :endDate
        ORDER BY i.issueDate DESC
        """
    )
    fun findByEntityIdAndIssueDateRange(
        @Param("entityId") entityId: UUID,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
        pageable: Pageable
    ): Page<Invoice>

    /**
     * Find overdue invoices (due date before given date and status not PAID/VOID).
     */
    @Query(
        """
        SELECT i FROM Invoice i
        WHERE i.entityId = :entityId AND i.dueDate < :asOfDate
              AND i.status NOT IN ('PAID', 'VOID')
        ORDER BY i.dueDate ASC
        """
    )
    fun findOverdueInvoices(
        @Param("entityId") entityId: UUID,
        @Param("asOfDate") asOfDate: LocalDate,
        pageable: Pageable
    ): Page<Invoice>

    /**
     * Find all invoices for a customer outstanding (not paid).
     */
    @Query(
        """
        SELECT i FROM Invoice i
        WHERE i.entityId = :entityId AND i.customerId = :customerId
              AND i.status IN ('APPROVED', 'SENT', 'PARTIALLY_PAID')
        ORDER BY i.dueDate ASC
        """
    )
    fun findOutstandingInvoicesByCustomer(
        @Param("entityId") entityId: UUID,
        @Param("customerId") customerId: UUID
    ): List<Invoice>

    /**
     * Sum outstanding amounts for a customer (for credit limit checks).
     */
    @Query(
        """
        SELECT COALESCE(SUM(i.outstandingAmount), 0)
        FROM Invoice i
        WHERE i.entityId = :entityId AND i.customerId = :customerId
              AND i.status IN ('APPROVED', 'SENT', 'PARTIALLY_PAID')
        """
    )
    fun sumOutstandingByCustomer(
        @Param("entityId") entityId: UUID,
        @Param("customerId") customerId: UUID
    ): java.math.BigDecimal

    /**
     * Paginated listing of all invoices for an entity (tenant isolation).
     */
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<Invoice>

    /**
     * Total invoice count for an entity — used by the sequential invoice-number generator.
     */
    fun countByEntityId(entityId: UUID): Long

    /** All invoices with outstanding balances — used by the dashboard AR KPI. */
    @Query("SELECT i FROM Invoice i WHERE i.entityId = :entityId AND i.status IN ('APPROVED', 'SENT', 'PARTIALLY_PAID')")
    fun findOutstandingByEntity(@Param("entityId") entityId: UUID): List<Invoice>

    /** Revenue invoices in a date range — used by the dashboard sparkline computation. */
    @Query("""
        SELECT i FROM Invoice i
        WHERE i.entityId = :entityId
          AND i.issueDate >= :startDate
          AND i.issueDate <= :endDate
          AND i.status NOT IN ('DRAFT', 'VOID')
    """)
    fun findRevenueInRange(
        @Param("entityId") entityId: UUID,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate
    ): List<Invoice>
}

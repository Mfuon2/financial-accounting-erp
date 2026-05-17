package com.qesuite.accounting.invoicing.repository

import com.qesuite.accounting.invoicing.domain.InvoiceLine
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * §14.1 — Invoice Line Repository
 * Data access layer for invoice line items.
 */
@Repository
interface InvoiceLineRepository : JpaRepository<InvoiceLine, UUID> {

    /**
     * Find all lines for an invoice.
     */
    fun findByInvoiceIdOrderByLineNumber(invoiceId: UUID): List<InvoiceLine>
}

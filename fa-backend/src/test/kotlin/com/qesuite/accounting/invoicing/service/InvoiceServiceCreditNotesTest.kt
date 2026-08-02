package com.qesuite.accounting.invoicing.service

import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.repository.InvoiceRepository
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.party.repository.CustomerRepository
import com.qesuite.accounting.shared.codegen.service.CodeGeneratorService
import com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService
import com.qesuite.accounting.tax.service.TaxService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * §14.2 — Focused unit test for the standalone `GET /api/v1/credit-notes` listing
 * (backing [InvoiceService.findCreditNotesByEntity]). Kept separate from a full
 * InvoiceServiceTest (the pre-existing one in this module is disabled/commented out)
 * because this only needs to prove the new query path, not re-test invoice lifecycle.
 */
class InvoiceServiceCreditNotesTest {

    private val invoiceRepository = mockk<InvoiceRepository>()
    private val customerRepository = mockk<CustomerRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val journalService = mockk<JournalService>()
    private val taxService = mockk<TaxService>()
    private val ifrs15Service = mockk<Ifrs15RecognitionService>()
    private val codeGeneratorService = mockk<CodeGeneratorService>()
    private val entityNumberConfigService = mockk<EntityNumberConfigService>()

    private val invoiceService = InvoiceService(
        invoiceRepository, customerRepository, accountRepository,
        journalService, taxService, ifrs15Service,
        codeGeneratorService, entityNumberConfigService,
    )

    private val entityId = UUID.randomUUID()

    @Test
    fun `should return only CREDIT_NOTE rows for the entity, not all invoices`() {
        // Given — a real credit-note Invoice row (negative amounts, terminal CREDIT_NOTE
        // status), as produced by InvoiceService#createCreditNote.
        val creditNote = Invoice(
            entityId = entityId,
            periodId = UUID.randomUUID(),
            invoiceNumber = "CN-2026-00001",
            customerId = UUID.randomUUID(),
            issueDate = LocalDate.of(2026, 8, 1),
            dueDate = LocalDate.of(2026, 8, 1),
            currencyCode = "KES",
            exchangeRate = BigDecimal.ONE,
            subtotal = BigDecimal("-1000.000000"),
            taxAmount = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            totalAmount = BigDecimal("-1000.000000"),
            outstandingAmount = BigDecimal("-1000.000000"),
            status = InvoiceStatus.CREDIT_NOTE,
            notes = "Credit note for INV-2026-00042: customer return",
        )
        val pageable = PageRequest.of(0, 50)
        every {
            invoiceRepository.findByEntityIdAndStatus(entityId, InvoiceStatus.CREDIT_NOTE, pageable)
        } returns PageImpl(listOf(creditNote), pageable, 1)

        // When
        val result = invoiceService.findCreditNotesByEntity(entityId, pageable)

        // Then — real data comes back, and only the status-filtered repository query fired
        assertEquals(1, result.totalElements)
        assertEquals("CN-2026-00001", result.content[0].invoiceNumber)
        assertEquals(InvoiceStatus.CREDIT_NOTE, result.content[0].status)
        assertEquals(0, BigDecimal("-1000.000000").compareTo(result.content[0].totalAmount))
        verify(exactly = 1) { invoiceRepository.findByEntityIdAndStatus(entityId, InvoiceStatus.CREDIT_NOTE, pageable) }
    }
}

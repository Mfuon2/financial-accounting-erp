package com.qesuite.accounting.invoicing.controller

import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.service.InvoiceService
import com.qesuite.accounting.shared.security.JwtService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * §14.2 — Proves `GET /api/v1/credit-notes` is actually wired end-to-end (controller →
 * service) and returns real credit-note data, not just that the route exists.
 */
@WebMvcTest(CreditNoteController::class)
class CreditNoteControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var invoiceService: InvoiceService

    @Test
    @WithMockUser
    fun `should list credit notes for an entity with real data in the response`() {
        // Given
        val entityId = UUID.randomUUID()
        val creditNote = Invoice(
            entityId = entityId,
            periodId = UUID.randomUUID(),
            invoiceNumber = "CN-2026-00007",
            customerId = UUID.randomUUID(),
            issueDate = LocalDate.of(2026, 8, 2),
            dueDate = LocalDate.of(2026, 8, 2),
            currencyCode = "KES",
            exchangeRate = BigDecimal.ONE,
            subtotal = BigDecimal("-500.000000"),
            taxAmount = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            totalAmount = BigDecimal("-500.000000"),
            outstandingAmount = BigDecimal("-500.000000"),
            status = InvoiceStatus.CREDIT_NOTE,
            notes = "Credit note for INV-2026-00099: goods returned",
        )
        val pageable = PageRequest.of(0, 50)
        every {
            invoiceService.findCreditNotesByEntity(entityId, pageable)
        } returns PageImpl(listOf(creditNote), pageable, 1)

        // When/Then
        mockMvc.get("/api/v1/credit-notes") {
            param("entityId", entityId.toString())
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.totalElements") { value(1) }
            jsonPath("$.data.content[0].invoiceNumber") { value("CN-2026-00007") }
            jsonPath("$.data.content[0].status") { value("CREDIT_NOTE") }
            jsonPath("$.data.content[0].totalAmount") { value(-500.0) }
        }
    }
}

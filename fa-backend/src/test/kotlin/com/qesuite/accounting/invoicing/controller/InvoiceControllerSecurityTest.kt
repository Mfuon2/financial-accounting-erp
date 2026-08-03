package com.qesuite.accounting.invoicing.controller

import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.service.InvoiceService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.mockUserContext
import com.qesuite.accounting.shared.security.UserRole
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * §18 — IDOR regression test: before the security sweep, `GET /api/v1/invoices?entityId=` never
 * checked the caller's own entityId — any authenticated user could list another entity's
 * customer invoices by changing the query param.
 */
@WebMvcTest(InvoiceController::class)
@Import(SecurityConfig::class)
class InvoiceControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var invoiceService: InvoiceService

    @Test
    fun `should reject listing another entity's invoices with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()

        mockMvc.get("/api/v1/invoices") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            param("entityId", otherEntityId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.errors[0].error_code") { value("FORBIDDEN") }
        }
    }

    // ── Segregation-of-duties role gate (fast-follow to the IDOR sweep) ──────────
    // approve() is the preparer/approver split: whoever created the DRAFT invoice
    // (DATA_ENTRY/ACCOUNTANT, per PREPARER) must not also be able to approve it —
    // matches BillController.approveBill's precedent (SENIOR_ACCOUNTANT/CONTROLLER_CFO/
    // SYSTEM_ADMIN only). Before this fix, any authenticated role — including the
    // preparer themselves — could approve their own invoice.

    private fun draftInvoice(entityId: UUID) = Invoice(
        entityId = entityId,
        periodId = UUID.randomUUID(),
        invoiceNumber = "INV-2026-00001",
        customerId = UUID.randomUUID(),
        issueDate = LocalDate.of(2026, 6, 1),
        dueDate = LocalDate.of(2026, 7, 1),
        currencyCode = "KES",
        exchangeRate = BigDecimal.ONE,
        subtotal = BigDecimal("1000.000000"),
        taxAmount = BigDecimal("160.000000"),
        discountAmount = BigDecimal.ZERO,
        totalAmount = BigDecimal("1160.000000"),
        outstandingAmount = BigDecimal("1160.000000"),
    )

    @Test
    fun `should reject an ACCOUNTANT approving an invoice with 403`() {
        val entityId = UUID.randomUUID()
        val invoiceId = UUID.randomUUID()
        every { invoiceService.findById(invoiceId) } returns draftInvoice(entityId)

        mockMvc.post("/api/v1/invoices/$invoiceId/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should allow a SENIOR_ACCOUNTANT to approve an invoice`() {
        val entityId = UUID.randomUUID()
        val invoiceId = UUID.randomUUID()
        val invoice = draftInvoice(entityId)
        every { invoiceService.findById(invoiceId) } returns invoice
        every { invoiceService.approve(invoiceId) } returns invoice

        mockMvc.post("/api/v1/invoices/$invoiceId/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.SENIOR_ACCOUNTANT))
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}

package com.qesuite.accounting.invoicing.controller

import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.invoicing.service.InvoiceService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.mockUserContext
import com.qesuite.accounting.shared.security.UserRole
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.UUID

/**
 * §18 — IDOR regression test: before the security sweep, `GET /api/v1/invoices?entityId=` never
 * checked the caller's own entityId — any authenticated user could list another entity's
 * customer invoices by changing the query param.
 */
@WebMvcTest(InvoiceController::class)
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
}

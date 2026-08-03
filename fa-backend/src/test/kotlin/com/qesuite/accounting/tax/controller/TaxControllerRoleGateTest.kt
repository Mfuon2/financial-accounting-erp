package com.qesuite.accounting.tax.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.shared.security.mockUserContext
import com.qesuite.accounting.tax.domain.TaxCode
import com.qesuite.accounting.tax.service.TaxService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * §18 — Segregation-of-duties role gate (fast-follow to the IDOR sweep). Defining a tax
 * code/rate is entity-wide configuration (matches OrganizationController.updateOrganization /
 * ClosingController.runClosing's CONTROLLER_CFO/SYSTEM_ADMIN-only precedent) — before this fix,
 * any authenticated role, including DATA_ENTRY, could create a tax code carrying whatever rate
 * it liked.
 */
@WebMvcTest(TaxController::class)
@Import(SecurityConfig::class)
class TaxControllerRoleGateTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var taxService: TaxService

    @Test
    fun `should reject an ACCOUNTANT creating a tax code with 403`() {
        val entityId = UUID.randomUUID()

        mockMvc.post("/api/v1/tax/codes") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("entityId" to entityId.toString(), "code" to "VAT_16")
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should allow a CONTROLLER_CFO to create a tax code`() {
        val entityId = UUID.randomUUID()
        every { taxService.createTaxCode(any()) } returns TaxCode(
            entityId = entityId,
            code = "VAT_16",
            name = "VAT Standard 16%",
            description = null,
            taxType = "OUTPUT",
            accountCode = null,
        )

        mockMvc.post("/api/v1/tax/codes") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.CONTROLLER_CFO))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("entityId" to entityId.toString(), "code" to "VAT_16")
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
        }
    }
}

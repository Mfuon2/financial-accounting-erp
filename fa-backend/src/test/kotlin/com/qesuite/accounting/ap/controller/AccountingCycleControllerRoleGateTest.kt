package com.qesuite.accounting.ap.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.ap.service.AccountingCycleResult
import com.qesuite.accounting.ap.service.AccountingCycleService
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.shared.security.mockUserContext
import io.mockk.every
import io.mockk.mockk
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
 * §18 — Segregation-of-duties role gate (fast-follow to the IDOR sweep). `runFullCycle`
 * executes the full 9-step cycle, INCLUDING posting closing entries and transitioning the
 * period to CLOSED — the same functionality `ClosingController.runClosing` already restricts
 * to CONTROLLER_CFO/SYSTEM_ADMIN. Before this fix, any authenticated role — including
 * DATA_ENTRY — could single-handedly close a period through this alternate endpoint,
 * bypassing ClosingController's existing restriction entirely.
 */
@WebMvcTest(AccountingCycleController::class)
@Import(SecurityConfig::class)
class AccountingCycleControllerRoleGateTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var accountingCycleService: AccountingCycleService

    @Test
    fun `should reject a DATA_ENTRY running the full accounting cycle with 403`() {
        val entityId = UUID.randomUUID()

        mockMvc.post("/api/v1/accounting-cycle/run") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("entityId" to entityId.toString(), "periodId" to UUID.randomUUID().toString())
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should reject a SENIOR_ACCOUNTANT running the full accounting cycle with 403`() {
        // Unlike JournalController's approve tier, closing a period matches
        // ClosingController.runClosing's narrower bar — CONTROLLER_CFO/SYSTEM_ADMIN only,
        // SENIOR_ACCOUNTANT is NOT sufficient here.
        val entityId = UUID.randomUUID()

        mockMvc.post("/api/v1/accounting-cycle/run") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.SENIOR_ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("entityId" to entityId.toString(), "periodId" to UUID.randomUUID().toString())
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should allow a CONTROLLER_CFO to run the full accounting cycle`() {
        val entityId = UUID.randomUUID()
        every {
            accountingCycleService.runFullCycle(any(), any(), any())
        } returns mockk<AccountingCycleResult>(relaxed = true)

        mockMvc.post("/api/v1/accounting-cycle/run") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.CONTROLLER_CFO))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("entityId" to entityId.toString(), "periodId" to UUID.randomUUID().toString())
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}

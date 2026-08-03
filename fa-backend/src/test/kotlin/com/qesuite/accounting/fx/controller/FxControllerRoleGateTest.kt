package com.qesuite.accounting.fx.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.fx.repository.ExchangeRateRepository
import com.qesuite.accounting.fx.service.ExchangeRateService
import com.qesuite.accounting.fx.service.FXRevaluationService
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.shared.security.mockUserContext
import io.mockk.every
import io.mockk.just
import io.mockk.runs
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
 * §18 — Segregation-of-duties role gate (fast-follow to the IDOR sweep).
 *
 * - `createCurrency` registers a functional/foreign currency for the entity — foundational,
 *   IAS-21-fixed configuration, matching OrganizationController/ClosingController's
 *   CONTROLLER_CFO/SYSTEM_ADMIN-only precedent.
 * - `runRevaluation` posts IAS 21 FX gain/loss journal entries — a GL-posting action of the
 *   same weight as JournalController.approve/BillController.approveBill, gated to
 *   SENIOR_ACCOUNTANT/CONTROLLER_CFO/SYSTEM_ADMIN.
 *
 * Before this fix, any authenticated role — including DATA_ENTRY — could register a
 * currency or single-handedly run a revaluation posting GL entries.
 */
@WebMvcTest(FxController::class)
@Import(SecurityConfig::class)
class FxControllerRoleGateTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var currencyRepository: CurrencyRepository

    @MockkBean
    private lateinit var exchangeRateRepository: ExchangeRateRepository

    @MockkBean
    private lateinit var exchangeRateService: ExchangeRateService

    @MockkBean
    private lateinit var fxRevaluationService: FXRevaluationService

    @Test
    fun `should reject a DATA_ENTRY registering a currency with 403`() {
        val entityId = UUID.randomUUID()

        mockMvc.post("/api/v1/fx/currencies") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("entityId" to entityId.toString(), "currencyCode" to "USD", "currencyName" to "US Dollar")
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should reject an ACCOUNTANT running FX revaluation with 403`() {
        val entityId = UUID.randomUUID()

        mockMvc.post("/api/v1/fx/revaluation") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "entityId" to entityId.toString(),
                    "periodId" to UUID.randomUUID().toString(),
                    "date" to "2026-06-30",
                    "gainLossAccountId" to UUID.randomUUID().toString(),
                )
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should allow a SENIOR_ACCOUNTANT to run FX revaluation`() {
        val entityId = UUID.randomUUID()
        every { fxRevaluationService.runRevaluation(any(), any(), any(), any()) } just runs

        mockMvc.post("/api/v1/fx/revaluation") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.SENIOR_ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "entityId" to entityId.toString(),
                    "periodId" to UUID.randomUUID().toString(),
                    "date" to "2026-06-30",
                    "gainLossAccountId" to UUID.randomUUID().toString(),
                )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}

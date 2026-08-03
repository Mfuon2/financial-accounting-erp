package com.qesuite.accounting.ap.controller

import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.shared.security.mockUserContext
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.*

// @Import(SecurityConfig::class) is required for @PreAuthorize/@EnableMethodSecurity to
// actually be enforced in this test: @WebMvcTest's slice does NOT load SecurityConfig by
// default (confirmed by direct inspection — no method-security beans exist in the slice
// without this import), so any role-gate rejection test would otherwise silently execute
// the handler method anyway instead of returning 403. This affects every @WebMvcTest that
// asserts a @PreAuthorize-driven rejection, not just this file.
@WebMvcTest(PeriodController::class)
@Import(SecurityConfig::class)
class PeriodControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var periodService: PeriodService

    @Test
    fun `should generate fiscal year`() {
        // Given — the controller's real contract is a JSON body (GenerateFiscalYearRequest
        // with entityId/fiscalYear fields), not request params, and not "startYear".
        val entityId = UUID.randomUUID()
        every { periodService.generateFiscalYear(entityId, 2024) } just runs

        // When/Then — authenticated as a user of the SAME entity (SecurityUtils.requireOwnEntity
        // guard added by the IDOR sweep requires a real UserContext principal, not @WithMockUser).
        mockMvc.post("/api/v1/periods/generate-fiscal-year") {
            with(csrf())
            with(mockUserContext(entityId))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("entityId" to entityId.toString(), "fiscalYear" to 2024)
            )
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
        }
    }

    @Test
    fun `should transition period`() {
        // Given
        val periodId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val existingPeriod = Period(
            entityId, "JANUARY 2024",
            java.time.LocalDate.of(2024, 1, 1), java.time.LocalDate.of(2024, 1, 31),
            PeriodStatus.FUTURE,
        )
        every { periodService.findById(periodId) } returns existingPeriod
        every { periodService.transitionPeriod(periodId, PeriodStatus.OPEN) } returns mockk<Period>(relaxed = true)

        // When/Then
        mockMvc.post("/api/v1/periods/$periodId/transition") {
            with(csrf())
            with(mockUserContext(entityId))
            param("nextStatus", "OPEN")
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }

    // ── Segregation-of-duties role gate (fast-follow to the IDOR sweep) ──────────
    // transitionPeriod can effect a CLOSING→CLOSED or CLOSED→REOPENED transition —
    // exactly what ClosingController.runClosing/reopenPeriod already restrict to
    // CONTROLLER_CFO/SYSTEM_ADMIN — so this endpoint must be gated at the same bar,
    // not left open to any authenticated role (DATA_ENTRY included, before this fix).

    @Test
    fun `should reject a DATA_ENTRY user transitioning a period with 403`() {
        val periodId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val existingPeriod = Period(
            entityId, "JANUARY 2024",
            java.time.LocalDate.of(2024, 1, 1), java.time.LocalDate.of(2024, 1, 31),
            PeriodStatus.FUTURE,
        )
        every { periodService.findById(periodId) } returns existingPeriod

        mockMvc.post("/api/v1/periods/$periodId/transition") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            param("nextStatus", "OPEN")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should allow a CONTROLLER_CFO to transition a period`() {
        val periodId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val existingPeriod = Period(
            entityId, "JANUARY 2024",
            java.time.LocalDate.of(2024, 1, 1), java.time.LocalDate.of(2024, 1, 31),
            PeriodStatus.FUTURE,
        )
        every { periodService.findById(periodId) } returns existingPeriod
        every { periodService.transitionPeriod(periodId, PeriodStatus.OPEN) } returns mockk<Period>(relaxed = true)

        mockMvc.post("/api/v1/periods/$periodId/transition") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.CONTROLLER_CFO))
            param("nextStatus", "OPEN")
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}

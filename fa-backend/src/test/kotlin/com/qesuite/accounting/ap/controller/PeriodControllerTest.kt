package com.qesuite.accounting.ap.controller

import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.*

@WebMvcTest(PeriodController::class)
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
    @WithMockUser
    fun `should generate fiscal year`() {
        // Given — the controller's real contract is a JSON body (GenerateFiscalYearRequest
        // with entityId/fiscalYear fields), not request params, and not "startYear".
        val entityId = UUID.randomUUID()
        every { periodService.generateFiscalYear(entityId, 2024) } just runs

        // When/Then
        mockMvc.post("/api/v1/periods/generate-fiscal-year") {
            with(csrf())
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
    @WithMockUser
    fun `should transition period`() {
        // Given
        val periodId = UUID.randomUUID()
        every { periodService.transitionPeriod(periodId, PeriodStatus.OPEN) } returns mockk<Period>(relaxed = true)

        // When/Then
        mockMvc.post("/api/v1/periods/$periodId/transition") {
            with(csrf())
            param("nextStatus", "OPEN")
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}

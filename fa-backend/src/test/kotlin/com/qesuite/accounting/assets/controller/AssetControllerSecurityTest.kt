package com.qesuite.accounting.assets.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.assets.domain.FixedAsset
import com.qesuite.accounting.assets.service.AssetMasterService
import com.qesuite.accounting.assets.service.DepreciationService
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.mockUserContext
import com.qesuite.accounting.shared.security.UserRole
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * §18 — IDOR regression test: before the security sweep, `GET /api/v1/assets?entityId=` never
 * checked the caller's own entityId — any authenticated user could list another entity's fixed
 * assets by changing the query param.
 *
 * @Import(SecurityConfig::class) is required for @PreAuthorize to actually be enforced here —
 * @WebMvcTest does not load SecurityConfig (and therefore @EnableMethodSecurity) by default.
 */
@WebMvcTest(AssetController::class)
@Import(SecurityConfig::class)
class AssetControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var assetMasterService: AssetMasterService

    @MockkBean
    private lateinit var depreciationService: DepreciationService

    @Test
    fun `should reject listing another entity's fixed assets with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()

        mockMvc.get("/api/v1/assets") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            param("entityId", otherEntityId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.errors[0].error_code") { value("FORBIDDEN") }
        }
    }

    // ── Segregation-of-duties role gate (fast-follow to the IDOR sweep) ──────────
    // disposeAsset posts a GL entry (gain/loss on disposal) — matches
    // LedgerController.postDepreciation's precedent (SENIOR_ACCOUNTANT/CONTROLLER_CFO/
    // SYSTEM_ADMIN only). Before this fix, a DATA_ENTRY or even ACCOUNTANT user could
    // single-handedly dispose of a fixed asset.

    private fun asset(entityId: UUID) = FixedAsset(
        entityId = entityId,
        assetCode = "FA-0001",
        assetName = "Delivery Van",
        costAccountId = UUID.randomUUID(),
        accumDepAccountId = UUID.randomUUID(),
        depExpenseAccountId = UUID.randomUUID(),
        acquisitionDate = LocalDate.of(2024, 1, 1),
        acquisitionCost = BigDecimal("30000.000000"),
        usefulLifeMonths = 60,
    )

    @Test
    fun `should reject an ACCOUNTANT disposing a fixed asset with 403`() {
        val entityId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        every { assetMasterService.findById(assetId) } returns asset(entityId)

        mockMvc.post("/api/v1/assets/$assetId/dispose") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "periodId" to UUID.randomUUID().toString(),
                    "disposalDate" to "2026-06-30",
                    "proceedsAmount" to 5000,
                    "proceedsAccountId" to UUID.randomUUID().toString(),
                )
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should allow a SENIOR_ACCOUNTANT to dispose a fixed asset`() {
        val entityId = UUID.randomUUID()
        val assetId = UUID.randomUUID()
        val existing = asset(entityId)
        every { assetMasterService.findById(assetId) } returns existing
        every {
            assetMasterService.disposeAsset(any(), any(), any(), any(), any())
        } returns existing.also { it.status = com.qesuite.accounting.assets.domain.AssetStatus.DISPOSED }

        mockMvc.post("/api/v1/assets/$assetId/dispose") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.SENIOR_ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "periodId" to UUID.randomUUID().toString(),
                    "disposalDate" to "2026-06-30",
                    "proceedsAmount" to 5000,
                    "proceedsAccountId" to UUID.randomUUID().toString(),
                )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}

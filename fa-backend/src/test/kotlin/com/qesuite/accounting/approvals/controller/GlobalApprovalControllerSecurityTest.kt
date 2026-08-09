package com.qesuite.accounting.approvals.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.approvals.service.GlobalApprovalService
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
 * §18 — Real, live IDOR + SoD gap found while independently re-verifying the completed security
 * sweep (ac16f42..3953672): `POST /api/v1/approvals/{id}/approve|reject` was never touched by that
 * sweep. It dispatched straight to `JournalService.postEntry` / `InvoiceService.approve` /
 * `BillService.approveBill` (and their reject/void counterparts) — none of which check entity
 * ownership themselves, since that check normally lives in the entity-specific controller
 * (JournalController, InvoiceController, BillController) that this global queue bypasses. There
 * was also no `@PreAuthorize` at all, so a DATA_ENTRY user could approve/post a journal entry or
 * bill belonging to a different entity outright.
 *
 * @Import(SecurityConfig::class) is required for @PreAuthorize to actually be enforced here —
 * @WebMvcTest does not load SecurityConfig (and therefore @EnableMethodSecurity) by default.
 */
@WebMvcTest(GlobalApprovalController::class)
@Import(SecurityConfig::class)
class GlobalApprovalControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var approvalService: GlobalApprovalService

    private fun body(type: String = "JOURNAL_ENTRY", reason: String = "") =
        objectMapper.writeValueAsString(mapOf("type" to type, "reason" to reason))

    @Test
    fun `should reject approving another entity's item with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        every { approvalService.resolveEntityId(itemId, "JOURNAL_ENTRY") } returns otherEntityId

        mockMvc.post("/api/v1/approvals/$itemId/approve") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.SENIOR_ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.errors[0].error_code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `should reject rejecting another entity's item with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        every { approvalService.resolveEntityId(itemId, "BILL") } returns otherEntityId

        mockMvc.post("/api/v1/approvals/$itemId/reject") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.SENIOR_ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = body(type = "BILL", reason = "duplicate")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    // ── Segregation-of-duties role gate ──────────────────────────────────────────
    // Approve/reject here routes to the exact same actions as JournalController.approve,
    // InvoiceController.approve, and BillController.approveBill — all gated to
    // SENIOR_ACCOUNTANT/CONTROLLER_CFO/SYSTEM_ADMIN (RoleSets.APPROVER). Before this fix, a
    // DATA_ENTRY user could single-handedly approve a journal entry through this queue.

    @Test
    fun `should reject a DATA_ENTRY user approving an item with 403`() {
        val entityId = UUID.randomUUID()
        val itemId = UUID.randomUUID()

        mockMvc.post("/api/v1/approvals/$itemId/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
        }
    }

    @Test
    fun `should allow a SENIOR_ACCOUNTANT to approve their own entity's item`() {
        val entityId = UUID.randomUUID()
        val itemId = UUID.randomUUID()
        every { approvalService.resolveEntityId(itemId, "JOURNAL_ENTRY") } returns entityId
        every { approvalService.approve(itemId, "JOURNAL_ENTRY") } just runs

        mockMvc.post("/api/v1/approvals/$itemId/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.SENIOR_ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = body()
        }.andExpect {
            status { isOk() }
            jsonPath("$.success") { value(true) }
        }
    }
}

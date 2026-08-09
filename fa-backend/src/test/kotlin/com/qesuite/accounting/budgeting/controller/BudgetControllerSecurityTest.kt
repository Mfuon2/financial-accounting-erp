package com.qesuite.accounting.budgeting.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.budgeting.domain.Budget
import com.qesuite.accounting.budgeting.domain.BudgetStatus
import com.qesuite.accounting.budgeting.dto.BudgetResponse
import com.qesuite.accounting.budgeting.service.BudgetService
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityConfig
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.shared.security.mockUserContext
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * IDOR + segregation-of-duties regression test for the Budgeting module, written against the
 * standing lesson from this codebase's own history (see MEMORY.md Known Issues — the codebase-wide
 * IDOR sweep, and the `GlobalApprovalController` gap its own re-verification later found): every
 * id-scoped endpoint must reject a caller from a different entity, and every mutating endpoint
 * must reject a role below its intended bar, proven by actually calling it — not by reading the
 * controller code and assuming the checks fire.
 *
 * @Import(SecurityConfig::class) is required for @PreAuthorize to actually be enforced here —
 * @WebMvcTest does not load SecurityConfig (and therefore @EnableMethodSecurity) by default.
 */
@WebMvcTest(BudgetController::class)
@Import(SecurityConfig::class)
class BudgetControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var budgetService: BudgetService

    private fun budget(entityId: UUID, status: BudgetStatus = BudgetStatus.DRAFT) =
        Budget(entityId = entityId, name = "FY2026 Operating Budget", status = status)

    // ── IDOR: entity-ownership check on every id-scoped endpoint ────────────────────

    @Test
    fun `should reject listing another entity's budgets with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()

        mockMvc.get("/api/v1/budgets") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            param("entityId", otherEntityId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.errors[0].error_code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `should reject getting another entity's budget by id with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val b = budget(otherEntityId)
        every { budgetService.findById(b.id) } returns b

        mockMvc.get("/api/v1/budgets/${b.id}") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject approving another entity's budget with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val b = budget(otherEntityId)
        every { budgetService.findById(b.id) } returns b

        mockMvc.post("/api/v1/budgets/${b.id}/approve") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.SENIOR_ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject fetching another entity's variance report with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val b = budget(otherEntityId)
        every { budgetService.findById(b.id) } returns b

        mockMvc.get("/api/v1/budgets/${b.id}/variance") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── Segregation-of-duties role gates ─────────────────────────────────────────

    @Test
    fun `should reject a DATA_ENTRY user creating a budget with 403`() {
        val entityId = UUID.randomUUID()

        mockMvc.post("/api/v1/budgets") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "entityId" to entityId.toString(),
                    "name" to "Budget",
                    "lines" to listOf(
                        mapOf("accountId" to UUID.randomUUID().toString(), "periodId" to UUID.randomUUID().toString(), "amount" to 100)
                    ),
                )
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject an ACCOUNTANT approving a budget with 403`() {
        val entityId = UUID.randomUUID()
        val b = budget(entityId)
        every { budgetService.findById(b.id) } returns b

        mockMvc.post("/api/v1/budgets/${b.id}/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should allow a SENIOR_ACCOUNTANT to approve their own entity's budget`() {
        val entityId = UUID.randomUUID()
        val b = budget(entityId)
        val approved = budget(entityId, BudgetStatus.APPROVED)
        every { budgetService.findById(b.id) } returns b
        every { budgetService.approve(b.id) } returns approved
        every { budgetService.toResponse(approved) } returns BudgetResponse(
            id = approved.id, entityId = entityId, name = approved.name, status = BudgetStatus.APPROVED,
            totalAmount = approved.totalAmount, notes = null, version = approved.version, lines = emptyList(),
        )

        mockMvc.post("/api/v1/budgets/${b.id}/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.SENIOR_ACCOUNTANT))
        }.andExpect {
            status { isOk() }
        }
    }
}

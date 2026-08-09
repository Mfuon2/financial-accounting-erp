package com.qesuite.accounting.expenses.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.expenses.domain.ExpenseClaim
import com.qesuite.accounting.expenses.domain.ExpenseClaimStatus
import com.qesuite.accounting.expenses.dto.ExpenseClaimResponse
import com.qesuite.accounting.expenses.service.ExpenseClaimService
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
import org.springframework.test.web.servlet.put
import java.time.LocalDate
import java.util.UUID

/**
 * IDOR + segregation-of-duties regression test for the Expense Management module, written against
 * the standing lesson from this codebase's own history (see MEMORY.md Known Issues — the
 * codebase-wide IDOR sweep, and the `GlobalApprovalController` gap its own re-verification later
 * found): every id-scoped endpoint must reject a caller from a different entity, and every
 * mutating endpoint must reject a role below its intended bar, proven by actually calling it — not
 * by reading the controller code and assuming the checks fire.
 *
 * @Import(SecurityConfig::class) is required for @PreAuthorize to actually be enforced here —
 * @WebMvcTest does not load SecurityConfig (and therefore @EnableMethodSecurity) by default.
 */
@WebMvcTest(ExpenseClaimController::class)
@Import(SecurityConfig::class)
class ExpenseClaimControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var expenseClaimService: ExpenseClaimService

    private fun claim(entityId: UUID, status: ExpenseClaimStatus = ExpenseClaimStatus.DRAFT, employeeId: UUID = UUID.randomUUID()) =
        ExpenseClaim(entityId = entityId, employeeId = employeeId, claimDate = LocalDate.of(2026, 8, 1), status = status)

    // ── IDOR: entity-ownership check on every id-scoped endpoint ────────────────────

    @Test
    fun `should reject listing another entity's expense claims with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()

        mockMvc.get("/api/v1/expense-claims") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            param("entityId", otherEntityId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.errors[0].error_code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `should reject getting another entity's expense claim by id with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val c = claim(otherEntityId)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.get("/api/v1/expense-claims/${c.id}") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject updating another entity's expense claim with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val c = claim(otherEntityId)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.put("/api/v1/expense-claims/${c.id}") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("notes" to "hi"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject submitting another entity's expense claim with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val c = claim(otherEntityId)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.post("/api/v1/expense-claims/${c.id}/submit") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject approving another entity's expense claim with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val c = claim(otherEntityId, ExpenseClaimStatus.SUBMITTED)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.post("/api/v1/expense-claims/${c.id}/approve") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.SENIOR_ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject rejecting another entity's expense claim with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val c = claim(otherEntityId, ExpenseClaimStatus.SUBMITTED)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.post("/api/v1/expense-claims/${c.id}/reject") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.SENIOR_ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("reason" to "no receipts"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject reopening another entity's expense claim with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val c = claim(otherEntityId, ExpenseClaimStatus.REJECTED)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.post("/api/v1/expense-claims/${c.id}/reopen") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── Segregation-of-duties role gates ─────────────────────────────────────────

    @Test
    fun `should allow a DATA_ENTRY user creating an expense claim`() {
        val entityId = UUID.randomUUID()
        val employeeId = UUID.randomUUID()
        val created = claim(entityId, employeeId = employeeId)
        every { expenseClaimService.createDraft(any()) } returns created
        every { expenseClaimService.toResponse(created) } returns ExpenseClaimResponse(
            id = created.id, entityId = entityId, employeeId = employeeId, employeeName = "Jane",
            claimDate = created.claimDate, status = ExpenseClaimStatus.DRAFT, totalAmount = created.totalAmount,
            notes = null, journalEntryId = null, rejectionReason = null, version = 0, lines = emptyList(),
        )

        mockMvc.post("/api/v1/expense-claims") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "entityId" to entityId.toString(),
                    "employeeId" to employeeId.toString(),
                    "claimDate" to "2026-08-01",
                    "lines" to listOf(
                        mapOf("accountId" to UUID.randomUUID().toString(), "description" to "Taxi", "amount" to 100, "dateIncurred" to "2026-08-01")
                    ),
                )
            )
        }.andExpect {
            status { isCreated() }
        }
    }

    @Test
    fun `should reject a DATA_ENTRY user approving an expense claim with 403`() {
        val entityId = UUID.randomUUID()
        val c = claim(entityId, ExpenseClaimStatus.SUBMITTED)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.post("/api/v1/expense-claims/${c.id}/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject an ACCOUNTANT approving an expense claim with 403`() {
        val entityId = UUID.randomUUID()
        val c = claim(entityId, ExpenseClaimStatus.SUBMITTED)
        every { expenseClaimService.findById(c.id) } returns c

        mockMvc.post("/api/v1/expense-claims/${c.id}/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should allow a SENIOR_ACCOUNTANT to approve their own entity's expense claim`() {
        val entityId = UUID.randomUUID()
        val c = claim(entityId, ExpenseClaimStatus.SUBMITTED)
        val approved = claim(entityId, ExpenseClaimStatus.REIMBURSED, employeeId = c.employeeId)
        every { expenseClaimService.findById(c.id) } returns c
        every { expenseClaimService.approve(c.id, any()) } returns approved
        every { expenseClaimService.toResponse(approved) } returns ExpenseClaimResponse(
            id = approved.id, entityId = entityId, employeeId = approved.employeeId, employeeName = "Jane",
            claimDate = approved.claimDate, status = ExpenseClaimStatus.REIMBURSED, totalAmount = approved.totalAmount,
            notes = null, journalEntryId = UUID.randomUUID(), rejectionReason = null, version = 0, lines = emptyList(),
        )

        mockMvc.post("/api/v1/expense-claims/${c.id}/approve") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.SENIOR_ACCOUNTANT))
        }.andExpect {
            status { isOk() }
        }
    }
}

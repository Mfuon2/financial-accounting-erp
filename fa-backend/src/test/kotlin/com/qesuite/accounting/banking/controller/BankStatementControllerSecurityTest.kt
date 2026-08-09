package com.qesuite.accounting.banking.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.banking.domain.BankStatementImport
import com.qesuite.accounting.banking.domain.BankStatementLine
import com.qesuite.accounting.banking.dto.BankStatementImportResponse
import com.qesuite.accounting.banking.service.BankStatementService
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
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * IDOR + segregation-of-duties regression test for the Cash & Bank Management module, mirroring
 * `BudgetControllerSecurityTest`'s reference shape exactly (see MEMORY.md's Known Issues — the
 * codebase-wide IDOR sweep, and the `GlobalApprovalController` gap its own re-verification later
 * found). Every id-scoped endpoint must reject a caller from a different entity, and every
 * mutating endpoint must reject a role below its intended bar — proven by actually calling it.
 *
 * @Import(SecurityConfig::class) is required for @PreAuthorize to actually be enforced here —
 * @WebMvcTest does not load SecurityConfig (and therefore @EnableMethodSecurity) by default.
 */
@WebMvcTest(BankStatementController::class)
@Import(SecurityConfig::class)
class BankStatementControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var bankStatementService: BankStatementService

    private fun import(entityId: UUID) = BankStatementImport(
        entityId = entityId, accountId = UUID.randomUUID(), statementDate = LocalDate.of(2026, 3, 31),
        openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO,
    )

    private fun line(entityId: UUID): BankStatementLine {
        val imp = import(entityId)
        val l = BankStatementLine(transDate = LocalDate.of(2026, 3, 14), description = "Test", amount = BigDecimal("1000"))
        imp.addLine(l)
        return l
    }

    // ── IDOR: entity-ownership check on every id-scoped endpoint ────────────────────

    @Test
    fun `should reject listing another entity's bank statements with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()

        mockMvc.get("/api/v1/bank-statements") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            param("entityId", otherEntityId.toString())
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.errors[0].error_code") { value("FORBIDDEN") }
        }
    }

    @Test
    fun `should reject getting another entity's bank statement by id with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val imp = import(otherEntityId)
        every { bankStatementService.findImportById(imp.id) } returns imp

        mockMvc.get("/api/v1/bank-statements/${imp.id}") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject fetching another entity's reconciliation report with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val imp = import(otherEntityId)
        every { bankStatementService.findImportById(imp.id) } returns imp

        mockMvc.get("/api/v1/bank-statements/${imp.id}/reconciliation") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject matching a bank line belonging to another entity with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val l = line(otherEntityId)
        every { bankStatementService.findLineById(l.id) } returns l

        mockMvc.post("/api/v1/bank-statements/lines/${l.id}/match") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("ledgerEntryIds" to listOf(UUID.randomUUID().toString())))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject ignoring a bank line belonging to another entity with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val l = line(otherEntityId)
        every { bankStatementService.findLineById(l.id) } returns l

        mockMvc.post("/api/v1/bank-statements/lines/${l.id}/ignore") {
            with(csrf())
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("reason" to "test"))
        }.andExpect {
            status { isForbidden() }
        }
    }

    // ── Segregation-of-duties role gates ─────────────────────────────────────────

    @Test
    fun `should reject a DATA_ENTRY user importing a bank statement with 403`() {
        val entityId = UUID.randomUUID()

        mockMvc.post("/api/v1/bank-statements") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "entityId" to entityId.toString(),
                    "accountId" to UUID.randomUUID().toString(),
                    "statementDate" to "2026-03-31",
                    "openingBalance" to 0,
                    "closingBalance" to 0,
                    "lines" to listOf(
                        mapOf("transactionDate" to "2026-03-14", "description" to "x", "amount" to 100)
                    ),
                )
            )
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should reject a DATA_ENTRY user matching a bank line with 403`() {
        val entityId = UUID.randomUUID()
        val l = line(entityId)
        every { bankStatementService.findLineById(l.id) } returns l

        mockMvc.post("/api/v1/bank-statements/lines/${l.id}/match") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.DATA_ENTRY))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("ledgerEntryIds" to listOf(UUID.randomUUID().toString())))
        }.andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `should allow an ACCOUNTANT to match a bank line for their own entity`() {
        val entityId = UUID.randomUUID()
        val l = line(entityId)
        val ledgerEntryId = UUID.randomUUID()
        every { bankStatementService.findLineById(l.id) } returns l
        every { bankStatementService.match(l.id, listOf(ledgerEntryId), any()) } returns l
        every { bankStatementService.findImportById(l.bankStatementImportId) } returns l.let { line ->
            // reconstruct the parent import for the response mapping call
            val imp = com.qesuite.accounting.banking.domain.BankStatementImport(
                entityId = entityId, accountId = UUID.randomUUID(), statementDate = LocalDate.of(2026, 3, 31),
                openingBalance = BigDecimal.ZERO, closingBalance = BigDecimal.ZERO,
            )
            imp
        }
        every { bankStatementService.toResponse(any()) } returns BankStatementImportResponse(
            id = UUID.randomUUID(), entityId = entityId, accountId = UUID.randomUUID(), accountCode = "1000",
            accountName = "Bank", statementDate = LocalDate.of(2026, 3, 31), openingBalance = BigDecimal.ZERO,
            closingBalance = BigDecimal.ZERO, notes = null, version = 0, lines = emptyList(),
        )

        mockMvc.post("/api/v1/bank-statements/lines/${l.id}/match") {
            with(csrf())
            with(mockUserContext(entityId, UserRole.ACCOUNTANT))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("ledgerEntryIds" to listOf(ledgerEntryId.toString())))
        }.andExpect {
            status { isOk() }
        }
    }
}

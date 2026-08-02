package com.qesuite.accounting.ledger.controller

import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.ledger.domain.LedgerEntry
import com.qesuite.accounting.ledger.service.LedgerService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.mockUserContext
import com.qesuite.accounting.shared.security.UserRole
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * §18 — IDOR regression test: before the security sweep, `GET /api/v1/ledger/entries/{id}` was
 * looked up by primary key only — any authenticated user who knew (or guessed) another entity's
 * ledger entry UUID could read it. The fix looks up the entry first, then checks its entityId
 * against the caller's own before returning it.
 */
@WebMvcTest(LedgerController::class)
class LedgerControllerSecurityTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var ledgerService: LedgerService

    @Test
    fun `should reject fetching a ledger entry that belongs to another entity with 403`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        val entryId = UUID.randomUUID()
        val foreignEntry = LedgerEntry(
            entityId = otherEntityId,
            accountId = UUID.randomUUID(),
            journalEntryLineId = UUID.randomUUID(),
            transDate = LocalDate.of(2026, 1, 15),
            functionalDebit = BigDecimal("1000.000000"),
            functionalCredit = BigDecimal.ZERO,
            runningBalance = BigDecimal("1000.000000"),
        )
        every { ledgerService.findById(entryId) } returns foreignEntry

        mockMvc.get("/api/v1/ledger/entries/$entryId") {
            with(mockUserContext(ownEntityId, UserRole.ACCOUNTANT))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.success") { value(false) }
            jsonPath("$.errors[0].error_code") { value("FORBIDDEN") }
        }
    }
}

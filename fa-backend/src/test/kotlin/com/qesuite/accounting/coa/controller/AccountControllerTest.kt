package com.qesuite.accounting.coa.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.service.AccountService
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.mockUserContext
import com.qesuite.accounting.coa.service.CreateAccountCommand
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.*

@WebMvcTest(AccountController::class)
class AccountControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var apiKeyService: ApiKeyService

    @MockkBean
    private lateinit var accountService: AccountService

    @Test
    fun `should create account and return 201 with ApiResponse`() {
        // Given
        val entityId = UUID.randomUUID()
        val command = CreateAccountCommand(
            entityId = entityId,
            accountCode = "1000",
            accountName = "Cash",
            accountSubtype = AccountSubtype.CASH_AND_EQUIVALENTS
        )

        val account = mockk<Account>(relaxed = true)
        every { account.accountCode } returns "1000"
        every { accountService.createAccount(any()) } returns account

        // When/Then — authenticated as a user of the SAME entity as command.entityId
        // (SecurityUtils.requireOwnEntity guard added by the IDOR sweep).
        mockMvc.post("/api/v1/coa/accounts") {
            with(csrf())
            with(mockUserContext(entityId))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(command)
        }.andExpect {
            status { isCreated() }
            jsonPath("$.success") { value(true) }
            jsonPath("$.data.accountCode") { value("1000") }
        }
    }
}

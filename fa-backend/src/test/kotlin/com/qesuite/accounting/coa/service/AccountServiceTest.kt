package com.qesuite.accounting.coa.service

import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

class AccountServiceTest {

    private val accountRepository = mockk<AccountRepository>()
    private val currencyRepository = mockk<com.qesuite.accounting.fx.repository.CurrencyRepository>()
    private val ledgerEntryRepository = mockk<com.qesuite.accounting.ledger.repository.LedgerEntryRepository>()
    private val accountService = AccountService(accountRepository, currencyRepository, ledgerEntryRepository)

    private val entityId = UUID.randomUUID()

    @Test
    fun `should successfully create a root account`() {
        // Given
        val command = CreateAccountCommand(
            entityId = entityId,
            accountCode = "1000",
            accountName = "Cash",
            accountSubtype = AccountSubtype.CASH_AND_EQUIVALENTS,
            currencyCode = "USD"
        )

        every { currencyRepository.findByEntityIdAndCurrencyCode(entityId, "USD") } returns Optional.of(mockk())
        every { accountRepository.existsByEntityIdAndAccountCode(entityId, "1000") } returns false
        every { accountRepository.existsByEntityIdAndAccountName(entityId, "Cash") } returns false
        every { accountRepository.save(any()) } answers { it.invocation.args[0] as Account }

        // When
        val result = accountService.createAccount(command)

        // Then
        assertNotNull(result)
        assertEquals("1000", result.accountCode)
        assertEquals(AccountType.ASSET, result.accountType)
        assertEquals(AccountSubtype.CASH_AND_EQUIVALENTS, result.accountSubtype)
        assertNull(result.parentAccountId)
        verify { accountRepository.save(any()) }
    }

    @Test
    fun `should throw error if account code already exists`() {
        // Given
        val command = CreateAccountCommand(
            entityId = entityId,
            accountCode = "1000",
            accountName = "Cash",
            accountSubtype = AccountSubtype.CASH_AND_EQUIVALENTS,
            currencyCode = "USD"
        )

        every { currencyRepository.findByEntityIdAndCurrencyCode(entityId, "USD") } returns Optional.of(mockk())
        every { accountRepository.existsByEntityIdAndAccountCode(entityId, "1000") } returns true

        // When/Then
        val exception = assertThrows<ValidationException> {
            accountService.createAccount(command)
        }
        assertEquals("DUPLICATE_ACCOUNT_CODE", exception.errorCode)
    }

    @Test
    fun `should throw error if hierarchy depth exceeds 5`() {
        // Given
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()
        val p3 = UUID.randomUUID()
        val p4 = UUID.randomUUID()
        val p5 = UUID.randomUUID()

        val command = CreateAccountCommand(
            entityId = entityId,
            accountCode = "1111",
            accountName = "Deep Account",
            accountSubtype = AccountSubtype.CASH_AND_EQUIVALENTS,
            parentAccountId = p5,
            currencyCode = "USD"
        )

        every { currencyRepository.findByEntityIdAndCurrencyCode(entityId, "USD") } returns Optional.of(mockk())
        every { accountRepository.existsByEntityIdAndAccountCode(any(), any()) } returns false
        every { accountRepository.existsByEntityIdAndAccountName(any(), any()) } returns false
        
        // Build chain of 5 parents
        val acc5 = mockAccount(p5, entityId, p4)
        val acc4 = mockAccount(p4, entityId, p3)
        val acc3 = mockAccount(p3, entityId, p2)
        val acc2 = mockAccount(p2, entityId, p1)
        val acc1 = mockAccount(p1, entityId, null)

        every { accountRepository.findById(p5) } returns Optional.of(acc5)
        every { accountRepository.findById(p4) } returns Optional.of(acc4)
        every { accountRepository.findById(p3) } returns Optional.of(acc3)
        every { accountRepository.findById(p2) } returns Optional.of(acc2)
        every { accountRepository.findById(p1) } returns Optional.of(acc1)

        // When/Then
        val exception = assertThrows<ValidationException> {
            accountService.createAccount(command)
        }
        assertEquals("INVALID_COA_HIERARCHY", exception.errorCode)
    }

    @Test
    fun `should detect circular reference`() {
        // Given
        val p1 = UUID.randomUUID()
        val p2 = UUID.randomUUID()

        val command = CreateAccountCommand(
            entityId = entityId,
            accountCode = "1234",
            accountName = "Circular",
            accountSubtype = AccountSubtype.CASH_AND_EQUIVALENTS,
            parentAccountId = p2,
            currencyCode = "USD"
        )

        every { currencyRepository.findByEntityIdAndCurrencyCode(entityId, "USD") } returns Optional.of(mockk())
        every { accountRepository.existsByEntityIdAndAccountCode(any(), any()) } returns false
        every { accountRepository.existsByEntityIdAndAccountName(any(), any()) } returns false

        // p2 -> p1 -> p2 (Cycle)
        val acc2 = mockAccount(p2, entityId, p1)
        val acc1 = mockAccount(p1, entityId, p2)

        every { accountRepository.findById(p2) } returns Optional.of(acc2)
        every { accountRepository.findById(p1) } returns Optional.of(acc1)

        // When/Then
        val exception = assertThrows<ValidationException> {
            accountService.createAccount(command)
        }
        assertEquals("CIRCULAR_ACCOUNT_REFERENCE", exception.errorCode)
    }

    private fun mockAccount(id: UUID, entityId: UUID, parentId: UUID?): Account {
        val acc = mockk<Account>()
        every { acc.id } returns id
        every { acc.entityId } returns entityId
        every { acc.parentAccountId } returns parentId
        return acc
    }
}

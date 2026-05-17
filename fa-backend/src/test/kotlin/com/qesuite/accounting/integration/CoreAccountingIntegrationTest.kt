package com.qesuite.accounting.integration

import com.ninjasquad.springmockk.MockkBean
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.service.AccountService
import com.qesuite.accounting.coa.service.CreateAccountCommand
import com.qesuite.accounting.journal.domain.JournalEntryStatus
import com.qesuite.accounting.journal.repository.JournalEntryRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.ledger.service.TrialBalanceService
import com.qesuite.accounting.shared.idempotency.service.IdempotencyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@SpringBootTest
@ActiveProfiles("test")
class CoreAccountingIntegrationTest {

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var periodService: PeriodService

    @Autowired
    private lateinit var journalService: JournalService

    @Autowired
    private lateinit var trialBalanceService: TrialBalanceService

    @Autowired
    private lateinit var journalEntryRepository: JournalEntryRepository

    @Autowired
    private lateinit var ledgerEntryRepository: LedgerEntryRepository

    @MockkBean
    private lateinit var idempotencyService: IdempotencyService

    @Autowired
    private lateinit var accountRepository: com.qesuite.accounting.coa.repository.AccountRepository

    @Autowired
    private lateinit var currencyRepository: com.qesuite.accounting.fx.repository.CurrencyRepository

    @Test
    @Transactional
    fun `should complete full accounting cycle from journal to trial balance`() {
        val entityId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        // §18 — Setup Authentication Context
        val userContext = com.qesuite.accounting.shared.security.UserContext(
            userId = userId,
            entityId = entityId,
            role = com.qesuite.accounting.shared.security.UserRole.ACCOUNTANT,
            email = "test@example.com"
        )
        val auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userContext, null, emptyList())
        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication = auth
        
        // 0. Setup Currency (§13.2)
        currencyRepository.save(com.qesuite.accounting.fx.domain.Currency(
            entityId = entityId,
            currencyCode = "USD",
            currencyName = "US Dollar",
            isFunctional = true
        ))

        // 1. Setup Periods
        periodService.generateFiscalYear(entityId, 2024)
        val period = periodService.findPeriodForDate(entityId, LocalDate.of(2024, 1, 15))

        // 2. Setup Accounts
        val cashAccount = accountService.createAccount(CreateAccountCommand(
            entityId = entityId,
            accountCode = "1000",
            accountName = "Cash",
            accountSubtype = AccountSubtype.CASH_AND_EQUIVALENTS
        ))

        val revenueAccount = accountService.createAccount(CreateAccountCommand(
            entityId = entityId,
            accountCode = "4000",
            accountName = "Sales Revenue",
            accountSubtype = AccountSubtype.OPERATING_REVENUE
        ))

        // 3. Create Journal Entry
        val command = CreateJournalEntryCommand(
            entityId = entityId,
            periodId = period.id,
            transDate = LocalDate.of(2024, 1, 15),
            description = "Sales Transaction",
            lines = listOf(
                CreateJournalLineCommand(
                    accountId = cashAccount.id,
                    description = "Cash received",
                    debitAmount = BigDecimal("1000.00"),
                    currencyCode = "USD"
                ),
                CreateJournalLineCommand(
                    accountId = revenueAccount.id,
                    description = "Revenue recognized",
                    creditAmount = BigDecimal("1000.00"),
                    currencyCode = "USD"
                )
            )
        )

        val entry = journalService.createDraft(command)
        assertNotNull(entry.id)
        assertEquals(JournalEntryStatus.DRAFT, entry.status)

        // 4. Post Entry
        journalService.submitEntry(entry.id)
        journalService.postEntry(entry.id)

        // 5. Verify Results
        val postedEntry = journalEntryRepository.findById(entry.id).get()
        assertEquals(JournalEntryStatus.POSTED, postedEntry.status)

        // Verify Ledger
        val ledgerEntries = ledgerEntryRepository.findAll()
        assertTrue(ledgerEntries.size >= 2)

        // Verify Account Balances
        val finalCash = accountRepository.findById(cashAccount.id).get()
        
        assertEquals(BigDecimal("1000.000000"), finalCash.currentBalance.setScale(6))
        assertEquals(BigDecimal("1000.000000"), finalCash.totalDebits.setScale(6))

        // 6. Verify Trial Balance
        val trialBalance = trialBalanceService.generateTrialBalance(entityId)
        assertEquals(BigDecimal("1000.000000"), trialBalance.totalDebits.setScale(6))
        assertEquals(BigDecimal("1000.000000"), trialBalance.totalCredits.setScale(6))
    }
}

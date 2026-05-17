package com.qesuite.accounting.ledger.service

import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class TrialBalanceServiceTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var ledgerEntryRepository: LedgerEntryRepository
    private lateinit var trialBalanceService: TrialBalanceService

    private val entityId = UUID.randomUUID()
    private val cashAccountId = UUID.randomUUID()
    private val revenueAccountId = UUID.randomUUID()
    private val asOfDate = LocalDate.of(2026, 5, 9)

    @BeforeEach
    fun setup() {
        accountRepository = mockk(relaxed = true)
        ledgerEntryRepository = mockk(relaxed = true)

        trialBalanceService = TrialBalanceService(
            accountRepository = accountRepository,
            ledgerEntryRepository = ledgerEntryRepository
        )
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private fun makeAccount(
        id: UUID,
        accountCode: String,
        accountName: String,
        accountType: AccountType,
        normalBalance: NormalBalance
    ): Account {
        val acc = Account(
            entityId = entityId,
            accountCode = accountCode,
            accountName = accountName,
            accountType = accountType,
            accountSubtype = when (accountType) {
                AccountType.ASSET -> AccountSubtype.CASH_AND_EQUIVALENTS
                AccountType.LIABILITY -> AccountSubtype.CURRENT_PAYABLE
                AccountType.EQUITY -> AccountSubtype.RETAINED_EARNINGS
                AccountType.REVENUE -> AccountSubtype.OPERATING_REVENUE
                AccountType.EXPENSE -> AccountSubtype.OPERATING_EXPENSES
            },
            normalBalance = normalBalance,
            isTemporary = accountType == AccountType.REVENUE || accountType == AccountType.EXPENSE,
            currencyCode = "KES"
        )
        setId(acc, id)
        return acc
    }

    private fun setId(entity: Any, id: java.util.UUID) {
        var clazz: Class<*>? = entity.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField("id")
                f.isAccessible = true
                f.set(entity, id)
                return
            } catch (_: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        error("Could not find 'id' field on ${entity.javaClass.name}")
    }
    // -------------------------------------------------------------------------
    // TEST 1 — generateTrialBalance returns balanced report for balanced ledger
    // -------------------------------------------------------------------------

    @Test
    fun `generateTrialBalance returns balanced report for balanced ledger`() {
        val cashAccount = makeAccount(
            cashAccountId, "1000", "Cash",
            AccountType.ASSET, NormalBalance.DEBIT
        )
        val revenueAccount = makeAccount(
            revenueAccountId, "4000", "Revenue",
            AccountType.REVENUE, NormalBalance.CREDIT
        )

        every { accountRepository.findAllByEntityId(entityId) } returns listOf(cashAccount, revenueAccount)

        // Cash: debits=10000, credits=0  → net debit balance 10000
        every { ledgerEntryRepository.sumFunctionalDebits(cashAccountId, asOfDate) } returns BigDecimal("10000")
        every { ledgerEntryRepository.sumFunctionalCredits(cashAccountId, asOfDate) } returns BigDecimal.ZERO

        // Revenue: debits=0, credits=10000  → net credit balance 10000
        every { ledgerEntryRepository.sumFunctionalDebits(revenueAccountId, asOfDate) } returns BigDecimal.ZERO
        every { ledgerEntryRepository.sumFunctionalCredits(revenueAccountId, asOfDate) } returns BigDecimal("10000")

        val report = trialBalanceService.generateTrialBalance(entityId, asOfDate)

        // Total debits and credits must balance
        assertTrue(
            report.totalDebits.compareTo(report.totalCredits) == 0,
            "Expected totalDebits == totalCredits but got debits=${report.totalDebits} credits=${report.totalCredits}"
        )

        val cashRow = report.rows.find { it.accountCode == "1000" }!!
        val revenueRow = report.rows.find { it.accountCode == "4000" }!!

        // Cash (DEBIT-normal, net=10000) → debitBalance=10000, creditBalance=0
        assertTrue(
            cashRow.debitBalance.compareTo(BigDecimal("10000")) == 0,
            "Expected Cash debitBalance 10000 but was ${cashRow.debitBalance}"
        )
        assertTrue(
            cashRow.creditBalance.compareTo(BigDecimal.ZERO) == 0,
            "Expected Cash creditBalance 0 but was ${cashRow.creditBalance}"
        )

        // Revenue (CREDIT-normal, net=10000) → debitBalance=0, creditBalance=10000
        assertTrue(
            revenueRow.debitBalance.compareTo(BigDecimal.ZERO) == 0,
            "Expected Revenue debitBalance 0 but was ${revenueRow.debitBalance}"
        )
        assertTrue(
            revenueRow.creditBalance.compareTo(BigDecimal("10000")) == 0,
            "Expected Revenue creditBalance 10000 but was ${revenueRow.creditBalance}"
        )
    }

    // -------------------------------------------------------------------------
    // TEST 2 — generateTrialBalance throws when ledger is unbalanced
    // -------------------------------------------------------------------------

    @Test
    fun `generateTrialBalance throws ValidationException with TRIAL_BALANCE_FAILURE when ledger is unbalanced`() {
        val cashAccount = makeAccount(
            cashAccountId, "1000", "Cash",
            AccountType.ASSET, NormalBalance.DEBIT
        )

        every { accountRepository.findAllByEntityId(entityId) } returns listOf(cashAccount)

        // Deliberately unbalanced: debits != credits
        every { ledgerEntryRepository.sumFunctionalDebits(cashAccountId, asOfDate) } returns BigDecimal("15000")
        every { ledgerEntryRepository.sumFunctionalCredits(cashAccountId, asOfDate) } returns BigDecimal("10000")

        val ex = assertThrows<ValidationException> {
            trialBalanceService.generateTrialBalance(entityId, asOfDate)
        }

        assertEquals("TRIAL_BALANCE_FAILURE", ex.errorCode)
    }

    // -------------------------------------------------------------------------
    // TEST 3 — generateTrialBalance uses today's date when asOfDate is null
    // -------------------------------------------------------------------------

    @Test
    fun `generateTrialBalance uses today's date when asOfDate is null`() {
        val cashAccount = makeAccount(
            cashAccountId, "1000", "Cash",
            AccountType.ASSET, NormalBalance.DEBIT
        )
        val revenueAccount = makeAccount(
            revenueAccountId, "4000", "Revenue",
            AccountType.REVENUE, NormalBalance.CREDIT
        )

        every { accountRepository.findAllByEntityId(entityId) } returns listOf(cashAccount, revenueAccount)

        // Use any() for date since null triggers LocalDate.now() internally
        every { ledgerEntryRepository.sumFunctionalDebits(cashAccountId, any()) } returns BigDecimal("5000")
        every { ledgerEntryRepository.sumFunctionalCredits(cashAccountId, any()) } returns BigDecimal.ZERO
        every { ledgerEntryRepository.sumFunctionalDebits(revenueAccountId, any()) } returns BigDecimal.ZERO
        every { ledgerEntryRepository.sumFunctionalCredits(revenueAccountId, any()) } returns BigDecimal("5000")

        // Should not throw — balanced ledger with null asOfDate falls back to today
        val report = trialBalanceService.generateTrialBalance(entityId, null)

        assertNotNull(report)
        assertEquals(entityId, report.entityId)
        // asOfDate on the report should be today (not null)
        assertEquals(LocalDate.now(), report.asOfDate)
    }

    // -------------------------------------------------------------------------
    // TEST 4 — debit-normal account balance shown in debit column
    // -------------------------------------------------------------------------

    @Test
    fun `debit-normal account balance shown in debit column`() {
        val assetAccount = makeAccount(
            cashAccountId, "1001", "Bank Account",
            AccountType.ASSET, NormalBalance.DEBIT
        )

        every { accountRepository.findAllByEntityId(entityId) } returns listOf(assetAccount)
        // More debits than credits → net positive debit balance
        every { ledgerEntryRepository.sumFunctionalDebits(cashAccountId, asOfDate) } returns BigDecimal("20000")
        every { ledgerEntryRepository.sumFunctionalCredits(cashAccountId, asOfDate) } returns BigDecimal("20000")

        // Balanced at 20000/20000 for the double-entry check — net balance = 0
        // Use a simpler scenario: 10000 debit only (raw total needs to balance with another account)
        // Re-setup: only one account, so raw totals must match. Use equal raw totals with net=0.
        every { ledgerEntryRepository.sumFunctionalDebits(cashAccountId, asOfDate) } returns BigDecimal("10000")
        every { ledgerEntryRepository.sumFunctionalCredits(cashAccountId, asOfDate) } returns BigDecimal("10000")

        val report = trialBalanceService.generateTrialBalance(entityId, asOfDate)

        val row = report.rows.find { it.accountCode == "1001" }!!
        // Net = debits - credits = 0 for DEBIT-normal → both columns are 0
        assertTrue(
            row.debitBalance.compareTo(BigDecimal.ZERO) == 0,
            "Expected debitBalance 0 but was ${row.debitBalance}"
        )
        assertTrue(
            row.creditBalance.compareTo(BigDecimal.ZERO) == 0,
            "Expected creditBalance 0 but was ${row.creditBalance}"
        )
    }

    @Test
    fun `debit-normal account with net positive balance appears in debit column only`() {
        // Two accounts to keep raw totals balanced
        val assetAccount = makeAccount(
            cashAccountId, "1001", "Cash",
            AccountType.ASSET, NormalBalance.DEBIT
        )
        val revenueAccount = makeAccount(
            revenueAccountId, "4001", "Sales Revenue",
            AccountType.REVENUE, NormalBalance.CREDIT
        )

        every { accountRepository.findAllByEntityId(entityId) } returns listOf(assetAccount, revenueAccount)

        // Asset: debits=8000, credits=0 → net debit balance=8000
        every { ledgerEntryRepository.sumFunctionalDebits(cashAccountId, asOfDate) } returns BigDecimal("8000")
        every { ledgerEntryRepository.sumFunctionalCredits(cashAccountId, asOfDate) } returns BigDecimal.ZERO

        // Revenue: debits=0, credits=8000 → net credit balance=8000  (raw totals balanced)
        every { ledgerEntryRepository.sumFunctionalDebits(revenueAccountId, asOfDate) } returns BigDecimal.ZERO
        every { ledgerEntryRepository.sumFunctionalCredits(revenueAccountId, asOfDate) } returns BigDecimal("8000")

        val report = trialBalanceService.generateTrialBalance(entityId, asOfDate)
        val assetRow = report.rows.find { it.accountCode == "1001" }!!

        // ASSET (DEBIT-normal) net=8000 → debitBalance=8000, creditBalance=0
        assertTrue(
            assetRow.debitBalance.compareTo(BigDecimal("8000")) == 0,
            "Expected debitBalance 8000 but was ${assetRow.debitBalance}"
        )
        assertTrue(
            assetRow.creditBalance.compareTo(BigDecimal.ZERO) == 0,
            "Expected creditBalance 0 but was ${assetRow.creditBalance}"
        )
    }

    // -------------------------------------------------------------------------
    // TEST 5 — credit-normal account balance shown in credit column
    // -------------------------------------------------------------------------

    @Test
    fun `credit-normal account with net positive balance appears in credit column only`() {
        val liabilityAccountId = UUID.randomUUID()
        val liabilityAccount = makeAccount(
            liabilityAccountId, "2001", "Accounts Payable",
            AccountType.LIABILITY, NormalBalance.CREDIT
        )
        val assetAccount = makeAccount(
            cashAccountId, "1001", "Cash",
            AccountType.ASSET, NormalBalance.DEBIT
        )

        every { accountRepository.findAllByEntityId(entityId) } returns listOf(assetAccount, liabilityAccount)

        // Asset: debits=12000, credits=0 — provides the raw debit total
        every { ledgerEntryRepository.sumFunctionalDebits(cashAccountId, asOfDate) } returns BigDecimal("12000")
        every { ledgerEntryRepository.sumFunctionalCredits(cashAccountId, asOfDate) } returns BigDecimal.ZERO

        // Liability: debits=0, credits=12000 → net credit balance=12000
        every { ledgerEntryRepository.sumFunctionalDebits(liabilityAccountId, asOfDate) } returns BigDecimal.ZERO
        every { ledgerEntryRepository.sumFunctionalCredits(liabilityAccountId, asOfDate) } returns BigDecimal("12000")

        val report = trialBalanceService.generateTrialBalance(entityId, asOfDate)
        val liabilityRow = report.rows.find { it.accountCode == "2001" }!!

        // LIABILITY (CREDIT-normal) net=12000 → debitBalance=0, creditBalance=12000
        assertTrue(
            liabilityRow.creditBalance.compareTo(BigDecimal("12000")) == 0,
            "Expected creditBalance 12000 but was ${liabilityRow.creditBalance}"
        )
        assertTrue(
            liabilityRow.debitBalance.compareTo(BigDecimal.ZERO) == 0,
            "Expected debitBalance 0 but was ${liabilityRow.debitBalance}"
        )
    }
}

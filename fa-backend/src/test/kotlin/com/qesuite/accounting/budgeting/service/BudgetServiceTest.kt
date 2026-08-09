package com.qesuite.accounting.budgeting.service

import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.budgeting.domain.Budget
import com.qesuite.accounting.budgeting.domain.BudgetLine
import com.qesuite.accounting.budgeting.domain.BudgetStatus
import com.qesuite.accounting.budgeting.dto.BudgetLineCommand
import com.qesuite.accounting.budgeting.dto.CreateBudgetCommand
import com.qesuite.accounting.budgeting.dto.UpdateBudgetCommand
import com.qesuite.accounting.budgeting.repository.BudgetRepository
import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class BudgetServiceTest {

    private val budgetRepository = mockk<BudgetRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val periodService = mockk<PeriodService>()
    private val ledgerEntryRepository = mockk<LedgerEntryRepository>()
    private val budgetService = BudgetService(budgetRepository, accountRepository, periodService, ledgerEntryRepository)

    private val entityId = UUID.randomUUID()
    private val otherEntityId = UUID.randomUUID()

    private fun account(
        entityId: UUID = this.entityId,
        isHeader: Boolean = false,
        normalBalance: NormalBalance = NormalBalance.DEBIT,
    ) = Account(
        entityId = entityId,
        accountCode = "6100",
        accountName = "Office Supplies",
        accountType = AccountType.EXPENSE,
        accountSubtype = AccountSubtype.OPERATING_EXPENSES,
        normalBalance = normalBalance,
        isTemporary = true,
        isHeader = isHeader,
    )

    private fun period(entityId: UUID = this.entityId) = Period(
        entityId = entityId,
        periodName = "MARCH 2026",
        startDate = LocalDate.of(2026, 3, 1),
        endDate = LocalDate.of(2026, 3, 31),
        status = PeriodStatus.OPEN,
    )

    // ── createDraft ──────────────────────────────────────────────────────────────

    @Test
    fun `createDraft computes totalAmount as the sum of line amounts, never trusting a client total`() {
        val acct = account()
        val per = period()
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { periodService.findById(per.id) } returns per
        val saved = slot<Budget>()
        every { budgetRepository.save(capture(saved)) } answers { saved.captured }

        val result = budgetService.createDraft(
            CreateBudgetCommand(
                entityId = entityId,
                name = "FY2026 Operating Budget",
                notes = null,
                lines = listOf(
                    BudgetLineCommand(acct.id, per.id, BigDecimal("1000.00")),
                ),
            )
        )

        assertEquals(BigDecimal("1000.000000"), result.totalAmount)
        assertEquals(1, result.lines.size)
        assertEquals(BudgetStatus.DRAFT, result.status)
    }

    @Test
    fun `createDraft sums multiple lines correctly`() {
        val acct1 = account()
        val acct2 = account().also { it.accountCode = "6200"; it.accountName = "Travel Expense" }
        val per = period()
        every { accountRepository.findById(acct1.id) } returns Optional.of(acct1)
        every { accountRepository.findById(acct2.id) } returns Optional.of(acct2)
        every { periodService.findById(per.id) } returns per
        val saved = slot<Budget>()
        every { budgetRepository.save(capture(saved)) } answers { saved.captured }

        val result = budgetService.createDraft(
            CreateBudgetCommand(
                entityId = entityId,
                name = "Budget",
                notes = null,
                lines = listOf(
                    BudgetLineCommand(acct1.id, per.id, BigDecimal("1000.00")),
                    BudgetLineCommand(acct2.id, per.id, BigDecimal("500.50")),
                ),
            )
        )

        assertEquals(BigDecimal("1500.500000"), result.totalAmount)
    }

    @Test
    fun `createDraft rejects a header account`() {
        val header = account(isHeader = true)
        val per = period()
        every { accountRepository.findById(header.id) } returns Optional.of(header)
        every { periodService.findById(per.id) } returns per

        assertThrows<BusinessRuleViolationException> {
            budgetService.createDraft(
                CreateBudgetCommand(
                    entityId = entityId, name = "Budget", notes = null,
                    lines = listOf(BudgetLineCommand(header.id, per.id, BigDecimal("100"))),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects an account belonging to a different entity`() {
        val foreignAccount = account(entityId = otherEntityId)
        val per = period()
        every { accountRepository.findById(foreignAccount.id) } returns Optional.of(foreignAccount)
        every { periodService.findById(per.id) } returns per

        assertThrows<ValidationException> {
            budgetService.createDraft(
                CreateBudgetCommand(
                    entityId = entityId, name = "Budget", notes = null,
                    lines = listOf(BudgetLineCommand(foreignAccount.id, per.id, BigDecimal("100"))),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects a period belonging to a different entity`() {
        val acct = account()
        val foreignPeriod = period(entityId = otherEntityId)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { periodService.findById(foreignPeriod.id) } returns foreignPeriod

        assertThrows<ValidationException> {
            budgetService.createDraft(
                CreateBudgetCommand(
                    entityId = entityId, name = "Budget", notes = null,
                    lines = listOf(BudgetLineCommand(acct.id, foreignPeriod.id, BigDecimal("100"))),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects duplicate (account, period) lines`() {
        val acct = account()
        val per = period()

        assertThrows<ValidationException> {
            budgetService.createDraft(
                CreateBudgetCommand(
                    entityId = entityId, name = "Budget", notes = null,
                    lines = listOf(
                        BudgetLineCommand(acct.id, per.id, BigDecimal("100")),
                        BudgetLineCommand(acct.id, per.id, BigDecimal("200")),
                    ),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects an unknown account with a 404`() {
        val per = period()
        val unknownAccountId = UUID.randomUUID()
        every { accountRepository.findById(unknownAccountId) } returns Optional.empty()

        assertThrows<ResourceNotFoundException> {
            budgetService.createDraft(
                CreateBudgetCommand(
                    entityId = entityId, name = "Budget", notes = null,
                    lines = listOf(BudgetLineCommand(unknownAccountId, per.id, BigDecimal("100"))),
                )
            )
        }
    }

    // ── update ───────────────────────────────────────────────────────────────────

    @Test
    fun `update rejects editing a non-DRAFT budget`() {
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.APPROVED)
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)

        assertThrows<BusinessRuleViolationException> {
            budgetService.update(budget.id, UpdateBudgetCommand(name = "New name"))
        }
    }

    @Test
    fun `update replaces lines wholesale and recomputes totalAmount`() {
        val acct = account()
        val per = period()
        val oldLine = BudgetLine(accountId = UUID.randomUUID(), periodId = per.id, amount = BigDecimal("999.000000"))
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.DRAFT, totalAmount = BigDecimal("999.000000"))
        budget.addLine(oldLine)
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { periodService.findById(per.id) } returns per
        every { budgetRepository.save(budget) } returns budget

        val result = budgetService.update(
            budget.id,
            UpdateBudgetCommand(lines = listOf(BudgetLineCommand(acct.id, per.id, BigDecimal("250.00")))),
        )

        assertEquals(1, result.lines.size)
        assertEquals(BigDecimal("250.000000"), result.totalAmount)
    }

    // ── approve / void ───────────────────────────────────────────────────────────

    @Test
    fun `approve transitions DRAFT to APPROVED`() {
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.DRAFT)
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        every { budgetRepository.save(budget) } returns budget

        val result = budgetService.approve(budget.id)

        assertEquals(BudgetStatus.APPROVED, result.status)
    }

    @Test
    fun `approve rejects a budget that is not DRAFT`() {
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.VOID)
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)

        assertThrows<BusinessRuleViolationException> { budgetService.approve(budget.id) }
    }

    // ── maker-checker (segregation of duties) ───────────────────────────────────

    @AfterEach
    fun clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(userId: UUID) {
        val principal = com.qesuite.accounting.shared.security.UserContext(
            userId = userId, entityId = entityId,
            role = com.qesuite.accounting.shared.security.UserRole.SENIOR_ACCOUNTANT, email = "u@example.com",
        )
        org.springframework.security.core.context.SecurityContextHolder.getContext().authentication =
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    @Test
    fun `approve rejects the preparer approving their own budget`() {
        val userId = UUID.randomUUID()
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.DRAFT)
        budget.createdBy = userId
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        authenticateAs(userId)

        val ex = assertThrows<com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException> {
            budgetService.approve(budget.id)
        }
        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.errorCode)
    }

    @Test
    fun `approve allows a different user to approve a budget they did not create`() {
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.DRAFT)
        budget.createdBy = UUID.randomUUID()
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        every { budgetRepository.save(budget) } returns budget
        authenticateAs(UUID.randomUUID())

        val result = budgetService.approve(budget.id)

        assertEquals(BudgetStatus.APPROVED, result.status)
    }

    @Test
    fun `void deactivates an APPROVED budget and records the reason`() {
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.APPROVED)
        val userId = UUID.randomUUID()
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        every { budgetRepository.save(budget) } returns budget

        val result = budgetService.void(budget.id, "No longer needed", userId)

        assertEquals(BudgetStatus.VOID, result.status)
        assertEquals(false, result.isActive)
        assertEquals("No longer needed", result.deactivationReason)
        assertEquals(userId, result.deactivatedBy)
    }

    @Test
    fun `void rejects a budget that is already VOID`() {
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.VOID)
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)

        assertThrows<BusinessRuleViolationException> { budgetService.void(budget.id, "reason", null) }
    }

    // ── variance report ──────────────────────────────────────────────────────────

    @Test
    fun `varianceReport nets debits and credits per the account's normal balance`() {
        val debitAcct = account(normalBalance = NormalBalance.DEBIT)
        val per = period()
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.APPROVED)
        budget.addLine(BudgetLine(accountId = debitAcct.id, periodId = per.id, amount = BigDecimal("1000.000000")))
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        every { accountRepository.findAllById(listOf(debitAcct.id)) } returns listOf(debitAcct)
        every { periodService.findById(per.id) } returns per
        every { ledgerEntryRepository.sumDebitsByAccountIdsAndRange(listOf(debitAcct.id), per.startDate, per.endDate) } returns BigDecimal("1200.000000")
        every { ledgerEntryRepository.sumCreditsByAccountIdsAndRange(listOf(debitAcct.id), per.startDate, per.endDate) } returns BigDecimal("50.000000")

        val report = budgetService.varianceReport(budget.id)

        // Debit-normal account: actual = debits - credits = 1200 - 50 = 1150
        assertEquals(BigDecimal("1150.000000"), report.lines[0].actualAmount)
        // variance = actual - budgeted = 1150 - 1000 = 150 (over budget)
        assertEquals(BigDecimal("150.000000"), report.lines[0].variance)
        assertEquals(BigDecimal("150.000000"), report.totalVariance)
    }

    @Test
    fun `varianceReport nets a credit-normal account the opposite way`() {
        val creditAcct = account(normalBalance = NormalBalance.CREDIT)
        val per = period()
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.APPROVED)
        budget.addLine(BudgetLine(accountId = creditAcct.id, periodId = per.id, amount = BigDecimal("5000.000000")))
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        every { accountRepository.findAllById(listOf(creditAcct.id)) } returns listOf(creditAcct)
        every { periodService.findById(per.id) } returns per
        every { ledgerEntryRepository.sumDebitsByAccountIdsAndRange(listOf(creditAcct.id), per.startDate, per.endDate) } returns BigDecimal("0.000000")
        every { ledgerEntryRepository.sumCreditsByAccountIdsAndRange(listOf(creditAcct.id), per.startDate, per.endDate) } returns BigDecimal("4500.000000")

        val report = budgetService.varianceReport(budget.id)

        // Credit-normal account: actual = credits - debits = 4500 - 0 = 4500
        assertEquals(BigDecimal("4500.000000"), report.lines[0].actualAmount)
        // variance = actual - budgeted = 4500 - 5000 = -500 (under budget)
        assertEquals(BigDecimal("-500.000000"), report.lines[0].variance)
    }

    @Test
    fun `varianceReport leaves variancePercent null when the budgeted amount is zero`() {
        val acct = account()
        val per = period()
        val budget = Budget(entityId = entityId, name = "Budget", status = BudgetStatus.APPROVED)
        budget.addLine(BudgetLine(accountId = acct.id, periodId = per.id, amount = BigDecimal.ZERO))
        every { budgetRepository.findById(budget.id) } returns Optional.of(budget)
        every { accountRepository.findAllById(listOf(acct.id)) } returns listOf(acct)
        every { periodService.findById(per.id) } returns per
        every { ledgerEntryRepository.sumDebitsByAccountIdsAndRange(listOf(acct.id), per.startDate, per.endDate) } returns BigDecimal("100.000000")
        every { ledgerEntryRepository.sumCreditsByAccountIdsAndRange(listOf(acct.id), per.startDate, per.endDate) } returns BigDecimal.ZERO

        val report = budgetService.varianceReport(budget.id)

        assertNull(report.lines[0].variancePercent)
    }
}

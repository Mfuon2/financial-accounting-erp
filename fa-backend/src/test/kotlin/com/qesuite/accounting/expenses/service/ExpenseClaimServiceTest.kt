package com.qesuite.accounting.expenses.service

import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.expenses.domain.ExpenseClaim
import com.qesuite.accounting.expenses.domain.ExpenseClaimLine
import com.qesuite.accounting.expenses.domain.ExpenseClaimStatus
import com.qesuite.accounting.expenses.dto.CreateExpenseClaimCommand
import com.qesuite.accounting.expenses.dto.ExpenseClaimLineCommand
import com.qesuite.accounting.expenses.dto.UpdateExpenseClaimCommand
import com.qesuite.accounting.expenses.repository.ExpenseClaimRepository
import com.qesuite.accounting.fx.domain.Currency
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.users.domain.User
import com.qesuite.accounting.users.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class ExpenseClaimServiceTest {

    private val expenseClaimRepository = mockk<ExpenseClaimRepository>()
    private val accountRepository = mockk<AccountRepository>()
    private val userRepository = mockk<UserRepository>()
    private val periodService = mockk<PeriodService>()
    private val currencyRepository = mockk<CurrencyRepository>()
    private val journalService = mockk<JournalService>()
    private val service = ExpenseClaimService(
        expenseClaimRepository, accountRepository, userRepository, periodService, currencyRepository, journalService,
    )

    private val entityId = UUID.randomUUID()
    private val otherEntityId = UUID.randomUUID()

    private fun expenseAccount(
        entityId: UUID = this.entityId,
        isHeader: Boolean = false,
        accountType: AccountType = AccountType.EXPENSE,
    ) = Account(
        entityId = entityId,
        accountCode = "5-3000",
        accountName = "Operating Expenses",
        accountType = accountType,
        accountSubtype = AccountSubtype.OPERATING_EXPENSES,
        normalBalance = NormalBalance.DEBIT,
        isTemporary = true,
        isHeader = isHeader,
    )

    private fun payableAccount(entityId: UUID = this.entityId, name: String = "Accounts Payable") = Account(
        entityId = entityId,
        accountCode = "2-1000",
        accountName = name,
        accountType = AccountType.LIABILITY,
        accountSubtype = AccountSubtype.CURRENT_PAYABLE,
        normalBalance = NormalBalance.CREDIT,
        isTemporary = false,
        isHeader = false,
    )

    private fun employee(entityId: UUID = this.entityId, id: UUID = UUID.randomUUID()) = User(
        id = id,
        entityId = entityId,
        fullName = "Jane Employee",
        email = "jane@example.com",
        passwordHash = "hash",
        createdBy = UUID.randomUUID(),
        modifiedBy = UUID.randomUUID(),
    )

    private fun period(entityId: UUID = this.entityId) = Period(
        entityId = entityId,
        periodName = "AUGUST 2026",
        startDate = LocalDate.of(2026, 8, 1),
        endDate = LocalDate.of(2026, 8, 31),
        status = PeriodStatus.OPEN,
    )

    private fun functionalCurrency(entityId: UUID = this.entityId) = Currency(
        entityId = entityId,
        currencyCode = "KES",
        currencyName = "Kenyan Shilling",
        isFunctional = true,
    )

    // ── createDraft ──────────────────────────────────────────────────────────────

    @Test
    fun `createDraft computes totalAmount as the sum of line amounts, never trusting a client total`() {
        val emp = employee()
        val acct = expenseAccount()
        every { userRepository.findById(emp.id) } returns Optional.of(emp)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        val saved = slot<ExpenseClaim>()
        every { expenseClaimRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.createDraft(
            CreateExpenseClaimCommand(
                entityId = entityId,
                employeeId = emp.id,
                claimDate = LocalDate.of(2026, 8, 1),
                notes = null,
                lines = listOf(
                    ExpenseClaimLineCommand(acct.id, "Taxi", BigDecimal("1000.00"), LocalDate.of(2026, 8, 1)),
                ),
            )
        )

        assertEquals(BigDecimal("1000.000000"), result.totalAmount)
        assertEquals(1, result.lines.size)
        assertEquals(ExpenseClaimStatus.DRAFT, result.status)
    }

    @Test
    fun `createDraft sums multiple lines correctly`() {
        val emp = employee()
        val acct1 = expenseAccount()
        val acct2 = expenseAccount().also { it.accountCode = "5-3100"; it.accountName = "Travel Expense" }
        every { userRepository.findById(emp.id) } returns Optional.of(emp)
        every { accountRepository.findById(acct1.id) } returns Optional.of(acct1)
        every { accountRepository.findById(acct2.id) } returns Optional.of(acct2)
        val saved = slot<ExpenseClaim>()
        every { expenseClaimRepository.save(capture(saved)) } answers { saved.captured }

        val result = service.createDraft(
            CreateExpenseClaimCommand(
                entityId = entityId, employeeId = emp.id, claimDate = LocalDate.of(2026, 8, 1), notes = null,
                lines = listOf(
                    ExpenseClaimLineCommand(acct1.id, "Taxi", BigDecimal("1000.00"), LocalDate.of(2026, 8, 1)),
                    ExpenseClaimLineCommand(acct2.id, "Flight", BigDecimal("500.50"), LocalDate.of(2026, 8, 2)),
                ),
            )
        )

        assertEquals(BigDecimal("1500.500000"), result.totalAmount)
    }

    @Test
    fun `createDraft rejects an unknown employee with a 404`() {
        val unknownEmployeeId = UUID.randomUUID()
        every { userRepository.findById(unknownEmployeeId) } returns Optional.empty()

        assertThrows<ResourceNotFoundException> {
            service.createDraft(
                CreateExpenseClaimCommand(
                    entityId = entityId, employeeId = unknownEmployeeId, claimDate = LocalDate.now(), notes = null,
                    lines = listOf(ExpenseClaimLineCommand(UUID.randomUUID(), "Taxi", BigDecimal("100"), LocalDate.now())),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects an employee belonging to a different entity`() {
        val emp = employee(entityId = otherEntityId)
        every { userRepository.findById(emp.id) } returns Optional.of(emp)

        assertThrows<ValidationException> {
            service.createDraft(
                CreateExpenseClaimCommand(
                    entityId = entityId, employeeId = emp.id, claimDate = LocalDate.now(), notes = null,
                    lines = listOf(ExpenseClaimLineCommand(UUID.randomUUID(), "Taxi", BigDecimal("100"), LocalDate.now())),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects a header account`() {
        val emp = employee()
        val header = expenseAccount(isHeader = true)
        every { userRepository.findById(emp.id) } returns Optional.of(emp)
        every { accountRepository.findById(header.id) } returns Optional.of(header)

        assertThrows<BusinessRuleViolationException> {
            service.createDraft(
                CreateExpenseClaimCommand(
                    entityId = entityId, employeeId = emp.id, claimDate = LocalDate.now(), notes = null,
                    lines = listOf(ExpenseClaimLineCommand(header.id, "Taxi", BigDecimal("100"), LocalDate.now())),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects a non-EXPENSE account`() {
        val emp = employee()
        val assetAcct = expenseAccount(accountType = AccountType.ASSET)
        every { userRepository.findById(emp.id) } returns Optional.of(emp)
        every { accountRepository.findById(assetAcct.id) } returns Optional.of(assetAcct)

        assertThrows<BusinessRuleViolationException> {
            service.createDraft(
                CreateExpenseClaimCommand(
                    entityId = entityId, employeeId = emp.id, claimDate = LocalDate.now(), notes = null,
                    lines = listOf(ExpenseClaimLineCommand(assetAcct.id, "Taxi", BigDecimal("100"), LocalDate.now())),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects an account belonging to a different entity`() {
        val emp = employee()
        val foreignAccount = expenseAccount(entityId = otherEntityId)
        every { userRepository.findById(emp.id) } returns Optional.of(emp)
        every { accountRepository.findById(foreignAccount.id) } returns Optional.of(foreignAccount)

        assertThrows<ValidationException> {
            service.createDraft(
                CreateExpenseClaimCommand(
                    entityId = entityId, employeeId = emp.id, claimDate = LocalDate.now(), notes = null,
                    lines = listOf(ExpenseClaimLineCommand(foreignAccount.id, "Taxi", BigDecimal("100"), LocalDate.now())),
                )
            )
        }
    }

    @Test
    fun `createDraft rejects a zero or negative line amount`() {
        val emp = employee()
        val acct = expenseAccount()
        every { userRepository.findById(emp.id) } returns Optional.of(emp)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)

        assertThrows<ValidationException> {
            service.createDraft(
                CreateExpenseClaimCommand(
                    entityId = entityId, employeeId = emp.id, claimDate = LocalDate.now(), notes = null,
                    lines = listOf(ExpenseClaimLineCommand(acct.id, "Taxi", BigDecimal.ZERO, LocalDate.now())),
                )
            )
        }
    }

    // ── update ───────────────────────────────────────────────────────────────────

    @Test
    fun `update rejects editing a non-DRAFT claim`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.SUBMITTED)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)

        assertThrows<BusinessRuleViolationException> {
            service.update(claim.id, UpdateExpenseClaimCommand(notes = "New note"))
        }
    }

    @Test
    fun `update replaces lines wholesale and recomputes totalAmount`() {
        val acct = expenseAccount()
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.DRAFT, totalAmount = BigDecimal("999.000000"))
        claim.addLine(ExpenseClaimLine(accountId = UUID.randomUUID(), description = "old", amount = BigDecimal("999.000000"), dateIncurred = LocalDate.now()))
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { accountRepository.findById(acct.id) } returns Optional.of(acct)
        every { expenseClaimRepository.save(claim) } returns claim

        val result = service.update(
            claim.id,
            UpdateExpenseClaimCommand(lines = listOf(ExpenseClaimLineCommand(acct.id, "New line", BigDecimal("250.00"), LocalDate.now()))),
        )

        assertEquals(1, result.lines.size)
        assertEquals(BigDecimal("250.000000"), result.totalAmount)
    }

    // ── submit ───────────────────────────────────────────────────────────────────

    @Test
    fun `submit transitions DRAFT to SUBMITTED`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.DRAFT, totalAmount = BigDecimal("100"))
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { expenseClaimRepository.save(claim) } returns claim

        val result = service.submit(claim.id)

        assertEquals(ExpenseClaimStatus.SUBMITTED, result.status)
    }

    @Test
    fun `submit rejects a claim that is not DRAFT`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.APPROVED)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)

        assertThrows<BusinessRuleViolationException> { service.submit(claim.id) }
    }

    @Test
    fun `submit rejects a zero-total claim`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.DRAFT, totalAmount = BigDecimal.ZERO)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)

        assertThrows<BusinessRuleViolationException> { service.submit(claim.id) }
    }

    // ── approve — the posting path ─────────────────────────────────────────────

    private fun submittedClaimWithLines(
        employeeId: UUID = UUID.randomUUID(),
        acct1: Account = expenseAccount(),
        acct2: Account = expenseAccount().also { it.accountCode = "5-3100"; it.accountName = "Travel" },
    ): Triple<ExpenseClaim, Account, Account> {
        val claim = ExpenseClaim(entityId = entityId, employeeId = employeeId, claimDate = LocalDate.of(2026, 8, 1), status = ExpenseClaimStatus.SUBMITTED)
        claim.addLine(ExpenseClaimLine(accountId = acct1.id, description = "Taxi", amount = BigDecimal("1000.000000"), dateIncurred = LocalDate.of(2026, 8, 1)))
        claim.addLine(ExpenseClaimLine(accountId = acct2.id, description = "Flight", amount = BigDecimal("4000.000000"), dateIncurred = LocalDate.of(2026, 8, 1)))
        claim.totalAmount = BigDecimal("5000.000000")
        return Triple(claim, acct1, acct2)
    }

    @Test
    fun `approve rejects self-approval by the claim's own employee`() {
        // Every downstream dependency (period, currency, employee, payable account, journal
        // posting) is stubbed to a fully working happy path — if the self-approval guard were
        // ever removed, this call would complete successfully instead of throwing. That is what
        // makes this test load-bearing rather than accidentally passing via some unrelated
        // unmocked-dependency exception earlier in the method.
        val employeeId = UUID.randomUUID()
        val (claim, _, _) = submittedClaimWithLines(employeeId = employeeId)
        val per = period()
        val payable = payableAccount()
        val emp = employee(id = employeeId)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { periodService.findPeriodForDate(entityId, claim.claimDate) } returns per
        every { currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId) } returns Optional.of(functionalCurrency())
        every { userRepository.findById(employeeId) } returns Optional.of(emp)
        every { accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CURRENT_PAYABLE) } returns listOf(payable)
        every { journalService.createEntry(any()) } returns JournalEntry(entityId = entityId, periodId = per.id, transDate = claim.claimDate)
        every { journalService.postEntryAsSystem(any()) } returns Unit
        every { expenseClaimRepository.save(claim) } returns claim

        val ex = assertThrows<BusinessRuleViolationException> { service.approve(claim.id, employeeId) }
        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.errorCode)
        verify(exactly = 0) { journalService.createEntry(any()) }
    }

    // ── maker-checker (segregation of duties) — separate from the employeeId check above.
    // That one guards the nominal beneficiary approving their own reimbursement; this one
    // guards the actual submitter (createdBy) rubber-stamping their own work regardless of
    // whose name is on the claim (delegated submission stays allowed). See MEMORY.md.

    @org.junit.jupiter.api.AfterEach
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
    fun `approve rejects the actual submitter approving their own claim even when filed on another employee's behalf`() {
        // Delegated submission: claim.employeeId is a DIFFERENT person than the one who actually
        // created it (createdBy) — proving the old employeeId check alone would NOT catch this.
        val submitterId = UUID.randomUUID()
        val (claim, _, _) = submittedClaimWithLines(employeeId = UUID.randomUUID())
        claim.createdBy = submitterId
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        authenticateAs(submitterId)

        val ex = assertThrows<BusinessRuleViolationException> { service.approve(claim.id, UUID.randomUUID()) }
        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.errorCode)
        verify(exactly = 0) { journalService.createEntry(any()) }
    }

    @Test
    fun `approve rejects a claim that is not SUBMITTED`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.DRAFT)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)

        assertThrows<BusinessRuleViolationException> { service.approve(claim.id, UUID.randomUUID()) }
    }

    @Test
    fun `approve throws NO_OPEN_PERIOD when no period covers the claim date`() {
        val (claim, _, _) = submittedClaimWithLines()
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { periodService.findPeriodForDate(entityId, claim.claimDate) } throws ValidationException("PERIOD_NOT_FOUND", "no period")

        assertThrows<BusinessRuleViolationException> { service.approve(claim.id, UUID.randomUUID()) }
    }

    @Test
    fun `approve throws when no CURRENT_PAYABLE account exists for the entity`() {
        val (claim, acct1, acct2) = submittedClaimWithLines()
        val per = period()
        val emp = employee(id = claim.employeeId)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { periodService.findPeriodForDate(entityId, claim.claimDate) } returns per
        every { currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId) } returns Optional.of(functionalCurrency())
        every { userRepository.findById(claim.employeeId) } returns Optional.of(emp)
        every { accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CURRENT_PAYABLE) } returns emptyList()

        assertThrows<BusinessRuleViolationException> { service.approve(claim.id, UUID.randomUUID()) }
    }

    /**
     * CLAUDE.md §11/§17 — "Every new journal-posting code path has a test asserting debits equal
     * credits — this is the one non-negotiable test in the entire system." This is that test for
     * the Expense Management module's one posting path.
     */
    @Test
    fun `approve posts a balanced journal entry — debits equal credits — and advances the claim to REIMBURSED`() {
        val (claim, acct1, acct2) = submittedClaimWithLines()
        val per = period()
        val payable = payableAccount()
        val emp = employee(id = claim.employeeId)
        val approverId = UUID.randomUUID()

        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { periodService.findPeriodForDate(entityId, claim.claimDate) } returns per
        every { currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId) } returns Optional.of(functionalCurrency())
        every { userRepository.findById(claim.employeeId) } returns Optional.of(emp)
        every { accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CURRENT_PAYABLE) } returns listOf(payable)

        val jeCommandSlot = slot<CreateJournalEntryCommand>()
        val fakeJe = JournalEntry(entityId = entityId, periodId = per.id, transDate = claim.claimDate)
        every { journalService.createEntry(capture(jeCommandSlot)) } returns fakeJe
        every { journalService.postEntryAsSystem(fakeJe.id) } returns Unit
        every { expenseClaimRepository.save(claim) } returns claim

        val result = service.approve(claim.id, approverId)

        // ── The non-negotiable assertion ──
        val lines = jeCommandSlot.captured.lines
        val totalDebits = lines.sumOf { it.debitAmount }
        val totalCredits = lines.sumOf { it.creditAmount }
        assertEquals(0, totalDebits.compareTo(totalCredits), "Reimbursement journal entry must balance: debits ($totalDebits) must equal credits ($totalCredits)")
        assertEquals(0, totalDebits.compareTo(BigDecimal("5000.000000")))

        // DR both expense accounts for their own amounts.
        val acct1Line = lines.first { it.accountId == acct1.id }
        val acct2Line = lines.first { it.accountId == acct2.id }
        assertEquals(0, acct1Line.debitAmount.compareTo(BigDecimal("1000.000000")))
        assertEquals(0, acct2Line.debitAmount.compareTo(BigDecimal("4000.000000")))

        // CR the payable account for the claim total.
        val payableLine = lines.first { it.accountId == payable.id }
        assertEquals(0, payableLine.creditAmount.compareTo(BigDecimal("5000.000000")))
        assertEquals(0, payableLine.debitAmount.compareTo(BigDecimal.ZERO))

        verify { journalService.postEntryAsSystem(fakeJe.id) }
        assertEquals(fakeJe.id, result.journalEntryId)
        assertEquals(ExpenseClaimStatus.REIMBURSED, result.status)
    }

    @Test
    fun `approve merges two lines against the same account into a single JE line`() {
        val acct = expenseAccount()
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.of(2026, 8, 1), status = ExpenseClaimStatus.SUBMITTED)
        claim.addLine(ExpenseClaimLine(accountId = acct.id, description = "Taxi AM", amount = BigDecimal("500.000000"), dateIncurred = LocalDate.of(2026, 8, 1)))
        claim.addLine(ExpenseClaimLine(accountId = acct.id, description = "Taxi PM", amount = BigDecimal("300.000000"), dateIncurred = LocalDate.of(2026, 8, 1)))
        claim.totalAmount = BigDecimal("800.000000")
        val per = period()
        val payable = payableAccount()
        val emp = employee(id = claim.employeeId)

        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { periodService.findPeriodForDate(entityId, claim.claimDate) } returns per
        every { currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId) } returns Optional.of(functionalCurrency())
        every { userRepository.findById(claim.employeeId) } returns Optional.of(emp)
        every { accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CURRENT_PAYABLE) } returns listOf(payable)

        val jeCommandSlot = slot<CreateJournalEntryCommand>()
        val fakeJe = JournalEntry(entityId = entityId, periodId = per.id, transDate = claim.claimDate)
        every { journalService.createEntry(capture(jeCommandSlot)) } returns fakeJe
        every { journalService.postEntryAsSystem(fakeJe.id) } returns Unit
        every { expenseClaimRepository.save(claim) } returns claim

        service.approve(claim.id, UUID.randomUUID())

        val debitLines = jeCommandSlot.captured.lines.filter { it.accountId == acct.id }
        assertEquals(1, debitLines.size)
        assertEquals(0, debitLines[0].debitAmount.compareTo(BigDecimal("800.000000")))
    }

    @Test
    fun `approve prefers a dedicated reimbursement-named payable account over the generic one`() {
        val (claim, _, _) = submittedClaimWithLines()
        val per = period()
        val genericAp = payableAccount(name = "Accounts Payable").also { it.accountCode = "2-1000" }
        val dedicated = payableAccount(name = "Employee Reimbursements Payable").also { it.accountCode = "2-1500" }
        val emp = employee(id = claim.employeeId)

        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { periodService.findPeriodForDate(entityId, claim.claimDate) } returns per
        every { currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId) } returns Optional.of(functionalCurrency())
        every { userRepository.findById(claim.employeeId) } returns Optional.of(emp)
        every { accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CURRENT_PAYABLE) } returns listOf(genericAp, dedicated)

        val jeCommandSlot = slot<CreateJournalEntryCommand>()
        val fakeJe = JournalEntry(entityId = entityId, periodId = per.id, transDate = claim.claimDate)
        every { journalService.createEntry(capture(jeCommandSlot)) } returns fakeJe
        every { journalService.postEntryAsSystem(fakeJe.id) } returns Unit
        every { expenseClaimRepository.save(claim) } returns claim

        service.approve(claim.id, UUID.randomUUID())

        val creditLine = jeCommandSlot.captured.lines.first { it.creditAmount.signum() > 0 }
        assertEquals(dedicated.id, creditLine.accountId)
    }

    // ── reject / reopen ──────────────────────────────────────────────────────────

    @Test
    fun `reject transitions SUBMITTED to REJECTED and records the reason`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.SUBMITTED)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { expenseClaimRepository.save(claim) } returns claim

        val result = service.reject(claim.id, "Missing receipts")

        assertEquals(ExpenseClaimStatus.REJECTED, result.status)
        assertEquals("Missing receipts", result.rejectionReason)
    }

    @Test
    fun `reject rejects a claim that is not SUBMITTED`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.DRAFT)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)

        assertThrows<BusinessRuleViolationException> { service.reject(claim.id, "reason") }
    }

    @Test
    fun `reopen transitions REJECTED back to DRAFT and clears the rejection reason`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.REJECTED, rejectionReason = "Missing receipts")
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)
        every { expenseClaimRepository.save(claim) } returns claim

        val result = service.reopen(claim.id)

        assertEquals(ExpenseClaimStatus.DRAFT, result.status)
        assertNull(result.rejectionReason)
    }

    @Test
    fun `reopen rejects a claim that is not REJECTED`() {
        val claim = ExpenseClaim(entityId = entityId, employeeId = UUID.randomUUID(), claimDate = LocalDate.now(), status = ExpenseClaimStatus.DRAFT)
        every { expenseClaimRepository.findById(claim.id) } returns Optional.of(claim)

        assertThrows<BusinessRuleViolationException> { service.reopen(claim.id) }
    }
}

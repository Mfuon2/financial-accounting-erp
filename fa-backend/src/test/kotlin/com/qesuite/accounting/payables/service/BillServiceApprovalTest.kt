package com.qesuite.accounting.payables.service

import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.party.repository.SupplierRepository
import com.qesuite.accounting.payables.domain.Bill
import com.qesuite.accounting.payables.domain.BillStatus
import com.qesuite.accounting.payables.repository.BillPaymentRepository
import com.qesuite.accounting.payables.repository.BillRepository
import com.qesuite.accounting.payables.repository.PaymentRunRepository
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.security.UserContext
import com.qesuite.accounting.shared.security.UserRole
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * Maker-checker (segregation of duties) on `BillService.approveBill` — see
 * `SecurityUtils.requireNotSelfApproval`. A dedicated preparer-vs-approver bug found and closed
 * across every approval flow in this codebase on 2026-08-09 (see MEMORY.md); this proves the
 * guard is genuinely wired into Bill's approval path, not just present in the shared helper.
 */
class BillServiceApprovalTest {

    private val billRepository = mockk<BillRepository>()
    private val billPaymentRepository = mockk<BillPaymentRepository>()
    private val paymentRunRepository = mockk<PaymentRunRepository>()
    private val journalService = mockk<JournalService>()
    private val periodService = mockk<PeriodService>()
    private val accountRepository = mockk<AccountRepository>()
    private val supplierRepository = mockk<SupplierRepository>()

    private val billService = BillService(
        billRepository, billPaymentRepository, paymentRunRepository,
        journalService, periodService, accountRepository, supplierRepository,
    )

    private val entityId = UUID.randomUUID()

    @AfterEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(userId: UUID) {
        val principal = UserContext(userId = userId, entityId = entityId, role = UserRole.SENIOR_ACCOUNTANT, email = "u@example.com")
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    private fun draftBill(createdBy: UUID?) = Bill(
        entityId = entityId,
        billNumber = "BILL-2026-00001",
        supplierName = "Acme Supplies",
        billDate = LocalDate.of(2026, 3, 1),
        status = BillStatus.DRAFT,
        totalAmount = BigDecimal("1000.00"),
        createdBy = createdBy,
    )

    @Test
    fun `approveBill rejects the preparer approving their own bill`() {
        val userId = UUID.randomUUID()
        val bill = draftBill(createdBy = userId)
        every { billRepository.findById(bill.id) } returns Optional.of(bill)
        authenticateAs(userId)

        val ex = assertThrows<BusinessRuleViolationException> { billService.approveBill(bill.id) }
        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.errorCode)
    }

    @Test
    fun `approveBill does not reject a different approver before reaching period resolution`() {
        val bill = draftBill(createdBy = UUID.randomUUID())
        every { billRepository.findById(bill.id) } returns Optional.of(bill)
        authenticateAs(UUID.randomUUID())
        // No period/account/journal mocks set up beyond this — the bill has no periodId, so
        // approveBill falls into its findPeriodForDate branch, which wraps any failure into
        // NO_OPEN_PERIOD (see BillService.kt:215-216). Seeing that specific error — not
        // SELF_APPROVAL_NOT_ALLOWED — proves the maker-checker guard let this request through
        // and the code actually reached period resolution.
        every { periodService.findPeriodForDate(any(), any()) } throws RuntimeException("no period configured for this test")

        val ex = assertThrows<com.qesuite.accounting.shared.exceptions.ValidationException> { billService.approveBill(bill.id) }
        assertEquals("NO_OPEN_PERIOD", ex.errorCode)
    }
}

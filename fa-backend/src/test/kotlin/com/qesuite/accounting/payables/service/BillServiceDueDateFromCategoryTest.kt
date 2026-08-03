package com.qesuite.accounting.payables.service

import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.party.domain.Supplier
import com.qesuite.accounting.party.repository.SupplierRepository
import com.qesuite.accounting.payables.repository.BillPaymentRepository
import com.qesuite.accounting.payables.repository.BillRepository
import com.qesuite.accounting.payables.repository.PaymentRunRepository
import com.qesuite.accounting.shared.categories.domain.CategoryType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

/**
 * Proves the AP due-date calculation (`BillService.parsePaymentTermsDays`, used by
 * `createBill`) is unaffected by *where* `Supplier.paymentTerms` came from. The field itself
 * never changed type or storage (still a plain `String` on the `Supplier` row) — only the
 * *frontend dropdown* that produces the value is now backed by the new `CategoryService`
 * instead of a hard-coded array (see `CategoryType.PAYMENT_TERM.defaultSeed()`). This test
 * uses the exact codes that seed produces to prove the accounting rule survives the rewiring.
 *
 * No BillService test existed before this change (`MEMORY.md` gap) — this closes it for the
 * one code path this feature touches.
 */
class BillServiceDueDateFromCategoryTest {

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
    private val billDate = LocalDate.of(2026, 3, 1)

    private fun stubCommonBillRepo(supplierId: UUID, supplier: Supplier) {
        every { billRepository.countByEntityId(entityId) } returns 0L
        every { billRepository.existsByEntityIdAndBillNumber(entityId, any()) } returns false
        every { supplierRepository.findById(supplierId) } returns Optional.of(supplier)
        every {
            billRepository.findPotentialDuplicates(any(), any(), any(), any(), any())
        } returns emptyList()
        every { billRepository.save(any()) } answers { firstArg() }
    }

    private fun billRequest(supplierId: UUID) = CreateBillRequest(
        entityId = entityId,
        supplierId = supplierId,
        supplierName = "Acme Supplies",
        billDate = billDate,
        items = listOf(BillItemRequest(description = "Widgets", unitPrice = BigDecimal("100.00"))),
    )

    @Test
    fun `NET_30 category code resolves the due date to bill date plus 30 days`() {
        val supplierId = UUID.randomUUID()
        val (code, _) = CategoryType.PAYMENT_TERM.defaultSeed().first { it.first == "NET_30" }
        val supplier = Supplier(entityId = entityId, supplierCode = "SUPP-01", name = "Acme Supplies", paymentTerms = code)
        stubCommonBillRepo(supplierId, supplier)

        val result = billService.createBill(billRequest(supplierId), createdBy = null)

        assertEquals(billDate.plusDays(30), result.bill.dueDate)
    }

    @Test
    fun `NET_60 category code resolves the due date to bill date plus 60 days`() {
        val supplierId = UUID.randomUUID()
        val (code, _) = CategoryType.PAYMENT_TERM.defaultSeed().first { it.first == "NET_60" }
        val supplier = Supplier(entityId = entityId, supplierCode = "SUPP-02", name = "Acme Supplies", paymentTerms = code)
        stubCommonBillRepo(supplierId, supplier)

        val result = billService.createBill(billRequest(supplierId), createdBy = null)

        assertEquals(billDate.plusDays(60), result.bill.dueDate)
    }

    @Test
    fun `DUE_ON_RECEIPT category code leaves the due date unset, same as before the rewiring`() {
        val supplierId = UUID.randomUUID()
        val (code, _) = CategoryType.PAYMENT_TERM.defaultSeed().first { it.first == "DUE_ON_RECEIPT" }
        val supplier = Supplier(entityId = entityId, supplierCode = "SUPP-03", name = "Acme Supplies", paymentTerms = code)
        stubCommonBillRepo(supplierId, supplier)

        val result = billService.createBill(billRequest(supplierId), createdBy = null)

        assertNull(result.bill.dueDate)
    }
}

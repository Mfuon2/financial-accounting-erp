// package com.qesuite.accounting.invoicing.service

// import com.qesuite.accounting.invoicing.domain.Invoice
// import com.qesuite.accounting.invoicing.domain.InvoiceStatus
// import com.qesuite.accounting.invoicing.dto.CreateCreditNoteCommand
// import com.qesuite.accounting.invoicing.dto.CreateInvoiceCommand
// import com.qesuite.accounting.invoicing.dto.CreateInvoiceLineCommand
// import com.qesuite.accounting.invoicing.repository.InvoiceRepository
// import com.qesuite.accounting.journal.domain.JournalEntry
// import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
// import com.qesuite.accounting.journal.service.JournalService
// import com.qesuite.accounting.party.domain.Customer
// import com.qesuite.accounting.party.repository.CustomerRepository
// import com.qesuite.accounting.shared.exceptions.BaseAccountingException
// import com.qesuite.accounting.tax.service.TaxService
// import io.mockk.every
// import io.mockk.mockk
// import io.mockk.verify
// import org.junit.jupiter.api.BeforeEach
// import org.junit.jupiter.api.Test
// import org.junit.jupiter.api.assertThrows
// import java.math.BigDecimal
// import java.time.LocalDate
// import java.util.Optional
// import java.util.UUID
// import kotlin.test.assertEquals

// /**
//  * §14.1, §14.2 — InvoiceService unit tests.
//  *
//  * Coverage:
//  *  • createDraft — happy path + customer-not-found + inactive-customer
//  *  • approve     — credit-limit-exceeded
//  *  • applyPayment — partial → PARTIALLY_PAID, full → PAID, overpayment guard
//  *  • void        — DRAFT-only guard
//  *  • createCreditNote — exceeds-original guard
//  */
// class InvoiceServiceTest {

//     private lateinit var invoiceRepository: InvoiceRepository
//     private lateinit var customerRepository: CustomerRepository
//     private lateinit var accountRepository: com.qesuite.accounting.coa.repository.AccountRepository
//     private lateinit var journalService: JournalService
//     private lateinit var taxService: TaxService
//     private lateinit var ifrs15Service: Ifrs15RecognitionService
//     private lateinit var numberGenerator: InvoiceNumberGenerator
//     private lateinit var invoiceService: InvoiceService

//     private val entityId = UUID.randomUUID()
//     private val periodId = UUID.randomUUID()
//     private val customerId = UUID.randomUUID()
//     private val arAccountId = UUID.randomUUID()
//     private val revenueAccountId = UUID.randomUUID()

//     @BeforeEach
//     fun setup() {
//         invoiceRepository = mockk(relaxed = true)
//         customerRepository = mockk(relaxed = true)
//         journalService = mockk(relaxed = true)
//         taxService = mockk(relaxed = true)
//         ifrs15Service = mockk(relaxed = true)
//         numberGenerator = mockk(relaxed = true)

//         every { numberGenerator.next(entityId, "INV") } returns "INV-TEST-0001"
//         every { numberGenerator.next(entityId, "CN") } returns "CN-TEST-0001"
//         every { invoiceRepository.existsByEntityIdAndInvoiceNumber(entityId, any()) } returns false
//         every { invoiceRepository.save(any<Invoice>()) } answers { firstArg() }

//         accountRepository = mockk(relaxed = true)
//         // Default: return an OPERATING_EXPENSES account so discount JE lines can be booked
//         val opexAccount = com.qesuite.accounting.coa.domain.Account(
//             entityId = entityId,
//             accountCode = "5000",
//             accountName = "Operating Expenses",
//             accountType = com.qesuite.accounting.coa.domain.AccountType.EXPENSE,
//             accountSubtype = com.qesuite.accounting.coa.domain.AccountSubtype.OPERATING_EXPENSES,
//             normalBalance = com.qesuite.accounting.coa.domain.NormalBalance.DEBIT,
//             isTemporary = true,
//             ifrsCategory = com.qesuite.accounting.coa.domain.IfrsCategory.OPERATING_EXPENSES,
//             currencyCode = "KES"
//         )
//         every { accountRepository.findAllByEntityId(any()) } returns listOf(opexAccount)

//         invoiceService = InvoiceService(
//             invoiceRepository = invoiceRepository,
//             customerRepository = customerRepository,
//             accountRepository = accountRepository,
//             journalService = journalService,
//             taxService = taxService,
//             ifrs15Service = ifrs15Service,
//             invoiceNumberGenerator = numberGenerator,
//         )
//     }

//     private fun activeCustomer(creditLimit: BigDecimal = BigDecimal("50000")): Customer = Customer(
//         id = customerId,
//         entityId = entityId,
//         periodId = periodId,
//         customerCode = "CUST-001",
//         name = "Test Customer Ltd",
//         creditLimit = creditLimit,
//         defaultArAccountId = arAccountId,
//     )

//     private fun draftInvoice(
//         total: BigDecimal = BigDecimal("1050"),
//         outstanding: BigDecimal = BigDecimal("1050"),
//         paid: BigDecimal = BigDecimal.ZERO,
//         status: InvoiceStatus = InvoiceStatus.DRAFT,
//     ): Invoice {
//         val id = UUID.randomUUID()
//         return Invoice(
//             id = id,
//             entityId = entityId,
//             periodId = periodId,
//             invoiceNumber = "INV-TEST-0001",
//             customerId = customerId,
//             issueDate = LocalDate.of(2026, 5, 8),
//             dueDate = LocalDate.of(2026, 6, 7),
//             currencyCode = "KES",
//             exchangeRate = BigDecimal.ONE,
//             subtotal = BigDecimal("1000"),
//             taxAmount = BigDecimal("100"),
//             discountAmount = BigDecimal("50"),
//             totalAmount = total,
//             paidAmount = paid,
//             outstandingAmount = outstanding,
//             status = status,
//         )
//     }

//     @Test
//     fun `createDraft - persists invoice in DRAFT with computed totals`() {
//         every { customerRepository.findById(customerId) } returns Optional.of(activeCustomer())

//         val command = CreateInvoiceCommand(
//             entityId = entityId,
//             periodId = periodId,
//             customerId = customerId,
//             issueDate = LocalDate.of(2026, 5, 8),
//             dueDate = LocalDate.of(2026, 6, 7),
//             currencyCode = "KES",
//             exchangeRate = BigDecimal.ONE,
//             subtotal = BigDecimal("1000"), // ignored — service recomputes from lines
//             taxAmount = BigDecimal.ZERO,
//             discountAmount = BigDecimal("50"),
//             lines = listOf(
//                 CreateInvoiceLineCommand(
//                     lineNumber = 1,
//                     accountId = revenueAccountId,
//                     description = "Widget",
//                     quantity = BigDecimal("10"),
//                     unitPrice = BigDecimal("100"),
//                     taxRateId = null,
//                 ),
//             ),
//         )

//         val result = invoiceService.createDraft(command)

//         assertEquals(InvoiceStatus.DRAFT, result.status)
//         // 10 × 100 = 1000 subtotal; 0 tax; -50 discount → 950 total.
//         assertEquals(BigDecimal("950").setScale(6), result.totalAmount)
//         assertEquals(BigDecimal("950").setScale(6), result.outstandingAmount)
//         assertEquals(BigDecimal.ZERO, result.paidAmount)
//         verify { invoiceRepository.save(any<Invoice>()) }
//     }

//     @Test
//     fun `createDraft - rejects inactive customer with ACCOUNT_INACTIVE`() {
//         val customer = activeCustomer().also { it.isActive = false }
//         every { customerRepository.findById(customerId) } returns Optional.of(customer)

//         val ex = assertThrows<BaseAccountingException> {
//             invoiceService.createDraft(
//                 CreateInvoiceCommand(
//                     entityId = entityId, periodId = periodId, customerId = customerId,
//                     issueDate = LocalDate.now(), dueDate = LocalDate.now().plusDays(30),
//                     currencyCode = "KES", exchangeRate = BigDecimal.ONE,
//                     subtotal = BigDecimal("100"),
//                     lines = listOf(
//                         CreateInvoiceLineCommand(
//                             accountId = revenueAccountId, description = "X",
//                             quantity = BigDecimal.ONE, unitPrice = BigDecimal("100"),
//                         ),
//                     ),
//                 )
//             )
//         }
//         assertEquals("ACCOUNT_INACTIVE", ex.errorCode)
//         assertEquals(422, ex.httpStatus)
//     }

//     @Test
//     fun `approve - throws CREDIT_LIMIT_EXCEEDED when customer is over limit`() {
//         val invoice = draftInvoice(total = BigDecimal("50000"), outstanding = BigDecimal("50000"))
//         val customer = activeCustomer(creditLimit = BigDecimal("40000"))

//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)
//         every { customerRepository.findById(customerId) } returns Optional.of(customer)
//         every { invoiceRepository.sumOutstandingByCustomer(entityId, customerId) } returns BigDecimal("30000")

//         val ex = assertThrows<BaseAccountingException> { invoiceService.approve(invoice.id) }
//         assertEquals("CREDIT_LIMIT_EXCEEDED", ex.errorCode)
//         assertEquals(422, ex.httpStatus)
//     }

//     @Test
//     fun `approve - posts journal and transitions DRAFT to SENT on happy path`() {
//         val invoice = draftInvoice()
//         val customer = activeCustomer()
//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)
//         every { customerRepository.findById(customerId) } returns Optional.of(customer)
//         every { invoiceRepository.sumOutstandingByCustomer(entityId, customerId) } returns BigDecimal.ZERO

//         val journalId = UUID.randomUUID()
//         every { journalService.createEntry(any<CreateJournalEntryCommand>()) } returns
//             JournalEntry(entityId = entityId, periodId = periodId, transDate = LocalDate.now()).also {
//                 // assign an id via reflection-style copy isn't possible; use the persisted id stored in BaseFinancialEntity
//             }

//         // Substitute a return value with a fresh JournalEntry whose id we know.
//         val fakeJe = JournalEntry(entityId = entityId, periodId = periodId, transDate = LocalDate.now())
//         every { journalService.createEntry(any<CreateJournalEntryCommand>()) } returns fakeJe

//         val saved = invoiceService.approve(invoice.id)

//         verify { journalService.postEntryAsSystem(fakeJe.id) }
//         assertEquals(InvoiceStatus.SENT, saved.status)
//         assertEquals(fakeJe.id, saved.journalEntryId)
//     }

//     @Test
//     fun `applyPayment - partial payment transitions to PARTIALLY_PAID`() {
//         val invoice = draftInvoice(
//             total = BigDecimal("1000"),
//             outstanding = BigDecimal("1000"),
//             status = InvoiceStatus.SENT,
//         )
//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)

//         val newStatus = invoiceService.applyPayment(invoice.id, BigDecimal("400"))

//         assertEquals(InvoiceStatus.PARTIALLY_PAID, newStatus)
//         assertEquals(BigDecimal("400").setScale(6), invoice.paidAmount)
//         assertEquals(BigDecimal("600").setScale(6), invoice.outstandingAmount)
//     }

//     @Test
//     fun `applyPayment - completing payment transitions to PAID`() {
//         val invoice = draftInvoice(
//             total = BigDecimal("1000"),
//             outstanding = BigDecimal("400"),
//             paid = BigDecimal("600"),
//             status = InvoiceStatus.PARTIALLY_PAID,
//         )
//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)

//         val newStatus = invoiceService.applyPayment(invoice.id, BigDecimal("400"))

//         assertEquals(InvoiceStatus.PAID, newStatus)
//         assertEquals(BigDecimal("1000").setScale(6), invoice.paidAmount)
//         assertEquals(BigDecimal.ZERO.setScale(6), invoice.outstandingAmount)
//     }

//     @Test
//     fun `applyPayment - rejects overpayment with PAYMENT_EXCEEDS_INVOICE_BALANCE`() {
//         val invoice = draftInvoice(
//             total = BigDecimal("1000"),
//             outstanding = BigDecimal("1000"),
//             status = InvoiceStatus.SENT,
//         )
//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)

//         val ex = assertThrows<BaseAccountingException> {
//             invoiceService.applyPayment(invoice.id, BigDecimal("1500"))
//         }
//         assertEquals("PAYMENT_EXCEEDS_INVOICE_BALANCE", ex.errorCode)
//         assertEquals(422, ex.httpStatus)
//     }

//     @Test
//     fun `void - rejects when invoice is already APPROVED`() {
//         val invoice = draftInvoice(status = InvoiceStatus.APPROVED)
//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)

//         val ex = assertThrows<BaseAccountingException> {
//             invoiceService.void(invoice.id, "Customer cancelled")
//         }
//         assertEquals("INVOICE_VOID_AFTER_POSTING", ex.errorCode)
//         assertEquals(422, ex.httpStatus)
//     }

//     @Test
//     fun `void - transitions DRAFT invoice to VOID`() {
//         val invoice = draftInvoice(status = InvoiceStatus.DRAFT)
//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)
//         every { invoiceRepository.save(any<Invoice>()) } answers { firstArg() }

//         val result = invoiceService.void(invoice.id, "wrong customer")

//         assertEquals(InvoiceStatus.VOID, result.status)
//         verify { invoiceRepository.save(any<Invoice>()) }
//     }

//     @Test
//     fun `createCreditNote - rejects amount greater than original invoice total`() {
//         val invoice = draftInvoice(total = BigDecimal("1000"), status = InvoiceStatus.PAID)
//         every { invoiceRepository.findById(invoice.id) } returns Optional.of(invoice)

//         val ex = assertThrows<BaseAccountingException> {
//             invoiceService.createCreditNote(
//                 originalInvoiceId = invoice.id,
//                 command = CreateCreditNoteCommand(
//                     creditNoteAmount = BigDecimal("2000"),
//                     reason = "Customer return",
//                 ),
//             )
//         }
//         // Fix C6: validation now checks outstandingAmount, error code updated
//         assertEquals("CREDIT_NOTE_EXCEEDS_OUTSTANDING_BALANCE", ex.errorCode)
//         assertEquals(422, ex.httpStatus)
//     }
// }

// package com.qesuite.accounting.receipts.service

// import com.qesuite.accounting.journal.domain.JournalEntry
// import com.qesuite.accounting.journal.domain.JournalEntryStatus
// import com.qesuite.accounting.journal.repository.JournalEntryRepository
// import com.qesuite.accounting.payments.domain.Payment
// import com.qesuite.accounting.payments.domain.PaymentMethod
// import com.qesuite.accounting.payments.domain.PaymentStatus
// import com.qesuite.accounting.payments.repository.PaymentRepository
// import com.qesuite.accounting.receipts.domain.Receipt
// import com.qesuite.accounting.receipts.domain.ReceiptStatus
// import com.qesuite.accounting.receipts.repository.ReceiptRepository
// import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
// import com.qesuite.accounting.shared.exceptions.ConflictException
// import com.qesuite.accounting.shared.exceptions.ValidationException
// import io.mockk.every
// import io.mockk.mockk
// import io.mockk.slot
// import io.mockk.verify
// import org.junit.jupiter.api.Assertions.*
// import org.junit.jupiter.api.BeforeEach
// import org.junit.jupiter.api.Test
// import org.junit.jupiter.api.assertThrows
// import java.math.BigDecimal
// import java.time.LocalDate
// import java.util.Optional
// import java.util.UUID

// class ReceiptServiceTest {

//     private lateinit var receiptRepository: ReceiptRepository
//     private lateinit var paymentRepository: PaymentRepository
//     private lateinit var journalEntryRepository: JournalEntryRepository
//     private lateinit var receiptService: ReceiptService

//     private val entityId = UUID.randomUUID()
//     private val periodId = UUID.randomUUID()
//     private val customerId = UUID.randomUUID()
//     private val paymentId = UUID.randomUUID()
//     private val journalEntryId = UUID.randomUUID()

//     @BeforeEach
//     fun setup() {
//         receiptRepository = mockk(relaxed = true)
//         paymentRepository = mockk(relaxed = true)
//         journalEntryRepository = mockk(relaxed = true)

//         receiptService = ReceiptService(
//             receiptRepository = receiptRepository,
//             paymentRepository = paymentRepository,
//             journalEntryRepository = journalEntryRepository
//         )

//         every { receiptRepository.save(any<Receipt>()) } answers { firstArg() }
//     }

//     // -------------------------------------------------------------------------
//     // Helper factories
//     // -------------------------------------------------------------------------

//     private fun makePayment(
//         id: UUID = paymentId,
//         status: PaymentStatus = PaymentStatus.POSTED,
//         jeId: UUID? = journalEntryId
//     ): Payment = Payment(
//         id = id,
//         entityId = entityId,
//         periodId = periodId,
//         paymentNumber = "PAY-2026-TEST-000001",
//         customerId = customerId,
//         paymentMethod = PaymentMethod.BANK_TRANSFER,
//         paymentAmount = BigDecimal("5000.000000"),
//         currencyCode = "KES",
//         exchangeRate = BigDecimal.ONE,
//         functionalAmount = BigDecimal("5000.000000"),
//         paymentDate = LocalDate.of(2026, 5, 9),
//         status = status,
//         journalEntryId = jeId
//     )

//     private fun makeJournalEntry(
//         id: UUID = journalEntryId,
//         status: JournalEntryStatus = JournalEntryStatus.POSTED
//     ): JournalEntry {
//         val entry = JournalEntry(
//             entityId = entityId,
//             periodId = periodId,
//             transDate = LocalDate.of(2026, 5, 9),
//             description = "Payment JE",
//             status = status
//         )
//         setId(entry, id)
//         return entry
//     }

//     private fun makeReceipt(
//         id: UUID = UUID.randomUUID(),
//         status: ReceiptStatus = ReceiptStatus.POSTED
//     ): Receipt = Receipt(
//         id = id,
//         entityId = entityId,
//         periodId = periodId,
//         receiptNumber = "RCT-2026-TEST-000001",
//         paymentId = paymentId,
//         customerId = customerId,
//         receiptDate = LocalDate.of(2026, 5, 9),
//         receiptAmount = BigDecimal("5000.000000"),
//         currencyCode = "KES",
//         journalEntryId = journalEntryId,
//         status = status
//     )

//     private fun setId(entity: Any, id: java.util.UUID) {
//         var clazz: Class<*>? = entity.javaClass
//         while (clazz != null) {
//             try {
//                 val f = clazz.getDeclaredField("id")
//                 f.isAccessible = true
//                 f.set(entity, id)
//                 return
//             } catch (_: NoSuchFieldException) {
//                 clazz = clazz.superclass
//             }
//         }
//         error("Could not find 'id' field on ${entity.javaClass.name}")
//     }
//     // -------------------------------------------------------------------------
//     // TEST 1 — generateReceipt throws when payment is not POSTED
//     // -------------------------------------------------------------------------

//     @Test
//     fun `generateReceipt throws when payment is not POSTED`() {
//         val payment = makePayment(status = PaymentStatus.PENDING)
//         every { paymentRepository.findById(paymentId) } returns Optional.of(payment)

//         val ex = assertThrows<BusinessRuleViolationException> {
//             receiptService.generateReceipt(paymentId, entityId, periodId)
//         }

//         assertEquals("PAYMENT_NOT_POSTED", ex.errorCode)
//     }

//     // -------------------------------------------------------------------------
//     // TEST 2 — generateReceipt throws when journal entry is not POSTED
//     // -------------------------------------------------------------------------

//     @Test
//     fun `generateReceipt throws when journal entry is not POSTED`() {
//         val payment = makePayment(status = PaymentStatus.POSTED, jeId = journalEntryId)
//         val draftJe = makeJournalEntry(id = journalEntryId, status = JournalEntryStatus.DRAFT)

//         every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
//         every { journalEntryRepository.findById(journalEntryId) } returns Optional.of(draftJe)

//         val ex = assertThrows<BusinessRuleViolationException> {
//             receiptService.generateReceipt(paymentId, entityId, periodId)
//         }

//         assertEquals("JOURNAL_ENTRY_NOT_POSTED", ex.errorCode)
//     }

//     // -------------------------------------------------------------------------
//     // TEST 3 — generateReceipt throws when journal entry ID is null
//     // -------------------------------------------------------------------------

//     @Test
//     fun `generateReceipt throws when journal entry ID is null`() {
//         val payment = makePayment(status = PaymentStatus.POSTED, jeId = null)

//         every { paymentRepository.findById(paymentId) } returns Optional.of(payment)

//         val ex = assertThrows<BusinessRuleViolationException> {
//             receiptService.generateReceipt(paymentId, entityId, periodId)
//         }

//         assertEquals("JOURNAL_ENTRY_NOT_POSTED", ex.errorCode)
//     }

//     // -------------------------------------------------------------------------
//     // TEST 4 — generateReceipt throws ConflictException when receipt already exists
//     // -------------------------------------------------------------------------

//     @Test
//     fun `generateReceipt throws ConflictException when receipt already exists for payment`() {
//         val payment = makePayment(status = PaymentStatus.POSTED, jeId = journalEntryId)
//         val postedJe = makeJournalEntry(id = journalEntryId, status = JournalEntryStatus.POSTED)
//         val existingReceipt = makeReceipt()

//         every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
//         every { journalEntryRepository.findById(journalEntryId) } returns Optional.of(postedJe)
//         every { receiptRepository.findByPaymentId(paymentId) } returns Optional.of(existingReceipt)

//         val ex = assertThrows<ConflictException> {
//             receiptService.generateReceipt(paymentId, entityId, periodId)
//         }

//         assertEquals("DUPLICATE_RECEIPT", ex.errorCode)
//     }

//     // -------------------------------------------------------------------------
//     // TEST 5 — generateReceipt successfully creates Receipt in POSTED status
//     // -------------------------------------------------------------------------

//     @Test
//     fun `generateReceipt successfully creates Receipt in POSTED status`() {
//         val payment = makePayment(status = PaymentStatus.POSTED, jeId = journalEntryId)
//         val postedJe = makeJournalEntry(id = journalEntryId, status = JournalEntryStatus.POSTED)

//         every { paymentRepository.findById(paymentId) } returns Optional.of(payment)
//         every { journalEntryRepository.findById(journalEntryId) } returns Optional.of(postedJe)
//         every { receiptRepository.findByPaymentId(paymentId) } returns Optional.empty()
//         every { receiptRepository.countByEntityId(entityId) } returns 5L

//         val savedSlot = slot<Receipt>()
//         every { receiptRepository.save(capture(savedSlot)) } answers { firstArg() }

//         val result = receiptService.generateReceipt(
//             paymentId = paymentId,
//             entityId = entityId,
//             periodId = periodId,
//             deliveryEmail = "customer@test.com"
//         )

//         assertEquals(ReceiptStatus.POSTED, result.status)
//         assertTrue(
//             result.receiptNumber.startsWith("RCT-"),
//             "Expected receiptNumber starting with 'RCT-' but was '${result.receiptNumber}'"
//         )
//         assertEquals(journalEntryId, result.journalEntryId)
//         assertTrue(
//             result.receiptAmount.compareTo(payment.paymentAmount) == 0,
//             "Expected receiptAmount ${payment.paymentAmount} but was ${result.receiptAmount}"
//         )

//         verify { receiptRepository.save(any()) }
//     }

//     // -------------------------------------------------------------------------
//     // TEST 6 — issueReceipt transitions POSTED receipt to ISSUED and sets issuedAt
//     // -------------------------------------------------------------------------

//     @Test
//     fun `issueReceipt transitions POSTED receipt to ISSUED and sets issuedAt`() {
//         val receiptId = UUID.randomUUID()
//         val receipt = makeReceipt(id = receiptId, status = ReceiptStatus.POSTED)

//         every { receiptRepository.findById(receiptId) } returns Optional.of(receipt)

//         val savedSlot = slot<Receipt>()
//         every { receiptRepository.save(capture(savedSlot)) } answers { firstArg() }

//         val result = receiptService.issueReceipt(receiptId)

//         assertEquals(ReceiptStatus.ISSUED, result.status)
//         assertNotNull(result.issuedAt, "issuedAt should be set after issuing the receipt")
//         verify { receiptRepository.save(any()) }
//     }

//     // -------------------------------------------------------------------------
//     // TEST 7 — issueReceipt throws ValidationException when receipt is VOID
//     // -------------------------------------------------------------------------

//     @Test
//     fun `issueReceipt throws ValidationException when receipt is VOID`() {
//         val receiptId = UUID.randomUUID()
//         val receipt = makeReceipt(id = receiptId, status = ReceiptStatus.VOID)

//         every { receiptRepository.findById(receiptId) } returns Optional.of(receipt)

//         val ex = assertThrows<ValidationException> {
//             receiptService.issueReceipt(receiptId)
//         }

//         assertEquals("INVALID_STATUS_TRANSITION", ex.errorCode)
//     }

//     // -------------------------------------------------------------------------
//     // TEST 8 — voidReceipt transitions POSTED receipt to VOID and soft-deletes
//     // -------------------------------------------------------------------------

//     @Test
//     fun `voidReceipt transitions POSTED receipt to VOID and soft-deletes`() {
//         val receiptId = UUID.randomUUID()
//         val receipt = makeReceipt(id = receiptId, status = ReceiptStatus.POSTED)

//         every { receiptRepository.findById(receiptId) } returns Optional.of(receipt)

//         val savedSlot = slot<Receipt>()
//         every { receiptRepository.save(capture(savedSlot)) } answers { firstArg() }

//         val result = receiptService.voidReceipt(receiptId, "customer request")

//         assertEquals(ReceiptStatus.VOID, result.status)
//         assertFalse(result.isActive, "isActive should be false after voiding")
//         assertEquals("customer request", result.deactivationReason)
//         assertNotNull(result.deactivatedAt, "deactivatedAt should be set after voiding")
//         verify { receiptRepository.save(any()) }
//     }

//     // -------------------------------------------------------------------------
//     // TEST 9 — voidReceipt throws ValidationException when already VOID
//     // -------------------------------------------------------------------------

//     @Test
//     fun `voidReceipt throws ValidationException when already VOID`() {
//         val receiptId = UUID.randomUUID()
//         val receipt = makeReceipt(id = receiptId, status = ReceiptStatus.VOID)

//         every { receiptRepository.findById(receiptId) } returns Optional.of(receipt)

//         val ex = assertThrows<ValidationException> {
//             receiptService.voidReceipt(receiptId, "reason")
//         }

//         assertEquals("INVALID_STATUS_TRANSITION", ex.errorCode)
//     }
// }

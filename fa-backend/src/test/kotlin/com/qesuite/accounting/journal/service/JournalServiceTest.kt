package com.qesuite.accounting.journal.service

import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.journal.domain.JournalEntryLine
import com.qesuite.accounting.journal.domain.JournalEntryStatus
import com.qesuite.accounting.journal.repository.JournalEntryRepository
import com.qesuite.accounting.ledger.service.PostingService
import com.qesuite.accounting.shared.audit.repository.AuditLogRepository
import com.qesuite.accounting.shared.codegen.service.CodeGeneratorService
import com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService
import com.qesuite.accounting.shared.codegen.service.PrefixConfig
import com.qesuite.accounting.shared.exceptions.ImmutableRecordException
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

class JournalServiceTest {

    private lateinit var journalEntryRepository: JournalEntryRepository
    private lateinit var postingService: PostingService
    private lateinit var doubleEntryValidator: DoubleEntryValidator
    private lateinit var auditLogRepository: AuditLogRepository
    private lateinit var codeGeneratorService: CodeGeneratorService
    private lateinit var numberConfigService: EntityNumberConfigService
    private lateinit var journalService: JournalService

    private val entityId = UUID.randomUUID()
    private val periodId = UUID.randomUUID()
    private val accountId1 = UUID.randomUUID()
    private val accountId2 = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        journalEntryRepository = mockk(relaxed = true)
        postingService = mockk(relaxed = true)
        doubleEntryValidator = mockk(relaxed = true)
        auditLogRepository = mockk(relaxed = true)
        codeGeneratorService = mockk(relaxed = true)
        numberConfigService  = mockk(relaxed = true)
        val periodService = mockk<com.qesuite.accounting.ap.service.PeriodService>(relaxed = true)

        every { numberConfigService.resolveConfig(any(), any()) } returns PrefixConfig("JE", yearScoped = true)
        every { codeGeneratorService.nextUniqueForConfig(any(), any(), any(), any()) } returns "JE-2026-0001"

        journalService = JournalService(
            journalEntryRepository = journalEntryRepository,
            postingService = postingService,
            doubleEntryValidator = doubleEntryValidator,
            auditLogRepository = auditLogRepository,
            periodService = periodService,
            codeGeneratorService = codeGeneratorService,
            numberConfigService = numberConfigService,
        )

        // Default: save returns whatever is passed in
        every { journalEntryRepository.save(any<JournalEntry>()) } answers { firstArg() }
    }

    // -------------------------------------------------------------------------
    // Helper factories
    // -------------------------------------------------------------------------

    private fun draftEntry(
        id: UUID = UUID.randomUUID(),
        status: JournalEntryStatus = JournalEntryStatus.DRAFT,
        description: String = "Test Entry"
    ): JournalEntry {
        val entry = JournalEntry(
            entityId = entityId,
            periodId = periodId,
            transDate = LocalDate.of(2026, 5, 9),
            description = description,
            status = status
        )
        // Set id via reflection — BaseFinancialEntity declares `val id` with default
        setId(entry, id)
        return entry
    }

    private fun entryWithTwoBalancedLines(
        status: JournalEntryStatus = JournalEntryStatus.DRAFT,
        debitAmount: BigDecimal = BigDecimal("1000"),
        creditAmount: BigDecimal = BigDecimal("1000"),
        exchangeRate: BigDecimal = BigDecimal.ONE
    ): JournalEntry {
        val entry = draftEntry(status = status)
        val line1 = JournalEntryLine(
            accountId = accountId1,
            description = "Debit line",
            debitAmount = debitAmount,
            creditAmount = BigDecimal.ZERO,
            currencyCode = "KES",
            exchangeRate = exchangeRate,
            functionalDebit = debitAmount.multiply(exchangeRate),
            functionalCredit = BigDecimal.ZERO
        )
        val line2 = JournalEntryLine(
            accountId = accountId2,
            description = "Credit line",
            debitAmount = BigDecimal.ZERO,
            creditAmount = creditAmount,
            currencyCode = "KES",
            exchangeRate = exchangeRate,
            functionalDebit = BigDecimal.ZERO,
            functionalCredit = creditAmount.multiply(exchangeRate)
        )
        entry.addLine(line1)
        entry.addLine(line2)
        return entry
    }

    // -------------------------------------------------------------------------
    // TEST 1 — createEntry creates DRAFT entry and calculates functional amounts
    // -------------------------------------------------------------------------

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
    @Test
    fun `createEntry creates DRAFT entry and calculates functional amounts`() {
        val rate = BigDecimal("130")
        val amount = BigDecimal("1000")

        val command = CreateJournalEntryCommand(
            entityId = entityId,
            periodId = periodId,
            transDate = LocalDate.of(2026, 5, 9),
            description = "FX Test",
            lines = listOf(
                CreateJournalLineCommand(
                    accountId = accountId1,
                    description = "Debit line",
                    debitAmount = amount,
                    creditAmount = BigDecimal.ZERO,
                    currencyCode = "KES",
                    exchangeRate = rate
                ),
                CreateJournalLineCommand(
                    accountId = accountId2,
                    description = "Credit line",
                    debitAmount = BigDecimal.ZERO,
                    creditAmount = amount,
                    currencyCode = "KES",
                    exchangeRate = rate
                )
            )
        )

        val savedSlot = slot<JournalEntry>()
        every { journalEntryRepository.save(capture(savedSlot)) } answers { firstArg() }

        val result = journalService.createEntry(command)

        assertEquals(JournalEntryStatus.DRAFT, result.status)
        assertEquals(2, result.lines.size)

        val debitLine = result.lines.first { it.debitAmount.compareTo(BigDecimal.ZERO) != 0 }
        val creditLine = result.lines.first { it.creditAmount.compareTo(BigDecimal.ZERO) != 0 }

        // 1000 * 130 = 130000
        assertTrue(
            debitLine.functionalDebit.compareTo(BigDecimal("130000")) == 0,
            "Expected functionalDebit 130000 but was ${debitLine.functionalDebit}"
        )
        assertTrue(
            creditLine.functionalCredit.compareTo(BigDecimal("130000")) == 0,
            "Expected functionalCredit 130000 but was ${creditLine.functionalCredit}"
        )

        verify { journalEntryRepository.save(any()) }
    }

    // -------------------------------------------------------------------------
    // TEST 2 — postEntry validates double-entry and marks entry POSTED
    // -------------------------------------------------------------------------

    @Test
    fun `postEntry validates double-entry and marks entry POSTED`() {
        val entryId = UUID.randomUUID()
        // postEntry now requires PENDING_APPROVAL status (approval workflow enforcement)
        val entry = entryWithTwoBalancedLines(status = JournalEntryStatus.PENDING_APPROVAL)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(entry)
        every { doubleEntryValidator.validate(entry) } returns Unit
        every { postingService.postJournalEntry(entry) } returns Unit

        val savedSlot = slot<JournalEntry>()
        every { journalEntryRepository.save(capture(savedSlot)) } answers { firstArg() }

        journalService.postEntry(entryId)

        verify { doubleEntryValidator.validate(entry) }
        verify { postingService.postJournalEntry(entry) }
        assertEquals(JournalEntryStatus.POSTED, savedSlot.captured.status)
    }

    // -------------------------------------------------------------------------
    // TEST 3 — postEntry on ALREADY_POSTED entry throws ValidationException
    // -------------------------------------------------------------------------

    @Test
    fun `postEntry on ALREADY_POSTED entry throws ValidationException`() {
        val entryId = UUID.randomUUID()
        val postedEntry = draftEntry(id = entryId, status = JournalEntryStatus.POSTED)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(postedEntry)

        val ex = assertThrows<ValidationException> {
            journalService.postEntry(entryId)
        }

        assertEquals("ALREADY_POSTED", ex.errorCode)
        verify(exactly = 0) { doubleEntryValidator.validate(any()) }
        verify(exactly = 0) { postingService.postJournalEntry(any()) }
    }

    // -------------------------------------------------------------------------
    // TEST 4 — submitEntry transitions DRAFT to PENDING_APPROVAL
    // -------------------------------------------------------------------------

    @Test
    fun `submitEntry transitions DRAFT to PENDING_APPROVAL`() {
        val entryId = UUID.randomUUID()
        val entry = entryWithTwoBalancedLines(status = JournalEntryStatus.DRAFT)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(entry)
        every { doubleEntryValidator.validate(entry) } returns Unit

        val savedSlot = slot<JournalEntry>()
        every { journalEntryRepository.save(capture(savedSlot)) } answers { firstArg() }

        journalService.submitEntry(entryId)

        assertEquals(JournalEntryStatus.PENDING_APPROVAL, savedSlot.captured.status)
        verify { doubleEntryValidator.validate(entry) }
        verify { journalEntryRepository.save(any()) }
    }

    // -------------------------------------------------------------------------
    // TEST 5 — submitEntry on non-DRAFT entry throws ValidationException
    // -------------------------------------------------------------------------

    @Test
    fun `submitEntry on non-DRAFT entry throws ValidationException`() {
        val entryId = UUID.randomUUID()
        val entry = draftEntry(id = entryId, status = JournalEntryStatus.PENDING_APPROVAL)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(entry)

        val ex = assertThrows<ValidationException> {
            journalService.submitEntry(entryId)
        }

        assertEquals("INVALID_STATUS", ex.errorCode)
        verify(exactly = 0) { doubleEntryValidator.validate(any()) }
    }

    // -------------------------------------------------------------------------
    // TEST 6 — rejectEntry transitions PENDING_APPROVAL to DRAFT
    // -------------------------------------------------------------------------

    @Test
    fun `rejectEntry transitions PENDING_APPROVAL to DRAFT`() {
        val entryId = UUID.randomUUID()
        val entry = draftEntry(id = entryId, status = JournalEntryStatus.PENDING_APPROVAL)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(entry)

        val savedSlot = slot<JournalEntry>()
        every { journalEntryRepository.save(capture(savedSlot)) } answers { firstArg() }

        journalService.rejectEntry(entryId, "incorrect amounts")

        assertEquals(JournalEntryStatus.DRAFT, savedSlot.captured.status)
        verify { journalEntryRepository.save(any()) }
    }

    // -------------------------------------------------------------------------
    // TEST 7 — reverseEntry creates reversing JE with flipped lines
    // -------------------------------------------------------------------------

    @Test
    fun `reverseEntry creates reversing JE with flipped lines and marks original REVERSED`() {
        val entryId = UUID.randomUUID()
        val original = draftEntry(id = entryId, status = JournalEntryStatus.POSTED, description = "Original Sale")

        // line1: debit=500 credit=0
        val line1 = JournalEntryLine(
            accountId = accountId1,
            description = "AR line",
            debitAmount = BigDecimal("500"),
            creditAmount = BigDecimal.ZERO,
            currencyCode = "KES",
            exchangeRate = BigDecimal.ONE,
            functionalDebit = BigDecimal("500"),
            functionalCredit = BigDecimal.ZERO
        )
        // line2: debit=0 credit=500
        val line2 = JournalEntryLine(
            accountId = accountId2,
            description = "Revenue line",
            debitAmount = BigDecimal.ZERO,
            creditAmount = BigDecimal("500"),
            currencyCode = "KES",
            exchangeRate = BigDecimal.ONE,
            functionalDebit = BigDecimal.ZERO,
            functionalCredit = BigDecimal("500")
        )
        original.addLine(line1)
        original.addLine(line2)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(original)
        every { doubleEntryValidator.validate(any()) } returns Unit
        every { postingService.postJournalEntry(any()) } returns Unit

        // Capture all saves: first call = reversing entry save, subsequent = status updates
        val savedEntries = mutableListOf<JournalEntry>()
        every { journalEntryRepository.save(capture(savedEntries)) } answers { firstArg() }

        val result = journalService.reverseEntry(entryId)

        // IAS 8 fix: description format is "REVERSAL as of {date}: {original description}"
        assertTrue(
            result.description?.contains("REVERSAL") == true,
            "Expected description containing 'REVERSAL' but was '${result.description}'"
        )

        // Verify flipped lines on the reversing entry
        val revLine1 = result.lines.find { it.accountId == accountId1 }!!
        val revLine2 = result.lines.find { it.accountId == accountId2 }!!

        // original line1 was debit=500 → reversed should be credit=500, debit=0
        assertTrue(
            revLine1.debitAmount.compareTo(BigDecimal.ZERO) == 0,
            "Reversed line1 debitAmount should be 0 but was ${revLine1.debitAmount}"
        )
        assertTrue(
            revLine1.creditAmount.compareTo(BigDecimal("500")) == 0,
            "Reversed line1 creditAmount should be 500 but was ${revLine1.creditAmount}"
        )

        // original line2 was credit=500 → reversed should be debit=500, credit=0
        assertTrue(
            revLine2.debitAmount.compareTo(BigDecimal("500")) == 0,
            "Reversed line2 debitAmount should be 500 but was ${revLine2.debitAmount}"
        )
        assertTrue(
            revLine2.creditAmount.compareTo(BigDecimal.ZERO) == 0,
            "Reversed line2 creditAmount should be 0 but was ${revLine2.creditAmount}"
        )

        // Original entry must be marked REVERSED
        assertEquals(JournalEntryStatus.REVERSED, original.status)

        verify { doubleEntryValidator.validate(any()) }
        verify { postingService.postJournalEntry(any()) }
    }

    // -------------------------------------------------------------------------
    // TEST 8 — deleteEntry on POSTED entry throws ImmutableRecordException
    // -------------------------------------------------------------------------

    @Test
    fun `deleteEntry on POSTED entry throws ImmutableRecordException`() {
        val entryId = UUID.randomUUID()
        val postedEntry = draftEntry(id = entryId, status = JournalEntryStatus.POSTED)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(postedEntry)

        assertThrows<ImmutableRecordException> {
            journalService.deleteEntry(entryId)
        }

        verify(exactly = 0) { journalEntryRepository.delete(any<JournalEntry>()) }
    }

    // -------------------------------------------------------------------------
    // TEST 9 — updateEntry on POSTED entry throws ImmutableRecordException
    // -------------------------------------------------------------------------

    @Test
    fun `updateEntry on POSTED entry throws ImmutableRecordException`() {
        val entryId = UUID.randomUUID()
        val postedEntry = draftEntry(id = entryId, status = JournalEntryStatus.POSTED)

        every { journalEntryRepository.findById(entryId) } returns Optional.of(postedEntry)

        val command = CreateJournalEntryCommand(
            entityId = entityId,
            periodId = periodId,
            transDate = LocalDate.of(2026, 5, 9),
            description = "Updated",
            lines = listOf(
                CreateJournalLineCommand(
                    accountId = accountId1,
                    description = "Updated debit",
                    debitAmount = BigDecimal("500"),
                    creditAmount = BigDecimal.ZERO,
                    currencyCode = "KES",
                    exchangeRate = BigDecimal.ONE
                ),
                CreateJournalLineCommand(
                    accountId = accountId2,
                    description = "Updated credit",
                    debitAmount = BigDecimal.ZERO,
                    creditAmount = BigDecimal("500"),
                    currencyCode = "KES",
                    exchangeRate = BigDecimal.ONE
                )
            )
        )

        assertThrows<ImmutableRecordException> {
            journalService.updateEntry(entryId, command)
        }

        // Repository save must NOT be called for an immutable record
        verify(exactly = 0) { journalEntryRepository.save(any()) }
    }
}

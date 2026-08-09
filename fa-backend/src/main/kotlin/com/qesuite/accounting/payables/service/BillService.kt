package com.qesuite.accounting.payables.service

import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.party.repository.SupplierRepository
import com.qesuite.accounting.payables.domain.Bill
import com.qesuite.accounting.payables.domain.BillItem
import com.qesuite.accounting.payables.domain.BillPayment
import com.qesuite.accounting.payables.domain.BillStatus
import com.qesuite.accounting.payables.domain.PaymentRun
import com.qesuite.accounting.payables.repository.BillPaymentRepository
import com.qesuite.accounting.payables.repository.BillRepository
import com.qesuite.accounting.payables.repository.PaymentRunRepository
import com.qesuite.accounting.payments.domain.PaymentMethod
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.SecurityUtils
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class BillService(
    private val billRepository: BillRepository,
    private val billPaymentRepository: BillPaymentRepository,
    private val paymentRunRepository: PaymentRunRepository,
    private val journalService: JournalService,
    private val periodService: PeriodService,
    private val accountRepository: AccountRepository,
    private val supplierRepository: SupplierRepository,
) {
    private val log = LoggerFactory.getLogger(BillService::class.java)

    companion object {
        private const val SCALE = 6
        private val ROUND = RoundingMode.HALF_EVEN
        private val DUPLICATE_TOLERANCE = BigDecimal("1.00")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Create
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun createBill(request: CreateBillRequest, createdBy: UUID?): BillCreateResult {
        val seq = billRepository.countByEntityId(request.entityId) + 1L
        val billNumber = request.billNumber ?: "BILL-${Year.now().value}-${seq.toString().padStart(5, '0')}"

        if (billRepository.existsByEntityIdAndBillNumber(request.entityId, billNumber)) {
            throw ValidationException(
                errorCode = "DUPLICATE_BILL_NUMBER",
                message   = "Bill number '$billNumber' already exists for this entity.",
            )
        }

        // Auto-populate dueDate from supplier payment terms when not provided
        val resolvedDueDate = request.dueDate ?: run {
            if (request.supplierId != null) {
                val supplier = supplierRepository.findById(request.supplierId).orElse(null)
                val terms = supplier?.paymentTerms
                if (terms != null) {
                    val days = parsePaymentTermsDays(terms)
                    if (days > 0) request.billDate.plusDays(days.toLong()) else null
                } else null
            } else null
        }

        val bill = Bill(
            entityId         = request.entityId,
            periodId         = request.periodId,
            billNumber       = billNumber,
            supplierId       = request.supplierId,
            supplierName     = request.supplierName,
            billDate         = request.billDate,
            dueDate          = resolvedDueDate,
            currencyCode     = request.currencyCode ?: "KES",
            exchangeRate     = request.exchangeRate ?: BigDecimal.ONE,
            description      = request.description,
            notes            = request.notes,
            sourceDocumentId = request.sourceDocumentId,
            createdBy        = createdBy,
        )

        request.items.forEach { item ->
            val lineSubtotal = item.quantity.multiply(item.unitPrice).setScale(SCALE, ROUND)
            val taxAmt = lineSubtotal.multiply(item.taxRate ?: BigDecimal.ZERO).setScale(SCALE, ROUND)
            bill.items.add(
                BillItem(
                    bill        = bill,
                    description = item.description,
                    quantity    = item.quantity,
                    unitPrice   = item.unitPrice,
                    taxCode     = item.taxCode,
                    taxRate     = item.taxRate ?: BigDecimal.ZERO,
                    lineTotal   = lineSubtotal.add(taxAmt),
                    accountCode = item.accountCode,
                )
            )
        }

        recalcTotals(bill)
        bill.functionalAmount = bill.totalAmount.multiply(bill.exchangeRate).setScale(SCALE, ROUND)

        // Duplicate detection — warn, don't block
        val threshold = bill.totalAmount
        val duplicates = billRepository.findPotentialDuplicates(
            entityId     = request.entityId,
            supplierName = request.supplierName,
            billDate     = request.billDate,
            minAmount    = threshold.subtract(DUPLICATE_TOLERANCE),
            maxAmount    = threshold.add(DUPLICATE_TOLERANCE),
        )
        val warnings = if (duplicates.isNotEmpty()) {
            listOf("Potential duplicate: bill(s) ${duplicates.joinToString { it.billNumber }} from same supplier, date and amount already exist.")
        } else emptyList()

        val saved = billRepository.save(bill)
        log.info("bill.create id={} billNumber={} supplier={} total={} duplicateWarnings={}", saved.id, saved.billNumber, saved.supplierName, saved.totalAmount, warnings.size)
        return BillCreateResult(saved, warnings)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update (DRAFT only)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun updateBill(id: UUID, request: UpdateBillRequest, modifiedBy: UUID?): Bill {
        val bill = findById(id)
        if (bill.status != BillStatus.DRAFT) {
            throw ValidationException(
                errorCode = "BILL_NOT_EDITABLE",
                message   = "Only DRAFT bills can be edited. Current status: ${bill.status}.",
            )
        }

        request.supplierName?.let { bill.supplierName = it }
        request.billDate?.let { bill.billDate = it }
        request.description?.let { bill.description = it }
        request.notes?.let { bill.notes = it }
        request.sourceDocumentId?.let { bill.sourceDocumentId = it }

        val sid = bill.supplierId
        if (request.billDate != null && request.dueDate == null && sid != null) {
            val terms = supplierRepository.findById(sid).orElse(null)?.paymentTerms
            if (terms != null) {
                val days = parsePaymentTermsDays(terms)
                if (days > 0) bill.dueDate = bill.billDate.plusDays(days.toLong())
            }
        }
        request.dueDate?.let { bill.dueDate = it }

        if (request.items != null) {
            bill.items.clear()
            request.items.forEach { item ->
                val lineSubtotal = item.quantity.multiply(item.unitPrice).setScale(SCALE, ROUND)
                val taxAmt = lineSubtotal.multiply(item.taxRate ?: BigDecimal.ZERO).setScale(SCALE, ROUND)
                bill.items.add(
                    BillItem(
                        bill        = bill,
                        description = item.description,
                        quantity    = item.quantity,
                        unitPrice   = item.unitPrice,
                        taxCode     = item.taxCode,
                        taxRate     = item.taxRate ?: BigDecimal.ZERO,
                        lineTotal   = lineSubtotal.add(taxAmt),
                        accountCode = item.accountCode,
                    )
                )
            }
            recalcTotals(bill)
        }

        bill.modifiedBy = modifiedBy
        bill.modifiedAt = java.time.Instant.now()
        bill.functionalAmount = bill.totalAmount.multiply(bill.exchangeRate).setScale(SCALE, ROUND)

        log.info("bill.update id={}", id)
        return billRepository.save(bill)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Approve
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun approveBill(id: UUID): Bill {
        val bill = findById(id)
        if (!bill.status.canTransitionTo(BillStatus.APPROVED)) {
            throw ValidationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message   = "Cannot approve a bill in status ${bill.status}. Only DRAFT bills can be approved.",
            )
        }

        // Segregation of duties — the preparer cannot also be the approver.
        SecurityUtils.requireNotSelfApproval(bill.createdBy)

        val period = if (bill.periodId != null) {
            periodService.findById(bill.periodId!!)
        } else {
            try { periodService.findPeriodForDate(bill.entityId, bill.billDate) }
            catch (e: Exception) { throw ValidationException("NO_OPEN_PERIOD", "No open period found for bill date ${bill.billDate}.") }
        }
        bill.periodId = period.id

        val apAccountId       = resolveApAccount(bill)
        val inputVatAccountId = findInputVatAccount(bill.entityId)

        val jeLines = mutableListOf<CreateJournalLineCommand>()
        val expenseByAccount  = mutableMapOf<UUID, BigDecimal>()
        var vatTotal          = BigDecimal.ZERO

        bill.items.forEach { item ->
            val lineSubtotal = item.quantity.multiply(item.unitPrice).setScale(SCALE, ROUND)
            val lineTax      = item.lineTotal.subtract(lineSubtotal).setScale(SCALE, ROUND)
            val accountId    = resolveExpenseAccount(bill.entityId, item.accountCode)

            if (inputVatAccountId != null && lineTax.signum() > 0) {
                expenseByAccount.merge(accountId, lineSubtotal, BigDecimal::add)
                vatTotal = vatTotal.add(lineTax)
            } else {
                expenseByAccount.merge(accountId, item.lineTotal, BigDecimal::add)
            }
        }

        expenseByAccount.forEach { (accountId, amount) ->
            if (amount.signum() > 0) {
                jeLines += CreateJournalLineCommand(
                    accountId    = accountId,
                    description  = "AP Bill ${bill.billNumber} – ${bill.supplierName}",
                    debitAmount  = amount,
                    creditAmount = BigDecimal.ZERO,
                    currencyCode = bill.currencyCode,
                    exchangeRate = bill.exchangeRate,
                )
            }
        }

        if (inputVatAccountId != null && vatTotal.signum() > 0) {
            jeLines += CreateJournalLineCommand(
                accountId    = inputVatAccountId,
                description  = "AP Bill ${bill.billNumber} – Input VAT",
                debitAmount  = vatTotal,
                creditAmount = BigDecimal.ZERO,
                currencyCode = bill.currencyCode,
                exchangeRate = bill.exchangeRate,
            )
        }

        jeLines += CreateJournalLineCommand(
            accountId    = apAccountId,
            description  = "AP Bill ${bill.billNumber} – ${bill.supplierName}",
            debitAmount  = BigDecimal.ZERO,
            creditAmount = bill.totalAmount,
            currencyCode = bill.currencyCode,
            exchangeRate = bill.exchangeRate,
        )

        val je = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId    = bill.entityId,
                periodId    = period.id,
                transDate   = bill.billDate,
                description = "AP Bill ${bill.billNumber} — ${bill.supplierName}",
                sourceType  = "AP_BILL",
                sourceId    = bill.id,
                lines       = jeLines,
            )
        )
        journalService.postEntryAsSystem(je.id)

        bill.journalEntryId = je.id
        bill.status         = BillStatus.APPROVED
        log.info("bill.approve id={} je={}", id, je.id)
        return billRepository.save(bill)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Void
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun voidBill(id: UUID, reason: String?): Bill {
        val bill = findById(id)
        if (!bill.status.canTransitionTo(BillStatus.VOID)) {
            throw ValidationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message   = "Cannot void a bill in status ${bill.status}.",
            )
        }
        bill.status   = BillStatus.VOID
        bill.isActive = false
        log.info("bill.void id={} reason={}", id, reason)
        return billRepository.save(bill)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Debit Note (purchase credit note — reduces AP balance)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun createDebitNote(originalBillId: UUID, request: CreateDebitNoteRequest, createdBy: UUID?): Bill {
        val original = findById(originalBillId)

        if (original.status !in listOf(BillStatus.APPROVED, BillStatus.PARTIALLY_PAID)) {
            throw BusinessRuleViolationException(
                errorCode = "BILL_NOT_APPROVABLE_FOR_DEBIT_NOTE",
                message   = "Debit notes can only be raised against APPROVED or PARTIALLY_PAID bills. Current status: ${original.status}.",
                context   = mapOf("bill_id" to originalBillId, "status" to original.status),
            )
        }

        val outstanding = original.outstandingAmount
        val amount = (request.amount ?: outstanding).setScale(SCALE, ROUND)

        if (amount.signum() <= 0 || amount > outstanding) {
            throw ValidationException(
                errorCode = "INVALID_DEBIT_NOTE_AMOUNT",
                message   = "Debit note amount ($amount) must be > 0 and ≤ outstanding balance ($outstanding).",
            )
        }

        val issueDate = request.issueDate ?: LocalDate.now()
        val period = try {
            periodService.findPeriodForDate(original.entityId, issueDate)
        } catch (e: Exception) {
            throw ValidationException("NO_OPEN_PERIOD", "No open period found for debit note date $issueDate.")
        }

        val apAccountId = resolveApAccount(original)

        // Spread credit reversal proportionally across original expense accounts
        val expenseAccounts = buildProportionalExpenseLines(original, amount)

        val jeLines = mutableListOf<CreateJournalLineCommand>()

        // DR AP — reduces the payable
        jeLines += CreateJournalLineCommand(
            accountId    = apAccountId,
            description  = "Debit Note – Bill ${original.billNumber} – ${original.supplierName}",
            debitAmount  = amount,
            creditAmount = BigDecimal.ZERO,
            currencyCode = original.currencyCode,
            exchangeRate = original.exchangeRate,
        )

        // CR Expense — reverses the original expense recognition
        expenseAccounts.forEach { (accountId, creditAmt) ->
            if (creditAmt.signum() > 0) {
                jeLines += CreateJournalLineCommand(
                    accountId    = accountId,
                    description  = "Debit Note – Purchase Return – Bill ${original.billNumber}",
                    debitAmount  = BigDecimal.ZERO,
                    creditAmount = creditAmt,
                    currencyCode = original.currencyCode,
                    exchangeRate = original.exchangeRate,
                )
            }
        }

        val seq = billRepository.countByEntityId(original.entityId) + 1L
        val dnNumber = "DN-${Year.now().value}-${seq.toString().padStart(5, '0')}"

        val je = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId    = original.entityId,
                periodId    = period.id,
                transDate   = issueDate,
                description = "Debit Note $dnNumber – ${original.supplierName}",
                sourceType  = "AP_DEBIT_NOTE",
                sourceId    = original.id,
                lines       = jeLines,
            )
        )
        journalService.postEntryAsSystem(je.id)

        // Create the debit note bill record
        val debitNote = Bill(
            entityId       = original.entityId,
            periodId       = period.id,
            billNumber     = dnNumber,
            supplierId     = original.supplierId,
            supplierName   = original.supplierName,
            billDate       = issueDate,
            currencyCode   = original.currencyCode,
            exchangeRate   = original.exchangeRate,
            status         = BillStatus.DEBIT_NOTE,
            subtotal       = amount.negate(),
            totalAmount    = amount.negate(),
            isDebitNote    = true,
            originalBillId = originalBillId,
            journalEntryId = je.id,
            description    = request.reason ?: "Debit note against ${original.billNumber}",
            createdBy      = createdBy,
        )

        // Apply immediately: reduce the original bill's outstanding amount
        original.paidAmount = original.paidAmount.add(amount).setScale(SCALE, ROUND)
        original.status = when {
            original.paidAmount >= original.totalAmount -> BillStatus.PAID
            else -> BillStatus.PARTIALLY_PAID
        }
        billRepository.save(original)

        val saved = billRepository.save(debitNote)
        log.info("bill.debitNote id={} originalId={} amount={} je={}", saved.id, originalBillId, amount, je.id)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payments — single
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun recordPayment(billId: UUID, request: RecordBillPaymentRequest, createdBy: UUID?): BillPayment {
        val bill = findById(billId)

        if (bill.status == BillStatus.VOID) throw BusinessRuleViolationException(
            errorCode = "BILL_VOIDED",
            message   = "Cannot record payment against a voided bill.",
            context   = mapOf("bill_id" to billId),
        )
        if (bill.status == BillStatus.DRAFT) throw BusinessRuleViolationException(
            errorCode = "BILL_NOT_APPROVED",
            message   = "Bill must be approved before recording a payment.",
            context   = mapOf("bill_id" to billId, "status" to bill.status),
        )

        val maxPayable = bill.totalAmount.subtract(bill.paidAmount)
        if (request.amount > maxPayable) throw ValidationException(
            errorCode = "OVERPAYMENT",
            message   = "Payment amount ${request.amount} exceeds outstanding balance $maxPayable.",
        )

        val apAccountId   = resolveApAccount(bill)
        val cashAccountId = request.cashAccountId ?: findCashAccount(bill.entityId)

        val period = try { periodService.findPeriodForDate(bill.entityId, request.paymentDate) }
        catch (e: Exception) { throw ValidationException("NO_OPEN_PERIOD", "No open period found for payment date ${request.paymentDate}.") }

        val paymentJe = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId    = bill.entityId,
                periodId    = period.id,
                transDate   = request.paymentDate,
                description = "AP Payment – Bill ${bill.billNumber} – ${bill.supplierName}",
                sourceType  = "AP_PAYMENT",
                sourceId    = bill.id,
                lines       = listOf(
                    CreateJournalLineCommand(
                        accountId    = apAccountId,
                        description  = "AP Payment – Bill ${bill.billNumber}",
                        debitAmount  = request.amount,
                        creditAmount = BigDecimal.ZERO,
                        currencyCode = bill.currencyCode,
                        exchangeRate = bill.exchangeRate,
                    ),
                    CreateJournalLineCommand(
                        accountId    = cashAccountId,
                        description  = "AP Payment – Bill ${bill.billNumber} – ${bill.supplierName}",
                        debitAmount  = BigDecimal.ZERO,
                        creditAmount = request.amount,
                        currencyCode = bill.currencyCode,
                        exchangeRate = bill.exchangeRate,
                    ),
                ),
            )
        )
        journalService.postEntryAsSystem(paymentJe.id)

        val payment = BillPayment(
            entityId       = bill.entityId,
            billId         = billId,
            paymentDate    = request.paymentDate,
            amount         = request.amount,
            currencyCode   = request.currencyCode ?: bill.currencyCode,
            reference      = request.reference,
            notes          = request.notes,
            paymentMethod  = request.paymentMethod,
            cashAccountId  = cashAccountId,
            journalEntryId = paymentJe.id,
            createdBy      = createdBy,
        )
        billPaymentRepository.save(payment)

        bill.paidAmount = bill.paidAmount.add(request.amount)
        bill.status     = if (bill.paidAmount >= bill.totalAmount) BillStatus.PAID else BillStatus.PARTIALLY_PAID
        billRepository.save(bill)

        log.info("bill.payment billId={} amount={} jeId={} status={}", billId, request.amount, paymentJe.id, bill.status)
        return payment
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Payment Run — batch payment of multiple bills in one journal entry
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    fun processPaymentRun(request: PaymentRunRequest, createdBy: UUID?): PaymentRun {
        val bills = billRepository.findAllByIdIn(request.billIds)
            .filter { it.entityId == request.entityId && it.status in listOf(BillStatus.APPROVED, BillStatus.PARTIALLY_PAID) }

        if (bills.isEmpty()) throw ValidationException(
            errorCode = "NO_PAYABLE_BILLS",
            message   = "None of the specified bill IDs are payable (must be APPROVED or PARTIALLY_PAID) within entity ${request.entityId}.",
        )

        val cashAccountId = request.cashAccountId ?: findCashAccount(request.entityId)
        val period = try { periodService.findPeriodForDate(request.entityId, request.paymentDate) }
        catch (e: Exception) { throw ValidationException("NO_OPEN_PERIOD", "No open period found for payment date ${request.paymentDate}.") }

        val jeLines = mutableListOf<CreateJournalLineCommand>()
        var totalFunctional = BigDecimal.ZERO

        // DR Accounts Payable — one line per bill
        bills.forEach { bill ->
            val apAccountId = resolveApAccount(bill)
            val outstanding = bill.outstandingAmount
            jeLines += CreateJournalLineCommand(
                accountId    = apAccountId,
                description  = "Payment Run – Bill ${bill.billNumber} – ${bill.supplierName}",
                debitAmount  = outstanding,
                creditAmount = BigDecimal.ZERO,
                currencyCode = bill.currencyCode,
                exchangeRate = bill.exchangeRate,
            )
            totalFunctional = totalFunctional.add(outstanding.multiply(bill.exchangeRate)).setScale(SCALE, ROUND)
        }

        // CR Cash/Bank — one consolidated line in functional currency
        jeLines += CreateJournalLineCommand(
            accountId    = cashAccountId,
            description  = "Payment Run – ${bills.size} bills – ${request.paymentDate}",
            debitAmount  = BigDecimal.ZERO,
            creditAmount = totalFunctional,
            currencyCode = "KES",
            exchangeRate = BigDecimal.ONE,
        )

        val je = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId    = request.entityId,
                periodId    = period.id,
                transDate   = request.paymentDate,
                description = "Vendor Payment Run – ${bills.size} bills – ${request.reference ?: request.paymentDate}",
                sourceType  = "AP_PAYMENT_RUN",
                sourceId    = null,
                lines       = jeLines,
            )
        )
        journalService.postEntryAsSystem(je.id)

        val run = paymentRunRepository.save(
            PaymentRun(
                entityId       = request.entityId,
                paymentDate    = request.paymentDate,
                paymentMethod  = request.paymentMethod,
                cashAccountId  = cashAccountId,
                totalAmount    = totalFunctional,
                billCount      = bills.size,
                journalEntryId = je.id,
                reference      = request.reference,
                notes          = request.notes,
                createdBy      = createdBy,
            )
        )

        bills.forEach { bill ->
            val payment = BillPayment(
                entityId       = bill.entityId,
                billId         = bill.id,
                paymentDate    = request.paymentDate,
                amount         = bill.outstandingAmount,
                currencyCode   = bill.currencyCode,
                reference      = request.reference,
                paymentMethod  = request.paymentMethod,
                cashAccountId  = cashAccountId,
                paymentRunId   = run.id,
                journalEntryId = je.id,
                createdBy      = createdBy,
            )
            billPaymentRepository.save(payment)

            bill.paidAmount = bill.totalAmount  // fully paid
            bill.status     = BillStatus.PAID
            billRepository.save(bill)
        }

        log.info("payment.run id={} bills={} total={} je={}", run.id, bills.size, totalFunctional, je.id)
        return run
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun findById(id: UUID): Bill = billRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("BILL_NOT_FOUND", id, "Bill") }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, status: BillStatus?, page: Int, size: Int): Page<Bill> {
        val pageable = PageRequest.of(page, size, Sort.by("billDate").descending())
        return if (status != null)
            billRepository.findByEntityIdAndStatusAndIsActiveTrueOrderByBillDateDesc(entityId, status, pageable)
        else
            billRepository.findByEntityIdAndIsActiveTrueOrderByBillDateDesc(entityId, pageable)
    }

    @Transactional(readOnly = true)
    fun getPayments(billId: UUID): List<BillPayment> = billPaymentRepository.findByBillId(billId)

    @Transactional(readOnly = true)
    fun listPaymentRuns(entityId: UUID): List<PaymentRun> =
        paymentRunRepository.findByEntityIdOrderByPaymentDateDesc(entityId)

    // ─────────────────────────────────────────────────────────────────────────
    // AP Ageing
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun getApAgeing(entityId: UUID): ApAgeingReport {
        val today       = LocalDate.now()
        val outstanding = billRepository.findOutstanding(entityId)

        data class Bucket(var amount: BigDecimal = BigDecimal.ZERO, val bills: MutableList<ApAgeingLine> = mutableListOf())

        val current = Bucket(); val b1to30 = Bucket(); val b31to60 = Bucket()
        val b61to90 = Bucket(); val b90plus = Bucket()

        outstanding.forEach { bill ->
            val amt        = bill.outstandingAmount
            val daysOverdue = if (bill.dueDate != null) ChronoUnit.DAYS.between(bill.dueDate, today).toInt() else 0
            val line = ApAgeingLine(
                billId       = bill.id,
                billNumber   = bill.billNumber,
                supplierName = bill.supplierName,
                billDate     = bill.billDate,
                dueDate      = bill.dueDate,
                totalAmount  = bill.totalAmount,
                paidAmount   = bill.paidAmount,
                outstanding  = amt,
                daysOverdue  = daysOverdue.coerceAtLeast(0),
                currencyCode = bill.currencyCode,
            )
            when {
                daysOverdue <= 0  -> { current.amount = current.amount.add(amt); current.bills.add(line) }
                daysOverdue <= 30 -> { b1to30.amount  = b1to30.amount.add(amt);  b1to30.bills.add(line) }
                daysOverdue <= 60 -> { b31to60.amount = b31to60.amount.add(amt); b31to60.bills.add(line) }
                daysOverdue <= 90 -> { b61to90.amount = b61to90.amount.add(amt); b61to90.bills.add(line) }
                else              -> { b90plus.amount = b90plus.amount.add(amt); b90plus.bills.add(line) }
            }
        }

        val grandTotal = outstanding.sumOf { it.outstandingAmount }
        return ApAgeingReport(
            asOfDate   = today,
            grandTotal = grandTotal,
            current    = AgeingBucket("Current / Not Yet Due", current.amount, current.bills),
            days1to30  = AgeingBucket("1–30 Days",             b1to30.amount,  b1to30.bills),
            days31to60 = AgeingBucket("31–60 Days",            b31to60.amount, b31to60.bills),
            days61to90 = AgeingBucket("61–90 Days",            b61to90.amount, b61to90.bills),
            days90plus = AgeingBucket("90+ Days",              b90plus.amount, b90plus.bills),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Account resolution helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun resolveApAccount(bill: Bill): UUID {
        val supplierId = bill.supplierId
        if (supplierId != null) {
            val apId = supplierRepository.findById(supplierId).orElse(null)?.defaultApAccountId
            if (apId != null) return apId
        }
        return accountRepository.findAllByEntityIdAndAccountSubtype(bill.entityId, AccountSubtype.CURRENT_PAYABLE)
            .filter { !it.isHeader }
            .minByOrNull { it.accountCode }?.id
            ?: throw BusinessRuleViolationException(
                errorCode = "MISSING_AP_ACCOUNT",
                message   = "No CURRENT_PAYABLE account found for entity ${bill.entityId}. Configure an Accounts Payable account in the Chart of Accounts.",
                context   = mapOf("entity_id" to bill.entityId),
            )
    }

    private fun resolveExpenseAccount(entityId: UUID, accountCode: String?): UUID {
        if (!accountCode.isNullOrBlank()) {
            val found = accountRepository.findByEntityIdAndAccountCode(entityId, accountCode).orElse(null)
            if (found != null && found.isActive) return found.id
        }
        return accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.OPERATING_EXPENSES)
            .filter { !it.isHeader }
            .minByOrNull { it.accountCode }?.id
            ?: throw BusinessRuleViolationException(
                errorCode = "MISSING_EXPENSE_ACCOUNT",
                message   = "No OPERATING_EXPENSES account found for entity $entityId. Configure an expense account in the Chart of Accounts.",
                context   = mapOf("entity_id" to entityId),
            )
    }

    private fun findInputVatAccount(entityId: UUID): UUID? =
        accountRepository.findAllByEntityId(entityId).filter { !it.isHeader }.firstOrNull { acct ->
            acct.isActive && !acct.isHeader &&
                acct.accountSubtype in listOf(AccountSubtype.CURRENT_PREPAID, AccountSubtype.CURRENT_RECEIVABLE) &&
                (acct.accountName.contains("VAT", ignoreCase = true) ||
                    acct.accountName.contains("Input Tax", ignoreCase = true) ||
                    acct.accountName.contains("Tax Recoverable", ignoreCase = true))
        }?.id

    private fun findCashAccount(entityId: UUID): UUID =
        accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CASH_AND_EQUIVALENTS)
            .filter { !it.isHeader }
            .minByOrNull { it.accountCode }?.id
            ?: throw BusinessRuleViolationException(
                errorCode = "MISSING_CASH_ACCOUNT",
                message   = "No CASH_AND_EQUIVALENTS account found for entity $entityId.",
                context   = mapOf("entity_id" to entityId),
            )

    private fun buildProportionalExpenseLines(original: Bill, creditAmount: BigDecimal): Map<UUID, BigDecimal> {
        val totalSubtotal = original.subtotal.takeIf { it.signum() > 0 } ?: BigDecimal.ONE
        val result = mutableMapOf<UUID, BigDecimal>()
        val lines = original.items.toList()

        lines.forEachIndexed { idx, item ->
            val accountId  = resolveExpenseAccount(original.entityId, item.accountCode)
            val proportion = item.quantity.multiply(item.unitPrice).divide(totalSubtotal, 12, ROUND)
            val share      = if (idx == lines.lastIndex) {
                // Last line absorbs rounding remainder
                creditAmount.subtract(result.values.fold(BigDecimal.ZERO, BigDecimal::add))
            } else {
                creditAmount.multiply(proportion).setScale(SCALE, ROUND)
            }
            result.merge(accountId, share, BigDecimal::add)
        }

        if (result.isEmpty()) {
            // No items — fall back to a single OPERATING_EXPENSES line
            result[resolveExpenseAccount(original.entityId, null)] = creditAmount
        }

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private fun recalcTotals(bill: Bill) {
        bill.subtotal    = bill.items.sumOf { it.quantity.multiply(it.unitPrice) }.setScale(SCALE, ROUND)
        bill.taxAmount   = bill.items.sumOf { it.lineTotal.subtract(it.quantity.multiply(it.unitPrice)) }.setScale(SCALE, ROUND)
        bill.totalAmount = bill.subtotal.add(bill.taxAmount)
    }

    private fun parsePaymentTermsDays(terms: String): Int {
        val cleaned = terms.uppercase().replace("_", "").replace(" ", "")
        return Regex("NET(\\d+)").find(cleaned)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Request / Response DTOs
// ─────────────────────────────────────────────────────────────────────────────

data class BillCreateResult(
    val bill: Bill,
    val warnings: List<String> = emptyList(),
)

data class CreateBillRequest(
    val entityId: UUID,
    val periodId: UUID? = null,
    val billNumber: String? = null,
    val supplierId: UUID? = null,
    val supplierName: String,
    val billDate: LocalDate,
    val dueDate: LocalDate? = null,
    val currencyCode: String? = null,
    val exchangeRate: BigDecimal? = null,
    val description: String? = null,
    val notes: String? = null,
    val sourceDocumentId: UUID? = null,
    val items: List<BillItemRequest> = emptyList(),
)

data class UpdateBillRequest(
    val supplierName: String? = null,
    val billDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val description: String? = null,
    val notes: String? = null,
    val sourceDocumentId: UUID? = null,
    val items: List<BillItemRequest>? = null,
)

data class BillItemRequest(
    val description: String,
    val quantity: BigDecimal = BigDecimal.ONE,
    val unitPrice: BigDecimal,
    val taxCode: String? = null,
    val taxRate: BigDecimal? = null,
    val accountCode: String? = null,
)

data class RecordBillPaymentRequest(
    val paymentDate: LocalDate,
    val amount: BigDecimal,
    val currencyCode: String? = null,
    val reference: String? = null,
    val notes: String? = null,
    val paymentMethod: PaymentMethod? = null,
    val cashAccountId: UUID? = null,
)

data class CreateDebitNoteRequest(
    val amount: BigDecimal? = null,       // null = full outstanding balance
    val issueDate: LocalDate? = null,     // null = today
    val reason: String? = null,
)

data class PaymentRunRequest(
    val entityId: UUID,
    val billIds: List<UUID>,
    val paymentDate: LocalDate,
    val paymentMethod: PaymentMethod? = null,
    val cashAccountId: UUID? = null,
    val reference: String? = null,
    val notes: String? = null,
)

data class ApAgeingReport(
    val asOfDate: LocalDate,
    val grandTotal: BigDecimal,
    val current: AgeingBucket,
    val days1to30: AgeingBucket,
    val days31to60: AgeingBucket,
    val days61to90: AgeingBucket,
    val days90plus: AgeingBucket,
)

data class AgeingBucket(
    val label: String,
    val total: BigDecimal,
    val bills: List<ApAgeingLine>,
)

data class ApAgeingLine(
    val billId: UUID,
    val billNumber: String,
    val supplierName: String,
    val billDate: LocalDate,
    val dueDate: LocalDate?,
    val totalAmount: BigDecimal,
    val paidAmount: BigDecimal,
    val outstanding: BigDecimal,
    val daysOverdue: Int,
    val currencyCode: String,
)

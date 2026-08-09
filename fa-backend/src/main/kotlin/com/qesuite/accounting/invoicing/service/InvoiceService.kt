package com.qesuite.accounting.invoicing.service

import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.domain.InvoiceLine
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.domain.PerformanceObligationType
import com.qesuite.accounting.invoicing.dto.AgingBucketResponse
import com.qesuite.accounting.invoicing.dto.ArAgeingResponse
import com.qesuite.accounting.invoicing.dto.CreateCreditNoteCommand
import com.qesuite.accounting.invoicing.dto.CreateInvoiceCommand
import com.qesuite.accounting.invoicing.repository.InvoiceRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.party.repository.CustomerRepository
import com.qesuite.accounting.shared.audit.annotation.AuditEntityId
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.SecurityUtils
import com.qesuite.accounting.tax.service.TaxService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * §14.1, §14.2, §14.4 — Invoice Service.
 *
 * Owns the full invoice lifecycle:
 *   DRAFT → APPROVED → SENT → PARTIALLY_PAID → PAID  (or → VOID / CREDIT_NOTE)
 *
 * Cross-module collaborators (Rule 11 — only via Spring service interfaces):
 *   • [JournalService] — auto-posts the AR journal on approve / credit-note.
 *   • [TaxService]     — line-level tax computation by tax_rate_id (§13.1).
 *   • [Ifrs15RecognitionService] — IFRS 15 5-step revenue split.
 *   • [CustomerRepository] — credit-limit and AR account lookup.
 *
 * Money discipline (Rule 01): every monetary calculation goes through `BigDecimal` and
 * is rounded with `HALF_EVEN` to scale 6.
 */
@Service
@Transactional
class InvoiceService(
    private val invoiceRepository: InvoiceRepository,
    private val customerRepository: CustomerRepository,
    private val accountRepository: AccountRepository,
    private val journalService: JournalService,
    private val taxService: TaxService,
    private val ifrs15Service: Ifrs15RecognitionService,
    private val codeGeneratorService: com.qesuite.accounting.shared.codegen.service.CodeGeneratorService,
    private val entityNumberConfigService: com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService,
) {

    private companion object {
        const val MONEY_SCALE = 6
        val ROUND = RoundingMode.HALF_EVEN
    }

    /**
     * §14.1 — Create an invoice in DRAFT status. No journal entry is posted here; that
     * is deferred to [approve]. Validates customer exists & is active, generates a
     * unique invoice number, computes line totals via [TaxService], and persists.
     */
    @Auditable(action = AuditAction.CREATE, resourceType = "INVOICE")
    fun createDraft(command: CreateInvoiceCommand): Invoice {
        // §14.3 — Customer must exist and be active.
        val customer = customerRepository.findById(command.customerId)
            .orElseThrow { ResourceNotFoundException("CUSTOMER_NOT_FOUND", command.customerId, "Customer") }

        if (!customer.isActive) {
            throw BusinessRuleViolationException(
                errorCode = "ACCOUNT_INACTIVE",
                message = "Customer ${customer.customerCode} is inactive and cannot receive invoices.",
                context = mapOf("customer_id" to customer.id),
            )
        }

        // §14.1 — Allocate a unique invoice number using the entity's configured format.
        val invConfig = entityNumberConfigService.resolveConfig(command.entityId, "SALES_INVOICE")
        val invoiceNumber = codeGeneratorService.nextUnique(
            command.entityId, invConfig.prefix, invConfig.yearScoped,
            customFormat = invConfig.customFormat,
        ) { !invoiceRepository.existsByEntityIdAndInvoiceNumber(command.entityId, it) }

        // §14.1 — Aggregate line subtotals + tax.
        // Line-level discountPercent reduces lineSubtotal before tax is applied and before
        // lineTotal is stored. This keeps the approve() journal balanced because revenue is
        // credited at the net lineTotal and AR is debited at totalAmount (also net).
        // The invoice-level discountAmount is reserved for any header-level discount posted
        // as a separate Sales Discount debit in the AR journal.
        var computedSubtotal = BigDecimal.ZERO
        var computedTax = BigDecimal.ZERO
        val lines = command.lines.mapIndexed { idx, lineCmd ->
            val lineGross = lineCmd.quantity.multiply(lineCmd.unitPrice).setScale(MONEY_SCALE, ROUND)
            val lineDiscount = if (lineCmd.discountPercent.signum() > 0)
                lineGross.multiply(lineCmd.discountPercent).divide(BigDecimal("100"), MONEY_SCALE, ROUND)
            else BigDecimal.ZERO.setScale(MONEY_SCALE, ROUND)
            val lineSubtotal = lineGross.subtract(lineDiscount).setScale(MONEY_SCALE, ROUND)
            val (lineTax, resolvedTaxRateId) = taxService.resolveLineTax(
                entityId   = command.entityId,
                taxRateId  = lineCmd.taxRateId,
                taxCodeId  = lineCmd.taxCodeId,
                baseAmount = lineSubtotal,
                invoiceDate = command.issueDate
            )
            val lineTaxScaled = lineTax.setScale(MONEY_SCALE, ROUND)
            val lineTotal = lineSubtotal.add(lineTaxScaled).setScale(MONEY_SCALE, ROUND)

            computedSubtotal = computedSubtotal.add(lineSubtotal)
            computedTax = computedTax.add(lineTaxScaled)

            InvoiceLine(
                lineNumber = if (lineCmd.lineNumber > 0) lineCmd.lineNumber else idx + 1,
                accountId = lineCmd.accountId,
                description = lineCmd.description,
                quantity = lineCmd.quantity,
                unitPrice = lineCmd.unitPrice,
                taxRateId = resolvedTaxRateId,
                recognitionType = lineCmd.recognitionType,
                lineSubtotal = lineSubtotal,
                lineTax = lineTaxScaled,
                lineTotal = lineTotal,
            )
        }

        // Header discount is subtracted from the net total and posted as a separate
        // Sales Discount debit in approve(). Line discounts are already inside computedSubtotal.
        val discountAmount = command.discountAmount.setScale(MONEY_SCALE, ROUND)
        val totalAmount = computedSubtotal.add(computedTax).subtract(discountAmount).setScale(MONEY_SCALE, ROUND)

        // §14.1 — Persist the invoice with its lines (cascade).
        val invoice = Invoice(
            entityId = command.entityId,
            periodId = command.periodId,
            invoiceNumber = invoiceNumber,
            customerId = command.customerId,
            issueDate = command.issueDate,
            dueDate = command.dueDate,
            currencyCode = command.currencyCode,
            exchangeRate = command.exchangeRate,
            subtotal = computedSubtotal,
            taxAmount = computedTax,
            discountAmount = discountAmount,
            totalAmount = totalAmount,
            outstandingAmount = totalAmount,
            notes = command.notes,
        )
        lines.forEach(invoice::addLine)
        return invoiceRepository.save(invoice)
    }

    /**
     * §14.2, §14.4 — Approve a DRAFT invoice: enforce the credit limit, post the AR
     * journal entry through [JournalService] (where double-entry validation occurs —
     * Rule 09), link the resulting `journalEntryId`, and transition DRAFT → APPROVED → SENT.
     */
    @Auditable(action = AuditAction.POST, resourceType = "INVOICE")
    fun approve(@AuditResourceId invoiceId: UUID): Invoice {
        val invoice = findById(invoiceId)
        if (invoice.status != InvoiceStatus.DRAFT) {
            throw BusinessRuleViolationException(
                errorCode = "INVOICE_NOT_APPROVABLE",
                message = "Invoice must be in DRAFT status to be approved (current: ${invoice.status}).",
                context = mapOf("invoice_id" to invoiceId, "current_status" to invoice.status.name),
            )
        }

        // Segregation of duties — the preparer cannot also be the approver.
        SecurityUtils.requireNotSelfApproval(invoice.createdBy)

        // §14.3 — Credit-limit enforcement (Rule §6.6 — `CREDIT_LIMIT_EXCEEDED`).
        val customer = customerRepository.findById(invoice.customerId)
            .orElseThrow { ResourceNotFoundException("CUSTOMER_NOT_FOUND", invoice.customerId, "Customer") }
        val arAccount = customer.defaultArAccountId
            ?: accountRepository.findAllByEntityIdAndAccountSubtype(invoice.entityId, AccountSubtype.CURRENT_RECEIVABLE)
                .filter { !it.isHeader }
                .minByOrNull { it.accountCode }?.id
            ?: throw BusinessRuleViolationException(
                errorCode = "AR_ACCOUNT_NOT_FOUND",
                message = "No Accounts Receivable account found. Add a CURRENT_RECEIVABLE account to your chart of accounts.",
                context = mapOf("entity_id" to invoice.entityId),
            )

        val currentExposure = invoiceRepository.sumOutstandingByCustomer(invoice.entityId, invoice.customerId)
        val projectedExposure = currentExposure.add(invoice.totalAmount)
        if (projectedExposure.compareTo(customer.creditLimit) > 0) {
            throw BusinessRuleViolationException(
                errorCode = "CREDIT_LIMIT_EXCEEDED",
                message = "Approving this invoice would breach the customer's credit limit.",
                context = mapOf(
                    "customer_id" to invoice.customerId,
                    "credit_limit" to customer.creditLimit,
                    "current_exposure" to currentExposure,
                    "invoice_amount" to invoice.totalAmount,
                    "projected_exposure" to projectedExposure,
                ),
            )
        }

        // §14.4, §11.2 — IFRS 15: split each line into POINT_IN_TIME (revenue) vs OVER_TIME
        // (deferred revenue). The OVER_TIME amount is held as a contract liability until
        // the period-end revenue-recognition job satisfies the obligation.
        val recognitionPerLine = ifrs15Service.computeRecognition(invoice, invoice.lines)

        val jeLines = mutableListOf<CreateJournalLineCommand>()

        // §14.4 — Debit AR (totalAmount).
        jeLines += CreateJournalLineCommand(
            accountId = arAccount,
            description = "Invoice ${invoice.invoiceNumber} - AR",
            debitAmount = invoice.totalAmount,
            creditAmount = BigDecimal.ZERO,
            currencyCode = invoice.currencyCode,
            exchangeRate = invoice.exchangeRate,
        )

        // §14.4, C2 — Credit revenue / deferred revenue per line (IFRS 15).
        // OVER_TIME: full line amount → Deferred Revenue (liability); recognised = 0 per IFRS 15.
        // POINT_IN_TIME: full line amount → Revenue account immediately.
        invoice.lines.forEach { line ->
            val isDeferred = line.recognitionType == PerformanceObligationType.OVER_TIME
            val creditTarget = if (isDeferred) {
                // Find the CURRENT_DEFERRED_REVENUE liability account for this entity.
                accountRepository.findAllByEntityId(invoice.entityId)
                    .firstOrNull {
                        it.accountSubtype == com.qesuite.accounting.coa.domain.AccountSubtype.CURRENT_DEFERRED_REVENUE
                            && it.isActive
                    }
                    ?.id ?: throw BusinessRuleViolationException(
                        errorCode = "MISSING_DEFERRED_REVENUE_ACCOUNT",
                        message = "Invoice has OVER_TIME lines but no CURRENT_DEFERRED_REVENUE account exists for entity ${invoice.entityId}.",
                        context = mapOf("entity_id" to invoice.entityId),
                    )
            } else {
                line.accountId  // revenue account
            }
            val creditAmt = line.lineTotal.setScale(MONEY_SCALE, ROUND)
            jeLines += CreateJournalLineCommand(
                accountId    = creditTarget,
                description  = if (isDeferred)
                    "Invoice ${invoice.invoiceNumber} – Deferred Revenue – L${line.lineNumber}: ${line.description}"
                else
                    "Invoice ${invoice.invoiceNumber} – Revenue – L${line.lineNumber}: ${line.description}",
                debitAmount  = BigDecimal.ZERO,
                creditAmount = creditAmt,
                currencyCode = invoice.currencyCode,
                exchangeRate = invoice.exchangeRate,
            )
        }

        // §14.4, C1 — Sales Discount debit line (contra-revenue) to balance the JE.
        // DR AR = totalAmount (net of discount)
        // DR Sales Discount = discountAmount
        // CR Revenue/Deferred lines = sum of line totals (subtotal + tax, before discount)
        if (invoice.discountAmount.signum() > 0) {
            val discountAccountId = accountRepository.findAllByEntityId(invoice.entityId)
                .firstOrNull {
                    it.accountSubtype == com.qesuite.accounting.coa.domain.AccountSubtype.OPERATING_EXPENSES
                        && it.isActive
                }
                ?.id ?: throw BusinessRuleViolationException(
                    errorCode = "MISSING_DISCOUNT_ACCOUNT",
                    message = "No active OPERATING_EXPENSES account found to book sales discount for entity ${invoice.entityId}.",
                    context = mapOf("entity_id" to invoice.entityId),
                )
            jeLines += CreateJournalLineCommand(
                accountId    = discountAccountId,
                description  = "Invoice ${invoice.invoiceNumber} – Sales Discount",
                debitAmount  = invoice.discountAmount.setScale(MONEY_SCALE, ROUND),
                creditAmount = BigDecimal.ZERO,
                currencyCode = invoice.currencyCode,
                exchangeRate = invoice.exchangeRate,
            )
        }

        // §14.4, §4.1 — Create + post journal entry.
        val journalEntry = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId = invoice.entityId,
                periodId = invoice.periodId
                    ?: throw BusinessRuleViolationException(
                        errorCode = "PERIOD_ID_MISSING",
                        message = "Invoice ${invoice.invoiceNumber} has no periodId.",
                        context = mapOf("invoice_id" to invoiceId),
                    ),
                transDate = invoice.issueDate,
                description = "Sales Invoice ${invoice.invoiceNumber} – ${customer.name}",
                sourceType = "INVOICE",
                sourceId = invoice.id,
                lines = jeLines,
            )
        )
        journalService.postEntryAsSystem(journalEntry.id)

        invoice.journalEntryId = journalEntry.id
        invoice.status         = InvoiceStatus.APPROVED
        val approved = invoiceRepository.save(invoice)   // persist APPROVED state

        // §14.2 — Auto-progress to SENT once approved (delivery channel handled separately).
        approved.status = InvoiceStatus.SENT
        return invoiceRepository.save(approved)
    }

    /**
     * §14.2 — Apply an external payment to an invoice. Updates `paidAmount` /
     * `outstandingAmount` and transitions to PARTIALLY_PAID or PAID. Called from M14
     * (Payments) once the payment journal has posted. Returns the new status.
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "INVOICE")
    fun applyPayment(@AuditResourceId invoiceId: UUID, paymentAmount: BigDecimal): Invoice {
        val invoice = findById(invoiceId)
        val payment = paymentAmount.setScale(MONEY_SCALE, ROUND)

        if (payment.signum() <= 0) {
            throw ValidationException(
                errorCode = "INVALID_AMOUNT",
                message = "Payment amount must be > 0.",
                context = mapOf("payment_amount" to payment),
            )
        }
        if (payment.compareTo(invoice.outstandingAmount) > 0) {
            throw BusinessRuleViolationException(
                errorCode = "PAYMENT_EXCEEDS_INVOICE_BALANCE",
                message = "Payment exceeds outstanding balance.",
                context = mapOf(
                    "invoice_id" to invoiceId,
                    "outstanding_balance" to invoice.outstandingAmount,
                    "payment_amount" to payment,
                ),
            )
        }

        invoice.paidAmount = invoice.paidAmount.add(payment).setScale(MONEY_SCALE, ROUND)
        invoice.outstandingAmount = invoice.totalAmount.subtract(invoice.paidAmount).setScale(MONEY_SCALE, ROUND)

        invoice.status = when {
            invoice.outstandingAmount.signum() == 0 -> InvoiceStatus.PAID
            invoice.paidAmount.signum() > 0 -> InvoiceStatus.PARTIALLY_PAID
            else -> invoice.status
        }
        return invoiceRepository.save(invoice)
    }

    /**
     * §14.2 — Void: only permitted while DRAFT. Posted invoices must use a credit note
     * (see [createCreditNote]) to preserve the audit trail.
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "INVOICE")
    fun void(@AuditResourceId invoiceId: UUID, reason: String): Invoice {
        val invoice = findById(invoiceId)
        if (invoice.status != InvoiceStatus.DRAFT) {
            throw BusinessRuleViolationException(
                errorCode = "INVOICE_VOID_AFTER_POSTING",
                message = "Cannot void a ${invoice.status} invoice. Use a credit note instead.",
                context = mapOf("invoice_id" to invoiceId, "current_status" to invoice.status.name),
            )
        }
        invoice.status            = InvoiceStatus.VOID
        invoice.isActive          = false
        invoice.deactivatedAt     = java.time.Instant.now()
        invoice.deactivationReason = reason
        return invoiceRepository.save(invoice)
    }

    /**
     * §14.2 — Issue a credit note against an existing posted invoice. The credit-note
     * row uses NEGATIVE amounts (subtotal/tax/total) — the V14 migration's CHECK
     * constraints permit negatives when status = `CREDIT_NOTE`.
     */
    @Auditable(action = AuditAction.CREATE, resourceType = "INVOICE")
    fun createCreditNote(
        @AuditResourceId originalInvoiceId: UUID,
        command: CreateCreditNoteCommand,
    ): Invoice {
        val original = findById(originalInvoiceId)
        if (original.status !in setOf(
                InvoiceStatus.APPROVED,
                InvoiceStatus.SENT,
                InvoiceStatus.PARTIALLY_PAID,
                InvoiceStatus.PAID,
            )
        ) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATE_TRANSITION",
                message = "Credit notes are only valid against posted invoices.",
                context = mapOf("invoice_id" to originalInvoiceId, "current_status" to original.status.name),
            )
        }

        val creditAmount = command.creditNoteAmount.setScale(MONEY_SCALE, ROUND)
        // C6 — validate against outstandingAmount, not totalAmount (partial payments reduce what can be credited).
        if (creditAmount.compareTo(original.outstandingAmount) > 0) {
            throw ValidationException(
                errorCode  = "CREDIT_NOTE_EXCEEDS_OUTSTANDING_BALANCE",
                message    = "Credit note amount ($creditAmount) exceeds invoice outstanding balance (${original.outstandingAmount}).",
                context    = mapOf(
                    "invoice_id"          to originalInvoiceId,
                    "outstanding_balance" to original.outstandingAmount,
                    "credit_note_amount"  to creditAmount,
                ),
                httpStatus = 422,
            )
        }

        val customer = customerRepository.findById(original.customerId)
            .orElseThrow { ResourceNotFoundException("CUSTOMER_NOT_FOUND", original.customerId, "Customer") }
        val arAccount = customer.defaultArAccountId
            ?: accountRepository.findAllByEntityIdAndAccountSubtype(original.entityId, AccountSubtype.CURRENT_RECEIVABLE)
                .filter { !it.isHeader }
                .minByOrNull { it.accountCode }?.id
            ?: throw BusinessRuleViolationException(
                errorCode = "AR_ACCOUNT_NOT_FOUND",
                message = "No Accounts Receivable account found. Add a CURRENT_RECEIVABLE account to your chart of accounts.",
                context = mapOf("entity_id" to original.entityId),
            )

        // C3 — resolve periodId before constructing Invoice (constructor requires non-nullable UUID).
        val creditNotePeriodId = original.periodId
            ?: throw BusinessRuleViolationException(
                errorCode = "PERIOD_ID_MISSING",
                message   = "Original invoice ${original.invoiceNumber} has no periodId — cannot create credit note.",
                context   = mapOf("invoice_id" to originalInvoiceId),
            )

        val cnConfig = entityNumberConfigService.resolveConfig(original.entityId, "CREDIT_NOTE")
        val cnNumber = codeGeneratorService.nextUnique(
            original.entityId, cnConfig.prefix, cnConfig.yearScoped,
            customFormat = cnConfig.customFormat,
        ) { !invoiceRepository.existsByEntityIdAndInvoiceNumber(original.entityId, it) }
        val creditNote = Invoice(
            entityId = original.entityId,
            periodId = creditNotePeriodId,
            invoiceNumber = cnNumber,
            customerId = original.customerId,
            issueDate = LocalDate.now(),
            dueDate = LocalDate.now(),
            currencyCode = original.currencyCode,
            exchangeRate = original.exchangeRate,
            subtotal = creditAmount.negate(),
            taxAmount = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            totalAmount = creditAmount.negate(),
            outstandingAmount = creditAmount.negate(),
            status = InvoiceStatus.CREDIT_NOTE,
            notes = "Credit note for ${original.invoiceNumber}: ${command.reason}",
        )
        original.lines.forEach { line ->
            creditNote.addLine(
                InvoiceLine(
                    lineNumber = line.lineNumber,
                    accountId = line.accountId,
                    description = "Credit: ${line.description}",
                    quantity = line.quantity.negate(),
                    unitPrice = line.unitPrice,
                    taxRateId = line.taxRateId,
                    lineSubtotal = line.lineSubtotal.negate(),
                    lineTax = line.lineTax.negate(),
                    lineTotal = line.lineTotal.negate(),
                )
            )
        }
        val saved = invoiceRepository.save(creditNote)

        // §14.4 — Reversing journal: CR AR / DR Revenue (mirror of original).
        val jeLines = mutableListOf<CreateJournalLineCommand>()
        jeLines += CreateJournalLineCommand(
            accountId = arAccount,
            description = "Credit note $cnNumber – AR reversal",
            debitAmount = BigDecimal.ZERO,
            creditAmount = creditAmount,
            currencyCode = original.currencyCode,
            exchangeRate = original.exchangeRate,
        )
        // Spread the credit amount proportionally across the original revenue accounts.
        // Last-line adjustment absorbs any rounding remainder so debits == creditAmount exactly.
        val totalOriginal = original.totalAmount.takeIf { it.signum() > 0 } ?: BigDecimal.ONE

        // Collect all reversal amounts first, then adjust the last line
        val reversalLines = mutableListOf<Pair<InvoiceLine, BigDecimal>>()
        original.lines.forEach { line ->
            val proportion = line.lineTotal.divide(totalOriginal, 12, ROUND)
            val reversal = creditAmount.multiply(proportion).setScale(MONEY_SCALE, ROUND)
            if (reversal.signum() > 0) {
                reversalLines.add(Pair(line, reversal))
            }
        }

        // Adjust last line to absorb rounding remainder so debits == creditAmount exactly
        if (reversalLines.isNotEmpty()) {
            val sumOfAllButLast = reversalLines.dropLast(1).sumOf { it.second }
            val lastLineAmount  = creditAmount.subtract(sumOfAllButLast).setScale(MONEY_SCALE, ROUND)
            val adjustedReversalLines = reversalLines.dropLast(1) + Pair(reversalLines.last().first, lastLineAmount)

            adjustedReversalLines.forEach { (line, amount) ->
                if (amount.signum() > 0) {
                    jeLines += CreateJournalLineCommand(
                        accountId   = line.accountId,
                        description = "Credit note $cnNumber – Revenue reversal – L${line.lineNumber}",
                        debitAmount  = amount,
                        creditAmount = BigDecimal.ZERO,
                        currencyCode = original.currencyCode,
                        exchangeRate = original.exchangeRate,
                    )
                }
            }
        }

        val je = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId = original.entityId,
                periodId = creditNotePeriodId,
                transDate = LocalDate.now(),
                description = "Credit note $cnNumber – reversal of ${original.invoiceNumber}",
                sourceType = "INVOICE",
                sourceId = saved.id,
                lines = jeLines,
            )
        )
        journalService.postEntryAsSystem(je.id)
        saved.journalEntryId = je.id
        invoiceRepository.save(saved)

        // Credit note settles part (or all) of the original invoice — treat as a payment
        // so the DB constraint outstanding = total - paid stays satisfied.
        original.paidAmount        = original.paidAmount.add(creditAmount).setScale(MONEY_SCALE, ROUND)
        original.outstandingAmount = original.totalAmount.subtract(original.paidAmount).setScale(MONEY_SCALE, ROUND)
        original.status = when {
            original.outstandingAmount.signum() <= 0 -> InvoiceStatus.PAID
            original.paidAmount.signum() > 0         -> InvoiceStatus.PARTIALLY_PAID
            else                                     -> original.status
        }
        invoiceRepository.save(original)

        return saved
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): Invoice = invoiceRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("INVOICE_NOT_FOUND", id, "Invoice") }

    /**
     * §14.2 — Standalone credit-notes listing for the `/api/v1/credit-notes` resource.
     * Credit notes are Invoice rows with status = CREDIT_NOTE (negative amounts, terminal
     * state — see [createCreditNote]). Deliberately kept separate from [findByEntity]:
     * that method's customerId branch takes priority over the status filter and would
     * silently return all of a customer's invoices instead of just their credit notes.
     */
    @Transactional(readOnly = true)
    fun findCreditNotesByEntity(entityId: UUID, pageable: Pageable): Page<Invoice> =
        invoiceRepository.findByEntityIdAndStatus(entityId, InvoiceStatus.CREDIT_NOTE, pageable)

    /**
     * §14, §7.3 — Paged listing with optional filters. Combinations of filters are
     * resolved by repository methods; unsupported combinations fall back to the
     * narrowest applicable filter.
     */
    @Transactional(readOnly = true)
    fun findByEntity(
        entityId: UUID,
        customerId: UUID? = null,
        status: InvoiceStatus? = null,
        fromDate: LocalDate? = null,
        toDate: LocalDate? = null,
        pageable: Pageable,
    ): Page<Invoice> = when {
        customerId != null -> invoiceRepository.findByEntityIdAndCustomerId(entityId, customerId, pageable)
        status != null -> invoiceRepository.findByEntityIdAndStatus(entityId, status, pageable)
        fromDate != null && toDate != null ->
            invoiceRepository.findByEntityIdAndIssueDateRange(entityId, fromDate, toDate, pageable)
        else -> invoiceRepository.findByEntityId(entityId, pageable)
    }

    /**
     * §14 — Accounts Receivable ageing. Buckets every outstanding invoice into 0–30 /
     * 31–60 / 61–90 / 90+ days past due as of [asOfDate].
     */
    @Transactional(readOnly = true)
    fun arAgeing(entityId: UUID, asOfDate: LocalDate): ArAgeingResponse {
        val outstanding = invoiceRepository.findOverdueInvoices(
            entityId, asOfDate, Pageable.unpaged(),
        ).content

        val bucket0to30 = mutableListOf<Invoice>()
        val bucket31to60 = mutableListOf<Invoice>()
        val bucket61to90 = mutableListOf<Invoice>()
        val bucket90Plus = mutableListOf<Invoice>()

        outstanding.forEach { inv ->
            val days = ChronoUnit.DAYS.between(inv.dueDate, asOfDate).toInt()
            when {
                days <= 30 -> bucket0to30.add(inv)
                days <= 60 -> bucket31to60.add(inv)
                days <= 90 -> bucket61to90.add(inv)
                else -> bucket90Plus.add(inv)
            }
        }

        fun bucket(items: List<Invoice>) = AgingBucketResponse(
            invoiceCount = items.size,
            totalAmount = items.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.outstandingAmount) }
                .setScale(MONEY_SCALE, ROUND),
        )

        val total = outstanding.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.outstandingAmount) }
            .setScale(MONEY_SCALE, ROUND)

        return ArAgeingResponse(
            entityId = entityId,
            asOfDate = asOfDate,
            current = bucket(bucket0to30),
            thirtyOneToSixty = bucket(bucket31to60),
            sixtyOneToNinety = bucket(bucket61to90),
            ninetyPlus = bucket(bucket90Plus),
            totalOutstanding = total,
        )
    }
}


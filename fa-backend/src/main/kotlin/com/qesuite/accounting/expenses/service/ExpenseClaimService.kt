package com.qesuite.accounting.expenses.service

import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.expenses.domain.ExpenseClaim
import com.qesuite.accounting.expenses.domain.ExpenseClaimLine
import com.qesuite.accounting.expenses.domain.ExpenseClaimStatus
import com.qesuite.accounting.expenses.dto.ExpenseClaimLineCommand
import com.qesuite.accounting.expenses.dto.ExpenseClaimLineResponse
import com.qesuite.accounting.expenses.dto.ExpenseClaimResponse
import com.qesuite.accounting.expenses.dto.CreateExpenseClaimCommand
import com.qesuite.accounting.expenses.dto.UpdateExpenseClaimCommand
import com.qesuite.accounting.expenses.repository.ExpenseClaimRepository
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.CreateJournalLineCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.SecurityUtils
import com.qesuite.accounting.users.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Expense Management (T&E) module service,
 * workplan.md Phase 1 item 3.
 *
 * Owns the full expense-claim lifecycle: DRAFT → SUBMITTED → APPROVED → REIMBURSED, or
 * SUBMITTED → REJECTED (with REJECTED → DRAFT reopen for correction + resubmission — see
 * [ExpenseClaimStatus] KDoc for why reopen was chosen over clone-a-new-claim).
 *
 * [approve] is the one posting path in this module: it resolves the accounting period from
 * `claimDate` (mirroring [com.qesuite.accounting.payables.service.BillService.approveBill]'s
 * `periodService.findPeriodForDate` fallback, since a claim carries a single date rather than an
 * explicit `periodId`), builds a balanced journal entry — DR each line's expense account (merged
 * by account, matching `BillService.approveBill`'s `expenseByAccount` merge so two lines against
 * the same account produce one JE line, not two) / CR the entity's Employee Reimbursements Payable
 * account — and posts it through [JournalService] in the same transaction as the DRAFT/SUBMITTED
 * → APPROVED → REIMBURSED transition, exactly the pattern `InvoiceService.approve` and
 * `BillService.approveBill` use (never a separate "post" call).
 *
 * IFRS treatment: an approved-but-unpaid employee expense reimbursement is a current liability —
 * "trade and other payables" per IAS 1 §54(k) — economically identical in nature to a trade
 * payable owed to a supplier. This codebase's [AccountSubtype] enum has no dedicated
 * "employee payable" subtype (see AccountEnums.kt), so [resolveReimbursementPayableAccount] reuses
 * `CURRENT_PAYABLE` — the same subtype `BillService.resolveApAccount` uses for trade payables —
 * preferring an account whose name signals it is earmarked for reimbursements if the entity has
 * configured one, and otherwise falling back to the entity's general payables account. This is a
 * closest-correct-fit reuse of an existing, correctly-modeled subtype, not an invented one, per
 * CLAUDE.md §2's configuration-driven guidance.
 *
 * Money discipline: every monetary calculation goes through `BigDecimal`, rounded `HALF_EVEN` to
 * scale 6, matching `InvoiceService`/`BudgetService`'s convention.
 */
@Service
@Transactional
class ExpenseClaimService(
    private val expenseClaimRepository: ExpenseClaimRepository,
    private val accountRepository: AccountRepository,
    private val userRepository: UserRepository,
    private val periodService: PeriodService,
    private val currencyRepository: CurrencyRepository,
    private val journalService: JournalService,
) {

    private companion object {
        const val MONEY_SCALE = 6
        val ROUND = RoundingMode.HALF_EVEN
    }

    /**
     * Create an expense claim in DRAFT status. Validates the employee (must exist, belong to the
     * same entity) and every line's account (must exist, belong to the same entity, be a
     * non-header EXPENSE-type account, and carry a positive amount). `totalAmount` is always
     * derived from the lines, never trusted from the client (same discipline as
     * `InvoiceService.createDraft`/`BudgetService.createDraft`).
     */
    @Auditable(action = AuditAction.CREATE, resourceType = "EXPENSE_CLAIM")
    fun createDraft(command: CreateExpenseClaimCommand): ExpenseClaim {
        val employee = userRepository.findById(command.employeeId)
            .orElseThrow { ResourceNotFoundException("EMPLOYEE_NOT_FOUND", command.employeeId, "User") }
        if (employee.entityId != command.entityId) {
            throw ValidationException(
                errorCode = "FORBIDDEN",
                message = "Employee ${command.employeeId} does not belong to entity ${command.entityId}.",
                httpStatus = 403,
            )
        }

        val validated = command.lines.map { it to validateLine(command.entityId, it) }

        val claim = ExpenseClaim(
            entityId = command.entityId,
            employeeId = command.employeeId,
            claimDate = command.claimDate,
            notes = command.notes,
            totalAmount = validated.sumOf { (line, _) -> line.amount }.setScale(MONEY_SCALE, ROUND),
        )
        validated.forEach { (line, _) ->
            claim.addLine(
                ExpenseClaimLine(
                    accountId = line.accountId,
                    description = line.description,
                    amount = line.amount.setScale(MONEY_SCALE, ROUND),
                    dateIncurred = line.dateIncurred,
                    receiptReference = line.receiptReference,
                )
            )
        }
        return expenseClaimRepository.save(claim)
    }

    /**
     * Update a DRAFT claim's claim date/notes, and — if `lines` is supplied — wholesale-replace
     * its lines (matching `BudgetService.update`'s shape). Only `DRAFT` claims are editable;
     * once `SUBMITTED` the claim follows this codebase's immutability-after-submission discipline
     * (corrections happen via reject → reopen → edit → resubmit, not an in-place edit).
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "EXPENSE_CLAIM")
    fun update(@AuditResourceId id: UUID, command: UpdateExpenseClaimCommand): ExpenseClaim {
        val claim = findById(id)
        if (claim.status != ExpenseClaimStatus.DRAFT) {
            throw BusinessRuleViolationException(
                errorCode = "EXPENSE_CLAIM_NOT_EDITABLE",
                message = "Only a DRAFT expense claim can be edited (current status: ${claim.status}).",
                context = mapOf("claim_id" to id, "current_status" to claim.status.name),
            )
        }
        command.claimDate?.let { claim.claimDate = it }
        command.notes?.let { claim.notes = it }
        command.lines?.let { newLines ->
            if (newLines.isEmpty()) {
                throw ValidationException(
                    errorCode = "EMPTY_EXPENSE_CLAIM",
                    message = "An expense claim must have at least one line.",
                )
            }
            val validated = newLines.map { it to validateLine(claim.entityId, it) }
            claim.clearLines()
            validated.forEach { (line, _) ->
                claim.addLine(
                    ExpenseClaimLine(
                        accountId = line.accountId,
                        description = line.description,
                        amount = line.amount.setScale(MONEY_SCALE, ROUND),
                        dateIncurred = line.dateIncurred,
                        receiptReference = line.receiptReference,
                    )
                )
            }
            claim.totalAmount = validated.sumOf { (line, _) -> line.amount }.setScale(MONEY_SCALE, ROUND)
        }
        return expenseClaimRepository.save(claim)
    }

    /** DRAFT → SUBMITTED. The submitter (preparer) hands the claim off for approval. */
    @Auditable(action = AuditAction.UPDATE, resourceType = "EXPENSE_CLAIM")
    fun submit(@AuditResourceId id: UUID): ExpenseClaim {
        val claim = findById(id)
        if (!claim.status.canTransitionTo(ExpenseClaimStatus.SUBMITTED)) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot submit an expense claim in status ${claim.status}. Only DRAFT claims can be submitted.",
                context = mapOf("claim_id" to id, "current_status" to claim.status.name),
            )
        }
        if (claim.totalAmount.signum() <= 0) {
            throw BusinessRuleViolationException(
                errorCode = "EMPTY_EXPENSE_CLAIM",
                message = "Cannot submit an expense claim with a zero or negative total.",
                context = mapOf("claim_id" to id),
            )
        }
        claim.status = ExpenseClaimStatus.SUBMITTED
        return expenseClaimRepository.save(claim)
    }

    /**
     * SUBMITTED → APPROVED → REIMBURSED, in one transaction. Segregation-of-duties: the approving
     * user may not be the claim's own employee (self-approval) — [approverId] is compared against
     * `claim.employeeId` before anything else happens, matching the spirit of
     * `JournalController.approve`/`BillController.approveBill`'s APPROVER-tier sign-off, made
     * concrete here because unlike those, this specific document has an obvious, cheaply-checkable
     * "person who benefits from approval" (the employee being reimbursed).
     *
     * Posts the reimbursement journal entry through [JournalService] — see class KDoc — then
     * advances the claim straight to REIMBURSED (mirroring `InvoiceService.approve`'s auto
     * DRAFT-approved-then-SENT progression).
     */
    @Auditable(action = AuditAction.POST, resourceType = "EXPENSE_CLAIM")
    fun approve(@AuditResourceId id: UUID, approverId: UUID): ExpenseClaim {
        val claim = findById(id)
        if (!claim.status.canTransitionTo(ExpenseClaimStatus.APPROVED)) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot approve an expense claim in status ${claim.status}. Only SUBMITTED claims can be approved.",
                context = mapOf("claim_id" to id, "current_status" to claim.status.name),
            )
        }
        if (claim.employeeId == approverId) {
            throw BusinessRuleViolationException(
                errorCode = "SELF_APPROVAL_NOT_ALLOWED",
                message = "The employee who submitted an expense claim may not approve their own reimbursement.",
                context = mapOf("claim_id" to id, "employee_id" to claim.employeeId),
            )
        }
        // Maker-checker (segregation of duties) — separate from the check above. That one
        // guards the nominal *beneficiary* (employeeId) approving their own reimbursement;
        // this one guards the actual *submitter* (createdBy) rubber-stamping their own work,
        // regardless of whose name is on the claim. Delegated submission (one person filing on
        // another's behalf) stays allowed — this only blocks the same person from being both
        // maker and checker. See SecurityUtils.requireNotSelfApproval and MEMORY.md for the
        // codebase-wide rollout this closes the last gap in.
        SecurityUtils.requireNotSelfApproval(claim.createdBy)

        val period = try {
            periodService.findPeriodForDate(claim.entityId, claim.claimDate)
        } catch (e: Exception) {
            throw BusinessRuleViolationException(
                errorCode = "NO_OPEN_PERIOD",
                message = "No open accounting period found for claim date ${claim.claimDate}.",
                context = mapOf("claim_id" to id, "claim_date" to claim.claimDate),
            )
        }
        claim.periodId = period.id

        val currencyCode = currencyRepository.findByEntityIdAndIsFunctionalTrue(claim.entityId)
            .orElseThrow {
                BusinessRuleViolationException(
                    errorCode = "FUNCTIONAL_CURRENCY_NOT_SET",
                    message = "No functional currency configured for entity ${claim.entityId}.",
                    context = mapOf("entity_id" to claim.entityId),
                )
            }.currencyCode

        val employee = userRepository.findById(claim.employeeId)
            .orElseThrow { ResourceNotFoundException("EMPLOYEE_NOT_FOUND", claim.employeeId, "User") }

        val payableAccountId = resolveReimbursementPayableAccount(claim.entityId)

        // DR each line's expense account, merged by account (two lines against the same account
        // produce one JE line — matches BillService.approveBill's expenseByAccount merge).
        val expenseByAccount = mutableMapOf<UUID, BigDecimal>()
        claim.lines.forEach { line -> expenseByAccount.merge(line.accountId, line.amount, BigDecimal::add) }

        val jeLines = mutableListOf<CreateJournalLineCommand>()
        expenseByAccount.forEach { (accountId, amount) ->
            if (amount.signum() > 0) {
                jeLines += CreateJournalLineCommand(
                    accountId = accountId,
                    description = "Expense Claim reimbursement – ${employee.fullName}",
                    debitAmount = amount,
                    creditAmount = BigDecimal.ZERO,
                    currencyCode = currencyCode,
                )
            }
        }

        // CR the reimbursement payable for the claim total.
        jeLines += CreateJournalLineCommand(
            accountId = payableAccountId,
            description = "Expense Claim reimbursement – ${employee.fullName}",
            debitAmount = BigDecimal.ZERO,
            creditAmount = claim.totalAmount,
            currencyCode = currencyCode,
        )

        val journalEntry = journalService.createEntry(
            CreateJournalEntryCommand(
                entityId = claim.entityId,
                periodId = period.id,
                transDate = claim.claimDate,
                description = "Expense Claim reimbursement – ${employee.fullName}",
                sourceType = "EXPENSE_CLAIM",
                sourceId = claim.id,
                lines = jeLines,
            )
        )
        journalService.postEntryAsSystem(journalEntry.id)

        claim.journalEntryId = journalEntry.id
        claim.status = ExpenseClaimStatus.APPROVED
        val approved = expenseClaimRepository.save(claim) // persist APPROVED state

        // Auto-progress to REIMBURSED once the liability is posted (delivery/disbursement is a
        // separate, out-of-scope concern — see class KDoc / handover notes).
        approved.status = ExpenseClaimStatus.REIMBURSED
        return expenseClaimRepository.save(approved)
    }

    /** SUBMITTED → REJECTED, with a mandatory reason. No journal entry is ever posted for a rejection. */
    @Auditable(action = AuditAction.REJECT, resourceType = "EXPENSE_CLAIM")
    fun reject(@AuditResourceId id: UUID, reason: String): ExpenseClaim {
        val claim = findById(id)
        if (!claim.status.canTransitionTo(ExpenseClaimStatus.REJECTED)) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot reject an expense claim in status ${claim.status}. Only SUBMITTED claims can be rejected.",
                context = mapOf("claim_id" to id, "current_status" to claim.status.name),
            )
        }
        claim.status = ExpenseClaimStatus.REJECTED
        claim.rejectionReason = reason
        return expenseClaimRepository.save(claim)
    }

    /**
     * REJECTED → DRAFT. Reopens a rejected claim for correction and resubmission — see
     * [ExpenseClaimStatus] KDoc for why this is preferred over cloning a new claim row.
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "EXPENSE_CLAIM")
    fun reopen(@AuditResourceId id: UUID): ExpenseClaim {
        val claim = findById(id)
        if (!claim.status.canTransitionTo(ExpenseClaimStatus.DRAFT)) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot reopen an expense claim in status ${claim.status}. Only REJECTED claims can be reopened.",
                context = mapOf("claim_id" to id, "current_status" to claim.status.name),
            )
        }
        claim.status = ExpenseClaimStatus.DRAFT
        claim.rejectionReason = null
        return expenseClaimRepository.save(claim)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): ExpenseClaim = expenseClaimRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("EXPENSE_CLAIM_NOT_FOUND", id, "ExpenseClaim") }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, status: ExpenseClaimStatus?, employeeId: UUID?, pageable: Pageable): Page<ExpenseClaim> = when {
        employeeId != null -> expenseClaimRepository.findByEntityIdAndEmployeeId(entityId, employeeId, pageable)
        status != null -> expenseClaimRepository.findByEntityIdAndStatus(entityId, status, pageable)
        else -> expenseClaimRepository.findByEntityId(entityId, pageable)
    }

    @Transactional(readOnly = true)
    fun toResponse(claim: ExpenseClaim): ExpenseClaimResponse {
        val accounts = accountRepository.findAllById(claim.lines.map { it.accountId }).associateBy { it.id }
        val employee = userRepository.findById(claim.employeeId).orElse(null)
        return ExpenseClaimResponse(
            id = claim.id,
            entityId = claim.entityId,
            employeeId = claim.employeeId,
            employeeName = employee?.fullName ?: "Unknown",
            claimDate = claim.claimDate,
            status = claim.status,
            totalAmount = claim.totalAmount,
            notes = claim.notes,
            journalEntryId = claim.journalEntryId,
            rejectionReason = claim.rejectionReason,
            version = claim.version,
            lines = claim.lines.map { line ->
                val account = accounts.getValue(line.accountId)
                ExpenseClaimLineResponse(
                    id = line.id,
                    accountId = line.accountId,
                    accountCode = account.accountCode,
                    accountName = account.accountName,
                    description = line.description,
                    amount = line.amount,
                    dateIncurred = line.dateIncurred,
                    receiptReference = line.receiptReference,
                )
            },
        )
    }

    /**
     * IAS 1 §54(k) — an approved-but-unpaid employee reimbursement is a current liability,
     * economically the same nature as a trade payable. No dedicated "employee payable" subtype
     * exists in [AccountSubtype] (see class KDoc), so this reuses `CURRENT_PAYABLE` — the same
     * subtype `BillService.resolveApAccount` uses — preferring an account whose name signals it is
     * earmarked for reimbursements ("Employee"/"Reimburse") if the entity has configured one,
     * otherwise falling back to the entity's general payables account (lowest account code, same
     * tie-break as `BillService.resolveApAccount`/`InvoiceService.approve`'s AR lookup).
     */
    private fun resolveReimbursementPayableAccount(entityId: UUID): UUID {
        val candidates = accountRepository.findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CURRENT_PAYABLE)
            .filter { !it.isHeader }
        val dedicated = candidates.firstOrNull {
            it.accountName.contains("Reimburse", ignoreCase = true) || it.accountName.contains("Employee", ignoreCase = true)
        }
        return dedicated?.id
            ?: candidates.minByOrNull { it.accountCode }?.id
            ?: throw BusinessRuleViolationException(
                errorCode = "MISSING_REIMBURSEMENT_PAYABLE_ACCOUNT",
                message = "No CURRENT_PAYABLE account found for entity $entityId. Configure an Accounts Payable / " +
                    "Employee Reimbursements Payable account in the Chart of Accounts.",
                context = mapOf("entity_id" to entityId),
            )
    }

    private fun validateLine(entityId: UUID, line: ExpenseClaimLineCommand): Account {
        val account = accountRepository.findById(line.accountId)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", line.accountId, "Account") }
        if (account.entityId != entityId) {
            throw ValidationException(
                errorCode = "FORBIDDEN",
                message = "Account ${line.accountId} does not belong to entity $entityId.",
                httpStatus = 403,
            )
        }
        if (account.isHeader) {
            throw BusinessRuleViolationException(
                errorCode = "HEADER_ACCOUNT_NOT_CLAIMABLE",
                message = "Account ${account.accountCode} (${account.accountName}) is a header account and cannot be used on an expense claim line.",
                context = mapOf("account_id" to account.id),
            )
        }
        if (account.accountType != AccountType.EXPENSE) {
            throw BusinessRuleViolationException(
                errorCode = "EXPENSE_ACCOUNT_REQUIRED",
                message = "Account ${account.accountCode} (${account.accountName}) is not an EXPENSE account.",
                context = mapOf("account_id" to account.id, "account_type" to account.accountType.name),
            )
        }
        if (line.amount.signum() <= 0) {
            throw ValidationException(
                errorCode = "INVALID_AMOUNT",
                message = "Expense claim line amount must be greater than zero.",
                context = mapOf("account_id" to line.accountId, "amount" to line.amount),
            )
        }
        return account
    }
}

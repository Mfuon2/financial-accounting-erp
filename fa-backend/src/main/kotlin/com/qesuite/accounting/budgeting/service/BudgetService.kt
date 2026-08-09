package com.qesuite.accounting.budgeting.service

import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.budgeting.domain.Budget
import com.qesuite.accounting.budgeting.domain.BudgetLine
import com.qesuite.accounting.budgeting.domain.BudgetStatus
import com.qesuite.accounting.budgeting.dto.BudgetLineCommand
import com.qesuite.accounting.budgeting.dto.BudgetLineResponse
import com.qesuite.accounting.budgeting.dto.BudgetResponse
import com.qesuite.accounting.budgeting.dto.BudgetVarianceLineResponse
import com.qesuite.accounting.budgeting.dto.BudgetVarianceReportResponse
import com.qesuite.accounting.budgeting.dto.CreateBudgetCommand
import com.qesuite.accounting.budgeting.dto.UpdateBudgetCommand
import com.qesuite.accounting.budgeting.repository.BudgetRepository
import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.NormalBalance
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Budgeting module service.
 *
 * Owns the full budget lifecycle: DRAFT → APPROVED (or → VOID from either). Never posts a journal
 * entry — a budget is a planning artifact, compared against actual ledger activity only at read
 * time via [varianceReport], which reuses [LedgerEntryRepository]'s existing range-sum methods
 * (the same mechanism [com.qesuite.accounting.analytics.service.DashboardService] and
 * [com.qesuite.accounting.ledger.service.TrialBalanceService] use) rather than re-deriving actuals.
 *
 * Money discipline: every monetary calculation goes through `BigDecimal`, rounded `HALF_EVEN` to
 * scale 6, matching InvoiceService's convention.
 */
@Service
@Transactional
class BudgetService(
    private val budgetRepository: BudgetRepository,
    private val accountRepository: AccountRepository,
    private val periodService: PeriodService,
    private val ledgerEntryRepository: LedgerEntryRepository,
) {

    private companion object {
        const val MONEY_SCALE = 6
        val ROUND = RoundingMode.HALF_EVEN
    }

    /**
     * Create a budget in DRAFT status. Validates every line's account (must exist, belong to the
     * same entity, and not be a header account — IAS 1 §29, the same rule journal posting
     * enforces, since a header account has no standalone actual balance to compare a budget
     * against) and period (must exist and belong to the same entity). `totalAmount` is always
     * derived from the lines, never trusted from the client (same discipline as
     * `InvoiceService.createDraft`).
     */
    @Auditable(action = AuditAction.CREATE, resourceType = "BUDGET")
    fun createDraft(command: CreateBudgetCommand): Budget {
        rejectDuplicateLines(command.lines)
        val validated = command.lines.map { it to validateLine(command.entityId, it) }

        val budget = Budget(
            entityId = command.entityId,
            name = command.name,
            notes = command.notes,
            totalAmount = validated.sumOf { (line, _) -> line.amount }.setScale(MONEY_SCALE, ROUND),
        )
        validated.forEach { (line, _) ->
            budget.addLine(
                BudgetLine(
                    accountId = line.accountId,
                    periodId = line.periodId,
                    amount = line.amount.setScale(MONEY_SCALE, ROUND),
                )
            )
        }
        return budgetRepository.save(budget)
    }

    /**
     * Update a DRAFT budget's name/notes, and — if `lines` is supplied — wholesale-replace its
     * lines (matching `InvoiceService`'s update-by-replacement shape). Only `DRAFT` budgets are
     * editable; `APPROVED` is a frozen plan (corrections are void-and-recreate).
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "BUDGET")
    fun update(@AuditResourceId id: UUID, command: UpdateBudgetCommand): Budget {
        val budget = findById(id)
        if (budget.status != BudgetStatus.DRAFT) {
            throw BusinessRuleViolationException(
                errorCode = "BUDGET_NOT_EDITABLE",
                message = "Only a DRAFT budget can be edited (current status: ${budget.status}).",
                context = mapOf("budget_id" to id, "current_status" to budget.status.name),
            )
        }
        command.name?.let { budget.name = it }
        command.notes?.let { budget.notes = it }
        command.lines?.let { newLines ->
            rejectDuplicateLines(newLines)
            val validated = newLines.map { it to validateLine(budget.entityId, it) }
            budget.clearLines()
            validated.forEach { (line, _) ->
                budget.addLine(
                    BudgetLine(
                        accountId = line.accountId,
                        periodId = line.periodId,
                        amount = line.amount.setScale(MONEY_SCALE, ROUND),
                    )
                )
            }
            budget.totalAmount = validated.sumOf { (line, _) -> line.amount }.setScale(MONEY_SCALE, ROUND)
        }
        return budgetRepository.save(budget)
    }

    @Auditable(action = AuditAction.POST, resourceType = "BUDGET")
    fun approve(@AuditResourceId id: UUID): Budget {
        val budget = findById(id)
        if (!budget.status.canTransitionTo(BudgetStatus.APPROVED)) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot approve a budget in status ${budget.status}. Only DRAFT budgets can be approved.",
                context = mapOf("budget_id" to id, "current_status" to budget.status.name),
            )
        }
        budget.status = BudgetStatus.APPROVED
        return budgetRepository.save(budget)
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "BUDGET")
    fun void(@AuditResourceId id: UUID, reason: String, voidedBy: UUID?): Budget {
        val budget = findById(id)
        if (!budget.status.canTransitionTo(BudgetStatus.VOID)) {
            throw BusinessRuleViolationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Cannot void a budget in status ${budget.status}.",
                context = mapOf("budget_id" to id, "current_status" to budget.status.name),
            )
        }
        budget.status = BudgetStatus.VOID
        budget.isActive = false
        budget.deactivatedAt = Instant.now()
        budget.deactivatedBy = voidedBy
        budget.deactivationReason = reason
        return budgetRepository.save(budget)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): Budget = budgetRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("BUDGET_NOT_FOUND", id, "Budget") }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<Budget> =
        budgetRepository.findByEntityId(entityId, pageable)

    @Transactional(readOnly = true)
    fun toResponse(budget: Budget): BudgetResponse {
        val accounts = accountRepository.findAllById(budget.lines.map { it.accountId }).associateBy { it.id }
        val periods = budget.lines.map { periodService.findById(it.periodId) }.associateBy { it.id }
        return BudgetResponse(
            id = budget.id,
            entityId = budget.entityId,
            name = budget.name,
            status = budget.status,
            totalAmount = budget.totalAmount,
            notes = budget.notes,
            version = budget.version,
            lines = budget.lines.map { line ->
                val account = accounts.getValue(line.accountId)
                val period = periods.getValue(line.periodId)
                BudgetLineResponse(
                    id = line.id,
                    accountId = line.accountId,
                    accountCode = account.accountCode,
                    accountName = account.accountName,
                    periodId = line.periodId,
                    periodName = period.periodName,
                    amount = line.amount,
                )
            },
        )
    }

    /**
     * Budget-vs-actual variance for every line of [id]. "Actual" is the net movement in
     * `accountId` over `periodId`'s date range, signed per the account's normal balance — the
     * exact pattern `DashboardService.getTbSummary` uses to turn raw debit/credit sums into a
     * signed actual, so a budget line and its actual are directly comparable regardless of
     * whether the account is debit-normal (assets/expenses) or credit-normal
     * (liabilities/equity/revenue). `variance = actual - budgeted`; whether a positive variance
     * is favorable depends on account type and is deliberately left for the caller to interpret
     * (see `BudgetVarianceLineResponse`'s KDoc) rather than hardcoded here.
     */
    @Transactional(readOnly = true)
    fun varianceReport(id: UUID): BudgetVarianceReportResponse {
        val budget = findById(id)
        val accounts = accountRepository.findAllById(budget.lines.map { it.accountId }).associateBy { it.id }
        val periods = budget.lines.map { periodService.findById(it.periodId) }.associateBy { it.id }

        val lines = budget.lines.map { line ->
            val account = accounts.getValue(line.accountId)
            val period = periods.getValue(line.periodId)
            val actual = actualForAccountInPeriod(account, period)
            val variance = actual.subtract(line.amount).setScale(MONEY_SCALE, ROUND)
            val variancePercent = if (line.amount.signum() != 0)
                variance.divide(line.amount.abs(), MONEY_SCALE, ROUND).multiply(BigDecimal(100)).setScale(MONEY_SCALE, ROUND)
            else null
            BudgetVarianceLineResponse(
                accountId = account.id,
                accountCode = account.accountCode,
                accountName = account.accountName,
                periodId = period.id,
                periodName = period.periodName,
                budgetedAmount = line.amount,
                actualAmount = actual,
                variance = variance,
                variancePercent = variancePercent,
            )
        }
        return BudgetVarianceReportResponse(
            budgetId = budget.id,
            budgetName = budget.name,
            lines = lines,
            totalBudgeted = lines.sumOf { it.budgetedAmount }.setScale(MONEY_SCALE, ROUND),
            totalActual = lines.sumOf { it.actualAmount }.setScale(MONEY_SCALE, ROUND),
            totalVariance = lines.sumOf { it.variance }.setScale(MONEY_SCALE, ROUND),
        )
    }

    private fun actualForAccountInPeriod(account: Account, period: Period): BigDecimal {
        val ids = listOf(account.id)
        val debits = ledgerEntryRepository.sumDebitsByAccountIdsAndRange(ids, period.startDate, period.endDate)
        val credits = ledgerEntryRepository.sumCreditsByAccountIdsAndRange(ids, period.startDate, period.endDate)
        return if (account.normalBalance == NormalBalance.DEBIT) debits.subtract(credits) else credits.subtract(debits)
    }

    private fun validateLine(entityId: UUID, line: BudgetLineCommand): Account {
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
                errorCode = "HEADER_ACCOUNT_NOT_BUDGETABLE",
                message = "Account ${account.accountCode} (${account.accountName}) is a header account and cannot be budgeted directly.",
                context = mapOf("account_id" to account.id),
            )
        }
        val period = periodService.findById(line.periodId)
        if (period.entityId != entityId) {
            throw ValidationException(
                errorCode = "FORBIDDEN",
                message = "Period ${line.periodId} does not belong to entity $entityId.",
                httpStatus = 403,
            )
        }
        return account
    }

    private fun rejectDuplicateLines(lines: List<BudgetLineCommand>) {
        val keys = lines.map { it.accountId to it.periodId }
        if (keys.size != keys.toSet().size) {
            throw ValidationException(
                errorCode = "DUPLICATE_BUDGET_LINE",
                message = "A budget may only have one line per (account, period) pair.",
            )
        }
    }
}

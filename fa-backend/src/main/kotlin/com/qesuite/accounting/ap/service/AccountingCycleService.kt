package com.qesuite.accounting.ap.service

import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.repository.PeriodRepository
import com.qesuite.accounting.journal.service.AdjustmentService
import com.qesuite.accounting.journal.service.ClosingService
import com.qesuite.accounting.ledger.service.FinancialStatementService
import com.qesuite.accounting.ledger.service.TrialBalanceReport
import com.qesuite.accounting.ledger.service.TrialBalanceService
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

// ──────────────────────────────────────────────────────────────────────────────
// §10.2 — Supporting data classes for the 9-step accounting cycle
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Optional override parameters for the period-end close (Step 8).
 * All fields default so callers that don't need explicit account overrides can
 * pass `ClosingParams()` with zero configuration.
 */
data class ClosingParams(
    /** Explicit Retained Earnings account; ClosingService auto-discovers if null. */
    val retainedEarningsAccountId: UUID? = null,
    /** Explicit Income Summary account; bypassed if null (close directly to RE). */
    val incomeSummaryAccountId: UUID? = null,
    /** Not used by ClosingService directly; reserved for future FX gain/loss routing. */
    val gainLossAccountId: UUID? = null,
    /** The journal date stamped on all closing entries. Defaults to today. */
    val closingDate: LocalDate = LocalDate.now()
)

/**
 * Immutable snapshot of all artefacts produced by [AccountingCycleService.runFullCycle].
 */
data class AccountingCycleResult(
    val periodId: UUID,
    val entityId: UUID,
    /** Step 3 — trial balance before adjusting entries. */
    val unadjustedTrialBalance: TrialBalanceReport,
    /** Step 5 — trial balance after adjusting entries. */
    val adjustedTrialBalance: TrialBalanceReport,
    /** Step 9 — trial balance after closing entries (only permanent accounts remain). */
    val postClosingTrialBalance: TrialBalanceReport,
    val completedAt: Instant
)

// ──────────────────────────────────────────────────────────────────────────────
// §10.2 — Accounting Cycle Orchestration Service
// ──────────────────────────────────────────────────────────────────────────────

@Service
@Transactional
class AccountingCycleService(
    private val periodRepository: PeriodRepository,
    private val periodService: PeriodService,
    private val trialBalanceService: TrialBalanceService,
    private val adjustmentService: AdjustmentService,
    private val financialStatementService: FinancialStatementService,
    private val closingService: ClosingService
) {

    private val log = LoggerFactory.getLogger(AccountingCycleService::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // §10.2 — Full 9-step accounting cycle
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Orchestrates the complete IFRS 9-step accounting cycle for [entityId]/[periodId].
     *
     * Steps 1–2 are continuous activities driven by journal endpoints; this method
     * picks up from Step 3 (unadjusted trial balance) through Step 9 (post-closing
     * trial balance) and manages all required period-status transitions.
     *
     * Expected pre-condition: period must be in OPEN or ADJUSTING state.
     * Post-condition: period ends in CLOSED state.
     *
     * @param entityId  Legal entity (tenant) UUID.
     * @param periodId  The accounting period to close.
     * @param params    Optional closing parameters (account overrides, closing date).
     * @return          [AccountingCycleResult] containing the three trial balance snapshots.
     */
    fun runFullCycle(entityId: UUID, periodId: UUID, params: ClosingParams): AccountingCycleResult {

        // Guard: period must belong to the requested entity.
        val period = periodRepository.findById(periodId)
            .orElseThrow { ValidationException("PERIOD_NOT_FOUND", "Period $periodId not found.") }

        if (period.entityId != entityId) {
            throw ValidationException("ENTITY_MISMATCH", "Period $periodId does not belong to entity $entityId.")
        }

        // Guard: pre-condition — period must be OPEN or ADJUSTING before the cycle starts.
        // Failing early (before any adjusting entries or trial-balance work) prevents partial
        // execution against a FUTURE, CLOSED, or REOPENED period.
        if (period.status != PeriodStatus.OPEN && period.status != PeriodStatus.ADJUSTING) {
            throw ValidationException(
                errorCode = "INVALID_CYCLE_PRE_CONDITION",
                message   = "Accounting cycle requires the period to be OPEN or ADJUSTING. " +
                            "Current status: ${period.status}. " +
                            "Transition the period to OPEN before running the full cycle.",
                context   = mapOf(
                    "period_id"      to periodId.toString(),
                    "current_status" to period.status.name,
                    "allowed_states" to listOf("OPEN", "ADJUSTING")
                )
            )
        }

        // ── Steps 1–2: Journalize / Post to Ledger ─────────────────────────────
        // These are continuous activities performed throughout the period via
        // JournalController. Nothing to execute here; log for audit trail clarity.
        log.info(
            "[Cycle:{}:{}] Steps 1-2 (journalize/post) are performed continuously via journal endpoints",
            entityId, periodId
        )

        // ── Step 3: Unadjusted Trial Balance ───────────────────────────────────
        log.info("[Cycle:{}:{}] Step 3 — generating unadjusted trial balance as of {}", entityId, periodId, params.closingDate)
        val unadjustedTB = trialBalanceService.generateTrialBalance(entityId, params.closingDate)

        // Transition to ADJUSTING if the period is still OPEN.
        if (period.status == PeriodStatus.OPEN) {
            log.info("[Cycle:{}:{}] Transitioning period OPEN → ADJUSTING", entityId, periodId)
            periodService.transitionPeriod(periodId, PeriodStatus.ADJUSTING)
        }

        // ── Step 4: Record & Post Adjusting Entries ───────────────────────────
        log.info("[Cycle:{}:{}] Step 4 — recording adjusting entries (prepayments + unearned revenue)", entityId, periodId)
        adjustmentService.amortizePrepayments(entityId, periodId)
        adjustmentService.recognizeUnearnedRevenue(entityId, periodId)
        // Note: FX revaluation (IAS 21) would be invoked here in a production deployment.

        // ── Step 5: Adjusted Trial Balance ────────────────────────────────────
        log.info("[Cycle:{}:{}] Step 5 — generating adjusted trial balance as of {}", entityId, periodId, params.closingDate)
        val adjustedTB = trialBalanceService.generateTrialBalance(entityId, params.closingDate)

        // ── Step 6: Income Statement / Profit & Loss ──────────────────────────
        log.info("[Cycle:{}:{}] Step 6 — generating Income Statement for period {} – {}", entityId, periodId, period.startDate, period.endDate)
        financialStatementService.getProfitLoss(entityId, period.startDate, period.endDate)

        // ── Step 7: Balance Sheet ─────────────────────────────────────────────
        log.info("[Cycle:{}:{}] Step 7 — generating Balance Sheet as of {}", entityId, periodId, params.closingDate)
        financialStatementService.getBalanceSheet(entityId, params.closingDate)

        // ── Step 8: Post Closing Entries ──────────────────────────────────────
        // Transition to CLOSING before invoking ClosingService.
        // ClosingService.runClosing() internally calls periodService.closePeriod()
        // which executes the final CLOSING → CLOSED transition, so we must NOT
        // call transitionPeriod(CLOSED) again after runClosing.
        log.info("[Cycle:{}:{}] Step 8 — transitioning period ADJUSTING → CLOSING", entityId, periodId)
        periodService.transitionPeriod(periodId, PeriodStatus.CLOSING)

        log.info("[Cycle:{}:{}] Step 8 — running closing entries (revenues, expenses, dividends → RE)", entityId, periodId)
        closingService.runClosing(
            entityId = entityId,
            periodId = periodId,
            closingDate = params.closingDate,
            retainedEarningsAccountId = params.retainedEarningsAccountId,
            incomeSummaryAccountId = params.incomeSummaryAccountId
        )
        // Period is now CLOSED (closingService.runClosing → periodService.closePeriod).
        log.info("[Cycle:{}:{}] Step 8 complete — period is now CLOSED", entityId, periodId)

        // ── Step 9: Post-Closing Trial Balance ────────────────────────────────
        log.info("[Cycle:{}:{}] Step 9 — generating post-closing trial balance as of {}", entityId, periodId, params.closingDate)
        val postClosingTB = trialBalanceService.generateTrialBalance(entityId, params.closingDate)

        log.info("[Cycle:{}:{}] 9-step accounting cycle completed successfully", entityId, periodId)

        return AccountingCycleResult(
            periodId = periodId,
            entityId = entityId,
            unadjustedTrialBalance = unadjustedTB,
            adjustedTrialBalance = adjustedTB,
            postClosingTrialBalance = postClosingTB,
            completedAt = Instant.now()
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // §10.2 — Pre-condition validation (unchanged from original)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * §10.2 — Validates that the period satisfies the pre-conditions required to
     * transition to [targetStatus]. Throws [ValidationException] on any violation.
     */
    fun validateStep(entityId: UUID, periodId: UUID, targetStatus: PeriodStatus) {
        val period = periodRepository.findById(periodId)
            .orElseThrow { ValidationException("PERIOD_NOT_FOUND", "Period $periodId not found.") }

        if (period.entityId != entityId) {
            throw ValidationException("ENTITY_MISMATCH", "Period must belong to the same entity.")
        }

        when (targetStatus) {
            PeriodStatus.ADJUSTING -> {
                if (period.status != PeriodStatus.OPEN) {
                    throw ValidationException("INVALID_CYCLE_TRANSITION", "Period must be OPEN to move to ADJUSTING.")
                }
            }
            PeriodStatus.CLOSING -> {
                if (period.status != PeriodStatus.ADJUSTING) {
                    throw ValidationException("INVALID_CYCLE_TRANSITION", "Period must be in ADJUSTING state to move to CLOSING.")
                }
            }
            PeriodStatus.CLOSED -> {
                if (period.status != PeriodStatus.CLOSING) {
                    throw ValidationException("INVALID_CYCLE_TRANSITION", "Period must be in CLOSING state to move to CLOSED.")
                }
            }
            else -> {}
        }
    }

    /**
     * §10.2 — Validates pre-conditions and persists the status transition.
     * Delegates validation to [validateStep], then saves through the repository.
     * Used by [AccountingCycleController] for manual ad-hoc transitions.
     */
    @Transactional
    fun transitionPeriod(entityId: UUID, periodId: UUID, newStatus: PeriodStatus) {
        validateStep(entityId, periodId, newStatus)
        val period = periodRepository.findById(periodId).get()
        period.status = newStatus
        periodRepository.save(period)
    }
}

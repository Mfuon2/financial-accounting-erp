package com.qesuite.accounting.ap.service

import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.repository.PeriodRepository
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.SecurityUtils
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Service
class PeriodService(private val periodRepository: PeriodRepository) {

    /**
     * §10.1 — Fiscal Calendar Manager
     * Generates 12 monthly periods for a given start year.
     */
    @Transactional
    fun generateFiscalYear(entityId: UUID, startYear: Int) {
        // Guard: prevent duplicate fiscal year generation. This is a conflict with existing
        // data (HTTP 409), not a malformed-input error (HTTP 400) — the request itself is
        // perfectly valid, it just can't be applied because the resource already exists.
        val firstMonthName = "${java.time.Month.JANUARY} $startYear"
        if (periodRepository.existsByEntityIdAndPeriodName(entityId, firstMonthName)) {
            throw ConflictException(
                errorCode = "FISCAL_YEAR_ALREADY_EXISTS",
                message = "Fiscal year $startYear has already been generated for this entity. " +
                    "Use the period transition endpoints to manage existing periods.",
                context = mapOf("entity_id" to entityId, "fiscal_year" to startYear)
            )
        }

        // BUG-27: All periods start as FUTURE so that generating a historical FY never
        // auto-switches the active context. Users must explicitly open the first period
        // they want to start using via the transition endpoint.
        val periods = (1..12).map { month ->
            val start = LocalDate.of(startYear, month, 1)
            val end = start.plusMonths(1).minusDays(1)
            Period(
                entityId = entityId,
                periodName = "${start.month} $startYear",
                startDate = start,
                endDate = end,
                status = PeriodStatus.FUTURE
            )
        }
        periodRepository.saveAll(periods)
    }

    /**
     * §10.1 — Returns all accounting periods for an entity, ordered by start date ascending.
     */
    @Transactional(readOnly = true)
    fun findAllByEntity(entityId: UUID): List<Period> =
        periodRepository.findAllByEntityId(entityId)
            .sortedBy { it.startDate }

    fun findPeriodForDate(entityId: UUID, date: LocalDate): Period {
        return periodRepository.findByEntityIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(entityId, date, date)
            .orElseThrow { ValidationException("PERIOD_NOT_FOUND", "No period found for date $date") }
    }

    /**
     * §10.1 — Period lookup by primary key. Used by [com.qesuite.accounting.ap.config.PeriodLockInterceptor]
     * and any cross-module collaborator that already holds the period UUID.
     * Throws [ResourceNotFoundException] (HTTP 404 / `PERIOD_NOT_FOUND`) if absent.
     */
    fun findById(periodId: UUID): Period {
        return periodRepository.findById(periodId)
            .orElseThrow { ResourceNotFoundException("PERIOD_NOT_FOUND", periodId, "AccountingPeriod") }
    }

    @Transactional
    @Auditable(action = AuditAction.UPDATE, resourceType = "ACCOUNTING_PERIOD")
    fun transitionPeriod(
        @AuditResourceId periodId: UUID,
        nextStatus: PeriodStatus
    ): Period {
        val period = periodRepository.findById(periodId)
            .orElseThrow { ValidationException("PERIOD_NOT_FOUND", "Period $periodId not found.") }

        if (!period.status.canTransitionTo(nextStatus)) {
            throw ValidationException(
                "INVALID_STATE_TRANSITION",
                "Cannot transition from ${period.status} to $nextStatus"
            )
        }

        // BUG-25: Only one OPEN period is allowed per entity at any time.
        if (nextStatus == PeriodStatus.OPEN &&
            periodRepository.existsByEntityIdAndStatus(period.entityId, PeriodStatus.OPEN)
        ) {
            throw BusinessRuleViolationException(
                errorCode = "PERIOD_ALREADY_OPEN",
                message = "Another period is already OPEN. Close it before opening a new one."
            )
        }

        period.status = nextStatus

        // BUG-30: Capture audit trail when a period is closed.
        if (nextStatus == PeriodStatus.CLOSED) {
            val currentUser = try { SecurityUtils.currentUser() } catch (_: Exception) { null }
            period.closedByUserId = currentUser?.userId
            period.closedAt = Instant.now()
        }

        return periodRepository.save(period)
    }

    @Transactional
    @Auditable(action = AuditAction.CLOSE, resourceType = "ACCOUNTING_PERIOD")
    fun closePeriod(@AuditResourceId periodId: UUID): Period {
        return transitionPeriod(periodId, PeriodStatus.CLOSED)
    }
}

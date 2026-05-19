package com.qesuite.accounting.ap.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * §10.1 — Fiscal Calendar Manager
 */
@Entity
@Table(name = "accounting_periods", uniqueConstraints = [
    UniqueConstraint(columnNames = ["entity_id", "period_name"]),
    UniqueConstraint(columnNames = ["entity_id", "start_date", "end_date"])
])
class Period(
    entityId: UUID,

    @Column(name = "period_name", length = 50, nullable = false)
    @Schema(example = "JANUARY 2026")
    var periodName: String,

    @Column(name = "start_date", nullable = false)
    @Schema(example = "2026-01-01")
    var startDate: LocalDate,

    @Column(name = "end_date", nullable = false)
    @Schema(example = "2026-01-31")
    var endDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Schema(example = "OPEN")
    var status: PeriodStatus = PeriodStatus.FUTURE,

    /** §10.3 — Populated when the period is transitioned to CLOSED status. */
    @Column(name = "closed_by_user_id", nullable = true)
    @Schema(description = "ID of the user who closed this period", example = "550e8400-e29b-41d4-a716-446655440000")
    var closedByUserId: UUID? = null,

    /** §10.3 — Timestamp of when the period was closed. */
    @Column(name = "closed_at", nullable = true)
    @Schema(description = "Timestamp when the period was closed")
    var closedAt: Instant? = null

) : BaseFinancialEntity(entityId = entityId)

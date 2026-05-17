package com.qesuite.accounting.ap.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*
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
    var status: PeriodStatus = PeriodStatus.FUTURE

) : BaseFinancialEntity(entityId = entityId)

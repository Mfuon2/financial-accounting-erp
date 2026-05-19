package com.qesuite.accounting.ap.repository

import com.qesuite.accounting.ap.domain.Period
import com.qesuite.accounting.ap.domain.PeriodStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface PeriodRepository : JpaRepository<Period, UUID> {
    fun findByEntityIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
        entityId: UUID,
        date: LocalDate,
        date2: LocalDate
    ): Optional<Period>

    fun findAllByEntityId(entityId: UUID): List<Period>

    fun existsByEntityIdAndPeriodName(entityId: UUID, periodName: String): Boolean

    fun countByEntityId(entityId: UUID): Long

    fun existsByEntityIdAndStatus(entityId: UUID, status: PeriodStatus): Boolean
}

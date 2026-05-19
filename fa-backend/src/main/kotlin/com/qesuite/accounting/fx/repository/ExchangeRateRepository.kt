package com.qesuite.accounting.fx.repository

import com.qesuite.accounting.fx.domain.ExchangeRate
import com.qesuite.accounting.fx.domain.RateType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface ExchangeRateRepository : JpaRepository<ExchangeRate, UUID> {
    fun findByEntityIdAndFromCurrencyAndToCurrencyAndRateDateAndRateType(
        entityId: UUID,
        fromCurrency: String,
        toCurrency: String,
        rateDate: LocalDate,
        rateType: RateType
    ): Optional<ExchangeRate>

    fun findAllByEntityIdOrderByRateDateDescFromCurrencyAsc(entityId: UUID): List<ExchangeRate>
}

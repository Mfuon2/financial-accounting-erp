package com.qesuite.accounting.fx.service

import com.qesuite.accounting.fx.domain.ExchangeRate
import com.qesuite.accounting.fx.domain.RateType
import com.qesuite.accounting.fx.repository.ExchangeRateRepository
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

@Service
class ExchangeRateService(private val exchangeRateRepository: ExchangeRateRepository) {

    /**
     * §13.2 — Get Rate for Transaction.
     *
     * Returns BigDecimal.ONE (scale 6) when fromCurrency == toCurrency — that is always valid.
     * Throws [ResourceNotFoundException] (HTTP 404, code EXCHANGE_RATE_NOT_FOUND) when no rate is
     * available, rather than silently returning 1.0 and producing incorrect financial figures.
     */
    fun getRate(
        entityId: UUID,
        fromCurrency: String,
        toCurrency: String,
        date: LocalDate,
        type: RateType = RateType.SPOT
    ): BigDecimal {
        // Same-currency: rate is always 1 — no lookup needed
        if (fromCurrency == toCurrency) return BigDecimal.ONE.setScale(6, RoundingMode.HALF_EVEN)

        return exchangeRateRepository.findByEntityIdAndFromCurrencyAndToCurrencyAndRateDateAndRateType(
            entityId, fromCurrency, toCurrency, date, type
        ).map { it.rateValue }
            .orElseThrow {
                ResourceNotFoundException(
                    errorCode    = "EXCHANGE_RATE_NOT_FOUND",
                    resourceId   = "$fromCurrency/$toCurrency on $date ($type)",
                    resourceType = "ExchangeRate"
                )
            }
    }

    fun saveRate(rate: ExchangeRate): ExchangeRate {
        return exchangeRateRepository.save(rate)
    }
}

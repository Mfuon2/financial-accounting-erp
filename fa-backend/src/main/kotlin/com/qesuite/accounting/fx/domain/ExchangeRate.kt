package com.qesuite.accounting.fx.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

enum class RateType {
    SPOT,
    CLOSING,
    AVERAGE
}

@Entity
@Table(name = "exchange_rates", uniqueConstraints = [
    UniqueConstraint(columnNames = ["entity_id", "from_currency", "to_currency", "rate_date", "rate_type"])
])
class ExchangeRate(
    entityId: UUID,

    @Column(name = "from_currency", length = 3, nullable = false)
    val fromCurrency: String,

    @Column(name = "to_currency", length = 3, nullable = false)
    val toCurrency: String,

    @Column(name = "rate_date", nullable = false)
    val rateDate: LocalDate,

    @Column(name = "rate_value", precision = 20, scale = 6, nullable = false)
    var rateValue: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false)
    val rateType: RateType

) : BaseFinancialEntity(entityId = entityId)

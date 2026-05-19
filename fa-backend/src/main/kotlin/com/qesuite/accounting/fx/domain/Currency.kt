package com.qesuite.accounting.fx.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import jakarta.persistence.*
import java.util.*

@Entity
@Table(name = "currencies", uniqueConstraints = [
    UniqueConstraint(columnNames = ["entity_id", "currency_code"])
])
class Currency(
    entityId: UUID,

    @Column(name = "currency_code", length = 3, nullable = false)
    val currencyCode: String,

    @Column(name = "currency_name", length = 50, nullable = false)
    var currencyName: String,

    @Column(name = "is_functional", nullable = false)
    var isFunctional: Boolean = false,

    @Column(name = "symbol", length = 10, nullable = true)
    var symbol: String? = null,

    @Column(name = "decimals", nullable = false)
    var decimals: Int = 2

) : BaseFinancialEntity(entityId = entityId)

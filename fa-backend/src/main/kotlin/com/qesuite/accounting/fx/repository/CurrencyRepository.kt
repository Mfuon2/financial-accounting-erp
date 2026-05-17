package com.qesuite.accounting.fx.repository

import com.qesuite.accounting.fx.domain.Currency
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface CurrencyRepository : JpaRepository<Currency, UUID> {
    fun findByEntityIdAndCurrencyCode(entityId: UUID, currencyCode: String): Optional<Currency>
    fun findByEntityIdAndIsFunctionalTrue(entityId: UUID): Optional<Currency>
    fun findAllByEntityId(entityId: UUID): List<Currency>
}

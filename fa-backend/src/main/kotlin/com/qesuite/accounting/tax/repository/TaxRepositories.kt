package com.qesuite.accounting.tax.repository

import com.qesuite.accounting.tax.domain.TaxCode
import com.qesuite.accounting.tax.domain.TaxRate
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
interface TaxCodeRepository : JpaRepository<TaxCode, UUID> {
    fun findByEntityIdAndCode(entityId: UUID, code: String): Optional<TaxCode>
    fun findByEntityId(entityId: UUID): List<TaxCode>
    fun existsByEntityIdAndCode(entityId: UUID, code: String): Boolean
}

@Repository
interface TaxRateRepository : JpaRepository<TaxRate, UUID> {
    @Query("SELECT r FROM TaxRate r WHERE r.taxCode.id = :taxCodeId AND r.effectiveFrom <= :date ORDER BY r.effectiveFrom DESC LIMIT 1")
    fun findEffectiveRate(taxCodeId: UUID, date: LocalDate): TaxRate?
    fun findByTaxCodeId(taxCodeId: UUID): List<TaxRate>
}

package com.qesuite.accounting.tax.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import jakarta.persistence.*
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Entity
@Table(name = "tax_codes", uniqueConstraints = [
    UniqueConstraint(columnNames = ["entity_id", "code"])
])
@Schema(description = "Master data for tax classifications (§13)")
class TaxCode(
    entityId: UUID,

    @Column(name = "code", length = 20, nullable = false)
    @Schema(example = "VAT_16")
    var code: String,

    @Column(name = "description")
    @Schema(example = "Value Added Tax 16%")
    var description: String?,

    @Column(name = "is_recoverable", nullable = false)
    @Schema(description = "True if the tax can be claimed back (Input VAT)")
    var isRecoverable: Boolean = true

) : BaseFinancialEntity(entityId = entityId)

@Entity
@Table(name = "tax_rates")
class TaxRate(
    entityId: UUID,

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tax_code_id", nullable = false)
    var taxCode: TaxCode,

    @Column(name = "rate", precision = 10, scale = 4, nullable = false)
    @Schema(example = "0.1600")
    var rate: BigDecimal,

    @Column(name = "effective_from", nullable = false)
    var effectiveFrom: java.time.LocalDate

) : BaseFinancialEntity(entityId = entityId)

package com.qesuite.accounting.assets.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

enum class DepreciationMethod {
    STRAIGHT_LINE,
    DOUBLE_DECLINING_BALANCE
}

enum class AssetStatus {
    ACTIVE,
    DISPOSED,
    FULLY_DEPRECIATED
}

@Entity
@Table(name = "fixed_assets")
class FixedAsset(
    entityId: UUID,
    periodId: UUID? = null,

    @Column(name = "asset_code", length = 50, nullable = false)
    val assetCode: String,

    @Column(name = "asset_name", length = 200, nullable = false)
    var assetName: String,

    /**
     * §11.1 — COA Mappings
     */
    @Column(name = "cost_account_id", nullable = false)
    val costAccountId: UUID,

    @Column(name = "accum_dep_account_id", nullable = false)
    val accumDepAccountId: UUID,

    @Column(name = "dep_expense_account_id", nullable = false)
    val depExpenseAccountId: UUID,

    @Column(name = "acquisition_date", nullable = false)
    val acquisitionDate: LocalDate,

    @Column(name = "acquisition_cost", precision = 20, scale = 6, nullable = false)
    val acquisitionCost: BigDecimal,

    @Column(name = "salvage_value", precision = 20, scale = 6, nullable = false)
    var salvageValue: BigDecimal = BigDecimal.ZERO,

    @Column(name = "useful_life_months", nullable = false)
    var usefulLifeMonths: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false)
    var depreciationMethod: DepreciationMethod = DepreciationMethod.STRAIGHT_LINE,

    @Column(name = "category", length = 80)
    var category: String? = null,

    @Column(name = "assigned_to", length = 200)
    var assignedTo: String? = null,

    @Column(name = "accumulated_depreciation", precision = 20, scale = 6, nullable = false)
    var accumulatedDepreciation: BigDecimal = BigDecimal.ZERO,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AssetStatus = AssetStatus.ACTIVE

) : BaseFinancialEntity(entityId = entityId, periodId = periodId) {

    /** Net book value — acquisition cost minus accumulated depreciation. */
    @get:Transient
    val netBookValue: BigDecimal
        get() = acquisitionCost.subtract(accumulatedDepreciation)

    /** Straight-line monthly charge (informational — actual run uses service). */
    @get:Transient
    val monthlyDepreciation: BigDecimal
        get() = if (usefulLifeMonths <= 0) BigDecimal.ZERO
                else acquisitionCost.subtract(salvageValue)
                         .divide(BigDecimal(usefulLifeMonths), 6, java.math.RoundingMode.HALF_EVEN)
}

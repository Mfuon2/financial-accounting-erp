package com.qesuite.accounting.budgeting.dto

import com.qesuite.accounting.budgeting.domain.BudgetStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "A single (account, period, amount) line within a budget create/update request")
data class BudgetLineCommand(
    @field:NotNull(message = "accountId is required")
    val accountId: UUID,

    @field:NotNull(message = "periodId is required")
    val periodId: UUID,

    @field:NotNull(message = "amount is required")
    val amount: BigDecimal,
)

@Schema(description = "Create a new budget in DRAFT status")
data class CreateBudgetCommand(
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,

    @field:NotBlank(message = "name is required")
    val name: String,

    val notes: String? = null,

    @field:NotEmpty(message = "A budget must have at least one line")
    @field:Valid
    val lines: List<BudgetLineCommand>,
)

@Schema(description = "Update a DRAFT budget's name, notes, and/or lines (wholesale line replacement)")
data class UpdateBudgetCommand(
    val name: String? = null,
    val notes: String? = null,
    @field:Valid
    val lines: List<BudgetLineCommand>? = null,
)

@Schema(description = "Void a budget (DRAFT or APPROVED) with a mandatory reason")
data class VoidBudgetCommand(
    @field:NotBlank(message = "A non-blank reason is required to void a budget")
    val reason: String,
)

@Schema(description = "A budget line enriched with the account/period it references, for display")
data class BudgetLineResponse(
    val id: UUID,
    val accountId: UUID,
    val accountCode: String,
    val accountName: String,
    val periodId: UUID,
    val periodName: String,
    val amount: BigDecimal,
)

@Schema(description = "A budget with its lines")
data class BudgetResponse(
    val id: UUID,
    val entityId: UUID,
    val name: String,
    val status: BudgetStatus,
    val totalAmount: BigDecimal,
    val notes: String?,
    val version: Long,
    val lines: List<BudgetLineResponse>,
)

@Schema(description = "One row of a budget-vs-actual variance report")
data class BudgetVarianceLineResponse(
    val accountId: UUID,
    val accountCode: String,
    val accountName: String,
    val periodId: UUID,
    val periodName: String,
    val budgetedAmount: BigDecimal,
    val actualAmount: BigDecimal,
    @Schema(description = "actualAmount - budgetedAmount, signed per the account's normal balance — " +
        "whether a positive variance is favorable or unfavorable depends on account type (e.g. " +
        "positive is favorable for a revenue account, unfavorable for an expense account); this is " +
        "left for the caller to interpret, not hardcoded here")
    val variance: BigDecimal,
    @Schema(description = "variance as a percentage of budgetedAmount; null when budgetedAmount is zero")
    val variancePercent: BigDecimal?,
)

@Schema(description = "Budget-vs-actual variance report for a single budget")
data class BudgetVarianceReportResponse(
    val budgetId: UUID,
    val budgetName: String,
    val lines: List<BudgetVarianceLineResponse>,
    val totalBudgeted: BigDecimal,
    val totalActual: BigDecimal,
    val totalVariance: BigDecimal,
)

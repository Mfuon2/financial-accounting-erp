package com.qesuite.accounting.expenses.dto

import com.qesuite.accounting.expenses.domain.ExpenseClaimStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Schema(description = "A single expense item within a claim create/update request")
data class ExpenseClaimLineCommand(
    @field:NotNull(message = "accountId is required")
    val accountId: UUID,

    @field:NotBlank(message = "description is required")
    val description: String,

    @field:NotNull(message = "amount is required")
    val amount: BigDecimal,

    @field:NotNull(message = "dateIncurred is required")
    val dateIncurred: LocalDate,

    val receiptReference: String? = null,
)

@Schema(description = "Create a new expense claim in DRAFT status")
data class CreateExpenseClaimCommand(
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,

    @field:NotNull(message = "employeeId is required")
    val employeeId: UUID,

    @field:NotNull(message = "claimDate is required")
    val claimDate: LocalDate,

    val notes: String? = null,

    @field:NotEmpty(message = "An expense claim must have at least one line")
    @field:Valid
    val lines: List<ExpenseClaimLineCommand>,
)

@Schema(description = "Update a DRAFT expense claim's claim date, notes, and/or lines (wholesale line replacement)")
data class UpdateExpenseClaimCommand(
    val claimDate: LocalDate? = null,
    val notes: String? = null,
    @field:Valid
    val lines: List<ExpenseClaimLineCommand>? = null,
)

@Schema(description = "Reject a SUBMITTED expense claim with a mandatory reason")
data class RejectExpenseClaimCommand(
    @field:NotBlank(message = "A non-blank reason is required to reject an expense claim")
    val reason: String,
)

@Schema(description = "An expense claim line enriched with the account it references, for display")
data class ExpenseClaimLineResponse(
    val id: UUID,
    val accountId: UUID,
    val accountCode: String,
    val accountName: String,
    val description: String,
    val amount: BigDecimal,
    val dateIncurred: LocalDate,
    val receiptReference: String?,
)

@Schema(description = "An expense claim with its lines")
data class ExpenseClaimResponse(
    val id: UUID,
    val entityId: UUID,
    val employeeId: UUID,
    val employeeName: String,
    val claimDate: LocalDate,
    val status: ExpenseClaimStatus,
    val totalAmount: BigDecimal,
    val notes: String?,
    val journalEntryId: UUID?,
    val rejectionReason: String?,
    val version: Long,
    val lines: List<ExpenseClaimLineResponse>,
)

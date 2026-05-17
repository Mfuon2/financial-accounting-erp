package com.qesuite.accounting.approvals.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.util.UUID

@Schema(description = "A single pending-approval item, normalised across entity types")
data class PendingApprovalItem(
    val id: UUID,
    val type: String,
    val ref: String,
    val title: String,
    val amount: BigDecimal,
    val currency: String,
    val submittedBy: String,
    val waitingFor: String,
    val submittedAt: String,
    val entityId: UUID
)

@Schema(description = "Approve or reject command — optionally includes a reason")
data class ApprovalActionCommand(
    @field:NotBlank
    val type: String,
    val reason: String = ""
)

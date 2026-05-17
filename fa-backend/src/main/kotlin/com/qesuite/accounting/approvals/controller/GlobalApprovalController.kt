package com.qesuite.accounting.approvals.controller

import com.qesuite.accounting.approvals.dto.ApprovalActionCommand
import com.qesuite.accounting.approvals.dto.PendingApprovalItem
import com.qesuite.accounting.approvals.service.GlobalApprovalService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/approvals")
@Tag(
    name = "Global Approvals Queue",
    description = """
Cross-entity-type approval queue. Surfaces pending journal entries and draft invoices
that require sign-off from the current user's role.

**Approve** routes to the entity-specific `approve` / `post` action.
**Reject** routes to `reject` (journal entries) or `void` (invoices).
"""
)
class GlobalApprovalController(
    private val approvalService: GlobalApprovalService
) {

    @GetMapping
    @Operation(
        summary = "List all pending approvals",
        description = "Returns journal entries in PENDING_APPROVAL and invoices in DRAFT, ordered newest-first."
    )
    fun list(): ApiResponse<List<PendingApprovalItem>> {
        val entityId = SecurityUtils.currentEntityIdOrSystem()
        return ApiResponse.success(approvalService.listPending(entityId))
    }

    @PostMapping("/{id}/approve")
    @Operation(
        summary = "Approve an item",
        description = "Routes to JournalService.postEntry (JOURNAL_ENTRY) or InvoiceService.approve (INVOICE)."
    )
    fun approve(
        @PathVariable @Parameter(description = "Item UUID") id: UUID,
        @Valid @RequestBody command: ApprovalActionCommand
    ): ApiResponse<String> {
        approvalService.approve(id, command.type)
        return ApiResponse.success("Approved")
    }

    @PostMapping("/{id}/reject")
    @Operation(
        summary = "Reject an item",
        description = "Returns journal entry to DRAFT with reason appended. Voids invoice with reason."
    )
    fun reject(
        @PathVariable @Parameter(description = "Item UUID") id: UUID,
        @Valid @RequestBody command: ApprovalActionCommand
    ): ApiResponse<String> {
        approvalService.reject(id, command.type, command.reason)
        return ApiResponse.success("Rejected")
    }
}

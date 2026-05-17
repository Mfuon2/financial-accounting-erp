package com.qesuite.accounting.shared.audit.controller

import com.qesuite.accounting.shared.audit.domain.AuditLog
import com.qesuite.accounting.shared.audit.service.AuditService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(
    name = "Global Audit Trail",
    description = """
Surfaces the forensic-grade, immutable audit log for the current entity. 
Includes all critical financial events (postings, voids, revaluations, etc.) 
and user actions.
"""
)
class GlobalAuditController(
    private val auditService: AuditService
) {

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'AUDITOR', 'CONTROLLER_CFO')")
    @Operation(
        summary = "List global audit logs",
        description = "Returns a paginated list of audit logs for the current entity, ordered newest-first by default."
    )
    fun list(
        @PageableDefault(size = 50, sort = ["createdAt"], direction = Sort.Direction.DESC)
        @Parameter(hidden = true)
        pageable: Pageable
    ): ApiResponse<PagedResponse<AuditLog>> {
        val entityId = SecurityUtils.currentEntityIdOrSystem()
        val page = auditService.findByEntity(entityId, pageable)
        return ApiResponse.success(page.toPagedResponse { it })
    }
}

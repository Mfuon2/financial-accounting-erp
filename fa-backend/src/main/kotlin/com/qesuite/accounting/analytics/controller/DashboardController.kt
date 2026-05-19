package com.qesuite.accounting.analytics.controller

import com.qesuite.accounting.analytics.dto.DashboardSummaryResponse
import com.qesuite.accounting.analytics.dto.SparklineResponse
import com.qesuite.accounting.analytics.dto.TbSummaryResponse
import com.qesuite.accounting.analytics.service.DashboardService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/analytics/dashboard")
@Tag(
    name = "Analytics: Dashboard",
    description = """
KPI aggregates and sparkline data for the main dashboard.

All metrics are scoped to the authenticated user's entity (tenant). No `entityId` query
parameter is required — the entity is resolved from the JWT.
"""
)
class DashboardController(
    private val dashboardService: DashboardService
) {

    @GetMapping
    @Operation(
        summary = "Full dashboard summary",
        description = "Returns KPIs (cash, AR, MTD revenue, MTD expenses), 12-month sparklines, pending-approval count, and recent audit activity."
    )
    fun summary(): ApiResponse<DashboardSummaryResponse> {
        val entityId = SecurityUtils.currentEntityIdOrSystem()
        return ApiResponse.success(dashboardService.getSummary(entityId))
    }

    @GetMapping("/sparklines")
    @Operation(
        summary = "Sparkline data only",
        description = "Lightweight endpoint returning only the 12-month sparkline arrays and labels — suitable for background polling."
    )
    fun sparklines(): ApiResponse<SparklineResponse> {
        val entityId = SecurityUtils.currentEntityIdOrSystem()
        return ApiResponse.success(dashboardService.getSparklines(entityId))
    }

    @GetMapping("/tb-summary")
    @Operation(
        summary = "Trial balance summary by account type",
        description = "Returns aggregated net balances per account type (Assets, Liabilities, Equity, Revenue, Expenses) as of today, plus a balanced flag."
    )
    fun tbSummary(): ApiResponse<TbSummaryResponse> {
        val entityId = SecurityUtils.currentEntityIdOrSystem()
        return ApiResponse.success(dashboardService.getTbSummary(entityId))
    }
}

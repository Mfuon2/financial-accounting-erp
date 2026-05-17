package com.qesuite.accounting.analytics.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Full dashboard summary response — KPIs, sparklines, and recent activity")
data class DashboardSummaryResponse(
    val cashAndEquivalents: BigDecimal,
    val accountsReceivable: BigDecimal,
    val mtdRevenue: BigDecimal,
    val mtdExpenses: BigDecimal,
    val sparkRev: List<BigDecimal>,
    val sparkExp: List<BigDecimal>,
    val sparkAr: List<BigDecimal>,
    val sparkCash: List<BigDecimal>,
    val sparkLabels: List<String>,
    val pendingApprovals: Int,
    val recentAudit: List<RecentAuditItem>
)

@Schema(description = "A single entry in the recent-activity feed on the dashboard")
data class RecentAuditItem(
    val ts: String,
    val detail: String,
    val actor: String
)

@Schema(description = "Sparkline-only subset of the dashboard — used for lightweight polling")
data class SparklineResponse(
    val revenue: List<BigDecimal>,
    val expenses: List<BigDecimal>,
    val ar: List<BigDecimal>,
    val cash: List<BigDecimal>,
    val labels: List<String>
)

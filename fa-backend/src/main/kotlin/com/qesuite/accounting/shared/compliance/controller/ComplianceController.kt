package com.qesuite.accounting.shared.compliance.controller

import com.qesuite.accounting.shared.compliance.service.ComplianceResult
import com.qesuite.accounting.shared.compliance.service.ComplianceService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/compliance")
@Tag(
    name = "Module 12: IFRS Compliance",
    description = """
IFRS compliance validation checks that run against the Chart of Accounts and ledger data.

**IAS 1 Check:** Validates that every account's `ifrsCategory` is consistent with its
`accountType` — e.g. an ASSET account should not carry an OPERATING_EXPENSES category.
Violations indicate misconfigured accounts that would produce incorrect financial statements.
"""
)
class ComplianceController(private val complianceService: ComplianceService) {

    @GetMapping("/ias1")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Run IAS 1 compliance check",
        description = """
Validates that every account in the entity's COA has an `ifrsCategory` consistent with
its `accountType`. For example:
- ASSET accounts must use CURRENT_ASSETS or NON_CURRENT_ASSETS
- REVENUE accounts must use REVENUE or OTHER_INCOME_EXPENSE
- EXPENSE accounts must use OPERATING_EXPENSES, COST_OF_SALES, FINANCE_COSTS, TAX_EXPENSE, or OTHER_INCOME_EXPENSE

Returns `passed = true` if no violations are found, along with a list of any violations
describing which accounts have inconsistent category assignments.

Run this check before generating financial statements to ensure all balances are
classified correctly.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Compliance check completed (check `passed` field for result)"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Missing entityId parameter")
    )
    fun checkIas1Compliance(
        @RequestParam
        @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        entityId: UUID
    ): ApiResponse<ComplianceResult> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(complianceService.validateIas1Compliance(entityId))
    }
}

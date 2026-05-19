package com.qesuite.accounting.shared.compliance.service

import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.IfrsCategory
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.fx.repository.CurrencyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ComplianceService(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository
) {

    @Transactional(readOnly = true)
    fun validateIas1Compliance(entityId: UUID): ComplianceResult {
        val accounts = accountRepository.findAllByEntityId(entityId)
        val violations = mutableListOf<String>()

        accounts.forEach { account ->
            if (!isCategoryConsistentWithType(account.ifrsCategory, account.accountType)) {
                violations.add("Account ${account.accountCode} (${account.accountName}) has inconsistent category ${account.ifrsCategory} for type ${account.accountType}")
            }
        }

        val checks = mutableListOf<ComplianceCheckItem>()

        // Check: account classification consistency
        if (violations.isEmpty()) {
            checks.add(ComplianceCheckItem("ias1-classification", "Account classification consistent",
                "All ${accounts.size} accounts have IFRS categories consistent with their account types", "PASS"))
        } else {
            checks.add(ComplianceCheckItem("ias1-classification", "Account classification consistent",
                "${violations.size} account${if (violations.size > 1) "s have" else " has"} inconsistent IFRS category assignments", "FAIL"))
        }

        // Check: functional currency registered
        val functionalCcy = currencyRepository.findByEntityIdAndIsFunctionalTrue(entityId)
        if (functionalCcy.isPresent) {
            val ccy = functionalCcy.get()
            checks.add(ComplianceCheckItem("ias1-functional-ccy", "Functional currency disclosed",
                "${ccy.currencyCode} (${ccy.currencyName}) registered as functional currency", "PASS"))
        } else {
            checks.add(ComplianceCheckItem("ias1-functional-ccy", "Functional currency disclosed",
                "No functional currency is registered — required under IAS 21", "WARN"))
        }

        // Check: chart of accounts covers all required account types
        val presentTypes = accounts.map { it.accountType }.toSet()
        val requiredTypes = setOf(AccountType.ASSET, AccountType.LIABILITY, AccountType.EQUITY, AccountType.REVENUE, AccountType.EXPENSE)
        val missingTypes = requiredTypes - presentTypes
        if (missingTypes.isEmpty()) {
            checks.add(ComplianceCheckItem("ias1-coa-coverage", "Chart of accounts coverage",
                "All five account types present (Asset, Liability, Equity, Revenue, Expense)", "PASS"))
        } else {
            checks.add(ComplianceCheckItem("ias1-coa-coverage", "Chart of accounts coverage",
                "Missing account types: ${missingTypes.joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }}", "WARN"))
        }

        // Check: postable accounts (leaf accounts) exist
        val postable = accounts.filter { !it.isHeader }
        if (postable.isNotEmpty()) {
            checks.add(ComplianceCheckItem("ias1-postable", "Postable accounts configured",
                "${postable.size} postable account${if (postable.size > 1) "s" else ""} available for journal entries", "PASS"))
        } else {
            checks.add(ComplianceCheckItem("ias1-postable", "Postable accounts configured",
                "No postable (non-header) accounts found — journal entries cannot be posted", "WARN"))
        }

        val passed = checks.all { it.status == "PASS" }
        return ComplianceResult(passed = passed, violations = violations, checks = checks)
    }

    private fun isCategoryConsistentWithType(category: IfrsCategory, type: AccountType): Boolean {
        return when (type) {
            AccountType.ASSET -> category == IfrsCategory.CURRENT_ASSETS || category == IfrsCategory.NON_CURRENT_ASSETS
            AccountType.LIABILITY -> category == IfrsCategory.CURRENT_LIABILITIES || category == IfrsCategory.NON_CURRENT_LIABILITIES
            AccountType.EQUITY -> category == IfrsCategory.EQUITY
            AccountType.REVENUE -> category == IfrsCategory.REVENUE || category == IfrsCategory.OTHER_INCOME_EXPENSE
            AccountType.EXPENSE -> category == IfrsCategory.OPERATING_EXPENSES ||
                                   category == IfrsCategory.COST_OF_SALES ||
                                   category == IfrsCategory.FINANCE_COSTS ||
                                   category == IfrsCategory.TAX_EXPENSE ||
                                   category == IfrsCategory.OTHER_INCOME_EXPENSE
        }
    }
}

data class ComplianceCheckItem(
    val id: String,
    val name: String,
    val detail: String,
    val status: String
)

data class ComplianceResult(
    val passed: Boolean,
    val violations: List<String>,
    val checks: List<ComplianceCheckItem> = emptyList()
)

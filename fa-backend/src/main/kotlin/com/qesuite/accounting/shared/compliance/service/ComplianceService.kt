package com.qesuite.accounting.shared.compliance.service

import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.IfrsCategory
import com.qesuite.accounting.coa.repository.AccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ComplianceService(
    private val accountRepository: AccountRepository
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

        return ComplianceResult(
            passed = violations.isEmpty(),
            violations = violations
        )
    }

    private fun isCategoryConsistentWithType(category: IfrsCategory, type: AccountType): Boolean {
        return when (type) {
            AccountType.ASSET -> category == IfrsCategory.CURRENT_ASSETS || category == IfrsCategory.NON_CURRENT_ASSETS
            AccountType.LIABILITY -> category == IfrsCategory.CURRENT_LIABILITIES || category == IfrsCategory.NON_CURRENT_LIABILITIES
            AccountType.EQUITY -> category == IfrsCategory.EQUITY
            // REVENUE accounts may be classified as REVENUE (primary income) or
            // OTHER_INCOME_EXPENSE (other income / gain on disposal etc.) under IAS 1.
            AccountType.REVENUE -> category == IfrsCategory.REVENUE ||
                                   category == IfrsCategory.OTHER_INCOME_EXPENSE
            AccountType.EXPENSE -> category == IfrsCategory.OPERATING_EXPENSES || 
                                   category == IfrsCategory.COST_OF_SALES ||
                                   category == IfrsCategory.FINANCE_COSTS ||
                                   category == IfrsCategory.TAX_EXPENSE ||
                                   category == IfrsCategory.OTHER_INCOME_EXPENSE
        }
    }
}

data class ComplianceResult(val passed: Boolean, val violations: List<String>)

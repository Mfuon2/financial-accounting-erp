package com.qesuite.accounting.coa.service

import com.qesuite.accounting.coa.domain.*
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val currencyRepository: com.qesuite.accounting.fx.repository.CurrencyRepository,
    private val ledgerEntryRepository: com.qesuite.accounting.ledger.repository.LedgerEntryRepository
) {

    @Transactional
    fun createAccount(command: CreateAccountCommand): Account {
        // Resolve currency: use command's explicit value, or fall back to entity functional currency
        val resolvedCurrency = if (command.currencyCode != null) {
            if (!currencyRepository.findByEntityIdAndCurrencyCode(command.entityId, command.currencyCode).isPresent) {
                throw ValidationException(
                    "INVALID_CURRENCY",
                    "Currency '${command.currencyCode}' is not registered for this entity. " +
                    "Register it via POST /api/v1/fx/currencies first."
                )
            }
            command.currencyCode
        } else {
            // Fall back to entity's functional currency
            currencyRepository.findByEntityIdAndIsFunctionalTrue(command.entityId)
                .orElseThrow {
                    ValidationException(
                        "FUNCTIONAL_CURRENCY_NOT_SET",
                        "No functional currency configured for entity ${command.entityId}. " +
                        "Set one via POST /api/v1/fx/currencies with isFunctional=true."
                    )
                }.currencyCode
        }

        if (accountRepository.existsByEntityIdAndAccountCode(command.entityId, command.accountCode)) {
            throw ValidationException("DUPLICATE_ACCOUNT_CODE", "Account code ${command.accountCode} already exists.")
        }
        if (accountRepository.existsByEntityIdAndAccountName(command.entityId, command.accountName)) {
            throw ValidationException("DUPLICATE_ACCOUNT_NAME", "Account name ${command.accountName} already exists.")
        }

        val subtype = command.accountSubtype
        val type = subtype.parentType
        val normalBalance = subtype.normalBalanceOverride ?: type.normalBalance
        validateHierarchy(command.entityId, command.parentAccountId)

        val account = Account(
            entityId = command.entityId,
            accountCode = command.accountCode,
            accountName = command.accountName,
            accountType = type,
            accountSubtype = subtype,
            normalBalance = normalBalance,
            isTemporary = isTemporaryType(type),
            parentAccountId = command.parentAccountId,
            ifrsCategory = command.ifrsCategory,
            ifrsClassification = command.ifrsClassification,
            currencyCode = resolvedCurrency
        )
        val saved = accountRepository.save(account)

        // Promote the parent to header status so it can no longer receive direct postings
        if (command.parentAccountId != null) {
            accountRepository.findById(command.parentAccountId).ifPresent { parent ->
                if (!parent.isHeader) {
                    parent.isHeader = true
                    accountRepository.save(parent)
                }
            }
        }

        return saved
    }

    @Transactional(readOnly = true)
    fun getAllAccounts(
        entityId: UUID,
        type: AccountType? = null,
        subtype: AccountSubtype? = null,
        isActive: Boolean? = null,
        parentAccountId: UUID? = null
    ): List<Account> {
        // FIX: use indexed entity-scoped query instead of full table scan
        return accountRepository.findAllByEntityId(entityId).filter {
            (type == null || it.accountType == type) &&
            (subtype == null || it.accountSubtype == subtype) &&
            (isActive == null || it.isActive == isActive) &&
            (parentAccountId == null || it.parentAccountId == parentAccountId)
        }
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): Account {
        return accountRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("ACCOUNT_NOT_FOUND", id, "Account") }
    }

    @Transactional
    fun updateAccount(id: UUID, command: UpdateAccountCommand): Account {
        val account = findById(id)
        if (account.accountCode != command.accountCode) {
            if (ledgerEntryRepository.existsByAccountId(id)) {
                throw ValidationException("IMMUTABLE_ACCOUNT_CODE", "Account code cannot be changed once transactions have been posted against it.")
            }
            if (accountRepository.existsByEntityIdAndAccountCode(account.entityId, command.accountCode)) {
                throw ValidationException("DUPLICATE_ACCOUNT_CODE", "Account code ${command.accountCode} already exists.")
            }
            account.accountCode = command.accountCode
        }
        account.accountName = command.accountName
        account.accountSubtype = command.accountSubtype
        account.accountType = command.accountSubtype.parentType
        account.normalBalance = command.accountSubtype.normalBalanceOverride ?: account.accountType.normalBalance
        account.ifrsCategory = command.ifrsCategory
        account.ifrsClassification = command.ifrsClassification
        return accountRepository.save(account)
    }

    @Transactional
    fun deactivateAccount(id: UUID) {
        val account = findById(id)
        if (ledgerEntryRepository.existsByAccountId(id)) {
            throw ValidationException("ACCOUNT_DEACTIVATION_BLOCKED", "Account $id cannot be deactivated because it has existing ledger history.")
        }
        account.isActive = false
        accountRepository.save(account)
    }

    @Transactional(readOnly = true)
    fun getHierarchy(id: UUID): List<Account> {
        val hierarchy = mutableListOf<Account>()
        var current: Account? = findById(id)
        while (current != null) {
            hierarchy.add(current)
            current = current.parentAccountId?.let { accountRepository.findById(it).orElse(null) }
        }
        return hierarchy.reversed()
    }

    @Transactional(readOnly = true)
    fun getBalance(id: UUID, asOfDate: LocalDate? = null): BigDecimal {
        val account = findById(id)
        val date = asOfDate ?: LocalDate.now()
        val totalDebits = ledgerEntryRepository.sumFunctionalDebits(id, date) ?: BigDecimal.ZERO
        val totalCredits = ledgerEntryRepository.sumFunctionalCredits(id, date) ?: BigDecimal.ZERO
        return if (account.normalBalance == NormalBalance.DEBIT) {
            totalDebits.subtract(totalCredits)
        } else {
            totalCredits.subtract(totalDebits)
        }
    }

    @Transactional
    fun applyTemplate(entityId: UUID, template: CoaTemplate) {
        when (template) {
            CoaTemplate.SERVICE -> {
                createAccount(CreateAccountCommand(entityId, "1000", "Cash and Bank", AccountSubtype.CASH_AND_EQUIVALENTS, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1200", "Accounts Receivable", AccountSubtype.CURRENT_RECEIVABLE, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1500", "Prepaid Expenses", AccountSubtype.CURRENT_PREPAID, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1600", "Property Plant & Equipment", AccountSubtype.NON_CURRENT_PPE, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1620", "Accumulated Depreciation", AccountSubtype.ACCUMULATED_DEPRECIATION, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "2000", "Accounts Payable", AccountSubtype.CURRENT_PAYABLE, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "2100", "Accrued Liabilities", AccountSubtype.CURRENT_ACCRUED, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "2200", "Deferred Revenue", AccountSubtype.CURRENT_DEFERRED_REVENUE, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "3000", "Share Capital", AccountSubtype.SHARE_CAPITAL, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "3100", "Retained Earnings", AccountSubtype.RETAINED_EARNINGS, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "4000", "Service Revenue", AccountSubtype.OPERATING_REVENUE, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "4900", "Other Income", AccountSubtype.OTHER_INCOME, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "5000", "Cost of Services", AccountSubtype.COGS, ifrsCategory = IfrsCategory.COST_OF_SALES))
                createAccount(CreateAccountCommand(entityId, "6000", "Operating Expenses", AccountSubtype.OPERATING_EXPENSES, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "6200", "Depreciation Expense", AccountSubtype.DEPRECIATION, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "7000", "Finance Costs", AccountSubtype.FINANCE_COST, ifrsCategory = IfrsCategory.FINANCE_COSTS))
                createAccount(CreateAccountCommand(entityId, "7500", "Tax Expense", AccountSubtype.TAX_EXPENSE, ifrsCategory = IfrsCategory.TAX_EXPENSE))
            }
            CoaTemplate.MERCHANDISING -> {
                createAccount(CreateAccountCommand(entityId, "1000", "Cash and Bank", AccountSubtype.CASH_AND_EQUIVALENTS, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1200", "Accounts Receivable", AccountSubtype.CURRENT_RECEIVABLE, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1300", "Inventory", AccountSubtype.CURRENT_INVENTORY, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1500", "Prepaid Expenses", AccountSubtype.CURRENT_PREPAID, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1600", "Property Plant & Equipment", AccountSubtype.NON_CURRENT_PPE, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1620", "Accumulated Depreciation", AccountSubtype.ACCUMULATED_DEPRECIATION, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "2000", "Accounts Payable", AccountSubtype.CURRENT_PAYABLE, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "2100", "Accrued Liabilities", AccountSubtype.CURRENT_ACCRUED, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "3000", "Share Capital", AccountSubtype.SHARE_CAPITAL, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "3100", "Retained Earnings", AccountSubtype.RETAINED_EARNINGS, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "4000", "Sales Revenue", AccountSubtype.OPERATING_REVENUE, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "5000", "Cost of Goods Sold", AccountSubtype.COGS, ifrsCategory = IfrsCategory.COST_OF_SALES))
                createAccount(CreateAccountCommand(entityId, "6000", "Selling & Distribution Expenses", AccountSubtype.OPERATING_EXPENSES, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "6100", "Administrative Expenses", AccountSubtype.OPERATING_EXPENSES, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "6200", "Depreciation Expense", AccountSubtype.DEPRECIATION, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "7000", "Finance Costs", AccountSubtype.FINANCE_COST, ifrsCategory = IfrsCategory.FINANCE_COSTS))
                createAccount(CreateAccountCommand(entityId, "7500", "Tax Expense", AccountSubtype.TAX_EXPENSE, ifrsCategory = IfrsCategory.TAX_EXPENSE))
            }
            CoaTemplate.MANUFACTURING -> {
                createAccount(CreateAccountCommand(entityId, "1000", "Cash and Bank", AccountSubtype.CASH_AND_EQUIVALENTS, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1200", "Accounts Receivable", AccountSubtype.CURRENT_RECEIVABLE, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1310", "Raw Materials Inventory", AccountSubtype.CURRENT_INVENTORY, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1320", "Work In Progress", AccountSubtype.CURRENT_INVENTORY, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1330", "Finished Goods Inventory", AccountSubtype.CURRENT_INVENTORY, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1500", "Prepaid Expenses", AccountSubtype.CURRENT_PREPAID, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1600", "Plant & Machinery", AccountSubtype.NON_CURRENT_PPE, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1620", "Accumulated Depreciation — Plant", AccountSubtype.ACCUMULATED_DEPRECIATION, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "2000", "Accounts Payable", AccountSubtype.CURRENT_PAYABLE, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "2100", "Accrued Wages Payable", AccountSubtype.CURRENT_ACCRUED, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "3000", "Share Capital", AccountSubtype.SHARE_CAPITAL, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "3100", "Retained Earnings", AccountSubtype.RETAINED_EARNINGS, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "4000", "Product Sales Revenue", AccountSubtype.OPERATING_REVENUE, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "5000", "Cost of Production", AccountSubtype.COGS, ifrsCategory = IfrsCategory.COST_OF_SALES))
                createAccount(CreateAccountCommand(entityId, "5100", "Direct Labour", AccountSubtype.COGS, ifrsCategory = IfrsCategory.COST_OF_SALES))
                createAccount(CreateAccountCommand(entityId, "5200", "Manufacturing Overhead", AccountSubtype.COGS, ifrsCategory = IfrsCategory.COST_OF_SALES))
                createAccount(CreateAccountCommand(entityId, "6000", "Selling & Admin Expenses", AccountSubtype.OPERATING_EXPENSES, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "6200", "Depreciation — Plant & Machinery", AccountSubtype.DEPRECIATION, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "7000", "Finance Costs", AccountSubtype.FINANCE_COST, ifrsCategory = IfrsCategory.FINANCE_COSTS))
                createAccount(CreateAccountCommand(entityId, "7500", "Tax Expense", AccountSubtype.TAX_EXPENSE, ifrsCategory = IfrsCategory.TAX_EXPENSE))
            }
            CoaTemplate.FINANCIAL_SERVICES -> {
                createAccount(CreateAccountCommand(entityId, "1000", "Cash and Bank", AccountSubtype.CASH_AND_EQUIVALENTS, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1100", "Loans and Advances Receivable", AccountSubtype.CURRENT_RECEIVABLE, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1200", "Investment Securities", AccountSubtype.NON_CURRENT_INVESTMENT, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1600", "Property and Equipment", AccountSubtype.NON_CURRENT_PPE, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "2000", "Customer Deposits", AccountSubtype.CURRENT_PAYABLE, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "2100", "Accrued Interest Payable", AccountSubtype.CURRENT_ACCRUED, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "2500", "Long-Term Borrowings", AccountSubtype.NON_CURRENT_LONG_TERM_DEBT, ifrsCategory = IfrsCategory.NON_CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "3000", "Share Capital", AccountSubtype.SHARE_CAPITAL, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "3100", "Retained Earnings", AccountSubtype.RETAINED_EARNINGS, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "4000", "Interest Income", AccountSubtype.FINANCE_INCOME, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "4100", "Fee and Commission Income", AccountSubtype.OPERATING_REVENUE, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "5000", "Interest Expense", AccountSubtype.FINANCE_COST, ifrsCategory = IfrsCategory.FINANCE_COSTS))
                createAccount(CreateAccountCommand(entityId, "6000", "Operating Expenses", AccountSubtype.OPERATING_EXPENSES, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "7500", "Tax Expense", AccountSubtype.TAX_EXPENSE, ifrsCategory = IfrsCategory.TAX_EXPENSE))
            }
            CoaTemplate.NON_PROFIT -> {
                createAccount(CreateAccountCommand(entityId, "1000", "Cash and Bank", AccountSubtype.CASH_AND_EQUIVALENTS, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1200", "Grants Receivable", AccountSubtype.CURRENT_RECEIVABLE, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1500", "Prepaid Project Costs", AccountSubtype.CURRENT_PREPAID, ifrsCategory = IfrsCategory.CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "1600", "Property and Equipment", AccountSubtype.NON_CURRENT_PPE, ifrsCategory = IfrsCategory.NON_CURRENT_ASSETS))
                createAccount(CreateAccountCommand(entityId, "2000", "Accounts Payable", AccountSubtype.CURRENT_PAYABLE, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "2200", "Deferred Grant Income", AccountSubtype.CURRENT_DEFERRED_REVENUE, ifrsCategory = IfrsCategory.CURRENT_LIABILITIES))
                createAccount(CreateAccountCommand(entityId, "3000", "Restricted Funds", AccountSubtype.SHARE_CAPITAL, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "3100", "Accumulated Surplus/Deficit", AccountSubtype.RETAINED_EARNINGS, ifrsCategory = IfrsCategory.EQUITY))
                createAccount(CreateAccountCommand(entityId, "4000", "Grant Income", AccountSubtype.OPERATING_REVENUE, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "4100", "Donation Income", AccountSubtype.OPERATING_REVENUE, ifrsCategory = IfrsCategory.REVENUE))
                createAccount(CreateAccountCommand(entityId, "6000", "Programme Expenses", AccountSubtype.OPERATING_EXPENSES, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "6100", "Administrative Expenses", AccountSubtype.OPERATING_EXPENSES, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
                createAccount(CreateAccountCommand(entityId, "6200", "Depreciation Expense", AccountSubtype.DEPRECIATION, ifrsCategory = IfrsCategory.OPERATING_EXPENSES))
            }
        }
    }

    @Transactional
    fun importAccounts(entityId: UUID, commands: List<CreateAccountCommand>) {
        commands.forEach { createAccount(it.copy(entityId = entityId)) }
        inferAndSaveHierarchy(entityId)
    }

    @Transactional
    fun rebuildHierarchy(entityId: UUID) = inferAndSaveHierarchy(entityId)

    private fun inferAndSaveHierarchy(entityId: UUID) {
        val accounts    = accountRepository.findAllByEntityId(entityId)
        val codeToId    = accounts.associate { it.accountCode to it.id }
        val idToAccount = accounts.associateBy { it.id }
        val codeSet     = codeToId.keys.toSet()
        val dirty       = mutableListOf<Account>()
        val newParentIds = mutableSetOf<UUID>()

        for (acct in accounts) {
            if (acct.parentAccountId != null) continue
            val parentCode = inferParentCode(acct.accountCode, codeSet) ?: continue
            val parentId   = codeToId[parentCode] ?: continue
            acct.parentAccountId = parentId
            newParentIds.add(parentId)
            dirty.add(acct)
        }

        // Mark newly discovered parents as header accounts
        for (parentId in newParentIds) {
            val parent = idToAccount[parentId]
            if (parent != null && !parent.isHeader) {
                parent.isHeader = true
                dirty.add(parent)
            }
        }

        if (dirty.isNotEmpty()) accountRepository.saveAll(dirty)
    }

    private fun inferParentCode(code: String, codeSet: Set<String>): String? {
        val dash = code.indexOf('-')
        if (dash == -1) return null
        val prefix = code.substring(0, dash)
        val suffix = code.substring(dash + 1)
        if (suffix.length != 4) return null
        val suffixNum = suffix.toIntOrNull() ?: return null
        if (suffixNum == 0) return null

        val d = suffix.toCharArray()

        // Zero rightmost non-zero digit → candidate parent
        for (i in d.indices.reversed()) {
            if (d[i] != '0') {
                val cand = d.copyOf().also { arr ->
                    for (j in i until arr.size) arr[j] = '0'
                }
                val pc = "$prefix-${String(cand)}"
                if (pc != code && codeSet.contains(pc)) return pc
                break
            }
        }

        // Fallback: nearest smaller code with strictly more trailing zeros
        val trailingZeros = suffix.reversed().indexOfFirst { it != '0' }.let { if (it == -1) 4 else it }
        return codeSet
            .filter { c ->
                if (!c.startsWith("$prefix-")) return@filter false
                if (c == code) return@filter false
                val s = c.substringAfter('-')
                if (s.length != 4) return@filter false
                val sn = s.toIntOrNull() ?: return@filter false
                if (sn >= suffixNum) return@filter false
                val tz = s.reversed().indexOfFirst { it != '0' }.let { if (it == -1) 4 else it }
                tz > trailingZeros
            }
            .maxByOrNull { it.substringAfter('-').toIntOrNull() ?: 0 }
    }

    fun validateAccountCode(entityId: UUID, code: String): Boolean {
        return !accountRepository.existsByEntityIdAndAccountCode(entityId, code)
    }

    private fun validateHierarchy(entityId: UUID, parentId: UUID?) {
        if (parentId == null) return
        var currentDepth = 1
        var currentParentId = parentId
        val visited = mutableSetOf<UUID>()
        while (currentParentId != null) {
            if (visited.contains(currentParentId)) {
                throw ValidationException("CIRCULAR_ACCOUNT_REFERENCE", "Circular reference detected in account hierarchy.")
            }
            visited.add(currentParentId)
            val parent = accountRepository.findById(currentParentId)
                .orElseThrow { ValidationException("PARENT_ACCOUNT_NOT_FOUND", "Parent account $currentParentId not found.") }
            if (parent.entityId != entityId) {
                throw ValidationException("ENTITY_MISMATCH", "Parent account must belong to the same entity.")
            }
            currentParentId = parent.parentAccountId
            currentDepth++
            if (currentDepth > 5) {
                throw ValidationException("INVALID_COA_HIERARCHY", "Account hierarchy depth exceeds 5 levels.")
            }
        }
    }

    private fun isTemporaryType(type: AccountType): Boolean {
        return type == AccountType.REVENUE || type == AccountType.EXPENSE
    }
}

@Schema(description = "Command to create a new ledger account")
data class CreateAccountCommand(
    val entityId: UUID,
    val accountCode: String,
    val accountName: String,
    val accountSubtype: AccountSubtype,
    val parentAccountId: UUID? = null,
    val ifrsCategory: IfrsCategory = IfrsCategory.OPERATING_EXPENSES,
    val ifrsClassification: String? = null,
    val currencyCode: String? = null   // null = use entity's functional currency
)

@Schema(description = "Command to update an existing account")
data class UpdateAccountCommand(
    val accountCode: String,
    val accountName: String,
    val accountSubtype: AccountSubtype,
    val ifrsCategory: IfrsCategory = IfrsCategory.OPERATING_EXPENSES,
    val ifrsClassification: String? = null
)

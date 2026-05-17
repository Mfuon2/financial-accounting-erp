package com.qesuite.accounting.coa.domain

/**
 * §2.1 — Account Type Enum
 */
enum class AccountType(val normalBalance: NormalBalance) {
    ASSET(NormalBalance.DEBIT),
    LIABILITY(NormalBalance.CREDIT),
    EQUITY(NormalBalance.CREDIT),
    REVENUE(NormalBalance.CREDIT),
    EXPENSE(NormalBalance.DEBIT)
}

/**
 * §2.1 — Account Subtype Enum (IFRS-aligned)
 */
enum class AccountSubtype(val parentType: AccountType, val isMonetary: Boolean = false) {
    // ASSET Subtypes
    CASH_AND_EQUIVALENTS(AccountType.ASSET, true),
    CURRENT_RECEIVABLE(AccountType.ASSET, true),
    CURRENT_INVENTORY(AccountType.ASSET, false),
    CURRENT_PREPAID(AccountType.ASSET, false),
    NON_CURRENT_PPE(AccountType.ASSET, false),
    NON_CURRENT_INTANGIBLE(AccountType.ASSET, false),
    NON_CURRENT_INVESTMENT(AccountType.ASSET, false),
    NON_CURRENT_OTHER(AccountType.ASSET, false),

    // LIABILITY Subtypes
    CURRENT_PAYABLE(AccountType.LIABILITY, true),
    CURRENT_ACCRUED(AccountType.LIABILITY, true),
    CURRENT_DEFERRED_REVENUE(AccountType.LIABILITY, true),
    CURRENT_TAX(AccountType.LIABILITY, true),
    NON_CURRENT_LONG_TERM_DEBT(AccountType.LIABILITY, true),
    NON_CURRENT_LEASE(AccountType.LIABILITY, true),
    NON_CURRENT_PROVISION(AccountType.LIABILITY, true),
    NON_CURRENT_DEFERRED_TAX(AccountType.LIABILITY, true),

    // EQUITY Subtypes
    SHARE_CAPITAL(AccountType.EQUITY, false),
    RETAINED_EARNINGS(AccountType.EQUITY, false),
    OTHER_COMPREHENSIVE_INCOME(AccountType.EQUITY, false),
    DIVIDENDS_DRAWINGS(AccountType.EQUITY, false),

    // REVENUE Subtypes
    OPERATING_REVENUE(AccountType.REVENUE, false),
    OTHER_INCOME(AccountType.REVENUE, false),
    FINANCE_INCOME(AccountType.REVENUE, false),

    // EXPENSE Subtypes
    COGS(AccountType.EXPENSE, false),
    OPERATING_EXPENSES(AccountType.EXPENSE, false),
    DEPRECIATION(AccountType.EXPENSE, false),
    AMORTISATION(AccountType.EXPENSE, false),
    FINANCE_COST(AccountType.EXPENSE, false),
    TAX_EXPENSE(AccountType.EXPENSE, false)
}

enum class NormalBalance {
    DEBIT, CREDIT
}

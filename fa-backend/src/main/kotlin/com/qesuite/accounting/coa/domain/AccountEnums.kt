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
 *
 * [normalBalanceOverride] allows contra-asset subtypes (e.g. ACCUMULATED_DEPRECIATION) to declare
 * CREDIT normal balance even though their [parentType] is ASSET (DEBIT-normal). When null, the
 * normal balance is derived from [parentType] as usual.
 */
enum class AccountSubtype(
    val parentType: AccountType,
    val isMonetary: Boolean = false,
    val normalBalanceOverride: NormalBalance? = null
) {
    // ASSET Subtypes
    CASH_AND_EQUIVALENTS(AccountType.ASSET, true),
    CURRENT_RECEIVABLE(AccountType.ASSET, true),
    CURRENT_INVENTORY(AccountType.ASSET, false),
    CURRENT_PREPAID(AccountType.ASSET, false),
    NON_CURRENT_PPE(AccountType.ASSET, false),
    NON_CURRENT_INTANGIBLE(AccountType.ASSET, false),
    NON_CURRENT_INVESTMENT(AccountType.ASSET, false),
    NON_CURRENT_OTHER(AccountType.ASSET, false),
    // Contra-asset: sits under NON_CURRENT_ASSETS on the balance sheet but has CREDIT normal balance
    ACCUMULATED_DEPRECIATION(AccountType.ASSET, false, NormalBalance.CREDIT),

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

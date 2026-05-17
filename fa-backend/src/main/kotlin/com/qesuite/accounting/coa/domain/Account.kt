package com.qesuite.accounting.coa.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.*
import java.math.BigDecimal
import java.util.*

/**
 * §2.1 — Account Master
 */
@Entity
@Table(name = "accounts", uniqueConstraints = [
    UniqueConstraint(columnNames = ["entity_id", "account_code"]),
    UniqueConstraint(columnNames = ["entity_id", "account_name"])
])
class Account(
    entityId: UUID,

    @Column(name = "account_code", length = 20, nullable = false)
    @Schema(example = "1001", description = "Account code for classification")
    var accountCode: String,

    @Column(name = "account_name", length = 100, nullable = false)
    @Schema(example = "Petty Cash", description = "Descriptive name of the ledger account")
    var accountName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    @Schema(example = "ASSET")
    var accountType: AccountType,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_subtype", nullable = false)
    @Schema(example = "CASH_AND_EQUIVALENTS")
    var accountSubtype: AccountSubtype,

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false)
    @Schema(example = "DEBIT")
    var normalBalance: NormalBalance,

    @Column(name = "is_temporary", nullable = false)
    @Schema(example = "false", description = "True if the account is closed at year-end (Revenue/Expense)")
    var isTemporary: Boolean,

    @Column(name = "parent_account_id", nullable = true)
    @Schema(example = "a10e8400-e29b-41d4-a716-446655440099")
    var parentAccountId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "ifrs_category", nullable = false)
    @Schema(example = "CURRENT_ASSETS")
    var ifrsCategory: IfrsCategory = IfrsCategory.OPERATING_EXPENSES,

    @Column(name = "ifrs_classification", nullable = true)
    @Schema(example = "IAS 1")
    var ifrsClassification: String? = null,

    @Column(name = "currency_code", length = 3, nullable = false)
    @Schema(example = "USD")
    var currencyCode: String = "USD",

    @Column(name = "total_debits", precision = 20, scale = 6, nullable = false)
    @Schema(type = "number", format = "decimal", example = "0.000000", description = "Total debits recorded (§18.2)")
    var totalDebits: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_credits", precision = 20, scale = 6, nullable = false)
    @Schema(type = "number", format = "decimal", example = "0.000000")
    var totalCredits: BigDecimal = BigDecimal.ZERO,

    @Column(name = "current_balance", precision = 20, scale = 6, nullable = false)
    @Schema(type = "number", format = "decimal", example = "0.000000")
    var currentBalance: BigDecimal = BigDecimal.ZERO,

    @Column(name = "original_currency_balance", precision = 20, scale = 6, nullable = false)
    @Schema(type = "number", format = "decimal", example = "0.000000")
    var originalCurrencyBalance: BigDecimal = BigDecimal.ZERO

) : BaseFinancialEntity(entityId = entityId)

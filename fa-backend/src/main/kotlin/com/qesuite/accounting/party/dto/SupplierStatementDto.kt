package com.qesuite.accounting.party.dto

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class SupplierStatementLine(
    val date: LocalDate,
    val type: String,
    val reference: String,
    val description: String,
    val debit: BigDecimal,
    val credit: BigDecimal,
    val balance: BigDecimal,
    val status: String? = null,
    val documentId: UUID? = null,
)

data class SupplierStatementResponse(
    val supplierId: UUID,
    val supplierName: String,
    val supplierCode: String,
    val currency: String,
    val totalDebits: BigDecimal,
    val totalCredits: BigDecimal,
    val closingBalance: BigDecimal,
    val lines: List<SupplierStatementLine>,
)

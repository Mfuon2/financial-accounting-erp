package com.qesuite.accounting.coa.domain

/**
 * §2.1 — Standardized Chart of Accounts Templates
 */
enum class CoaTemplate(val description: String) {
    SERVICE("Standard template for service-based businesses"),
    MERCHANDISING("Template including Inventory and Cost of Goods Sold"),
    MANUFACTURING("Template for production with Work-in-Progress and Raw Materials"),
    FINANCIAL_SERVICES("Template for banks and credit providers"),
    NON_PROFIT("Template for NGOs and non-profit organizations")
}

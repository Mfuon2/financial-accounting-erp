package com.qesuite.accounting.shared.codegen.domain

enum class NumberingModule(
    val moduleKey: String,
    val label: String,
    val defaultPrefix: String,
    val allowedPrefixes: List<String>,
    val yearScoped: Boolean,
) {
    CUSTOMER        ("CUSTOMER",         "Customer",         "CU",   listOf("CU", "CUST", "CLIENT"),        false),
    SUPPLIER        ("SUPPLIER",         "Supplier",         "SUPP", listOf("SUPP", "VEND", "VENDOR"),       false),
    FIXED_ASSET     ("FIXED_ASSET",      "Fixed Asset",      "FA",   listOf("FA", "AST", "ASSET"),           false),
    SALES_INVOICE   ("SALES_INVOICE",    "Sales Invoice",    "INV",  listOf("INV", "SINV", "SI"),            true),
    PURCHASE_BILL   ("PURCHASE_BILL",    "Purchase Bill",    "BILL", listOf("BILL", "PINV", "PB"),           true),
    JOURNAL_ENTRY   ("JOURNAL_ENTRY",    "Journal Entry",    "JE",   listOf("JE", "JNL", "JV"),              true),
    SOURCE_DOCUMENT ("SOURCE_DOCUMENT",  "Source Document",  "SD",   listOf("SD", "DOC", "REF"),             true),
    PAYMENT         ("PAYMENT",          "Payment",          "PAY",  listOf("PAY", "PMT"),                   true),
    RECEIPT         ("RECEIPT",          "Receipt",          "RCT",  listOf("RCT", "RCPT"),                  true),
    CREDIT_NOTE     ("CREDIT_NOTE",      "Credit Note",      "CN",   listOf("CN", "CRNOTE"),                 true),
    DEBIT_NOTE      ("DEBIT_NOTE",       "Debit Note",       "DN",   listOf("DN", "DRNOTE"),                 true),
    PURCHASE_ORDER  ("PURCHASE_ORDER",   "Purchase Order",   "PO",   listOf("PO", "PORD"),                   true),
    QUOTATION       ("QUOTATION",        "Quotation",        "QT",   listOf("QT", "QUOT"),                   true);

    companion object {
        fun fromKey(key: String): NumberingModule? = entries.firstOrNull { it.moduleKey == key }
    }
}

package com.qesuite.accounting.shared.categories.domain

/**
 * §2 (CLAUDE.md — "Configuration-driven, not hard-coded") — the *kind* of category is a
 * deliberate code change (a new kind needs new UI copy/plumbing), but the *values within* a
 * kind (see [Category]) are entity-managed data, never a hard-coded frontend array.
 *
 * This is the reference pattern already established by `shared/codegen`'s [NumberingModule]
 * (13 document types, each independently configurable per entity) applied to a second concept:
 * one generic table serving every "pick one of these business codes" screen, starting with the
 * two concrete violations found in `MEMORY.md` (payment terms, payment methods) and designed so
 * a third kind (e.g. `EXPENSE_CATEGORY`) is just one more enum constant plus a seed list — no new
 * backend module.
 */
enum class CategoryType(val label: String) {
    PAYMENT_TERM("Payment Term"),
    PAYMENT_METHOD("Payment Method"),
    DOCUMENT_TYPE("Document Type");

    /**
     * The values that were hard-coded per-view before this module existed, preserved verbatim
     * (code AND order) so a migration/bootstrap can seed existing and new entities without
     * changing what's already stored on `Customer.paymentTerms`, `Supplier.paymentTerms`,
     * `Payment.paymentMethod`, `BillPayment.paymentMethod` records.
     *
     * PAYMENT_TERM codes are free-form strings on Customer/Supplier (parsed by
     * `BillService.parsePaymentTermsDays` via a `NET(\d+)` regex) — the codes below are exactly
     * what `Suppliers.vue`/`Customers.vue` hard-coded.
     *
     * PAYMENT_METHOD codes must match `com.qesuite.accounting.payments.domain.PaymentMethod`
     * enum constant names (minus DEBIT_NOTE, which is never a user-selectable payment method —
     * it is the system-internal counterpart of a supplier debit note) because they still
     * deserialize into that enum on the actual Payment/BillPayment request DTOs.
     *
     * DOCUMENT_TYPE codes must match `com.qesuite.accounting.source.domain.SourceDocumentType`
     * enum constant names exactly, for the same reason — `SourceDocument.type` deserializes
     * into that enum. This kind is a curated relabeling of a fixed backend classification (like
     * PAYMENT_METHOD), not a freely-extensible one: adding a category value here with no
     * matching `SourceDocumentType` constant will fail at document-creation time, not at
     * category-creation time. Was the hard-coded `DOC_TYPES` array in `SourceDocs.vue`.
     */
    fun defaultSeed(): List<Pair<String, String>> = when (this) {
        PAYMENT_TERM -> listOf(
            "DUE_ON_RECEIPT" to "Due On Receipt",
            "NET_15" to "Net 15",
            "NET_30" to "Net 30",
            "NET_45" to "Net 45",
            "NET_60" to "Net 60",
            "NET_90" to "Net 90",
        )
        PAYMENT_METHOD -> listOf(
            "BANK_TRANSFER" to "Bank Transfer",
            "MPESA" to "M-Pesa",
            "CASH" to "Cash",
            "CHEQUE" to "Cheque",
            "CREDIT_CARD" to "Credit Card",
        )
        DOCUMENT_TYPE -> listOf(
            "SALES_INVOICE" to "Sales Invoice",
            "PURCHASE_INVOICE" to "Purchase Invoice",
            "CASH_RECEIPT" to "Cash Receipt",
            "PAYMENT_VOUCHER" to "Payment Voucher",
            "BANK_STATEMENT" to "Bank Statement",
            "CREDIT_NOTE" to "Credit Note",
            "DEBIT_NOTE" to "Debit Note",
            "PAYROLL_RECORD" to "Payroll Record",
            "TAX_DECLARATION" to "Tax Declaration",
            "JOURNAL_VOUCHER" to "Journal Voucher",
        )
    }

    companion object {
        fun fromKey(key: String): CategoryType? = entries.find { it.name.equals(key, ignoreCase = true) }
    }
}

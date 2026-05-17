package com.qesuite.accounting.source.domain

/**
 * §2.2 — Source Document Status Lifecycle
 *
 * Valid transitions:
 *   DRAFT      → SUBMITTED, VOID
 *   SUBMITTED  → REVIEWED, DRAFT (rejected back)
 *   REVIEWED   → APPROVED, SUBMITTED (rejected back)
 *   APPROVED   → POSTED, ARCHIVED, REVIEWED (rejected back)
 *   POSTED     → ARCHIVED
 *   ARCHIVED   → DRAFT (restore — audit-logged, requires reason)
 *   VOID       → terminal (permanent cancellation — not restorable)
 */
enum class SourceDocumentStatus {
    DRAFT,
    SUBMITTED,
    REVIEWED,
    APPROVED,
    POSTED,
    ARCHIVED,
    VOID;

    fun canTransitionTo(next: SourceDocumentStatus): Boolean = when (this) {
        DRAFT      -> next == SUBMITTED || next == VOID
        SUBMITTED  -> next == REVIEWED  || next == DRAFT
        REVIEWED   -> next == APPROVED  || next == SUBMITTED
        APPROVED   -> next == POSTED    || next == ARCHIVED || next == REVIEWED
        POSTED     -> next == ARCHIVED
        ARCHIVED   -> next == DRAFT  // restore path
        VOID       -> false
    }
}

/**
 * §2.2 — Source Document Taxonomy (10 types)
 */
enum class SourceDocumentType {
    SALES_INVOICE,
    PURCHASE_INVOICE,
    CASH_RECEIPT,
    PAYMENT_VOUCHER,
    BANK_STATEMENT,
    CREDIT_NOTE,
    DEBIT_NOTE,
    PAYROLL_RECORD,
    TAX_DECLARATION,
    JOURNAL_VOUCHER
}

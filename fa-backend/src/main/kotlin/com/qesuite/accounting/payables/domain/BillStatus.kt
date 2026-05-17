package com.qesuite.accounting.payables.domain

enum class BillStatus {
    DRAFT,
    APPROVED,
    PARTIALLY_PAID,
    PAID,
    VOID,
    DEBIT_NOTE;

    fun canTransitionTo(next: BillStatus): Boolean = when (this) {
        DRAFT          -> next == APPROVED || next == VOID
        APPROVED       -> next == PARTIALLY_PAID || next == PAID || next == VOID
        PARTIALLY_PAID -> next == PAID || next == VOID
        PAID           -> false
        VOID           -> false
        DEBIT_NOTE     -> false
    }
}

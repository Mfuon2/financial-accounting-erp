package com.qesuite.accounting.receipts.domain

/**
 * §15 — Receipt lifecycle state machine.
 *
 * Valid transitions:
 *   PENDING  → POSTED
 *   POSTED   → ISSUED
 *   POSTED   → VOID
 *   ISSUED   → VOID
 *   VOID     — terminal
 */
enum class ReceiptStatus {
    PENDING,
    POSTED,
    ISSUED,
    VOID;

    /**
     * Returns true if transitioning from this status to [next] is permitted.
     */
    fun canTransitionTo(next: ReceiptStatus): Boolean = when (this) {
        PENDING -> next == POSTED
        POSTED  -> next == ISSUED || next == VOID
        ISSUED  -> next == VOID
        VOID    -> false // terminal — no further transitions
    }
}

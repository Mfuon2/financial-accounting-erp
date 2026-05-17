package com.qesuite.accounting.payments.domain

/**
 * §14 (Module 14) — Payment lifecycle state machine.
 *
 * Valid transitions:
 *   PENDING  → MATCHED
 *   MATCHED  → APPROVED
 *   APPROVED → POSTED
 *   POSTED   → REVERSED  (terminal after reversal)
 *   REVERSED — terminal, no further transitions
 */
enum class PaymentStatus {
    PENDING,
    MATCHED,
    APPROVED,
    POSTED,
    REVERSED;

    /**
     * Returns true if transitioning from this status to [next] is permitted.
     * Used by the service layer to enforce state-machine rules (Rule §6.6).
     */
    fun canTransitionTo(next: PaymentStatus): Boolean = when (this) {
        PENDING  -> next == MATCHED
        MATCHED  -> next == APPROVED
        APPROVED -> next == POSTED
        POSTED   -> next == REVERSED
        REVERSED -> false // terminal — no further transitions
    }
}

package com.qesuite.accounting.ap.domain

/**
 * §10.1 — Period States
 */
enum class PeriodStatus {
    FUTURE,
    OPEN,
    ADJUSTING,
    CLOSING,
    CLOSED,
    REOPENED;

    fun canTransitionTo(next: PeriodStatus): Boolean = when (this) {
        FUTURE    -> next == OPEN
        OPEN      -> next == ADJUSTING
        ADJUSTING -> next == CLOSING || next == OPEN
        CLOSING   -> next == CLOSED || next == ADJUSTING
        CLOSED    -> next == REOPENED
        REOPENED  -> next == ADJUSTING || next == CLOSING || next == CLOSED
    }
}

package com.qesuite.accounting.journal.domain

/**
 * §4.1 — Journal Entry Status Lifecycle
 */
enum class JournalEntryStatus {
    DRAFT,
    PENDING_APPROVAL,
    POSTED,
    REVERSED;

    fun canTransitionTo(next: JournalEntryStatus): Boolean = when (this) {
        DRAFT -> next == PENDING_APPROVAL
        PENDING_APPROVAL -> next == POSTED || next == DRAFT
        POSTED -> next == REVERSED
        REVERSED -> false // Terminal state
    }
}

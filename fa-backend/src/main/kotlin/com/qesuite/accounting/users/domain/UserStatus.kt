package com.qesuite.accounting.users.domain

enum class UserStatus {
    PENDING_VERIFICATION,  // registered but email not verified
    ACTIVE,                // fully active
    SUSPENDED,             // admin-suspended, cannot login
    DEACTIVATED,           // soft-deleted
    LOCKED;                // too many failed login attempts

    fun canLogin(): Boolean = this == ACTIVE

    fun canTransitionTo(next: UserStatus): Boolean = when (this) {
        PENDING_VERIFICATION -> next == ACTIVE || next == DEACTIVATED
        ACTIVE               -> next == SUSPENDED || next == DEACTIVATED || next == LOCKED
        SUSPENDED            -> next == ACTIVE || next == DEACTIVATED
        LOCKED               -> next == ACTIVE || next == DEACTIVATED
        DEACTIVATED          -> false
    }
}

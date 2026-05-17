package com.qesuite.accounting.shared.security

import java.util.UUID

/**
 * §18 — Security & Access Control Roles
 */
enum class UserRole {
    DATA_ENTRY,
    ACCOUNTANT,
    SENIOR_ACCOUNTANT,
    CONTROLLER_CFO,
    AUDITOR,
    SYSTEM_ADMIN
}

data class UserContext(
    val userId: UUID,
    val entityId: UUID,
    val role: UserRole,
    val email: String
)

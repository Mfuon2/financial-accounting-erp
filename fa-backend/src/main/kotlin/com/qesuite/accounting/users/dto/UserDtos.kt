package com.qesuite.accounting.users.dto

import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.users.domain.UserStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RegisterUserRequest(
    @field:NotNull
    val entityId: UUID,

    @field:NotBlank
    @field:Size(min = 2, max = 255)
    val fullName: String,

    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val rawPassword: String,

    val role: UserRole = UserRole.DATA_ENTRY
)

data class LoginRequest(
    val entityId: UUID? = null,   // optional — auto-resolved from email if omitted

    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotBlank
    val password: String
)

data class ChangePasswordRequest(
    @field:NotBlank
    val currentPassword: String,

    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val newPassword: String
)

data class ResetPasswordRequest(
    @field:NotBlank
    val token: String,

    @field:NotBlank
    @field:Size(min = 8, max = 128)
    val newPassword: String
)

data class UpdateRoleRequest(
    @field:NotNull
    val role: UserRole
)

data class UpdateProfileRequest(
    @field:Size(min = 2, max = 255)
    val fullName: String?
)

/**
 * Public-facing user response DTO. Never includes passwordHash.
 */
data class UserResponse(
    val id: UUID,
    val entityId: UUID,
    val fullName: String,
    val email: String,
    val role: UserRole,
    val status: UserStatus,
    val emailVerified: Boolean,
    val mustChangePassword: Boolean,
    val lastLoginAt: Instant?,
    val createdAt: Instant
) {
    companion object {
        fun from(user: com.qesuite.accounting.users.domain.User) = UserResponse(
            id = user.id,
            entityId = user.entityId,
            fullName = user.fullName,
            email = user.email,
            role = user.role,
            status = user.status,
            emailVerified = user.emailVerified,
            mustChangePassword = user.mustChangePassword,
            lastLoginAt = user.lastLoginAt,
            createdAt = user.createdAt
        )
    }
}

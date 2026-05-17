package com.qesuite.accounting.users.controller

import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.SecurityUtils
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.users.domain.UserStatus
import com.qesuite.accounting.users.dto.RegisterUserRequest
import com.qesuite.accounting.users.dto.UpdateProfileRequest
import com.qesuite.accounting.users.dto.UpdateRoleRequest
import com.qesuite.accounting.users.dto.UserResponse
import com.qesuite.accounting.users.service.RegisterUserCommand
import com.qesuite.accounting.users.service.UpdateProfileCommand
import com.qesuite.accounting.users.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "User CRUD and role management — requires SYSTEM_ADMIN or CONTROLLER_CFO")
class UserController(private val userService: UserService) {

    private val log = LoggerFactory.getLogger(UserController::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users   (SYSTEM_ADMIN only)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Admin creates a new user account within their entity")
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    fun createUser(
        @Valid @RequestBody request: RegisterUserRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val admin = SecurityUtils.currentUser()
        val currentUser = SecurityUtils.currentUser()
        if (request.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Cannot create users in another entity.", httpStatus = 403)
        }
        val command = RegisterUserCommand(
            entityId    = request.entityId,
            fullName    = request.fullName,
            email       = request.email,
            rawPassword = request.rawPassword,
            role        = request.role,
            createdBy   = admin.userId
        )
        val user = userService.registerUser(command)
        log.info("users.create: admin={} created user={}", admin.userId, user.id)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(UserResponse.from(user)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/users?entityId=&status=   (paginated)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "List users for an entity, optionally filtered by status")
    @GetMapping
    @PreAuthorize(
        "hasRole('SYSTEM_ADMIN') or hasRole('CONTROLLER_CFO') or hasRole('AUDITOR')"
    )
    fun findByEntity(
        @RequestParam entityId: UUID,
        @RequestParam(required = false) status: UserStatus?,
        @PageableDefault(size = 20, sort = ["fullName"]) pageable: Pageable
    ): ResponseEntity<ApiResponse<Page<UserResponse>>> {
        val currentUser = SecurityUtils.currentUser()
        if (currentUser.entityId != entityId && currentUser.role != UserRole.SYSTEM_ADMIN) {
            throw ValidationException("FORBIDDEN", "You can only access users within your own entity.", httpStatus = 403)
        }
        val page: Page<UserResponse> = if (status != null) {
            userService.findByEntityAndStatus(entityId, status, pageable).map { UserResponse.from(it) }
        } else {
            userService.findByEntity(entityId, pageable).map { UserResponse.from(it) }
        }
        return ResponseEntity.ok(ApiResponse.success(page))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/users/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Retrieve a user by ID")
    @GetMapping("/{id}")
    @PreAuthorize(
        "hasRole('SYSTEM_ADMIN') or hasRole('CONTROLLER_CFO') or hasRole('AUDITOR')"
    )
    fun findById(
        @PathVariable id: UUID
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val user = userService.findById(id)
        val currentUser = SecurityUtils.currentUser()
        if (user.entityId != currentUser.entityId && currentUser.role != UserRole.SYSTEM_ADMIN) {
            throw ValidationException("FORBIDDEN", "Access denied to user in another entity.", httpStatus = 403)
        }
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/users/{id}/role   (SYSTEM_ADMIN only)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Update a user's role")
    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    fun updateRole(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateRoleRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val admin = SecurityUtils.currentUser()
        val user = userService.updateRole(id, request.role, admin.userId)
        log.info("users.updateRole: admin={} updated role of user={} to {}", admin.userId, id, request.role)
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/users/{id}/profile   (self or SYSTEM_ADMIN)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Update a user's profile (self or admin)")
    @PutMapping("/{id}/profile")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    fun updateProfile(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val current = SecurityUtils.currentUser()
        // Only the user themselves or a SYSTEM_ADMIN may update a profile
        if (current.userId != id && current.role != UserRole.SYSTEM_ADMIN) {
            throw ValidationException(
                errorCode = "FORBIDDEN",
                message   = "You do not have permission to update this user's profile."
            )
        }
        val command = UpdateProfileCommand(fullName = request.fullName)
        val user = userService.updateProfile(id, command)
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/{id}/deactivate   (SYSTEM_ADMIN only)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Deactivate a user account")
    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    fun deactivate(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DeactivateRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val admin = SecurityUtils.currentUser()
        val user = userService.deactivate(id, request.reason, admin.userId)
        log.info("users.deactivate: admin={} deactivated user={}, reason={}", admin.userId, id, request.reason)
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/{id}/reactivate   (SYSTEM_ADMIN only)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Reactivate a user account")
    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    fun reactivate(
        @PathVariable id: UUID
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val admin = SecurityUtils.currentUser()
        val user = userService.reactivate(id, admin.userId)
        log.info("users.reactivate: admin={} reactivated user={}", admin.userId, id)
        return ResponseEntity.ok(ApiResponse.success(UserResponse.from(user)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/users/{id}/reset-password   (SYSTEM_ADMIN only)
    // Returns the plaintext reset token exactly once — admin must relay it securely
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Admin-initiated password reset — returns one-time plaintext token",
        description = "The plaintext token is returned once. The admin must relay it to the user via a secure channel."
    )
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Transactional
    fun adminResetPassword(
        @PathVariable id: UUID
    ): ResponseEntity<ApiResponse<AdminResetPasswordResponse>> {
        val admin = SecurityUtils.currentUser()
        // Resolve the user's email + entityId so we can call initiatePasswordReset(email, entityId)
        val user = userService.findById(id)
        val rawToken = userService.initiatePasswordReset(user.email, user.entityId)
        log.info("users.admin-reset-password: admin={} issued reset token for user={}", admin.userId, id)
        return ResponseEntity.ok(
            ApiResponse.success(
                AdminResetPasswordResponse(
                    resetToken = rawToken,
                    message    = "Provide this token to the user via a secure channel. It is valid for 1 hour and can only be used once."
                )
            )
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Request / Response DTOs local to UserController
// ──────────────────────────────────────────────────────────────────────────────

data class DeactivateRequest(
    @field:NotBlank
    val reason: String
)

data class AdminResetPasswordResponse(
    val resetToken: String,
    val message: String
)

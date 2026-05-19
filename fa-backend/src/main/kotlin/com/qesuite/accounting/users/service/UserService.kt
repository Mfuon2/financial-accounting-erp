package com.qesuite.accounting.users.service

import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.users.domain.PasswordResetToken
import com.qesuite.accounting.users.domain.User
import com.qesuite.accounting.users.domain.UserStatus
import com.qesuite.accounting.users.repository.PasswordResetTokenRepository
import com.qesuite.accounting.users.repository.RefreshTokenRepository
import com.qesuite.accounting.users.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

/**
 * Core user management service.
 *
 * Password policy: minimum 8 characters, at least 1 uppercase letter,
 * 1 digit, and 1 special character (non-alphanumeric).
 *
 * BCrypt strength 12 is used for all password hashing.
 * SHA-256 (via java.security.MessageDigest) is used for password reset token storage.
 */
@Service
@Transactional
class UserService(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository
) {

    private val log = LoggerFactory.getLogger(UserService::class.java)

    private val passwordEncoder = BCryptPasswordEncoder(12)
    private val secureRandom = SecureRandom()

    /**
     * Regex: min 8 chars, ≥1 uppercase, ≥1 digit, ≥1 special char.
     */
    private val PASSWORD_POLICY_REGEX = Regex("^(?=.*[A-Z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$")

    /**
     * Sentinel UUID used as createdBy/modifiedBy when the very first user in the system
     * bootstraps themselves (no authenticated principal exists yet).
     */
    private val SYSTEM_BOOTSTRAP_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    // ─────────────────────────────────────────────────────────────────────────
    // Registration & Email Verification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new user within the given entity.
     *
     * If this is the first user in the entity (countByEntityId == 0), they are
     * automatically promoted to SYSTEM_ADMIN with ACTIVE status and email
     * pre-verified, so the system can be bootstrapped without an existing admin.
     */
    fun registerUser(command: RegisterUserCommand): User {
        // Validate email uniqueness within entity
        if (userRepository.existsByEntityIdAndEmail(command.entityId, command.email)) {
            throw ConflictException(
                errorCode = "DUPLICATE_EMAIL",
                message = "A user with email '${command.email}' already exists in this entity.",
                context = mapOf("email" to command.email, "entity_id" to command.entityId.toString())
            )
        }

        // Validate password policy
        validatePasswordPolicy(command.rawPassword)

        val isFirstUser = userRepository.countByEntityId(command.entityId) == 0L

        // Admin-created users (createdBy != SYSTEM_BOOTSTRAP_ID) are activated immediately
        // since a trusted admin is creating the account on their behalf.
        // Self-registered users (createdBy == SYSTEM_BOOTSTRAP_ID) start as PENDING_VERIFICATION
        // until their email is verified.
        val isAdminCreated = !isFirstUser && command.createdBy != SYSTEM_BOOTSTRAP_ID
        val initialStatus = when {
            isFirstUser    -> UserStatus.ACTIVE           // bootstrap admin — always ACTIVE
            isAdminCreated -> UserStatus.ACTIVE           // admin-provisioned user — ACTIVE immediately
            else           -> UserStatus.PENDING_VERIFICATION  // self-registered — needs email verification
        }

        val passwordHash = passwordEncoder.encode(command.rawPassword)
        val actorId = if (isFirstUser) SYSTEM_BOOTSTRAP_ID else command.createdBy

        val user = User(
            entityId = command.entityId,
            fullName = command.fullName,
            email = command.email,
            passwordHash = passwordHash,
            role = if (isFirstUser) UserRole.SYSTEM_ADMIN else command.role,
            status = initialStatus,
            emailVerified = isFirstUser || isAdminCreated,
            emailVerifiedAt = if (isFirstUser || isAdminCreated) Instant.now() else null,
            mustChangePassword = false,
            createdBy = actorId,
            modifiedBy = actorId
        )

        val saved = userRepository.save(user)
        log.info(
            "User registered: id={} entity={} role={} firstUser={}",
            saved.id, saved.entityId, saved.role, isFirstUser
        )
        return saved
    }

    /**
     * Verifies the user's email address and transitions status from
     * PENDING_VERIFICATION to ACTIVE.
     */
    fun verifyEmail(userId: UUID): User {
        val user = findById(userId)

        if (user.status != UserStatus.PENDING_VERIFICATION) {
            throw ValidationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Email verification is only valid for accounts in PENDING_VERIFICATION status. Current status: ${user.status}.",
                context = mapOf("current_status" to user.status.name)
            )
        }

        user.emailVerified = true
        user.emailVerifiedAt = Instant.now()
        user.status = UserStatus.ACTIVE
        user.modifiedBy = userId  // self-verified

        val saved = userRepository.save(user)
        log.info("Email verified for user id={}", saved.id)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Authentication
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user by email + entity + raw password.
     *
     * Account lockout policy: 5 consecutive failed attempts triggers a 30-minute
     * lock (status = LOCKED, lockedUntil = now + 30 min). On successful login the
     * counter and lock are reset.
     */
    fun authenticate(email: String, entityId: UUID?, rawPassword: String, resolveByEmail: Boolean = false): User {
        val user = if (resolveByEmail || entityId == null) {
            // Email-only login: look up by email globally.
            // If multiple accounts share the same email across entities, reject with a clear error.
            val matches = userRepository.findAllByEmail(email)
            when {
                matches.isEmpty() -> throw ResourceNotFoundException("USER_NOT_FOUND", email, "User")
                matches.size > 1  -> throw ValidationException(
                    errorCode = "AMBIGUOUS_EMAIL",
                    message   = "Multiple accounts found for this email. Please contact your administrator.",
                    httpStatus = 400
                )
                else -> matches.first()
            }
        } else {
            userRepository.findByEntityIdAndEmail(entityId, email)
                .orElseThrow {
                    ResourceNotFoundException(
                        errorCode    = "USER_NOT_FOUND",
                        resourceId   = email,
                        resourceType = "User"
                    )
                }
        }

        // ── Auto-clear expired lockout BEFORE status check ──────────────────────
        // If status is LOCKED but the lockout window has passed, silently restore
        // the account to ACTIVE so the user can attempt to log in again.
        if (user.status == UserStatus.LOCKED &&
            user.lockedUntil != null &&
            user.lockedUntil!!.isBefore(Instant.now())) {
            user.status = UserStatus.ACTIVE
            user.lockedUntil = null
            user.failedLoginAttempts = 0
            user.modifiedAt = Instant.now()
            userRepository.save(user)
            log.info("auth.authenticate: lockout expired for userId={}, restored to ACTIVE", user.id)
        }

        // ── Status gate (runs AFTER expired-lock reset) ──────────────────────────
        if (!user.status.canLogin()) {
            when (user.status) {
                UserStatus.SUSPENDED -> throw ValidationException(
                    errorCode = "ACCOUNT_NOT_ACTIVE",
                    message = "This account has been suspended. Please contact your administrator.",
                    context = mapOf("status" to user.status.name)
                )
                UserStatus.DEACTIVATED -> throw ValidationException(
                    errorCode = "ACCOUNT_NOT_ACTIVE",
                    message = "This account has been deactivated.",
                    context = mapOf("status" to user.status.name)
                )
                UserStatus.PENDING_VERIFICATION -> throw ValidationException(
                    errorCode = "ACCOUNT_NOT_ACTIVE",
                    message = "Email address has not been verified. Please check your inbox.",
                    context = mapOf("status" to user.status.name)
                )
                else -> throw ValidationException(
                    errorCode = "ACCOUNT_NOT_ACTIVE",
                    message = "Account is not active. Current status: ${user.status}.",
                    context = mapOf("status" to user.status.name)
                )
            }
        }

        // Lockout gate (time-based: active lock still in effect)
        val now = Instant.now()
        if (user.lockedUntil != null && user.lockedUntil!!.isAfter(now)) {
            throw ValidationException(
                errorCode = "ACCOUNT_LOCKED",
                message = "Account is locked due to too many failed login attempts. Try again after ${user.lockedUntil}.",
                context = mapOf(
                    "locked_until" to user.lockedUntil.toString(),
                    "status" to user.status.name
                )
            )
        }

        // Password check
        if (!passwordEncoder.matches(rawPassword, user.passwordHash)) {
            user.failedLoginAttempts += 1

            if (user.failedLoginAttempts >= 5) {
                user.lockedUntil = now.plus(30, ChronoUnit.MINUTES)
                user.status = UserStatus.LOCKED
                userRepository.save(user)
                log.warn("Account locked after {} failed attempts: userId={}", user.failedLoginAttempts, user.id)
                throw ValidationException(
                    errorCode = "INVALID_CREDENTIALS",
                    message = "Invalid credentials. Account has been locked for 30 minutes due to too many failed attempts.",
                    context = mapOf("locked_until" to user.lockedUntil.toString())
                )
            }

            userRepository.save(user)
            log.warn("Failed login attempt {}/5 for userId={}", user.failedLoginAttempts, user.id)
            throw ValidationException(
                errorCode = "INVALID_CREDENTIALS",
                message = "Invalid email or password. ${5 - user.failedLoginAttempts} attempt(s) remaining before lockout.",
                context = mapOf("attempts_remaining" to (5 - user.failedLoginAttempts))
            )
        }

        // Successful authentication — reset failure counters
        user.failedLoginAttempts = 0
        user.lockedUntil = null
        user.lastLoginAt = now
        // modifiedBy is the user themselves on login
        user.modifiedBy = user.id

        val saved = userRepository.save(user)
        log.info("Successful login for userId={} entityId={}", saved.id, saved.entityId)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role & Profile Updates
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Updates a user's role. Prevents the last SYSTEM_ADMIN in an entity from
     * demoting themselves.
     */
    fun updateRole(userId: UUID, newRole: UserRole, updatedBy: UUID): User {
        val user = findById(userId)

        // Guard: do not allow the last SYSTEM_ADMIN to lose admin status
        if (user.role == UserRole.SYSTEM_ADMIN && newRole != UserRole.SYSTEM_ADMIN) {
            val adminCount = userRepository.findByEntityIdAndRole(
                user.entityId,
                UserRole.SYSTEM_ADMIN,
                Pageable.unpaged()
            ).totalElements

            if (adminCount <= 1) {
                throw ValidationException(
                    errorCode = "LAST_ADMIN_DEMOTION",
                    message = "Cannot change the role of the last SYSTEM_ADMIN in this entity. Promote another user to SYSTEM_ADMIN first.",
                    context = mapOf("user_id" to userId.toString(), "entity_id" to user.entityId.toString())
                )
            }
        }

        user.role = newRole
        user.modifiedBy = updatedBy

        val saved = userRepository.save(user)
        log.info("Role updated for userId={} newRole={} by={}", saved.id, newRole, updatedBy)
        return saved
    }

    /**
     * Updates mutable profile fields. Currently supports fullName.
     */
    fun updateProfile(userId: UUID, command: UpdateProfileCommand): User {
        val user = findById(userId)

        command.fullName?.let {
            if (it.isBlank()) throw ValidationException(
                errorCode = "INVALID_FULL_NAME",
                message = "Full name must not be blank."
            )
            user.fullName = it.trim()
        }

        user.modifiedBy = userId  // self-update

        val saved = userRepository.save(user)
        log.info("Profile updated for userId={}", saved.id)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Password Management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Changes the password for an authenticated user who knows their current password.
     */
    fun changePassword(userId: UUID, currentRaw: String, newRaw: String, changedBy: UUID): User {
        val user = findById(userId)

        if (!passwordEncoder.matches(currentRaw, user.passwordHash)) {
            throw ValidationException(
                errorCode = "INVALID_CURRENT_PASSWORD",
                message = "The current password provided is incorrect."
            )
        }

        validatePasswordPolicy(newRaw)

        user.passwordHash = passwordEncoder.encode(newRaw)
        user.mustChangePassword = false
        user.modifiedBy = changedBy

        val saved = userRepository.save(user)
        log.info("Password changed for userId={} by={}", saved.id, changedBy)
        return saved
    }

    /**
     * Issues a password reset token. The plaintext token is returned to the caller
     * who is responsible for transmitting it to the user (e.g. via email).
     *
     * For security, callers should respond with a generic success message regardless
     * of whether the user/entity combination was found.
     *
     * @return plaintext token (Base64-URL encoded, 32 secure random bytes)
     * @throws ResourceNotFoundException if no matching user is found (caller should
     *   catch and suppress for security if needed)
     */
    fun initiatePasswordReset(email: String, entityId: UUID): String {
        val user = userRepository.findByEntityIdAndEmail(entityId, email)
            .orElseThrow {
                ResourceNotFoundException(
                    errorCode = "USER_NOT_FOUND",
                    resourceId = email,
                    resourceType = "User"
                )
            }

        // 32 bytes = 256 bits of entropy
        val rawBytes = ByteArray(32)
        secureRandom.nextBytes(rawBytes)
        val plaintextToken = Base64.getUrlEncoder().withoutPadding().encodeToString(rawBytes)

        val tokenHash = sha256Hex(plaintextToken)
        val expiresAt = Instant.now().plus(1, ChronoUnit.HOURS)

        val resetToken = PasswordResetToken(
            userId = user.id,
            tokenHash = tokenHash,
            expiresAt = expiresAt
        )

        passwordResetTokenRepository.save(resetToken)
        log.info("Password reset token issued for userId={} expiresAt={}", user.id, expiresAt)

        return plaintextToken
    }

    /**
     * Completes a password reset using the plaintext token issued by [initiatePasswordReset].
     * The token is single-use and must not be expired.
     */
    fun completePasswordReset(token: String, newRaw: String): User {
        val tokenHash = sha256Hex(token)

        val resetToken = passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(tokenHash)
            .orElseThrow {
                ValidationException(
                    errorCode = "INVALID_OR_EXPIRED_TOKEN",
                    message = "The password reset token is invalid, already used, or does not exist."
                )
            }

        val now = Instant.now()
        if (resetToken.expiresAt.isBefore(now)) {
            throw ValidationException(
                errorCode = "INVALID_OR_EXPIRED_TOKEN",
                message = "The password reset token has expired. Please request a new one.",
                context = mapOf("expired_at" to resetToken.expiresAt.toString())
            )
        }

        validatePasswordPolicy(newRaw)

        val user = findById(resetToken.userId)
        user.passwordHash = passwordEncoder.encode(newRaw)
        user.mustChangePassword = false
        user.modifiedBy = user.id  // self-reset

        // Mark token as used (do not delete — audit trail)
        resetToken.usedAt = now

        passwordResetTokenRepository.save(resetToken)
        val saved = userRepository.save(user)

        // Revoke all active sessions so the attacker who triggered the reset loses access
        val revoked = refreshTokenRepository.revokeAllByUser(
            userId = user.id,
            now = Instant.now(),
            revokedBy = user.id
        )
        log.info("password.reset: revoked {} active session(s) for userId={}", revoked, user.id)

        log.info("Password reset completed for userId={}", saved.id)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deactivation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Soft-deactivates a user. Deactivated users cannot log in and cannot be
     * reactivated through normal flows.
     */
    fun deactivate(userId: UUID, reason: String, deactivatedBy: UUID): User {
        val user = findById(userId)

        if (user.status == UserStatus.DEACTIVATED) {
            throw ConflictException(
                errorCode = "ALREADY_DEACTIVATED",
                message = "User is already deactivated.",
                context = mapOf("user_id" to userId.toString())
            )
        }

        val now = Instant.now()
        user.isActive = false
        user.status = UserStatus.DEACTIVATED
        user.deactivatedAt = now
        user.deactivatedBy = deactivatedBy
        user.deactivationReason = reason
        user.modifiedBy = deactivatedBy

        val saved = userRepository.save(user)
        log.info("User deactivated: userId={} by={} reason={}", saved.id, deactivatedBy, reason)
        return saved
    }

    /**
     * Reactivates a previously deactivated or suspended user.
     */
    fun reactivate(userId: UUID, reactivatedBy: UUID): User {
        val user = findById(userId)

        if (user.status == UserStatus.ACTIVE) {
            return user
        }

        user.isActive = true
        user.status = UserStatus.ACTIVE
        user.deactivatedAt = null
        user.deactivatedBy = null
        user.deactivationReason = null
        user.modifiedBy = reactivatedBy

        val saved = userRepository.save(user)
        log.info("User reactivated: userId={} by={}", saved.id, reactivatedBy)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun findById(id: UUID): User =
        userRepository.findById(id).orElseThrow {
            ResourceNotFoundException(
                errorCode = "USER_NOT_FOUND",
                resourceId = id,
                resourceType = "User"
            )
        }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<User> =
        userRepository.findByEntityId(entityId, pageable)

    @Transactional(readOnly = true)
    fun findByEntityAndStatus(entityId: UUID, status: UserStatus, pageable: Pageable): Page<User> =
        userRepository.findByEntityIdAndStatus(entityId, status, pageable)

    @Transactional(readOnly = true)
    fun countActiveByEntity(entityId: UUID): Long =
        userRepository.countByEntityIdAndStatus(entityId, UserStatus.ACTIVE)

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun validatePasswordPolicy(rawPassword: String) {
        if (!PASSWORD_POLICY_REGEX.matches(rawPassword)) {
            throw ValidationException(
                errorCode = "WEAK_PASSWORD",
                message = "Password must be at least 8 characters long and contain at least one uppercase letter, one digit, and one special character.",
                context = mapOf(
                    "policy" to "min 8 chars, ≥1 uppercase, ≥1 digit, ≥1 special character"
                )
            )
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Command objects
// ─────────────────────────────────────────────────────────────────────────────

data class RegisterUserCommand(
    val entityId: UUID,
    val fullName: String,
    val email: String,
    val rawPassword: String,
    val role: UserRole = UserRole.DATA_ENTRY,
    val createdBy: UUID
)

data class UpdateProfileCommand(
    val fullName: String?
)

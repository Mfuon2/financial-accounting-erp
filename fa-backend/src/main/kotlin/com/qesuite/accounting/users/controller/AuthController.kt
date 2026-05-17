package com.qesuite.accounting.users.controller

import com.qesuite.accounting.email.EmailService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.InvalidTokenTypeException
import com.qesuite.accounting.shared.security.JwtService
import com.qesuite.accounting.shared.security.SecurityUtils
import com.qesuite.accounting.shared.security.UserContext
import com.qesuite.accounting.users.domain.RefreshToken
import com.qesuite.accounting.users.dto.ChangePasswordRequest
import com.qesuite.accounting.users.dto.LoginRequest
import com.qesuite.accounting.users.dto.RegisterUserRequest
import com.qesuite.accounting.users.dto.ResetPasswordRequest
import com.qesuite.accounting.users.dto.UserResponse
import com.qesuite.accounting.users.repository.RefreshTokenRepository
import com.qesuite.accounting.users.service.RegisterUserCommand
import com.qesuite.accounting.users.service.UserService
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User authentication — login, token refresh, password management")
class AuthController(
    private val userService: UserService,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val emailService: EmailService,
) {

    private val log = LoggerFactory.getLogger(AuthController::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/login
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Authenticate user and issue access + refresh tokens")
    @PostMapping("/login")
    @Transactional
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<LoginResponse>> {
        val user = userService.authenticate(request.email, request.entityId, request.password, request.entityId == null)

        val userContext = UserContext(
            userId   = user.id,
            entityId = user.entityId,
            role     = user.role,
            email    = user.email
        )

        val accessToken  = jwtService.generateToken(userContext)
        val refreshToken = jwtService.generateRefreshToken(userContext)

        // Store SHA-256 hash of the refresh token for server-side revocation.
        // Use jwtService.refreshExpirationMs (from config) — never hardcode.
        val tokenHash        = sha256Hex(refreshToken)
        val refreshExpiresAt = Instant.now().plusMillis(jwtService.refreshExpirationMs)
        refreshTokenRepository.save(
            RefreshToken(
                userId    = user.id,
                tokenHash = tokenHash,
                expiresAt = refreshExpiresAt,
                userAgent = httpRequest.getHeader("User-Agent")?.take(500),
                clientIp  = resolveClientIp(httpRequest)
            )
        )

        val warnings = mutableListOf<String>()
        if (user.mustChangePassword) {
            warnings.add("Password change required. Please update your password before proceeding.")
        }

        // expiresIn is in SECONDS — derive from config milliseconds
        val body = LoginResponse(
            accessToken        = accessToken,
            refreshToken       = refreshToken,
            expiresIn          = jwtService.expirationMs / 1_000L,
            tokenType          = "Bearer",
            user               = UserResponse.from(user),
            mustChangePassword = user.mustChangePassword
        )

        return ResponseEntity.ok(
            ApiResponse(success = true, data = body, warnings = warnings)
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/register
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Self-register a new user account")
    @PostMapping("/register")
    @Transactional
    fun register(
        @Valid @RequestBody request: RegisterUserRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<UserResponse>> {
        val systemId = UUID(0L, 0L) // 00000000-0000-0000-0000-000000000000
        val command = RegisterUserCommand(
            entityId    = request.entityId,
            fullName    = request.fullName,
            email       = request.email,
            rawPassword = request.rawPassword,
            role        = request.role,
            createdBy   = systemId
        )
        val user = userService.registerUser(command)

        // Production: send verification email. Logging the fact here (never the token).
        log.info("auth.register: user={} created, verification email pending", user.id)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(UserResponse.from(user)))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/refresh
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Exchange a valid refresh token for a new access token + a new refresh token.
     *
     * ROTATION PATTERN — industry standard (RFC 6749 + OAuth 2.1):
     *  1. Validate the incoming refresh token (JWT signature + type claim + DB lookup).
     *  2. Revoke the used refresh token immediately (single-use enforcement).
     *  3. Issue a brand-new refresh token and persist its hash.
     *  4. Issue a new access token.
     *  5. Return both new tokens to the client.
     *
     * Theft detection: if an attacker replays an already-used refresh token,
     * step 1 fails because the token was revoked in step 2 of the legitimate use.
     */
    @Operation(
        summary = "Rotate refresh token — returns new access token AND new refresh token",
        description = "Implements refresh token rotation: the supplied refresh token is " +
            "immediately revoked and a fresh pair (access + refresh) is returned. " +
            "Clients must store the new refresh token for the next rotation."
    )
    @PostMapping("/refresh")
    @Transactional
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<ApiResponse<TokenResponse>> {

        // ── Step 1: Validate the JWT itself ───────────────────────────────────
        val userContext = try {
            jwtService.extractUserContextFromRefreshToken(request.refreshToken)
        } catch (e: ExpiredJwtException) {
            throw ValidationException(
                errorCode = "REFRESH_TOKEN_EXPIRED",
                message   = "Your session has expired. Please log in again.",
                httpStatus = 401
            )
        } catch (e: InvalidTokenTypeException) {
            throw ValidationException(
                errorCode = "WRONG_TOKEN_TYPE",
                message   = "The provided token is not a refresh token.",
                httpStatus = 401
            )
        } catch (e: JwtException) {
            throw ValidationException(
                errorCode = "INVALID_REFRESH_TOKEN",
                message   = "The refresh token is invalid or has been tampered with.",
                httpStatus = 401
            )
        }

        // ── Step 2: Look up and validate the stored token record ─────────────
        val tokenHash = sha256Hex(request.refreshToken)
        val stored = refreshTokenRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
            .orElseThrow {
                // Token not found or already revoked — possible replay attack
                log.warn(
                    "auth.refresh: token not found or revoked for userId={} — possible replay attack",
                    userContext.userId
                )
                ValidationException(
                    errorCode  = "INVALID_REFRESH_TOKEN",
                    message    = "Refresh token has already been used or does not exist. Please log in again.",
                    httpStatus = 401
                )
            }

        if (stored.isExpired) {
            throw ValidationException(
                errorCode  = "REFRESH_TOKEN_EXPIRED",
                message    = "Your session has expired. Please log in again.",
                httpStatus = 401
            )
        }

        // ── Step 3: ROTATE — revoke the used token immediately ───────────────
        stored.revokedAt = Instant.now()
        stored.revokedBy = userContext.userId
        refreshTokenRepository.save(stored)
        log.debug("auth.refresh: rotated refresh token for userId={}", userContext.userId)

        // ── Step 4: Issue new refresh token and persist its hash ─────────────
        val newRefreshToken      = jwtService.generateRefreshToken(userContext)
        val newRefreshTokenHash  = sha256Hex(newRefreshToken)
        val newRefreshExpiresAt  = Instant.now().plusMillis(jwtService.refreshExpirationMs)

        refreshTokenRepository.save(
            RefreshToken(
                userId    = userContext.userId,
                tokenHash = newRefreshTokenHash,
                expiresAt = newRefreshExpiresAt,
                userAgent = httpRequest.getHeader("User-Agent")?.take(500),
                clientIp  = resolveClientIp(httpRequest)
            )
        )

        // ── Step 5: Issue new access token and return both ────────────────────
        val newAccessToken = jwtService.generateToken(userContext)

        return ResponseEntity.ok(
            ApiResponse.success(
                TokenResponse(
                    accessToken  = newAccessToken,
                    refreshToken = newRefreshToken,           // ← new refresh token (rotate)
                    expiresIn    = jwtService.expirationMs / 1_000L,
                    tokenType    = "Bearer"
                )
            )
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/logout   (requires JWT)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Logout — revoke all refresh tokens for the current user")
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    fun logout(): ResponseEntity<ApiResponse<String>> {
        val current = SecurityUtils.currentUser()
        val revoked = refreshTokenRepository.revokeAllByUser(
            userId    = current.userId,
            now       = Instant.now(),
            revokedBy = current.userId
        )
        log.info("auth.logout: user={} revoked {} refresh token(s)", current.userId, revoked)
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully."))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/v1/auth/sessions   (requires JWT)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "List all active refresh tokens/sessions for the current user")
    @GetMapping("/sessions")
    @PreAuthorize("isAuthenticated()")
    fun listSessions(): ResponseEntity<ApiResponse<List<SessionResponse>>> {
        val current = SecurityUtils.currentUser()
        val sessions = refreshTokenRepository.findByUserId(current.userId)
            .filter { it.revokedAt == null && !it.isExpired }
            .map { SessionResponse.from(it) }
        return ResponseEntity.ok(ApiResponse.success(sessions))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/auth/sessions/{id}   (requires JWT)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Revoke a specific session by its refresh token ID")
    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    fun revokeSession(
        @PathVariable id: UUID
    ): ResponseEntity<ApiResponse<String>> {
        val current = SecurityUtils.currentUser()
        val token = refreshTokenRepository.findById(id).orElseThrow {
            ResourceNotFoundException("SESSION_NOT_FOUND", id, "Session")
        }

        if (token.userId != current.userId) {
            throw ValidationException(
                errorCode = "FORBIDDEN",
                message   = "You do not have permission to revoke this session.",
                httpStatus = 403
            )
        }

        token.revokedAt = Instant.now()
        token.revokedBy = current.userId
        refreshTokenRepository.save(token)

        log.info("auth.revoke-session: user={} revoked session={}", current.userId, id)
        return ResponseEntity.ok(ApiResponse.success("Session revoked successfully."))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/sessions/revoke-all-others   (requires JWT)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(
        summary = "Sign out all other sessions, keeping the most recent one active",
        description = "Revokes all active refresh tokens for the current user except the newest one, " +
            "which is assumed to be the caller's current session."
    )
    @PostMapping("/sessions/revoke-all-others")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    fun revokeAllOtherSessions(): ResponseEntity<ApiResponse<String>> {
        val current = SecurityUtils.currentUser()
        val active = refreshTokenRepository.findByUserId(current.userId)
            .filter { it.revokedAt == null && !it.isExpired }
            .sortedByDescending { it.issuedAt }
        val toRevoke = active.drop(1) // keep newest (current session)
        val now = Instant.now()
        toRevoke.forEach { token ->
            token.revokedAt = now
            token.revokedBy = current.userId
            refreshTokenRepository.save(token)
        }
        log.info("auth.revoke-all-others: user={} revoked {} session(s)", current.userId, toRevoke.size)
        return ResponseEntity.ok(ApiResponse.success("Revoked ${toRevoke.size} other session(s)."))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/change-password   (requires JWT)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Change password for the authenticated user")
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<ApiResponse<String>> {
        val current = SecurityUtils.currentUser()
        userService.changePassword(current.userId, request.currentPassword, request.newPassword, current.userId)

        // Force re-login on all devices after password change
        refreshTokenRepository.revokeAllByUser(
            userId    = current.userId,
            now       = Instant.now(),
            revokedBy = current.userId
        )
        log.info("auth.change-password: user={} changed password, all sessions revoked", current.userId)

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully. Please log in again."))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/forgot-password   (public)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Initiate password reset — sends reset link if email exists")
    @PostMapping("/forgot-password")
    @Transactional
    fun forgotPassword(
        @Valid @RequestBody request: ForgotPasswordRequest
    ): ResponseEntity<ApiResponse<String>> {
        try {
            val plaintextToken = userService.initiatePasswordReset(request.email, request.entityId)
            // Fire-and-forget async email — never log the raw token
            emailService.sendPasswordReset(request.email, plaintextToken)
            log.info("auth.forgot-password: reset token issued for email={} entity={}", request.email, request.entityId)
        } catch (e: ResourceNotFoundException) {
            // Intentionally swallowed — do not reveal whether the email exists
            log.debug("auth.forgot-password: no user found for email={} entity={} (suppressed)", request.email, request.entityId)
        }
        return ResponseEntity.ok(
            ApiResponse.success("If the email exists, a reset link has been sent.")
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/reset-password   (public)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Complete password reset using the one-time token")
    @PostMapping("/reset-password")
    @Transactional
    fun resetPassword(
        @Valid @RequestBody request: ResetPasswordRequest
    ): ResponseEntity<ApiResponse<String>> {
        userService.completePasswordReset(request.token, request.newPassword)
        log.info("auth.reset-password: password reset completed")
        return ResponseEntity.ok(ApiResponse.success("Password has been reset successfully. You may now log in."))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/v1/auth/verify-email/{userId}   (public)
    // ──────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Verify a user's email address")
    @PostMapping("/verify-email/{userId}")
    @Transactional
    fun verifyEmail(
        @PathVariable userId: UUID
    ): ResponseEntity<ApiResponse<String>> {
        userService.verifyEmail(userId)
        log.info("auth.verify-email: userId={} email verified", userId)
        return ResponseEntity.ok(ApiResponse.success("Email address verified successfully."))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun resolveClientIp(request: HttpServletRequest): String {
        val forwardedFor = request.getHeader("X-Forwarded-For")
        return if (!forwardedFor.isNullOrBlank()) {
            forwardedFor.split(",").first().trim().take(45)
        } else {
            request.remoteAddr?.take(45) ?: "unknown"
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Request / Response DTOs local to auth
// ──────────────────────────────────────────────────────────────────────────────

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String,
    val user: UserResponse,
    val mustChangePassword: Boolean = false
)

/**
 * Returned by POST /auth/refresh.
 * Contains BOTH a new access token AND a new refresh token (rotation).
 * Clients MUST replace their stored refresh token with the new one — the old
 * one is immediately revoked upon issuance of this response.
 */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,   // ← rotated; old refresh token is now revoked
    val expiresIn: Long,
    val tokenType: String
)

data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String
)

data class ForgotPasswordRequest(
    @field:NotBlank
    @field:Email
    val email: String,

    @field:NotNull
    val entityId: UUID
)

data class SessionResponse(
    val id: UUID,
    val userId: UUID,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val userAgent: String?,
    val clientIp: String?
) {
    companion object {
        fun from(token: RefreshToken) = SessionResponse(
            id        = token.id,
            userId    = token.userId,
            issuedAt  = token.issuedAt,
            expiresAt = token.expiresAt,
            userAgent = token.userAgent,
            clientIp  = token.clientIp
        )
    }
}

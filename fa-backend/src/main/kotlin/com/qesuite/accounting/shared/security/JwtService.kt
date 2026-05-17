package com.qesuite.accounting.shared.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*
import javax.crypto.SecretKey

/**
 * §18 — JWT token lifecycle service.
 *
 * Token philosophy:
 *  • ACCESS token  — short-lived (default 24 h), sent on every API call via
 *                    `Authorization: Bearer <token>`. Carries `type = "access"`.
 *  • REFRESH token — long-lived (default 30 d), stored server-side (hashed) and
 *                    used ONLY at `POST /auth/refresh`. Carries `type = "refresh"`.
 *                    Rotated on every use — old token is revoked, new one issued.
 *
 * Separation is enforced in two places:
 *  1. [extractUserContext]            — rejects tokens whose `type != "access"`
 *  2. [extractUserContextFromRefreshToken] — rejects tokens whose `type != "refresh"`
 *
 * This prevents a stolen refresh token from being used directly as an access
 * token and vice-versa.
 */
@Service
class JwtService(
    @Value("\${app.security.jwt.secret:your-very-secret-key-minimum-256-bits-long-change-in-production}")
    private val secret: String,

    @Value("\${app.security.jwt.expiration:86400000}")
    val expirationMs: Long,          // exposed so AuthController can compute expiresIn

    @Value("\${app.security.jwt.refresh-expiration:2592000000}")
    val refreshExpirationMs: Long    // exposed so AuthController can set DB expiresAt
) {
    init {
        val knownInsecureDefault = "your-very-secret-key-minimum-256-bits-long-change-in-production"
        if (secret == knownInsecureDefault) {
            val msg = "SECURITY: app.security.jwt.secret is set to the well-known insecure default. " +
                      "Set the JWT_SECRET environment variable to a cryptographically random 256-bit key before starting."
            if (System.getenv("ALLOW_INSECURE_JWT_SECRET") == null) {
                throw IllegalStateException(msg)
            }
            LoggerFactory.getLogger(JwtService::class.java).warn(msg)
        }
        require(secret.length >= 32) {
            "app.security.jwt.secret must be at least 32 characters (256 bits). Current length: ${secret.length}"
        }
    }
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    // ──────────────────────────────────────────────────────────────────────────
    // ACCESS TOKEN
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Issue a short-lived access token for [user].
     * The `type = "access"` claim is mandatory and validated by [extractUserContext].
     */
    fun generateToken(user: UserContext): String =
        Jwts.builder()
            .setSubject(user.userId.toString())
            .claim("entity_id", user.entityId.toString())
            .claim("role", user.role.name)
            .claim("email", user.email)
            .claim("type", "access")           // ← MANDATORY type discriminator
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + expirationMs))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    /**
     * Parse and validate an ACCESS token from the `Authorization` header.
     *
     * Throws [InvalidTokenTypeException] if the token carries `type = "refresh"` —
     * preventing a refresh token from being used as an access token.
     * Throws [ExpiredJwtException] / [JwtException] for any other JWT error.
     */
    fun extractUserContext(token: String): UserContext {
        val claims = parseClaims(token)

        // ── BUG FIX: enforce type = "access" ──────────────────────────────────
        val type = claims["type"] as? String
        if (type != "access") {
            throw InvalidTokenTypeException(
                "Expected access token (type='access') but received '${type ?: "untyped"}' token. " +
                "Use POST /api/v1/auth/refresh to obtain a new access token."
            )
        }
        // ──────────────────────────────────────────────────────────────────────

        return UserContext(
            userId   = UUID.fromString(claims.subject),
            entityId = UUID.fromString(claims["entity_id"] as String),
            role     = UserRole.valueOf(claims["role"] as String),
            email    = claims["email"] as String
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // REFRESH TOKEN
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Issue a long-lived refresh token for [user].
     * The `type = "refresh"` claim is mandatory and validated by
     * [extractUserContextFromRefreshToken].
     */
    fun generateRefreshToken(user: UserContext): String =
        Jwts.builder()
            .setSubject(user.userId.toString())
            .claim("entity_id", user.entityId.toString())
            .claim("role", user.role.name)
            .claim("email", user.email)
            .claim("type", "refresh")          // ← MANDATORY type discriminator
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + refreshExpirationMs))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

    /**
     * Parse and validate a REFRESH token from `POST /auth/refresh`.
     *
     * Throws [InvalidTokenTypeException] if `type != "refresh"`.
     * Throws [ExpiredJwtException] if the refresh token itself has expired (30 d).
     */
    fun extractUserContextFromRefreshToken(token: String): UserContext {
        val claims = parseClaims(token)

        val type = claims["type"] as? String
        if (type != "refresh") {
            throw InvalidTokenTypeException(
                "Expected refresh token but received '${type ?: "untyped"}' token."
            )
        }

        return UserContext(
            userId   = UUID.fromString(claims.subject),
            entityId = UUID.fromString(claims["entity_id"] as String),
            role     = UserRole.valueOf(claims["role"] as String),
            email    = claims["email"] as String
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UTILITIES
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Returns true if [token] is expired (or cannot be parsed).
     * Does NOT validate the token type.
     */
    fun isTokenExpired(token: String): Boolean =
        try {
            parseClaims(token).expiration.before(Date())
        } catch (_: ExpiredJwtException) {
            true
        } catch (_: JwtException) {
            true
        }

    // ──────────────────────────────────────────────────────────────────────────
    // INTERNAL
    // ──────────────────────────────────────────────────────────────────────────

    private fun parseClaims(token: String): Claims =
        Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
}

/**
 * Thrown when a token's `type` claim does not match the expected value.
 * Caught by [com.qesuite.accounting.shared.exceptions.GlobalExceptionHandler]
 * and mapped to HTTP 401.
 */
class InvalidTokenTypeException(message: String) : RuntimeException(message)

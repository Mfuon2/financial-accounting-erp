package com.qesuite.accounting.shared.security

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * §18 — JWT Authentication Filter.
 *
 * Intercepts every request that carries `Authorization: Bearer <token>` and
 * validates it as an ACCESS token. Distinct error handling per failure mode:
 *
 *  • Expired access token  → HTTP 401, `TOKEN_EXPIRED`
 *    Client must call `POST /auth/refresh` with their refresh token.
 *
 *  • Refresh token used as access token → HTTP 401, `WRONG_TOKEN_TYPE`
 *    Prevents a refresh token from being misused as an access credential.
 *
 *  • Malformed / invalid signature → HTTP 401, `INVALID_TOKEN`
 *
 * On any error the filter writes a JSON 401 response immediately and does NOT
 * continue the filter chain — preventing the request from reaching controllers
 * in an unauthenticated state.
 */
@Component
class JwtAuthenticationFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        // No Bearer header — pass through (unauthenticated requests handled by SecurityConfig)
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val jwt = authHeader.removePrefix("Bearer ").trim()

        try {
            // extractUserContext enforces type == "access" — rejects refresh tokens
            val userContext = jwtService.extractUserContext(jwt)

            val authentication = UsernamePasswordAuthenticationToken(
                userContext,
                null,
                listOf(SimpleGrantedAuthority("ROLE_${userContext.role.name}"))
            )
            authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
            SecurityContextHolder.getContext().authentication = authentication

            filterChain.doFilter(request, response)

        } catch (e: ExpiredJwtException) {
            // ── Access token expired — client must refresh ─────────────────────
            log.debug("jwt.filter: expired access token for sub={}", e.claims?.subject)
            writeUnauthorized(
                response,
                errorCode = "TOKEN_EXPIRED",
                message   = "Access token has expired. Call POST /api/v1/auth/refresh to obtain a new one."
            )

        } catch (e: InvalidTokenTypeException) {
            // ── Refresh token presented as access token ────────────────────────
            log.warn("jwt.filter: wrong token type in Authorization header — {}", e.message)
            writeUnauthorized(
                response,
                errorCode = "WRONG_TOKEN_TYPE",
                message   = e.message ?: "Invalid token type."
            )

        } catch (e: JwtException) {
            // ── Malformed token / bad signature / unsupported algorithm ─────────
            log.warn("jwt.filter: invalid JWT — {}: {}", e.javaClass.simpleName, e.message)
            writeUnauthorized(
                response,
                errorCode = "INVALID_TOKEN",
                message   = "The provided token is invalid or has been tampered with."
            )

        } catch (e: Exception) {
            // ── Unexpected error (e.g. ClassCastException on claims) ─────────────
            log.error("jwt.filter: unexpected error processing token", e)
            writeUnauthorized(
                response,
                errorCode = "INVALID_TOKEN",
                message   = "Authentication failed due to an unexpected error."
            )
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Write a JSON 401 response and stop the filter chain.
     * Uses inline JSON (no Jackson dependency) to avoid circular wiring issues.
     */
    private fun writeUnauthorized(
        response: HttpServletResponse,
        errorCode: String,
        message: String
    ) {
        response.status      = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(
            """{"success":false,"data":null,"errors":[{"error_code":"$errorCode","http_status":401,"message":"${message.replace("\"","'")}"}],"warnings":[]}"""
        )
    }
}

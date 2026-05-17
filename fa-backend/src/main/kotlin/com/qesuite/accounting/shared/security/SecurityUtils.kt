package com.qesuite.accounting.shared.security

import com.qesuite.accounting.shared.exceptions.BaseAccountingException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * §18 — Security utilities. Resolves the current authenticated user from the
 * `SecurityContextHolder`. Both an instance form (Spring-managed `@Component`) and a
 * companion-object form (static helpers for AOP / interceptors) are exposed.
 */
@Component
class SecurityUtils {

    /**
     * §18 — Returns the current authenticated [UserContext] or throws
     * [UnauthenticatedAccessException] (HTTP 401 / `UNAUTHENTICATED`).
     */
    fun getCurrentUser(): UserContext = currentUser()

    companion object {

        /**
         * §18 — Returns the current authenticated [UserContext] or throws
         * [UnauthenticatedAccessException]. Used from contexts where DI is awkward
         * (AOP advice, AuditAware suppliers).
         */
        fun currentUser(): UserContext {
            val authentication = SecurityContextHolder.getContext().authentication
            val principal = authentication?.principal
            if (principal is UserContext) return principal
            throw UnauthenticatedAccessException()
        }

        /**
         * §18 — Returns the current authenticated user's [UserContext.entityId].
         * Throws [UnauthenticatedAccessException] if no user is authenticated.
         *
         * No silent fallback to a hardcoded UUID is permitted (Rule 03 of the
         * Zero-Tolerance Policy in instructions.md). Anonymous endpoints (e.g. the
         * M-Pesa webhook) MUST handle multi-tenant resolution explicitly via the
         * payload's `BillRefNumber` / `entityCode` mapping table — they do NOT use
         * this helper.
         */
        fun currentEntityIdOrSystem(): UUID = currentUser().entityId
    }
}

/**
 * §6.3 — `UNAUTHENTICATED` (HTTP 401). Raised by [SecurityUtils.currentUser] when no
 * principal is present on the security context.
 */
class UnauthenticatedAccessException(
    message: String = "Authentication is required for this operation.",
) : BaseAccountingException(
    errorCode = "UNAUTHENTICATED",
    message = message,
    httpStatus = 401,
    context = null,
)

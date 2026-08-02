package com.qesuite.accounting.shared.security

import com.qesuite.accounting.shared.exceptions.BaseAccountingException
import com.qesuite.accounting.shared.exceptions.ValidationException
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

        /**
         * §18, IDOR guard — verifies the authenticated user may access a resource that
         * belongs to [entityId]. Every endpoint that accepts a client-supplied `entityId`
         * (query param, path variable, or a DTO field) — or that first looks up a resource
         * and must then check *that resource's* owning entityId — calls this before
         * touching the resource. Without it, any authenticated user could read/write
         * another entity's data merely by changing the entityId in the request (IDOR).
         *
         * Call it either before a repository lookup (when `entityId` is directly
         * client-supplied, e.g. a `GET ?entityId=` list endpoint) or immediately after one
         * (when the entity must be read off the fetched resource first, e.g.
         * `getById(id)` → check `resource.entityId`) — matching the two call-site shapes
         * already established in [com.qesuite.accounting.integration.controller.ApiKeyController]
         * and [com.qesuite.accounting.users.controller.UserController].
         *
         * [allowSystemAdmin] defaults to `true`, matching UserController's existing
         * (already-reviewed) pattern: a SYSTEM_ADMIN may act across entities. Pass
         * `allowSystemAdmin = false` for endpoints already restricted to SYSTEM_ADMIN-only
         * via `@PreAuthorize`, matching ApiKeyController's stricter (also already-reviewed)
         * pattern — an entity's own SYSTEM_ADMIN managing that entity's *own* sensitive
         * credentials (API keys) should not be able to widen their reach to another
         * entity's credentials just by changing the `entityId` param. There is no
         * `allowAuditor` parameter: every existing `@PreAuthorize` that combines AUDITOR
         * with SYSTEM_ADMIN in this codebase also combines it with the standard per-entity
         * operational roles (ACCOUNTANT, CONTROLLER_CFO, etc.) — AUDITOR is an
         * entity-scoped compliance role here, not a platform-wide one. The one genuinely
         * cross-entity AUDITOR endpoint (`OrganizationController#findAll`, listing every
         * organization) takes no `entityId` parameter at all, so this guard never applies
         * there.
         */
        fun requireOwnEntity(entityId: UUID, allowSystemAdmin: Boolean = true) {
            val user = currentUser()
            val allowed = user.entityId == entityId || (allowSystemAdmin && user.role == UserRole.SYSTEM_ADMIN)
            if (!allowed) {
                throw ValidationException(
                    errorCode = "FORBIDDEN",
                    message = "Access denied to resource in another entity.",
                    httpStatus = 403
                )
            }
        }
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

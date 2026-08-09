package com.qesuite.accounting.shared.security

import com.qesuite.accounting.shared.exceptions.ValidationException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

/**
 * §18 — Unit tests for [SecurityUtils.requireOwnEntity], the shared IDOR guard applied
 * across every controller that accepts a client-supplied `entityId`.
 */
class SecurityUtilsTest {

    @AfterEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun authenticateAs(entityId: UUID, role: UserRole) {
        val principal = UserContext(
            userId = UUID.randomUUID(),
            entityId = entityId,
            role = role,
            email = "user@example.com",
        )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())
    }

    @Test
    fun `should allow access when entityId matches the authenticated user's own entity`() {
        val entityId = UUID.randomUUID()
        authenticateAs(entityId, UserRole.ACCOUNTANT)

        assertDoesNotThrow { SecurityUtils.requireOwnEntity(entityId) }
    }

    @Test
    fun `should reject access when entityId belongs to a different entity`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        authenticateAs(ownEntityId, UserRole.ACCOUNTANT)

        val ex = assertThrows<ValidationException> { SecurityUtils.requireOwnEntity(otherEntityId) }
        assertEquals("FORBIDDEN", ex.errorCode)
        assertEquals(403, ex.httpStatus)
    }

    @Test
    fun `should allow SYSTEM_ADMIN to cross entities by default`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        authenticateAs(ownEntityId, UserRole.SYSTEM_ADMIN)

        assertDoesNotThrow { SecurityUtils.requireOwnEntity(otherEntityId) }
    }

    @Test
    fun `should reject SYSTEM_ADMIN cross-entity access when allowSystemAdmin is false`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        authenticateAs(ownEntityId, UserRole.SYSTEM_ADMIN)

        val ex = assertThrows<ValidationException> {
            SecurityUtils.requireOwnEntity(otherEntityId, allowSystemAdmin = false)
        }
        assertEquals("FORBIDDEN", ex.errorCode)
        assertEquals(403, ex.httpStatus)
    }

    @Test
    fun `should reject a non-admin role from crossing entities regardless of allowSystemAdmin`() {
        val ownEntityId = UUID.randomUUID()
        val otherEntityId = UUID.randomUUID()
        authenticateAs(ownEntityId, UserRole.AUDITOR)

        assertThrows<ValidationException> { SecurityUtils.requireOwnEntity(otherEntityId) }
    }

    @Test
    fun `should throw UnauthenticatedAccessException when no user is authenticated`() {
        SecurityContextHolder.clearContext()
        assertThrows<UnauthenticatedAccessException> {
            SecurityUtils.requireOwnEntity(UUID.randomUUID())
        }
    }

    // ── requireNotSelfApproval (maker-checker) ──────────────────────────────────

    @Test
    fun `requireNotSelfApproval rejects when the approver is the same person who created the record`() {
        val userId = UUID.randomUUID()
        val entityId = UUID.randomUUID()
        val principal = UserContext(userId = userId, entityId = entityId, role = UserRole.SENIOR_ACCOUNTANT, email = "u@example.com")
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())

        val ex = assertThrows<com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException> {
            SecurityUtils.requireNotSelfApproval(userId)
        }
        assertEquals("SELF_APPROVAL_NOT_ALLOWED", ex.errorCode)
    }

    @Test
    fun `requireNotSelfApproval allows when the approver differs from the creator`() {
        authenticateAs(UUID.randomUUID(), UserRole.SENIOR_ACCOUNTANT)

        assertDoesNotThrow { SecurityUtils.requireNotSelfApproval(UUID.randomUUID()) }
    }

    @Test
    fun `requireNotSelfApproval allows when the creator is unknown (null)`() {
        authenticateAs(UUID.randomUUID(), UserRole.SENIOR_ACCOUNTANT)

        assertDoesNotThrow { SecurityUtils.requireNotSelfApproval(null) }
    }
}

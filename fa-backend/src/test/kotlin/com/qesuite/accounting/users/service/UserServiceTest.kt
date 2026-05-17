package com.qesuite.accounting.users.service

import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.UserRole
import com.qesuite.accounting.users.domain.User
import com.qesuite.accounting.users.domain.UserStatus
import com.qesuite.accounting.users.dto.UserResponse
import com.qesuite.accounting.users.repository.PasswordResetTokenRepository
import com.qesuite.accounting.users.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Unit tests for [UserService] — written against Worker A's published contract.
 *
 * NOTE: The current implementation of [UserService] constructs [BCryptPasswordEncoder]
 * internally (strength 12). To allow mocking of password checks in authenticate tests,
 * the tests use a real BCrypt hash for the matching cases and rely on the service's
 * real encoder for verification. For tests that need to exercise password-mismatch paths
 * (tests 7 & 8), the stored hash on the User is set to a BCrypt hash of a *different*
 * password so the service's internal encoder returns false.
 *
 * Coverage:
 *  1. First user in entity → SYSTEM_ADMIN + ACTIVE + emailVerified=true
 *  2. Non-first user → PENDING_VERIFICATION
 *  3. Duplicate email → ConflictException("DUPLICATE_EMAIL")
 *  4. Weak password → ValidationException("WEAK_PASSWORD")
 *  5. Authenticate — success path (lastLoginAt set, failedLoginAttempts reset to 0)
 *  6. Authenticate — ACCOUNT_LOCKED (lockedUntil in future)
 *  7. Authenticate — wrong password → INVALID_CREDENTIALS, failedLoginAttempts+1
 *  8. Authenticate — 5th failed attempt → status=LOCKED, lockedUntil non-null
 *  9. Deactivate — soft-delete: isActive=false, status=DEACTIVATED, deactivationReason set
 */
class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    private lateinit var userService: UserService

    private val entityId: UUID = UUID.randomUUID()
    private val adminId: UUID = UUID.randomUUID()

    /** A valid password that satisfies the policy: ≥8 chars, uppercase, digit, special char. */
    private val STRONG_PASSWORD = "Secure@123"

    /**
     * Real BCrypt encoder used to pre-hash passwords stored on mock User objects.
     * Strength 4 is used here to keep tests fast; the service uses strength 12 internally.
     */
    private val realEncoder = BCryptPasswordEncoder(4)

    @BeforeEach
    fun setup() {
        userRepository = mockk(relaxed = true)
        passwordResetTokenRepository = mockk(relaxed = true)
        val refreshTokenRepository = mockk<com.qesuite.accounting.users.repository.RefreshTokenRepository>(relaxed = true)

        userService = UserService(
            userRepository = userRepository,
            passwordResetTokenRepository = passwordResetTokenRepository,
            refreshTokenRepository = refreshTokenRepository
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper — build a User with explicit fields for authenticate / deactivate tests
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildUser(
        id: UUID = UUID.randomUUID(),
        email: String = "test@example.com",
        status: UserStatus = UserStatus.ACTIVE,
        failedLoginAttempts: Int = 0,
        lockedUntil: Instant? = null,
        passwordHash: String = realEncoder.encode(STRONG_PASSWORD),
        role: UserRole = UserRole.ACCOUNTANT
    ): User = User(
        id = id,
        entityId = entityId,
        fullName = "Test User",
        email = email,
        passwordHash = passwordHash,
        role = role,
        status = status,
        failedLoginAttempts = failedLoginAttempts,
        lockedUntil = lockedUntil,
        createdBy = adminId,
        modifiedBy = adminId
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 1. registerUser creates ACTIVE SYSTEM_ADMIN user when first user in entity
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `registerUser creates ACTIVE SYSTEM_ADMIN user when first user in entity`() {
        every { userRepository.existsByEntityIdAndEmail(entityId, any()) } returns false
        every { userRepository.countByEntityId(entityId) } returns 0L
        every { userRepository.save(any<User>()) } answers { firstArg() }

        val command = RegisterUserCommand(
            entityId = entityId,
            fullName = "First Admin",
            email = "admin@example.com",
            rawPassword = STRONG_PASSWORD,
            role = UserRole.ACCOUNTANT, // requested role — must be overridden to SYSTEM_ADMIN
            createdBy = adminId
        )

        val result = userService.registerUser(command)

        // Role must be promoted to SYSTEM_ADMIN regardless of requested role
        assertEquals(UserRole.SYSTEM_ADMIN, result.role)
        // Status must be ACTIVE for the first user
        assertEquals(UserStatus.ACTIVE, result.status)
        // Email must be pre-verified
        assertTrue(result.emailVerified)
        // emailVerifiedAt must be populated
        assertNotNull(result.emailVerifiedAt)

        // UserResponse DTO must NOT expose passwordHash
        val dto = UserResponse.from(result)
        val dtoFieldNames = dto::class.java.declaredFields.map { it.name }
        assertFalse(
            dtoFieldNames.contains("passwordHash"),
            "UserResponse must NOT contain a passwordHash field"
        )

        verify { userRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. registerUser creates PENDING_VERIFICATION user for non-first registration
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `registerUser creates PENDING_VERIFICATION user for non-first registration`() {
        every { userRepository.existsByEntityIdAndEmail(entityId, any()) } returns false
        every { userRepository.countByEntityId(entityId) } returns 5L
        every { userRepository.save(any<User>()) } answers { firstArg() }

        val command = RegisterUserCommand(
            entityId = entityId,
            fullName = "Regular User",
            email = "user@example.com",
            rawPassword = STRONG_PASSWORD,
            role = UserRole.ACCOUNTANT,
            createdBy = adminId
        )

        val result = userService.registerUser(command)

        assertEquals(UserStatus.PENDING_VERIFICATION, result.status)
        assertFalse(result.emailVerified)
        assertNull(result.emailVerifiedAt)

        verify { userRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. registerUser throws ConflictException when email already exists
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `registerUser throws ConflictException when email already exists`() {
        every { userRepository.existsByEntityIdAndEmail(entityId, "dup@example.com") } returns true

        val command = RegisterUserCommand(
            entityId = entityId,
            fullName = "Duplicate",
            email = "dup@example.com",
            rawPassword = STRONG_PASSWORD,
            role = UserRole.ACCOUNTANT,
            createdBy = adminId
        )

        val ex = assertThrows<ConflictException> {
            userService.registerUser(command)
        }
        assertEquals("DUPLICATE_EMAIL", ex.errorCode)

        // Save must never be called when validation fails
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. registerUser throws ValidationException when password too weak
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `registerUser throws ValidationException when password too weak`() {
        every { userRepository.existsByEntityIdAndEmail(entityId, any()) } returns false

        val command = RegisterUserCommand(
            entityId = entityId,
            fullName = "Weak Pass User",
            email = "weak@example.com",
            rawPassword = "password", // no uppercase, no digit, no special char
            role = UserRole.ACCOUNTANT,
            createdBy = adminId
        )

        val ex = assertThrows<ValidationException> {
            userService.registerUser(command)
        }
        assertEquals("WEAK_PASSWORD", ex.errorCode)

        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. authenticate returns user on valid credentials
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `authenticate returns user on valid credentials`() {
        // Use a real BCrypt hash so the service's internal encoder matches successfully
        val passwordHash = realEncoder.encode(STRONG_PASSWORD)
        val user = buildUser(
            status = UserStatus.ACTIVE,
            failedLoginAttempts = 2,
            passwordHash = passwordHash
        )
        val savedSlot = slot<User>()

        every { userRepository.findByEntityIdAndEmail(entityId, "test@example.com") } returns Optional.of(user)
        every { userRepository.save(capture(savedSlot)) } answers { firstArg() }

        val result = userService.authenticate("test@example.com", entityId, STRONG_PASSWORD)

        // lastLoginAt must be set on successful login
        assertNotNull(result.lastLoginAt)
        // Failed attempts counter must be reset
        assertEquals(0, result.failedLoginAttempts)
        // Lock must be cleared
        assertNull(result.lockedUntil)

        verify { userRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. authenticate throws ACCOUNT_LOCKED when lockedUntil is in future
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `authenticate throws ACCOUNT_LOCKED when lockedUntil is in future`() {
        val futureLockedUntil = Instant.now().plusSeconds(1800)
        val user = buildUser(
            status = UserStatus.ACTIVE,
            lockedUntil = futureLockedUntil
        )

        every { userRepository.findByEntityIdAndEmail(entityId, "test@example.com") } returns Optional.of(user)

        val ex = assertThrows<ValidationException> {
            userService.authenticate("test@example.com", entityId, "anyPassword")
        }
        assertEquals("ACCOUNT_LOCKED", ex.errorCode)

        // No save should occur for a lockout gate rejection
        verify(exactly = 0) { userRepository.save(any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. authenticate increments failedLoginAttempts on wrong password
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `authenticate increments failedLoginAttempts on wrong password`() {
        // Hash is for STRONG_PASSWORD; we will pass a different password to force a mismatch
        val passwordHash = realEncoder.encode(STRONG_PASSWORD)
        val user = buildUser(
            status = UserStatus.ACTIVE,
            failedLoginAttempts = 1,
            passwordHash = passwordHash
        )
        val savedSlot = slot<User>()

        every { userRepository.findByEntityIdAndEmail(entityId, "test@example.com") } returns Optional.of(user)
        every { userRepository.save(capture(savedSlot)) } answers { firstArg() }

        val ex = assertThrows<ValidationException> {
            userService.authenticate("test@example.com", entityId, "WrongPass!9") // different password
        }
        assertEquals("INVALID_CREDENTIALS", ex.errorCode)

        // failedLoginAttempts must have been incremented from 1 to 2
        verify { userRepository.save(match { it.failedLoginAttempts == 2 }) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. authenticate locks account after 5 failed attempts
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `authenticate locks account after 5 failed attempts`() {
        val passwordHash = realEncoder.encode(STRONG_PASSWORD)
        val user = buildUser(
            status = UserStatus.ACTIVE,
            failedLoginAttempts = 4, // this attempt will be the 5th
            passwordHash = passwordHash
        )
        val savedSlot = slot<User>()

        every { userRepository.findByEntityIdAndEmail(entityId, "test@example.com") } returns Optional.of(user)
        every { userRepository.save(capture(savedSlot)) } answers { firstArg() }

        // Pass the wrong password to trigger a failure
        assertThrows<ValidationException> {
            userService.authenticate("test@example.com", entityId, "WrongPass!9")
        }

        val captured = savedSlot.captured
        assertEquals(UserStatus.LOCKED, captured.status,
            "Status must be LOCKED after 5 consecutive failed login attempts")
        assertNotNull(captured.lockedUntil,
            "lockedUntil must be set when account is locked")
        assertTrue(captured.lockedUntil!!.isAfter(Instant.now()),
            "lockedUntil must be in the future")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. deactivate soft-deletes user correctly
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `deactivate soft-deletes user correctly`() {
        val userId = UUID.randomUUID()
        val user = buildUser(id = userId, status = UserStatus.ACTIVE)
        val savedSlot = slot<User>()

        every { userRepository.findById(userId) } returns Optional.of(user)
        every { userRepository.save(capture(savedSlot)) } answers { firstArg() }

        val result = userService.deactivate(userId, "left company", adminId)

        // Soft-delete fields must all be set
        assertFalse(result.isActive, "isActive must be false after deactivation")
        assertEquals(UserStatus.DEACTIVATED, result.status)
        assertEquals("left company", result.deactivationReason)
        assertNotNull(result.deactivatedAt)
        assertEquals(adminId, result.deactivatedBy)

        verify { userRepository.save(any()) }
    }
}

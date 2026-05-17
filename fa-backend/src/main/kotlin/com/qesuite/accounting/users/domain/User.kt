package com.qesuite.accounting.users.domain

import com.qesuite.accounting.shared.security.UserRole
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant
import java.util.UUID

/**
 * User entity — infrastructure/security record, NOT a financial entity.
 * Does not extend BaseFinancialEntity; manages its own audit columns so that
 * createdBy/modifiedBy are always explicitly set by the service layer (not
 * via Spring Data JPA auditing principal lookup, which requires a principal
 * of type UUID that is not yet available at bootstrap time).
 */
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener::class)
class User(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false, updatable = false)
    val entityId: UUID,

    @Column(name = "full_name", nullable = false, length = 255)
    var fullName: String,

    @Column(nullable = false, length = 255)
    val email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var role: UserRole = UserRole.DATA_ENTRY,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: UserStatus = UserStatus.PENDING_VERIFICATION,

    @Column(name = "failed_login_attempts", nullable = false)
    var failedLoginAttempts: Int = 0,

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "email_verified_at")
    var emailVerifiedAt: Instant? = null,

    @Column(name = "must_change_password", nullable = false)
    var mustChangePassword: Boolean = false,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "deactivated_at")
    var deactivatedAt: Instant? = null,

    @Column(name = "deactivated_by")
    var deactivatedBy: UUID? = null,

    @Column(name = "deactivation_reason")
    var deactivationReason: String? = null,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    /**
     * Explicitly set by service layer. Cannot use @CreatedBy here because the
     * JPA AuditorAware principal is not available during bootstrap registration.
     */
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    var modifiedAt: Instant = Instant.now(),

    /**
     * Explicitly set by service layer on every mutating operation.
     */
    @Column(name = "modified_by", nullable = false)
    var modifiedBy: UUID,

    @Version
    @Column(nullable = false)
    var version: Long = 0
)

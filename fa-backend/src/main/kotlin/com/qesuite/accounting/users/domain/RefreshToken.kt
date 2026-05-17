package com.qesuite.accounting.users.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(name = "token_hash", nullable = false, length = 255)
    val tokenHash: String,

    @Column(name = "issued_at", nullable = false, updatable = false)
    val issuedAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "revoked_by")
    var revokedBy: UUID? = null,

    @Column(name = "user_agent", length = 500)
    val userAgent: String? = null,

    @Column(name = "client_ip", length = 45)
    val clientIp: String? = null
) {
    val isRevoked: Boolean get() = revokedAt != null
    val isExpired: Boolean get() = Instant.now().isAfter(expiresAt)
    val isValid: Boolean get() = !isRevoked && !isExpired
}

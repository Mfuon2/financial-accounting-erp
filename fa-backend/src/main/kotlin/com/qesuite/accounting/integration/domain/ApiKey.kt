package com.qesuite.accounting.integration.domain

import com.qesuite.accounting.shared.security.UserRole
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

enum class ApiKeyStatus {
    ACTIVE, REVOKED, EXPIRED;
    fun isUsable(): Boolean = this == ACTIVE
}

@Entity
@Table(name = "api_keys")
class ApiKey(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false, updatable = false)
    val entityId: UUID,

    /** Public prefix shown in listings — safe to display */
    @Column(name = "key_id", nullable = false, updatable = false, length = 40)
    val keyId: String,

    /** SHA-256 hash of the full secret — never the plaintext */
    @Column(name = "key_hash", nullable = false, length = 255)
    val keyHash: String,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var role: UserRole = UserRole.DATA_ENTRY,

    /** Comma-separated scope tokens e.g. "read:journals,write:invoices" */
    @Column(columnDefinition = "TEXT")
    var scopes: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ApiKeyStatus = ApiKeyStatus.ACTIVE,

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null,

    @Column(name = "last_used_ip", length = 45)
    var lastUsedIp: String? = null,

    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "modified_at", nullable = false)
    var modifiedAt: Instant = Instant.now(),

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "revoked_by")
    var revokedBy: UUID? = null,

    @Column(name = "revocation_reason", columnDefinition = "TEXT")
    var revocationReason: String? = null,

    @Version
    @Column(nullable = false)
    var version: Long = 0
) {
    val isExpired: Boolean get() = expiresAt != null && Instant.now().isAfter(expiresAt)
    val isUsable: Boolean get() = status.isUsable() && !isExpired

    fun scopeList(): List<String> = scopes?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}

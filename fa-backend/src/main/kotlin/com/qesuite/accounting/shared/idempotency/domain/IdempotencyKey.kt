package com.qesuite.accounting.shared.idempotency.domain

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * §7.2 — Idempotency Key Entity
 * Stores idempotency keys and their cached responses to prevent duplicate request processing.
 * Implements write-through caching with Redis + DB persistence.
 */
@Entity
@Table(name = "idempotency_keys")
data class IdempotencyKey(
    @Id
    @Column(name = "id", nullable = false)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "idempotency_key", nullable = false, length = 255)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440001", description = "UUID-format idempotency key from request header")
    val idempotencyKey: String,

    @Column(name = "entity_id", nullable = false)
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000", description = "Tenant/entity for key scoping")
    val entityId: UUID,

    @Column(name = "request_hash", nullable = true, length = 255)
    @Schema(description = "SHA-256 hash of request body for integrity validation")
    val requestHash: String? = null,

    @Column(name = "response_body", nullable = true, columnDefinition = "TEXT")
    @Schema(description = "Cached response body (JSON string)")
    val responseBody: String? = null,

    @Column(name = "created_at", nullable = false)
    @Schema(description = "When this key was first stored")
    val createdAt: Instant = Instant.now(),

    @Column(name = "ttl_expires_at", nullable = false)
    @Schema(description = "When this key expires (24h TTL)")
    val ttlExpiresAt: Instant = Instant.now().plusSeconds(86400) // 24 hours
)

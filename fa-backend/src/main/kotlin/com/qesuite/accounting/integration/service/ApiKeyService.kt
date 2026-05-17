package com.qesuite.accounting.integration.service

import com.qesuite.accounting.integration.domain.ApiKey
import com.qesuite.accounting.integration.domain.ApiKeyStatus
import com.qesuite.accounting.integration.repository.ApiKeyRepository
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.BusinessRuleViolationException
import com.qesuite.accounting.shared.security.UserRole
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
@Transactional
class ApiKeyService(private val apiKeyRepository: ApiKeyRepository) {

    private val secureRandom = SecureRandom()

    // ─────────────────────────────────────────────────────────────────────────
    // Key generation helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a random alphanumeric string of [length] characters using SecureRandom.
     * Uses Base64URL characters restricted to [A-Za-z0-9] to guarantee alphanumeric output.
     */
    private fun randomAlphanumeric(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val sb = StringBuilder(length)
        repeat(length) {
            sb.append(alphabet[secureRandom.nextInt(alphabet.length)])
        }
        return sb.toString()
    }

    /**
     * Generates [byteCount] cryptographically random bytes and returns them Base64URL-encoded
     * (no padding), making the result URL-safe and header-safe.
     */
    private fun randomBytesBase64Url(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * SHA-256 hash of [input] string (UTF-8 encoded), returned as a lowercase hex string.
     */
    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates a new API key pair. The [ApiKeyCreationResult.fullKeyOnce] value is the
     * only time the plaintext secret is available — it is never stored and cannot be
     * recovered after this call returns.
     */
    fun generateApiKey(command: GenerateApiKeyCommand): ApiKeyCreationResult {
        // 1. Name uniqueness within entity
        if (apiKeyRepository.existsByEntityIdAndName(command.entityId, command.name)) {
            throw ConflictException(
                errorCode = "DUPLICATE_API_KEY_NAME",
                message = "An API key named '${command.name}' already exists for this entity",
                context = mapOf("entity_id" to command.entityId, "name" to command.name)
            )
        }

        // 2. Generate public keyId: "ak_" + 16 random alphanumeric characters
        val keyId = "ak_${randomAlphanumeric(16)}"

        // 3. Generate raw secret: 32 random bytes Base64URL-encoded
        val rawSecret = randomBytesBase64Url(32)

        // 4. Full key = what the client puts in X-Api-Key header
        val fullKey = "$keyId:$rawSecret"

        // 5. Hash for storage: SHA-256 of the FULL key string
        val keyHash = sha256Hex(fullKey)

        // 6. Persist the entity
        val apiKey = ApiKey(
            entityId = command.entityId,
            keyId = keyId,
            keyHash = keyHash,
            name = command.name,
            description = command.description,
            role = command.role,
            scopes = if (command.scopes.isEmpty()) null else command.scopes.joinToString(","),
            expiresAt = command.expiresAt,
            createdBy = command.createdBy
        )
        val saved = apiKeyRepository.save(apiKey)

        // 7. Return result — fullKeyOnce is shown ONCE and never retrievable again
        return ApiKeyCreationResult(apiKey = saved, fullKeyOnce = fullKey)
    }

    /**
     * Validates an incoming API key header value of the form `{keyId}:{secret}`.
     *
     * Returns the [ApiKey] entity on success, or `null` if:
     * - the header cannot be split into two parts
     * - no key with that keyId exists
     * - the SHA-256 hash of the full header does not match the stored hash
     * - the key is not usable (revoked, expired)
     *
     * On success, [ApiKey.lastUsedAt] and [ApiKey.lastUsedIp] are updated.
     *
     * @param rawKeyHeader the raw value of the `X-Api-Key` header
     * @param clientIp     the originating IP address (for audit purposes)
     */
    fun validateApiKey(rawKeyHeader: String, clientIp: String?): ApiKey? {
        // Split on the FIRST colon only — secret itself may contain colons (Base64URL does not,
        // but this makes the logic robust to future secret formats)
        val colonIndex = rawKeyHeader.indexOf(':')
        if (colonIndex < 0) return null

        val keyId = rawKeyHeader.substring(0, colonIndex)
        // secret is everything after the first colon (not used directly — only the full header is hashed)

        val apiKey = apiKeyRepository.findByKeyId(keyId).orElse(null) ?: return null

        // Hash the ENTIRE header string and compare with stored hash
        val incomingHash = sha256Hex(rawKeyHeader)
        if (incomingHash != apiKey.keyHash) return null

        // Reject unusable keys (revoked or expired)
        if (!apiKey.isUsable) return null

        // Update usage audit fields
        apiKey.lastUsedAt = Instant.now()
        apiKey.lastUsedIp = clientIp
        apiKey.modifiedAt = Instant.now()
        apiKeyRepository.save(apiKey)

        return apiKey
    }

    /**
     * Revokes an API key immediately. The key must currently be [ApiKeyStatus.ACTIVE].
     *
     * @param id        the [ApiKey.id] (UUID primary key)
     * @param reason    human-readable revocation reason stored for audit
     * @param revokedBy the UUID of the user performing the revocation
     */
    fun revokeApiKey(id: UUID, reason: String, revokedBy: UUID): ApiKey {
        val apiKey = findById(id)
        if (apiKey.status != ApiKeyStatus.ACTIVE) {
            throw BusinessRuleViolationException(
                errorCode = "API_KEY_NOT_ACTIVE",
                message = "API key '${apiKey.name}' is not active (current status: ${apiKey.status})",
                context = mapOf("id" to id, "current_status" to apiKey.status.name)
            )
        }
        apiKey.status = ApiKeyStatus.REVOKED
        apiKey.revokedAt = Instant.now()
        apiKey.revokedBy = revokedBy
        apiKey.revocationReason = reason
        apiKey.modifiedAt = Instant.now()
        return apiKeyRepository.save(apiKey)
    }

    /**
     * Rotates an API key: revokes the existing key and issues a new one with the same
     * configuration. The new key name has `"_rotated"` appended.
     *
     * The old key is revoked with reason `"Rotated"` before the new one is created so
     * the unique-name constraint is not violated (old name becomes `name`, new becomes
     * `name_rotated`).
     *
     * @param id         the [ApiKey.id] of the key to rotate
     * @param rotatedBy  UUID of the user performing the rotation
     */
    fun rotateApiKey(id: UUID, rotatedBy: UUID): ApiKeyCreationResult {
        val old = findById(id)
        if (old.status != ApiKeyStatus.ACTIVE) {
            throw BusinessRuleViolationException(
                errorCode = "API_KEY_NOT_ACTIVE",
                message = "Only ACTIVE keys can be rotated (current status: ${old.status})",
                context = mapOf("id" to id, "current_status" to old.status.name)
            )
        }

        // Revoke the old key first so its name is no longer "in use" for the uniqueness check
        old.status = ApiKeyStatus.REVOKED
        old.revokedAt = Instant.now()
        old.revokedBy = rotatedBy
        old.revocationReason = "Rotated"
        old.modifiedAt = Instant.now()
        apiKeyRepository.save(old)

        // Generate replacement with same configuration
        val command = GenerateApiKeyCommand(
            entityId = old.entityId,
            name = "${old.name}_rotated",
            description = old.description,
            role = old.role,
            scopes = old.scopeList(),
            expiresAt = old.expiresAt,
            createdBy = rotatedBy
        )
        return generateApiKey(command)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): ApiKey =
        apiKeyRepository.findById(id).orElseThrow {
            ResourceNotFoundException(
                errorCode = "API_KEY_NOT_FOUND",
                resourceId = id,
                resourceType = "ApiKey"
            )
        }

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<ApiKey> =
        apiKeyRepository.findByEntityId(entityId, pageable)

    @Transactional(readOnly = true)
    fun findActiveByEntity(entityId: UUID, pageable: Pageable): Page<ApiKey> =
        apiKeyRepository.findByEntityIdAndStatus(entityId, ApiKeyStatus.ACTIVE, pageable)
}

// ─────────────────────────────────────────────────────────────────────────────
// Command / Result data classes
// ─────────────────────────────────────────────────────────────────────────────

data class GenerateApiKeyCommand(
    val entityId: UUID,
    val name: String,
    val description: String? = null,
    val role: UserRole = UserRole.DATA_ENTRY,
    val scopes: List<String> = emptyList(),
    val expiresAt: Instant? = null,
    val createdBy: UUID
)

data class ApiKeyCreationResult(
    val apiKey: ApiKey,
    /** The full key string shown ONCE: "{keyId}:{secret}". Store it now — it cannot be recovered. */
    val fullKeyOnce: String
)

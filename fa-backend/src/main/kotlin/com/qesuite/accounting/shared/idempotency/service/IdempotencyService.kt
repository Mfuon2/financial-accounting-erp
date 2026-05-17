package com.qesuite.accounting.shared.idempotency.service

import com.qesuite.accounting.shared.exceptions.BaseAccountingException
import com.qesuite.accounting.shared.idempotency.domain.IdempotencyKey
import com.qesuite.accounting.shared.idempotency.repository.IdempotencyKeyRepository
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * §7.2 — Idempotency Service
 * Implements write-through caching pattern: Redis for fast lookup, DB for source of truth.
 * Returns either NEW (first request) or DUPLICATE (cached response).
 * 24-hour TTL on both Redis and DB entries.
 */
@Service
class IdempotencyService(
    private val idempotencyRepo: IdempotencyKeyRepository,
    private val redis: RedisTemplate<String, String>
) {

    companion object {
        private const val REDIS_KEY_PREFIX = "idempotency:"
        private const val TTL_HOURS = 24L
    }

    /**
     * Check for an existing idempotency key and return NEW or DUPLICATE.
     * Implements §7.2 pattern: check Redis first (fast), then DB (source of truth),
     * then store if NEW.
     *
     * @param idempotencyKey The key from Idempotency-Key header (should be UUID format)
     * @param entityId The tenant/entity for key scoping
     * @param requestHash Optional SHA-256 hash of request body for integrity checks
     * @return IdempotencyResult.NEW if key is new, or IdempotencyResult.DUPLICATE with cached response
     */
    @Transactional
    fun checkAndStore(
        idempotencyKey: String,
        entityId: UUID,
        requestHash: String? = null
    ): IdempotencyResult {
        val cacheKey = buildCacheKey(entityId, idempotencyKey)

        // §7.2 Step 1: Fast path — check Redis
        val cachedResponse = redis.opsForValue().get(cacheKey)
        if (cachedResponse != null && cachedResponse != "PROCESSING") {
            return IdempotencyResult.DUPLICATE(cachedResponse)
        }

        // §7.2 Step 2: Source of truth — check database
        val stored = idempotencyRepo.findByIdempotencyKeyAndEntityId(idempotencyKey, entityId)
        if (stored != null) {
            if (stored.responseBody != null) {
                // Key exists with a completed response — restore to cache and return duplicate.
                redis.opsForValue().set(cacheKey, stored.responseBody, TTL_HOURS, TimeUnit.HOURS)
                return IdempotencyResult.DUPLICATE(stored.responseBody)
            }
            // Key exists but response not yet written (first request still in-flight).
            // Returning DUPLICATE here prevents a second attempt from falling through to
            // the INSERT below which would hit the unique constraint and throw
            // DataIntegrityViolationException. The aspect will surface a 409 to the caller.
            return IdempotencyResult.IN_FLIGHT
        }

        // §7.2 Step 3: NEW key — store placeholder
        // Mark as PROCESSING in Redis to guard against concurrent requests
        redis.opsForValue().set(cacheKey, "PROCESSING", TTL_HOURS, TimeUnit.HOURS)

        // Store in DB (source of truth)
        val now = Instant.now()
        val ttlExpires = now.plusSeconds(TTL_HOURS * 3600)
        val newKey = IdempotencyKey(
            idempotencyKey = idempotencyKey,
            entityId = entityId,
            requestHash = requestHash,
            createdAt = now,
            ttlExpiresAt = ttlExpires
        )
        idempotencyRepo.save(newKey)

        return IdempotencyResult.NEW
    }

    /**
     * Update the cached response for a key after processing completes.
     * Called from controller after successful operation to cache the response.
     *
     * @param idempotencyKey The idempotency key
     * @param entityId The tenant/entity
     * @param responseBody The response JSON to cache
     */
    @Transactional
    fun updateResponse(
        idempotencyKey: String,
        entityId: UUID,
        responseBody: String
    ) {
        val cacheKey = buildCacheKey(entityId, idempotencyKey)

        // Update Redis cache
        redis.opsForValue().set(cacheKey, responseBody, TTL_HOURS, TimeUnit.HOURS)

        // Update DB (source of truth)
        val stored = idempotencyRepo.findByIdempotencyKeyAndEntityId(idempotencyKey, entityId)
        if (stored != null) {
            val updated = stored.copy(responseBody = responseBody)
            idempotencyRepo.save(updated)
        }
    }

    /**
     * Build the Redis cache key with proper scoping.
     */
    private fun buildCacheKey(entityId: UUID, idempotencyKey: String): String {
        return "$REDIS_KEY_PREFIX$entityId:$idempotencyKey"
    }
}

/**
 * §7.2 — Result of idempotency check
 */
sealed class IdempotencyResult {
    /**
     * First request with this key; proceed with processing.
     */
    data object NEW : IdempotencyResult()

    /**
     * Duplicate request with a completed cached response; return it directly.
     */
    data class DUPLICATE(val cachedResponse: String) : IdempotencyResult()

    /**
     * Key exists in the DB but the first request is still in-flight (responseBody is null).
     * Return 409 so the caller can retry after the first request completes.
     */
    data object IN_FLIGHT : IdempotencyResult()
}

/**
 * §6.5 — Exception for duplicate requests
 */
class IdempotencyConflictException(
    val idempotencyKey: String,
    val originalRequestAt: Instant,
    errorCode: String = "DUPLICATE_REQUEST",
    message: String = "Duplicate Idempotency-Key; returning cached response",
    httpStatus: Int = 409,
    context: Map<String, Any?> = emptyMap()
) : BaseAccountingException(
    errorCode = errorCode,
    message = message,
    httpStatus = httpStatus,
    context = context + mapOf(
        "idempotency_key" to idempotencyKey,
        "original_request_at" to originalRequestAt
    )
)

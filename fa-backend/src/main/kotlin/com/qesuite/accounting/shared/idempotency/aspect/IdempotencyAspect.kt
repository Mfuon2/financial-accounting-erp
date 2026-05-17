package com.qesuite.accounting.shared.idempotency.aspect

import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.idempotency.service.IdempotencyResult
import com.qesuite.accounting.shared.idempotency.service.IdempotencyService
import com.qesuite.accounting.shared.security.SecurityUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

/**
 * §7.2, §11.06 — AOP enforcement for [RequireIdempotencyKey].
 *
 * For every annotated method, the aspect:
 *   1. Extracts the `Idempotency-Key` header from the current servlet request.
 *   2. Returns 400 / `MISSING_IDEMPOTENCY_KEY` if absent.
 *   3. Returns 400 / `INVALID_IDEMPOTENCY_KEY_FORMAT` if not a valid UUID.
 *   4. Resolves the caller's `entityId` from [SecurityUtils] (multi-tenant scoping).
 *   5. Calls [IdempotencyService.checkAndStore]:
 *        - `NEW`        → proceed; on success serialize the response body and store it.
 *        - `DUPLICATE`  → bypass the controller and return the cached body via 409
 *                        envelope per §6.5 `DUPLICATE_REQUEST`.
 *
 * Ordered before audit / period-lock interceptors so duplicates do not pollute the
 * audit log.
 */
@Aspect
@Component
@Order(0)
class IdempotencyAspect(
    private val idempotencyService: IdempotencyService,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(IdempotencyAspect::class.java)

    @Around("@annotation(com.qesuite.accounting.shared.idempotency.aspect.RequireIdempotencyKey)")
    fun enforce(joinPoint: ProceedingJoinPoint): Any? {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            ?: throw IllegalStateException(
                "@RequireIdempotencyKey is only valid inside an HTTP request context."
            )
        val request = attributes.request

        // §6.2 — `MISSING_IDEMPOTENCY_KEY`.
        val raw = request.getHeader("Idempotency-Key")
            ?: throw ValidationException(
                errorCode = "MISSING_IDEMPOTENCY_KEY",
                message = "Idempotency-Key header is required for this endpoint.",
                context = mapOf("endpoint" to request.requestURI),
            )

        // §6.2 — `INVALID_IDEMPOTENCY_KEY_FORMAT`. We accept canonical UUIDs only.
        val key = raw.trim()
        try {
            UUID.fromString(key)
        } catch (_: IllegalArgumentException) {
            throw ValidationException(
                errorCode = "INVALID_IDEMPOTENCY_KEY_FORMAT",
                message = "Idempotency-Key must be a UUID.",
                context = mapOf("idempotency_key" to key),
            )
        }

        // §5.1 (skills) — Multi-tenant scoping. Falls back to the system-tenant UUID
        // (all-zeros) only when the request is genuinely anonymous (e.g. M-Pesa
        // webhooks); see [SecurityUtils.currentEntityIdOrSystem].
        val entityId = SecurityUtils.currentEntityIdOrSystem()

        return when (val result = idempotencyService.checkAndStore(key, entityId)) {
            is IdempotencyResult.NEW -> {
                val response = joinPoint.proceed()
                // §7.2 — Persist the response so replays return the same result.
                // Failure here must be logged (not swallowed) so ops can detect broken idempotency.
                try {
                    val body = objectMapper.writeValueAsString(response)
                    idempotencyService.updateResponse(key, entityId, body)
                } catch (ex: Exception) {
                    log.error(
                        "idempotency.aspect: failed to persist response for key={} entity={} — " +
                        "idempotency guarantee is broken for this key; original request succeeded",
                        key, entityId, ex
                    )
                }
                response
            }
            is IdempotencyResult.DUPLICATE -> {
                // §6.5 — `DUPLICATE_REQUEST`. The duplicate is not an error from the
                // caller's perspective, but we surface it as 409 with the cached
                // response embedded in the envelope so they can detect the replay.
                val cached: Any? = runCatching {
                    objectMapper.readValue(result.cachedResponse, Any::class.java)
                }.getOrNull()
                ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                        ApiResponse.error(
                            errorCode = "DUPLICATE_REQUEST",
                            message = "Idempotency-Key replay; cached response returned.",
                            context = mapOf(
                                "idempotency_key" to key,
                                "cached_response" to cached,
                            ),
                            status = 409,
                        )
                    )
            }
            is IdempotencyResult.IN_FLIGHT -> {
                // First request is still processing — client should retry after a short delay.
                ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(
                        ApiResponse.error(
                            errorCode = "REQUEST_IN_FLIGHT",
                            message = "A request with this Idempotency-Key is already being processed. " +
                                      "Please retry after a few seconds.",
                            context = mapOf("idempotency_key" to key),
                            status = 409,
                        )
                    )
            }
        }
    }

    /**
     * Defensive: if the [ConflictException] path bubbles up from the service layer
     * (e.g. a race resulting in a write-through gap), it is mapped by
     * [com.qesuite.accounting.shared.exceptions.GlobalExceptionHandler] just like any
     * other domain conflict — no special handling required here.
     */
    @Suppress("unused")
    private val _conflictAlias = ConflictException::class
}

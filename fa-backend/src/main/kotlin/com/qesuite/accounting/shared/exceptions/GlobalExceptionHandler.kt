package com.qesuite.accounting.shared.exceptions

import com.qesuite.accounting.shared.security.InvalidTokenTypeException
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * §6.11 — ControllerAdvice Implementation
 *
 * Handles all known exception types and maps them to a consistent [ApiResponse] envelope.
 *
 * Priority order (Spring matches the most specific handler first):
 * 0. JWT/token exceptions (401) and [AccessDeniedException] (403) — security-layer exceptions
 *    that must NOT fall through to the generic 500 catch-all (see §0.5 below: before it was
 *    added, every `@PreAuthorize` denial anywhere in this app returned 500 INTERNAL_ERROR
 *    instead of 403 FORBIDDEN, because AccessDeniedException isn't a BaseAccountingException
 *    and has no more-specific handler than the catch-all).
 * 1. [BaseAccountingException] subtypes — domain exceptions with their own HTTP status.
 *    Covers: ValidationException (400), ResourceNotFoundException (404),
 *    ImmutableRecordException (422), PeriodLockedException (422),
 *    BusinessRuleViolationException (422), ConflictException (409).
 * 2. Spring MVC / Bean Validation framework exceptions — mapped below.
 *    2f. [DataIntegrityViolationException] — DB unique/FK/check constraint → 409 Conflict.
 * 3. Catch-all [Exception] — HTTP 500 (logged at ERROR level).
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // ──────────────────────────────────────────────────────────────────────────
    // 0. JWT / Token exceptions — HTTP 401
    //    These are distinct from domain exceptions and must return 401, not 400/422.
    //    Note: most JWT errors are already handled in JwtAuthenticationFilter before
    //    reaching controllers. These handlers cover cases where token validation
    //    happens inside service/controller code (e.g. /auth/refresh).
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(InvalidTokenTypeException::class)
    fun handleInvalidTokenType(
        ex: InvalidTokenTypeException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = 401
        return ResponseEntity.status(status).body(
            ApiResponse.error("WRONG_TOKEN_TYPE", ex.message ?: "Invalid token type.", null, status)
        )
    }

    @ExceptionHandler(ExpiredJwtException::class)
    fun handleExpiredJwt(
        ex: ExpiredJwtException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = 401
        return ResponseEntity.status(status).body(
            ApiResponse.error(
                "TOKEN_EXPIRED",
                "Your session has expired. Call POST /api/v1/auth/refresh to obtain a new access token.",
                null, status
            )
        )
    }

    @ExceptionHandler(JwtException::class)
    fun handleJwtException(
        ex: JwtException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("jwt.handler: invalid JWT — {}", ex.message)
        val status = 401
        return ResponseEntity.status(status).body(
            ApiResponse.error("INVALID_TOKEN", "The provided token is invalid or has been tampered with.", null, status)
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 0.5 — Method-security denial (@PreAuthorize failure) → HTTP 403.
    //     Without this handler, AccessDeniedException (which is NOT a
    //     BaseAccountingException) falls through to the generic Exception catch-all
    //     below and returns 500 INTERNAL_ERROR instead of 403 FORBIDDEN — silently
    //     defeating every @PreAuthorize role check in the entire application.
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("access.denied: {}", ex.message)
        val status = 403
        return ResponseEntity.status(status).body(
            ApiResponse.error("FORBIDDEN", "You do not have permission to perform this action.", null, status)
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. Domain exceptions (BaseAccountingException hierarchy)
    //    BusinessRuleViolationException and ConflictException both extend
    //    BaseAccountingException and are therefore handled here automatically.
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(BaseAccountingException::class)
    fun handleAccountingException(ex: BaseAccountingException, request: WebRequest): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity
            .status(ex.httpStatus)
            .body(ApiResponse.error(ex.errorCode, ex.message ?: "An error occurred", ex.context, ex.httpStatus))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2a. Bean Validation — @Valid on @RequestBody (MethodArgumentNotValidException)
    //     Extracts all field-level errors into the errors array of ApiResponse.
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val fieldErrors = ex.bindingResult.fieldErrors
            .associate { it.field to (it.defaultMessage ?: "Invalid value") }
        val status = 400
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "VALIDATION_FAILED",
                    message = "Request validation failed: ${fieldErrors.size} field error(s)",
                    context = fieldErrors,
                    status = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2b. Bean Validation — @Validated on @RequestParam / @PathVariable
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val violations = ex.constraintViolations
            .associate { it.propertyPath.toString() to it.message }
        val status = 400
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "VALIDATION_FAILED",
                    message = "Constraint violation: ${violations.size} violation(s)",
                    context = violations,
                    status = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2c. Missing required request header (e.g. Idempotency-Key) → 400.
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeader(
        ex: MissingRequestHeaderException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = 400
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "MISSING_REQUIRED_HEADER",
                    message   = "Required header '${ex.headerName}' is missing.",
                    context   = mapOf("header" to ex.headerName),
                    status    = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2d. Missing required @RequestParam — returns 400 instead of falling to 500.
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingRequestParam(
        ex: MissingServletRequestParameterException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = 400
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "MISSING_REQUIRED_PARAMETER",
                    message   = "Required parameter '${ex.parameterName}' of type ${ex.parameterType} is missing.",
                    context   = mapOf("parameter" to ex.parameterName, "type" to ex.parameterType),
                    status    = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2e. Malformed or unreadable request body (e.g. invalid JSON)
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = 400
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "INVALID_REQUEST_BODY",
                    message = "Request body is missing or cannot be parsed: ${ex.mostSpecificCause.message}",
                    context = null,
                    status = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2d. Unknown endpoint — Spring 6 raises NoResourceFoundException
    //     (replaces the older NoHandlerFoundException in Spring Boot 3.2+)
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: NoResourceFoundException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = 404
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "ENDPOINT_NOT_FOUND",
                    message = "No endpoint found for ${ex.httpMethod} /${ex.resourcePath}",
                    context = null,
                    status = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2e. Optimistic locking conflict (concurrent modification detected by JPA)
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(OptimisticLockingFailureException::class)
    fun handleOptimisticLockingFailure(
        ex: OptimisticLockingFailureException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        val status = 409
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "OPTIMISTIC_LOCK_CONFLICT",
                    message = "The resource was modified by another request. Please reload and retry.",
                    context = null,
                    status = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2f. DB constraint violations — unique key, FK, check constraints
    //     Spring wraps the underlying JDBC ConstraintViolationException here.
    //     Surface as 409 Conflict so callers get an actionable status code
    //     instead of a generic 500.
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: DataIntegrityViolationException,
        request: WebRequest
    ): ResponseEntity<ApiResponse<Nothing>> {
        log.warn("data.integrity: constraint violation — {}", ex.mostSpecificCause.message)
        val status = 409
        return ResponseEntity
            .status(status)
            .body(
                ApiResponse.error(
                    errorCode = "DATA_INTEGRITY_VIOLATION",
                    message = "The request conflicts with existing data. A unique or referential constraint was violated.",
                    context = null,
                    status = status
                )
            )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. Catch-all — unexpected runtime exceptions
    // ──────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception, request: WebRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.error("unhandled.exception: {} — {}", ex.javaClass.simpleName, ex.message, ex)
        val errorCode = "INTERNAL_ERROR"
        val status = 500
        return ResponseEntity
            .status(status)
            .body(ApiResponse.error(errorCode, "An unexpected error occurred", null, status))
    }
}

abstract class BaseAccountingException(
    val errorCode: String,
    override val message: String?,
    val httpStatus: Int = 400,
    val context: Map<String, Any?>? = null
) : RuntimeException(message)

class ValidationException(
    errorCode: String = "VALIDATION_FAILED",
    message: String,
    context: Map<String, Any?>? = null,
    httpStatus: Int = 400   // allows 401 for token-related validation errors
) : BaseAccountingException(errorCode, message, httpStatus, context)

class ResourceNotFoundException(
    errorCode: String = "RESOURCE_NOT_FOUND",
    resourceId: Any,
    resourceType: String = "Resource"
) : BaseAccountingException(errorCode, "$resourceType not found with ID: $resourceId", 404, mapOf("resource_id" to resourceId.toString(), "resource_type" to resourceType))

class ImmutableRecordException(
    errorCode: String = "IMMUTABLE_RECORD",
    message: String,
    resourceType: String,
    resourceId: java.util.UUID
) : BaseAccountingException(
    errorCode,
    message,
    422,
    mapOf("resource_type" to resourceType, "resource_id" to resourceId.toString())
)

/**
 * §6.6 — Business rule violation (HTTP 422). Use this for domain invariants that the
 * client *could* fix with a different request: balance mismatches, period state errors,
 * credit-limit breaches, immutable record edits, etc.
 *
 * Distinct from [ValidationException] (HTTP 400 — input format / required fields).
 */
class BusinessRuleViolationException(
    errorCode: String,
    message: String,
    context: Map<String, Any?>? = null,
) : BaseAccountingException(errorCode, message, 422, context)

/**
 * §6.5 — Conflict / duplicate request (HTTP 409). Idempotency replays, duplicate keys,
 * optimistic-lock conflicts.
 */
class ConflictException(
    errorCode: String,
    message: String,
    context: Map<String, Any?>? = null,
) : BaseAccountingException(errorCode, message, 409, context)

package com.qesuite.accounting.shared.exceptions

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

/**
 * §6.1 — Standardized Response Envelope
 */
@Schema(description = "Standardized API response envelope (§6.1)")
data class ApiResponse<T>(
    @Schema(example = "true")
    val success: Boolean,
    val data: T? = null,
    val errors: List<ApiError>? = null,
    @Schema(example = "[]")
    val warnings: List<String> = emptyList(),
    val metadata: ResponseMetadata = ResponseMetadata()
) {
    companion object {
        fun <T> success(data: T?): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun error(errorCode: String, message: String, context: Map<String, Any?>? = null, status: Int = 400): ApiResponse<Nothing> =
            ApiResponse(
                success = false,
                errors = listOf(ApiError(errorCode = errorCode, message = message, context = context, httpStatus = status))
            )
    }
}

@Schema(description = "Error details for failed requests")
data class ApiError(
    @get:JsonProperty("error_code")
    @Schema(example = "VALIDATION_ERROR")
    val errorCode: String,
    @get:JsonProperty("http_status")
    @Schema(example = "400")
    val httpStatus: Int,
    @Schema(example = "Invalid account code format")
    val message: String,
    @Schema(example = "accountCode")
    val field: String? = null,
    val context: Map<String, Any?>? = null
)

@Schema(description = "Tracing and context metadata")
data class ResponseMetadata(
    @get:JsonProperty("entity_id")
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val entityId: UUID? = null,
    @get:JsonProperty("period_id")
    @Schema(example = "660e8400-e29b-41d4-a716-446655440001")
    val periodId: UUID? = null,
    @Schema(example = "2026-05-04T23:00:00Z")
    val timestamp: Instant = Instant.now(),
    @get:JsonProperty("trace_id")
    @Schema(example = "tr-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    val traceId: String = UUID.randomUUID().toString()
)

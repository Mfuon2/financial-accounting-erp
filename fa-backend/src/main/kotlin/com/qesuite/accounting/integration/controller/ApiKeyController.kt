package com.qesuite.accounting.integration.controller

import com.qesuite.accounting.integration.domain.ApiKey
import com.qesuite.accounting.integration.domain.ApiKeyStatus
import com.qesuite.accounting.integration.service.ApiKeyCreationResult
import com.qesuite.accounting.integration.service.ApiKeyService
import com.qesuite.accounting.integration.service.GenerateApiKeyCommand
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.UserContext
import com.qesuite.accounting.shared.security.UserRole
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/integration/keys")
@Tag(
    name = "Integration Access",
    description = "API Key management for third-party service integrations. " +
        "Keys authenticate via the X-Api-Key header using the format {keyId}:{secret}. " +
        "The plaintext secret is returned ONCE at creation and cannot be retrieved again."
)
class ApiKeyController(private val apiKeyService: ApiKeyService) {

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/integration/keys — Generate a new API key
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "Generate API key",
        description = "Creates a new API key pair for a third-party integration. " +
            "The full key (keyId:secret) is returned ONCE in the response field `fullKeyOnce` " +
            "and cannot be retrieved again. Store it securely immediately."
    )
    fun generateApiKey(
        @Valid @RequestBody request: GenerateApiKeyRequest,
        @AuthenticationPrincipal userContext: UserContext
    ): ApiResponse<ApiKeyCreationResponse> {
        val currentUser = SecurityUtils.currentUser()
        if (request.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        val command = GenerateApiKeyCommand(
            entityId    = request.entityId,
            name        = request.name,
            description = request.description,
            role        = request.role,
            scopes      = request.scopes,
            expiresAt   = request.expiresAt,
            createdBy   = userContext.userId
        )
        val result = apiKeyService.generateApiKey(command)
        return ApiResponse.success(ApiKeyCreationResponse.from(result))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/integration/keys — List keys (paginated)
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "List API keys",
        description = "Returns a paginated list of API keys for the specified entity. " +
            "Filter by status to retrieve only ACTIVE or REVOKED keys. " +
            "The plaintext secret is never returned in list responses."
    )
    fun listApiKeys(
        @Parameter(description = "Entity UUID to list keys for", required = true)
        @RequestParam entityId: UUID,
        @Parameter(description = "Filter by status (ACTIVE, REVOKED, EXPIRED). Omit for all.")
        @RequestParam(required = false) status: ApiKeyStatus?,
        @PageableDefault(size = 20, sort = ["createdAt"]) pageable: Pageable
    ): ApiResponse<Page<ApiKeyResponse>> {
        val currentUser = SecurityUtils.currentUser()
        if (entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        val page = if (status == ApiKeyStatus.ACTIVE) {
            apiKeyService.findActiveByEntity(entityId, pageable)
        } else {
            apiKeyService.findByEntity(entityId, pageable)
        }.map { ApiKeyResponse.from(it) }
        return ApiResponse.success(page)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/integration/keys/{id} — Get single key by id
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "Get API key by ID",
        description = "Returns the API key metadata. The plaintext secret is never returned."
    )
    fun getApiKey(
        @Parameter(description = "API key UUID") @PathVariable id: UUID
    ): ApiResponse<ApiKeyResponse> {
        val apiKey = apiKeyService.findById(id)
        val currentUser = SecurityUtils.currentUser()
        if (apiKey.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        return ApiResponse.success(ApiKeyResponse.from(apiKey))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/integration/keys/{id}/revoke — Revoke a key
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "Revoke API key",
        description = "Immediately revokes the API key. The key will no longer authenticate. " +
            "Revocation is permanent — a new key must be generated to restore access."
    )
    fun revokeApiKey(
        @Parameter(description = "API key UUID") @PathVariable id: UUID,
        @Valid @RequestBody request: RevokeApiKeyRequest,
        @AuthenticationPrincipal userContext: UserContext
    ): ApiResponse<ApiKeyResponse> {
        val currentUser = SecurityUtils.currentUser()
        val existingKey = apiKeyService.findById(id)
        if (existingKey.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        val revoked = apiKeyService.revokeApiKey(id, request.reason, userContext.userId)
        return ApiResponse.success(ApiKeyResponse.from(revoked))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/integration/keys/{id}/rotate — Rotate a key
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/rotate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "Rotate API key",
        description = "Revokes the existing key and issues a replacement with the same " +
            "role, scopes, and expiry. The new full key is returned in `fullKeyOnce` — " +
            "store it immediately, it cannot be recovered."
    )
    fun rotateApiKey(
        @Parameter(description = "API key UUID to rotate") @PathVariable id: UUID,
        @AuthenticationPrincipal userContext: UserContext
    ): ApiResponse<ApiKeyCreationResponse> {
        val currentUser = SecurityUtils.currentUser()
        val existingKey = apiKeyService.findById(id)
        if (existingKey.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        val result = apiKeyService.rotateApiKey(id, userContext.userId)
        return ApiResponse.success(ApiKeyCreationResponse.from(result))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Request DTOs
// ─────────────────────────────────────────────────────────────────────────────

data class GenerateApiKeyRequest(
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,

    @field:NotBlank(message = "name is required")
    @field:Size(max = 255, message = "name must not exceed 255 characters")
    val name: String,

    val description: String? = null,

    val role: UserRole = UserRole.DATA_ENTRY,

    val scopes: List<String> = emptyList(),

    val expiresAt: Instant? = null
)

data class RevokeApiKeyRequest(
    @field:NotBlank(message = "reason is required")
    val reason: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Response DTOs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Safe view of an API key — never exposes the secret or its hash.
 */
data class ApiKeyResponse(
    val id: UUID,
    val entityId: UUID,
    val keyId: String,
    val name: String,
    val description: String?,
    val role: UserRole,
    val scopes: List<String>,
    val status: ApiKeyStatus,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val createdAt: Instant
) {
    companion object {
        fun from(k: ApiKey) = ApiKeyResponse(
            id          = k.id,
            entityId    = k.entityId,
            keyId       = k.keyId,
            name        = k.name,
            description = k.description,
            role        = k.role,
            scopes      = k.scopeList(),
            status      = k.status,
            expiresAt   = k.expiresAt,
            lastUsedAt  = k.lastUsedAt,
            createdAt   = k.createdAt
        )
    }
}

/**
 * Response returned at key creation or rotation — includes the [fullKeyOnce] secret.
 * This is the ONLY time the secret is ever transmitted. The caller must store it
 * immediately in a secrets manager; it cannot be retrieved again.
 */
data class ApiKeyCreationResponse(
    val id: UUID,
    val keyId: String,
    val name: String,
    val role: UserRole,
    val scopes: List<String>,
    val expiresAt: Instant?,
    /** SHOWN ONCE ONLY — store securely, cannot be retrieved again */
    val fullKeyOnce: String,
    val createdAt: Instant
) {
    companion object {
        fun from(result: ApiKeyCreationResult) = ApiKeyCreationResponse(
            id          = result.apiKey.id,
            keyId       = result.apiKey.keyId,
            name        = result.apiKey.name,
            role        = result.apiKey.role,
            scopes      = result.apiKey.scopeList(),
            expiresAt   = result.apiKey.expiresAt,
            fullKeyOnce = result.fullKeyOnce,
            createdAt   = result.apiKey.createdAt
        )
    }
}

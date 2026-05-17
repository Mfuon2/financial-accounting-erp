package com.qesuite.accounting.organization.controller

import com.qesuite.accounting.organization.domain.Organization
import com.qesuite.accounting.organization.domain.OrganizationStatus
import com.qesuite.accounting.organization.service.CreateOrganizationCommand
import com.qesuite.accounting.organization.service.OrganizationService
import com.qesuite.accounting.organization.service.UpdateOrganizationCommand
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/organizations")
@Tag(
    name = "Organization Management",
    description = "Manage legal entities / tenants of the accounting system"
)
class OrganizationController(private val organizationService: OrganizationService) {

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/organizations
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // No @PreAuthorize — this is the public sign-up / bootstrap endpoint.
    // There is no super-admin before the first organization exists, so authentication
    // cannot be required here. All other organization management endpoints remain protected.
    @Operation(
        summary = "Create a new organization (public — no auth required)",
        description = """
**Public bootstrap endpoint.** Creates a new legal entity / tenant in the accounting system.
No JWT token is required — this is the entry point before any users exist.

**Onboarding flow:**
1. `POST /api/v1/organizations` — create your organization (this endpoint)
2. `POST /api/v1/auth/register` — register the first admin user using the returned `id` as `entityId`
3. `POST /api/v1/auth/login` — login and receive your JWT access token
4. All subsequent calls use `Authorization: Bearer <token>`

**Validation rules:**
- `name` must be unique across all organizations.
- `registrationNumber`, if provided, must be unique.
- `functionalCurrency` and `reportingCurrency` must be valid 3-letter ISO 4217 codes.
- `fiscalYearStartMonth` must be between 1 (January) and 12 (December).
- `timezone` must be a valid IANA timezone identifier (e.g. "Africa/Nairobi").
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Organization created successfully"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate organization name or registration number")
    )
    fun createOrganization(
        @Valid @RequestBody request: CreateOrganizationRequest
    ): ApiResponse<Organization> {
        // Resolve the actor: use the authenticated user if one exists (platform admin creating
        // a tenant on behalf of a customer), otherwise fall back to the system bootstrap actor
        // (unauthenticated self-service sign-up — the normal first-run path).
        val actorId = try {
            SecurityUtils.currentUser().userId
        } catch (_: Exception) {
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001") // SYSTEM_ACTOR_ID
        }

        val command = CreateOrganizationCommand(
            name = request.name,
            legalName = request.legalName,
            registrationNumber = request.registrationNumber,
            taxIdentificationNumber = request.taxIdentificationNumber,
            functionalCurrency = request.functionalCurrency,
            reportingCurrency = request.reportingCurrency,
            countryCode = request.countryCode,
            timezone = request.timezone,
            fiscalYearStartMonth = request.fiscalYearStartMonth,
            addressLine1 = request.addressLine1,
            addressLine2 = request.addressLine2,
            city = request.city,
            postalCode = request.postalCode,
            phone = request.phone,
            email = request.email,
            website = request.website,
            createdBy = actorId
        )
        return ApiResponse.success(organizationService.createOrganization(command))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/organizations
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'AUDITOR')")
    @Operation(
        summary = "List all organizations",
        description = "Returns a paginated list of all organizations. Optionally filter by status."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of organizations")
    )
    fun findAll(
        @RequestParam(required = false)
        @Parameter(description = "Filter by organization status (ACTIVE, SUSPENDED, DEACTIVATED)")
        status: OrganizationStatus?,
        @PageableDefault(size = 50) pageable: Pageable
    ): ApiResponse<PagedResponse<Organization>> {
        val page = if (status != null) {
            organizationService.findByStatus(status, pageable)
        } else {
            organizationService.findAll(pageable)
        }
        return ApiResponse.success(page.toPagedResponse { it })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/organizations/me
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(
        summary = "Get the current user's organization",
        description = "Returns the organization record for the authenticated user's entity. Accessible to all authenticated users."
    )
    fun getMyOrganization(): ApiResponse<Organization> {
        val entityId = SecurityUtils.currentUser().entityId
        return ApiResponse.success(organizationService.findById(entityId))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/organizations/me
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CONTROLLER_CFO')")
    @Operation(
        summary = "Update the current user's organization",
        description = "Updates mutable organization fields. Restricted to CONTROLLER_CFO and SYSTEM_ADMIN."
    )
    fun updateMyOrganization(
        @Valid @RequestBody command: UpdateOrganizationCommand
    ): ApiResponse<Organization> {
        val currentUser = SecurityUtils.currentUser()
        return ApiResponse.success(organizationService.updateOrganization(currentUser.entityId, command, currentUser.userId))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/organizations/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'AUDITOR', 'CONTROLLER_CFO')")
    @Operation(
        summary = "Get organization by ID",
        description = "Returns a single organization record by its UUID."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Organization found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Organization not found")
    )
    fun findById(
        @PathVariable @Parameter(description = "Organization UUID") id: UUID
    ): ApiResponse<Organization> {
        val currentUser = SecurityUtils.currentUser()
        if (currentUser.entityId != id) {
            throw com.qesuite.accounting.shared.exceptions.ValidationException(
                errorCode = "FORBIDDEN",
                message   = "You do not have permission to access organization $id.",
                httpStatus = 403
            )
        }
        return ApiResponse.success(organizationService.findById(id))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/v1/organizations/{id}
    // ─────────────────────────────────────────────────────────────────────────

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "Update an organization",
        description = "Updates mutable fields on an organization. All fields are optional — only non-null fields in the request body are applied."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Organization updated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Organization not found")
    )
    fun updateOrganization(
        @PathVariable @Parameter(description = "Organization UUID to update") id: UUID,
        @Valid @RequestBody command: UpdateOrganizationCommand
    ): ApiResponse<Organization> {
        val currentUser = SecurityUtils.currentUser()
        if (currentUser.entityId != id) {
            throw com.qesuite.accounting.shared.exceptions.ValidationException(
                errorCode = "FORBIDDEN",
                message   = "You do not have permission to update organization $id.",
                httpStatus = 403
            )
        }
        return ApiResponse.success(organizationService.updateOrganization(id, command, currentUser.userId))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/organizations/{id}/suspend
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "Suspend an organization",
        description = "Suspends an ACTIVE organization. The organization must be in ACTIVE status. A reason must be provided."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Organization suspended"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status transition"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Organization not found")
    )
    fun suspendOrganization(
        @PathVariable @Parameter(description = "Organization UUID to suspend") id: UUID,
        @Valid @RequestBody request: SuspendOrganizationRequest
    ): ApiResponse<Organization> {
        val currentUser = SecurityUtils.currentUser()
        return ApiResponse.success(
            organizationService.suspendOrganization(id, request.reason, currentUser.userId)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/organizations/{id}/activate
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
        summary = "Activate a suspended organization",
        description = "Re-activates a SUSPENDED organization, setting its status back to ACTIVE."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Organization activated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status transition (not SUSPENDED)"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Organization not found")
    )
    fun activateOrganization(
        @PathVariable @Parameter(description = "Organization UUID to activate") id: UUID
    ): ApiResponse<Organization> {
        val currentUser = SecurityUtils.currentUser()
        return ApiResponse.success(organizationService.activateOrganization(id, currentUser.userId))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Request DTOs (scoped to controller layer — commands live in service layer)
// ─────────────────────────────────────────────────────────────────────────────

data class CreateOrganizationRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val name: String,

    @field:Size(max = 255)
    val legalName: String? = null,

    @field:Size(max = 100)
    val registrationNumber: String? = null,

    @field:Size(max = 100)
    val taxIdentificationNumber: String? = null,

    @field:Size(min = 3, max = 3)
    val functionalCurrency: String = "USD",

    @field:Size(min = 3, max = 3)
    val reportingCurrency: String = "USD",

    @field:NotBlank
    @field:Size(min = 2, max = 2)
    val countryCode: String = "KE",

    @field:NotBlank
    @field:Size(max = 100)
    val timezone: String = "Africa/Nairobi",

    @field:NotNull
    val fiscalYearStartMonth: Int = 1,

    @field:Size(max = 255)
    val addressLine1: String? = null,

    @field:Size(max = 255)
    val addressLine2: String? = null,

    @field:Size(max = 100)
    val city: String? = null,

    @field:Size(max = 20)
    val postalCode: String? = null,

    @field:Size(max = 30)
    val phone: String? = null,

    @field:Size(max = 255)
    val email: String? = null,

    @field:Size(max = 255)
    val website: String? = null
)

data class SuspendOrganizationRequest(
    @field:NotBlank
    val reason: String
)

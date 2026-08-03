package com.qesuite.accounting.shared.categories.controller

import com.qesuite.accounting.shared.categories.domain.CategoryType
import com.qesuite.accounting.shared.categories.service.CategoryDto
import com.qesuite.accounting.shared.categories.service.CategoryService
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.SecurityUtils
import com.qesuite.accounting.shared.security.UserContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * §2 (CLAUDE.md — "Configuration-driven, not hard-coded") — one generic, per-entity CRUD
 * surface for every "pick one of these business codes" concept, following the same shape as
 * the configurable document numbering system (`shared/codegen`'s `NumberConfigController`).
 *
 * Ships with two [CategoryType]s (`PAYMENT_TERM`, `PAYMENT_METHOD`) covering the concrete
 * hard-coded-array violations logged in `MEMORY.md`
 * (`Suppliers.vue`/`Customers.vue`/`Bills.vue`/`Payments.vue`/`Invoices.vue`); a future kind
 * (e.g. `EXPENSE_CATEGORY`) is one new enum constant plus a seed list, not a new controller.
 *
 * Reads are available to any authenticated user of the entity (every role needs the dropdown
 * when creating a bill/invoice/customer/supplier). Mutations are gated to admin/controller
 * roles per CLAUDE.md's "config-shaped actions are admin/controller-level" standing rule —
 * `TaxController`/`NumberConfigController` were checked for precedent first and found to have
 * **no** role gate and **no** entity-ownership check at all (a pre-existing gap in both,
 * logged in `MEMORY.md`, not copied here).
 */
@RestController
@RequestMapping("/api/v1/categories")
@Tag(
    name = "Categories",
    description = "Generic, entity-owned, dynamically managed category values (payment terms, payment methods, and future kinds) — CLAUDE.md §2.",
)
class CategoryController(
    private val categoryService: CategoryService,
) {

    @GetMapping
    @Operation(
        summary = "List category values for a type",
        description = "Returns every category value of the given type for the entity, seeding the built-in defaults on first access. Pass activeOnly=false to include deactivated values (used by the admin management screen).",
    )
    fun list(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID,
        @RequestParam @Parameter(description = "PAYMENT_TERM, PAYMENT_METHOD, ...", required = true) type: String,
        @RequestParam(required = false, defaultValue = "true") activeOnly: Boolean,
    ): ApiResponse<List<CategoryDto>> {
        val currentUser = SecurityUtils.currentUser()
        if (entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        val categoryType = resolveType(type)
        return ApiResponse.success(categoryService.listByType(entityId, categoryType, activeOnly))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CONTROLLER_CFO')")
    @Operation(
        summary = "Create a category value",
        description = "Adds a new value to a category type for the entity (e.g. a custom payment term). Admin/Controller-CFO only.",
    )
    fun create(@Valid @RequestBody request: CreateCategoryRequest): ApiResponse<CategoryDto> {
        val currentUser = SecurityUtils.currentUser()
        if (request.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        val categoryType = resolveType(request.categoryType)
        val created = categoryService.create(request.entityId, categoryType, request.code, request.label, request.sortOrder)
        return ApiResponse.success(created)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CONTROLLER_CFO')")
    @Operation(
        summary = "Update a category value",
        description = "Updates the display label and/or sort order. The code is immutable once created (historical documents reference it). Admin/Controller-CFO only.",
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCategoryRequest,
    ): ApiResponse<CategoryDto> {
        val currentUser = SecurityUtils.currentUser()
        val existing = categoryService.findEntityById(id)
        if (existing.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        return ApiResponse.success(categoryService.update(id, request.label, request.sortOrder))
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CONTROLLER_CFO')")
    @Operation(
        summary = "Deactivate a category value (soft delete)",
        description = "Sets isActive=false. Existing records referencing this value are unaffected and keep resolving correctly. Admin/Controller-CFO only.",
    )
    fun deactivate(
        @PathVariable id: UUID,
        @Valid @RequestBody request: DeactivateCategoryRequest,
        @AuthenticationPrincipal userContext: UserContext,
    ): ApiResponse<CategoryDto> {
        val currentUser = SecurityUtils.currentUser()
        val existing = categoryService.findEntityById(id)
        if (existing.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        return ApiResponse.success(categoryService.deactivate(id, request.reason, userContext.userId))
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CONTROLLER_CFO')")
    @Operation(
        summary = "Reactivate a previously deactivated category value",
        description = "Sets isActive=true again. Admin/Controller-CFO only.",
    )
    fun activate(@PathVariable id: UUID): ApiResponse<CategoryDto> {
        val currentUser = SecurityUtils.currentUser()
        val existing = categoryService.findEntityById(id)
        if (existing.entityId != currentUser.entityId) {
            throw ValidationException("FORBIDDEN", "Access denied to resource in another entity.", httpStatus = 403)
        }
        return ApiResponse.success(categoryService.activate(id))
    }

    private fun resolveType(type: String): CategoryType =
        CategoryType.fromKey(type)
            ?: throw ValidationException(
                errorCode = "UNKNOWN_CATEGORY_TYPE",
                message = "Unknown category type '$type'. Known types: ${CategoryType.entries.joinToString { it.name }}.",
            )
}

data class CreateCategoryRequest(
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,
    @field:NotBlank(message = "categoryType is required")
    val categoryType: String,
    @field:NotBlank(message = "code is required")
    val code: String,
    @field:NotBlank(message = "label is required")
    val label: String,
    val sortOrder: Int? = null,
)

data class UpdateCategoryRequest(
    val label: String? = null,
    val sortOrder: Int? = null,
)

data class DeactivateCategoryRequest(
    @field:NotBlank(message = "Deactivation reason is required")
    val reason: String,
)

package com.qesuite.accounting.party.controller

import com.qesuite.accounting.party.domain.Supplier
import com.qesuite.accounting.party.dto.CreateSupplierCommand
import com.qesuite.accounting.party.dto.SupplierStatementResponse
import com.qesuite.accounting.party.dto.UpdateSupplierCommand
import com.qesuite.accounting.party.service.SupplierService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/suppliers")
@Tag(
    name = "Module 14: Suppliers",
    description = """
Supplier master data management for the Accounts Payable cycle.

Suppliers are uniquely identified by `supplierCode` within an entity. The code is
immutable once created. Deactivation is a soft-delete (§3.5) — history is preserved.
"""
)
class SupplierController(
    private val supplierService: SupplierService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new supplier",
        description = "Creates a supplier record. The supplierCode must be unique within the entity."
    )
    fun create(
        @Valid @RequestBody command: CreateSupplierCommand
    ): ApiResponse<Supplier> {
        SecurityUtils.requireOwnEntity(command.entityId)
        return ApiResponse.success(supplierService.create(command))
    }

    @GetMapping
    @Operation(
        summary = "List suppliers for an entity",
        description = "Returns a paginated list of suppliers. Pass activeOnly=true to exclude deactivated suppliers."
    )
    fun list(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID,
        @RequestParam(required = false, defaultValue = "true")
        @Parameter(description = "When true, returns only active suppliers") activeOnly: Boolean,
        @PageableDefault(size = 50) pageable: Pageable
    ): ApiResponse<PagedResponse<Supplier>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = if (activeOnly) {
            supplierService.findByEntityActive(entityId, pageable)
        } else {
            supplierService.findByEntity(entityId, pageable)
        }
        return ApiResponse.success(page.toPagedResponse { it })
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a supplier by ID")
    fun findById(
        @PathVariable @Parameter(description = "Supplier UUID") id: UUID
    ): ApiResponse<Supplier> {
        val supplier = supplierService.findById(id)
        SecurityUtils.requireOwnEntity(supplier.entityId)
        return ApiResponse.success(supplier)
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a supplier",
        description = "Updates mutable fields: email, phone, paymentTerms. Only non-null fields in the request body are applied."
    )
    fun update(
        @PathVariable @Parameter(description = "Supplier UUID") id: UUID,
        @Valid @RequestBody command: UpdateSupplierCommand
    ): ApiResponse<Supplier> {
        SecurityUtils.requireOwnEntity(supplierService.findById(id).entityId)
        return ApiResponse.success(supplierService.update(id, command))
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
        summary = "Deactivate a supplier (soft delete)",
        description = "Sets isActive=false. All historical records are preserved."
    )
    fun deactivate(
        @PathVariable @Parameter(description = "Supplier UUID") id: UUID,
        @Valid @RequestBody command: DeactivateSupplierCommand
    ): ApiResponse<Supplier> {
        SecurityUtils.requireOwnEntity(supplierService.findById(id).entityId)
        return ApiResponse.success(supplierService.deactivate(id, command.reason, command.deactivatedBy))
    }

    @GetMapping("/{id}/statement")
    @Operation(
        summary = "Supplier statement",
        description = "Returns all bills and payments for a supplier with running balance, sorted by date ascending."
    )
    fun getStatement(
        @PathVariable @Parameter(description = "Supplier UUID") id: UUID
    ): ApiResponse<SupplierStatementResponse> {
        SecurityUtils.requireOwnEntity(supplierService.findById(id).entityId)
        return ApiResponse.success(supplierService.getStatement(id))
    }
}

data class DeactivateSupplierCommand(
    @field:NotBlank(message = "Deactivation reason is required")
    val reason: String,
    @field:NotNull(message = "deactivatedBy user ID is required")
    val deactivatedBy: UUID
)

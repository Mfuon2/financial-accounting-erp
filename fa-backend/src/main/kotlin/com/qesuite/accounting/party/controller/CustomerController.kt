package com.qesuite.accounting.party.controller

import com.qesuite.accounting.party.domain.Customer
import com.qesuite.accounting.party.dto.CreateCustomerCommand
import com.qesuite.accounting.party.dto.UpdateCustomerCommand
import com.qesuite.accounting.party.service.CustomerService
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
@RequestMapping("/api/v1/customers")
@Tag(
    name = "Module 14: Customers",
    description = """
Customer master data management for the Accounts Receivable cycle.

Customers are uniquely identified by `customerCode` within an entity. The code is
immutable once created. Deactivation is a soft-delete (§3.5) — history is preserved.
"""
)
class CustomerController(
    private val customerService: CustomerService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new customer",
        description = "Creates a customer record. The customerCode must be unique within the entity."
    )
    fun create(
        @Valid @RequestBody command: CreateCustomerCommand
    ): ApiResponse<Customer> {
        SecurityUtils.requireOwnEntity(command.entityId)
        return ApiResponse.success(customerService.create(command))
    }

    @GetMapping
    @Operation(
        summary = "List customers for an entity",
        description = "Returns a paginated list of customers. Pass activeOnly=true to exclude deactivated customers."
    )
    fun list(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID,
        @RequestParam(required = false, defaultValue = "true")
        @Parameter(description = "When true, returns only active customers") activeOnly: Boolean,
        @PageableDefault(size = 50) pageable: Pageable
    ): ApiResponse<PagedResponse<Customer>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = if (activeOnly) {
            customerService.findByEntityActive(entityId, pageable)
        } else {
            customerService.findByEntity(entityId, pageable)
        }
        return ApiResponse.success(page.toPagedResponse { it })
    }

    @GetMapping("/{id}")
    @Operation(summary = "Retrieve a customer by ID")
    fun findById(
        @PathVariable @Parameter(description = "Customer UUID") id: UUID
    ): ApiResponse<Customer> {
        val customer = customerService.findById(id)
        SecurityUtils.requireOwnEntity(customer.entityId)
        return ApiResponse.success(customer)
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update a customer",
        description = "Updates mutable fields: creditLimit, paymentTerms, email, phone. Only non-null fields in the request body are applied."
    )
    fun update(
        @PathVariable @Parameter(description = "Customer UUID") id: UUID,
        @Valid @RequestBody command: UpdateCustomerCommand
    ): ApiResponse<Customer> {
        SecurityUtils.requireOwnEntity(customerService.findById(id).entityId)
        return ApiResponse.success(customerService.update(id, command))
    }

    @PostMapping("/{id}/deactivate")
    @Operation(
        summary = "Deactivate a customer (soft delete)",
        description = "Sets isActive=false. All historical records are preserved."
    )
    fun deactivate(
        @PathVariable @Parameter(description = "Customer UUID") id: UUID,
        @Valid @RequestBody command: DeactivateCustomerCommand
    ): ApiResponse<Customer> {
        SecurityUtils.requireOwnEntity(customerService.findById(id).entityId)
        return ApiResponse.success(customerService.deactivate(id, command.reason, command.deactivatedBy))
    }
}

data class DeactivateCustomerCommand(
    @field:NotBlank(message = "Deactivation reason is required")
    val reason: String,
    @field:NotNull(message = "deactivatedBy user ID is required")
    val deactivatedBy: UUID
)

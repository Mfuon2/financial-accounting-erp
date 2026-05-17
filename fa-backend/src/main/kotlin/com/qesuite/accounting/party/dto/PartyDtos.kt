package com.qesuite.accounting.party.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.util.UUID

// ========== CUSTOMER DTOs ==========

/**
 * §14.3 — Create Customer Command
 */
data class CreateCustomerCommand(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "Entity ID is required")
    val entityId: UUID,

    @Schema(example = "2024-01-01T00:00:00Z", description = "Period ID for period-scoped tracking")
    val periodId: UUID? = null,

    @Schema(example = "CU0001", description = "Unique customer code. Leave blank to auto-generate.")
    val customerCode: String = "",

    @Schema(example = "Acme Corporation")
    @NotBlank(message = "Customer name is required")
    val name: String,

    @Schema(example = "A001234567A", description = "Tax ID / KRA PIN")
    val taxNumber: String? = null,

    @Schema(example = "accounts@acme.com")
    @Email(message = "Email must be valid")
    val email: String? = null,

    @Schema(example = "+254712345678")
    val phone: String? = null,

    @Schema(example = "50000.000000")
    @Positive(message = "Credit limit must be positive")
    val creditLimit: BigDecimal? = null,

    @Schema(example = "NET_30")
    val paymentTerms: String? = null,

    @Schema(description = "Default AR account ID")
    val defaultArAccountId: UUID? = null
)

/**
 * §14.3 — Update Customer Command
 */
data class UpdateCustomerCommand(
    @Schema(example = "75000.000000")
    @Positive(message = "Credit limit must be positive")
    val creditLimit: BigDecimal? = null,

    @Schema(example = "accounts-new@acme.com")
    @Email(message = "Email must be valid")
    val email: String? = null,

    @Schema(example = "+254787654321")
    val phone: String? = null,

    @Schema(example = "NET_60")
    val paymentTerms: String? = null
)

/**
 * §14.3 — Customer Response
 */
data class CustomerResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440001")
    val id: UUID,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val entityId: UUID,

    @Schema(example = "CUST-00042")
    val customerCode: String,

    @Schema(example = "Acme Corporation")
    val name: String,

    @Schema(example = "A001234567A")
    val taxNumber: String? = null,

    @Schema(example = "accounts@acme.com")
    val email: String? = null,

    @Schema(example = "+254712345678")
    val phone: String? = null,

    @Schema(example = "50000.000000")
    val creditLimit: BigDecimal = BigDecimal.ZERO,

    @Schema(example = "NET_30")
    val paymentTerms: String? = null,

    @Schema(example = "true")
    val isActive: Boolean = true
)

// ========== SUPPLIER DTOs ==========

/**
 * §14.3 — Create Supplier Command
 */
data class CreateSupplierCommand(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    @NotNull(message = "Entity ID is required")
    val entityId: UUID,

    @Schema(example = "2024-01-01T00:00:00Z")
    val periodId: UUID? = null,

    @Schema(example = "SUPP0001", description = "Unique supplier code. Leave blank to auto-generate.")
    val supplierCode: String = "",

    @Schema(example = "Widget Manufacturing Ltd")
    @NotBlank(message = "Supplier name is required")
    val name: String,

    @Schema(example = "P001234567B")
    val taxNumber: String? = null,

    @Schema(example = "orders@widgets.com")
    @Email(message = "Email must be valid")
    val email: String? = null,

    @Schema(example = "+254712345678")
    val phone: String? = null,

    @Schema(example = "NET_30")
    val paymentTerms: String? = null,

    @Schema(description = "Default AP account ID")
    val defaultApAccountId: UUID? = null
)

/**
 * §14.3 — Update Supplier Command
 */
data class UpdateSupplierCommand(
    @Schema(example = "orders-new@widgets.com")
    @Email(message = "Email must be valid")
    val email: String? = null,

    @Schema(example = "+254787654321")
    val phone: String? = null,

    @Schema(example = "NET_60")
    val paymentTerms: String? = null
)

/**
 * §14.3 — Supplier Response
 */
data class SupplierResponse(
    @Schema(example = "550e8400-e29b-41d4-a716-446655440005")
    val id: UUID,

    @Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val entityId: UUID,

    @Schema(example = "SUPP-00001")
    val supplierCode: String,

    @Schema(example = "Widget Manufacturing Ltd")
    val name: String,

    @Schema(example = "P001234567B")
    val taxNumber: String? = null,

    @Schema(example = "orders@widgets.com")
    val email: String? = null,

    @Schema(example = "+254712345678")
    val phone: String? = null,

    @Schema(example = "NET_30")
    val paymentTerms: String? = null,

    @Schema(example = "true")
    val isActive: Boolean = true
)

// §7.3 — `PagedResponse` and `Page.toPagedResponse` are now in
// `com.qesuite.accounting.shared.dto`. Import from there.

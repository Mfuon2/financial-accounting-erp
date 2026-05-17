package com.qesuite.accounting.tax.controller

import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.tax.domain.TaxCode
import com.qesuite.accounting.tax.domain.TaxRate
import com.qesuite.accounting.tax.service.CreateTaxCodeCommand
import com.qesuite.accounting.tax.service.CreateTaxRateCommand
import com.qesuite.accounting.tax.service.TaxService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1/tax")
@Tag(name = "Module 13: Tax", description = "Tax code and rate management plus real-time tax calculation (§13)")
class TaxController(
    private val taxService: TaxService
) {

    // -----------------------------------------------------------------------
    // Tax Codes
    // -----------------------------------------------------------------------

    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a tax code",
        description = "Registers a new tax classification code for the entity (e.g., VAT_16, WHT_5). " +
                "Code must be unique within the entity."
    )
    fun createTaxCode(@Valid @RequestBody request: CreateTaxCodeRequest): ApiResponse<TaxCode> =
        ApiResponse.success(
            taxService.createTaxCode(
                CreateTaxCodeCommand(
                    entityId = request.entityId,
                    code = request.code,
                    description = request.description,
                    isRecoverable = request.isRecoverable
                )
            )
        )

    @GetMapping("/codes")
    @Operation(
        summary = "List tax codes",
        description = "Returns all tax codes defined for the given entity."
    )
    fun listTaxCodes(@RequestParam entityId: UUID): ApiResponse<List<TaxCode>> =
        ApiResponse.success(taxService.listTaxCodes(entityId))

    @GetMapping("/codes/{id}")
    @Operation(
        summary = "Get a tax code by ID",
        description = "Returns the detail of a single tax code by its primary key."
    )
    fun getTaxCode(@PathVariable id: UUID): ApiResponse<TaxCode> =
        ApiResponse.success(taxService.getTaxCodeById(id))

    // -----------------------------------------------------------------------
    // Tax Rates
    // -----------------------------------------------------------------------

    @PostMapping("/rates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a tax rate",
        description = "Registers an effective rate for a tax code. Rate must be between 0 and 1 " +
                "(e.g., 0.1600 for 16%). Multiple rates with different effective dates are supported."
    )
    fun createTaxRate(@Valid @RequestBody request: CreateTaxRateRequest): ApiResponse<TaxRate> =
        ApiResponse.success(
            taxService.createTaxRate(
                CreateTaxRateCommand(
                    entityId = request.entityId,
                    taxCodeId = request.taxCodeId,
                    rate = request.rate,
                    effectiveFrom = request.effectiveFrom
                )
            )
        )

    @GetMapping("/rates")
    @Operation(
        summary = "List rates for a tax code",
        description = "Returns all tax rates associated with the given tax code, ordered by effective date."
    )
    fun listRates(@RequestParam taxCodeId: UUID): ApiResponse<List<TaxRate>> =
        ApiResponse.success(taxService.listRates(taxCodeId))

    // -----------------------------------------------------------------------
    // Tax Calculation
    // -----------------------------------------------------------------------

    @PostMapping("/calculate")
    @Operation(
        summary = "Calculate tax",
        description = "Computes the tax amount for a given base amount using the effective rate of the " +
                "specified tax code on the given date. Returns the tax amount (not inclusive of base)."
    )
    fun calculateTax(@Valid @RequestBody request: CalculateTaxRequest): ApiResponse<TaxCalculationResult> {
        val taxAmount = taxService.calculateTax(
            entityId = request.entityId,
            taxCodeStr = request.taxCode,
            baseAmount = request.baseAmount,
            date = request.date
        )
        return ApiResponse.success(
            TaxCalculationResult(
                entityId = request.entityId,
                taxCode = request.taxCode,
                baseAmount = request.baseAmount,
                taxAmount = taxAmount,
                totalAmount = request.baseAmount.add(taxAmount),
                date = request.date
            )
        )
    }

    @PutMapping("/codes/{id}")
    @Operation(
        summary = "Update a tax code",
        description = "Updates the description and recoverability of an existing tax code."
    )
    fun updateTaxCode(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTaxCodeRequest
    ): ApiResponse<TaxCode> {
        val command = com.qesuite.accounting.tax.service.UpdateTaxCodeCommand(
            description = request.description,
            isRecoverable = request.isRecoverable
        )
        return ApiResponse.success(taxService.updateTaxCode(id, command))
    }
}

// ---------------------------------------------------------------------------
// Request / Response DTOs
// ---------------------------------------------------------------------------

data class CreateTaxCodeRequest(
    @field:NotNull val entityId: UUID,
    @field:NotNull val code: String,
    val description: String? = null,
    val isRecoverable: Boolean = true
)

data class CreateTaxRateRequest(
    @field:NotNull val entityId: UUID,
    @field:NotNull val taxCodeId: UUID,
    @field:NotNull @field:Positive val rate: BigDecimal,
    @field:NotNull val effectiveFrom: LocalDate
)

data class CalculateTaxRequest(
    @field:NotNull val entityId: UUID,
    @field:NotNull val taxCode: String,
    @field:NotNull @field:Positive val baseAmount: BigDecimal,
    @field:NotNull val date: LocalDate
)

data class TaxCalculationResult(
    val entityId: UUID,
    val taxCode: String,
    val baseAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val date: LocalDate
)

data class UpdateTaxCodeRequest(
    val description: String? = null,
    val isRecoverable: Boolean = true
)

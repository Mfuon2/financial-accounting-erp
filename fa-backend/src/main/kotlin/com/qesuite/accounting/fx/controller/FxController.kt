package com.qesuite.accounting.fx.controller

import com.qesuite.accounting.fx.domain.Currency
import com.qesuite.accounting.fx.domain.ExchangeRate
import com.qesuite.accounting.fx.domain.RateType
import com.qesuite.accounting.fx.repository.CurrencyRepository
import com.qesuite.accounting.fx.repository.ExchangeRateRepository
import com.qesuite.accounting.fx.service.ExchangeRateService
import com.qesuite.accounting.fx.service.FXRevaluationService
import com.qesuite.accounting.fx.service.RevaluationPreviewResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/fx")
@Tag(
    name = "Module 13: Foreign Exchange",
    description = """
Foreign currency management: currency registration, exchange rate maintenance, and
IAS 21 monetary-item revaluation.

**Functional Currency:** Each entity must have exactly one currency marked as functional
(`isFunctional = true`). This is used as the reporting currency for all journal entries
and financial statements.

**Exchange Rates:** Spot, Closing, and Average rates are maintained per entity,
currency pair, and date. Missing rates fall back to 1:1 (development only — production
must configure external rate feeds).

**Revaluation:** The revaluation job re-translates all foreign-currency monetary-item
account balances to the closing rate as of the revaluation date, posting a gain/loss
journal entry for each account with a material difference.
"""
)
class FxController(
    private val currencyRepository: CurrencyRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val exchangeRateService: ExchangeRateService,
    private val fxRevaluationService: FXRevaluationService
) {

    // ─── Currencies ───────────────────────────────────────────────────────────

    @PostMapping("/currencies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Register a currency for an entity",
        description = "Creates a currency record. At most one currency per entity may have isFunctional=true."
    )
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
    fun createCurrency(
        @Valid @RequestBody command: CreateCurrencyCommand
    ): ApiResponse<Currency> {
        SecurityUtils.requireOwnEntity(command.entityId)
        // Validate: if marking as functional, ensure no other functional currency exists.
        if (command.isFunctional) {
            val existing = currencyRepository.findByEntityIdAndIsFunctionalTrue(command.entityId)
            if (existing.isPresent) {
                throw ValidationException(
                    errorCode = "FUNCTIONAL_CURRENCY_ALREADY_SET",
                    message = "Entity ${command.entityId} already has a functional currency: ${existing.get().currencyCode}.",
                    context = mapOf(
                        "entity_id" to command.entityId,
                        "existing_functional" to existing.get().currencyCode
                    )
                )
            }
        }
        // Check for duplicate currency code within entity.
        val duplicate = currencyRepository.findByEntityIdAndCurrencyCode(command.entityId, command.currencyCode)
        if (duplicate.isPresent) {
            throw ValidationException(
                errorCode = "DUPLICATE_CURRENCY",
                message = "Currency ${command.currencyCode} is already registered for entity ${command.entityId}.",
                context = mapOf("entity_id" to command.entityId, "currency_code" to command.currencyCode)
            )
        }
        val currency = Currency(
            entityId = command.entityId,
            currencyCode = command.currencyCode,
            currencyName = command.currencyName,
            isFunctional = command.isFunctional,
            symbol = command.symbol,
            decimals = command.decimals
        )
        return ApiResponse.success(currencyRepository.save(currency))
    }

    @GetMapping("/currencies")
    @Operation(
        summary = "List currencies registered for an entity",
        description = "Returns all currencies (functional and non-functional) registered for the entity."
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun listCurrencies(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID
    ): ApiResponse<List<Currency>> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(currencyRepository.findAllByEntityId(entityId))
    }

    @PutMapping("/currencies/{id}")
    @Operation(summary = "Update currency metadata")
    @PreAuthorize(RoleSets.ADMIN_CONFIG)
    fun updateCurrency(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCurrencyRequest
    ): ApiResponse<Currency> {
        val currency = currencyRepository.findById(id).orElseThrow {
            ResourceNotFoundException("CURRENCY_NOT_FOUND", id, "Currency")
        }
        SecurityUtils.requireOwnEntity(currency.entityId)
        currency.currencyName = request.currencyName
        request.symbol?.let { currency.symbol = it }
        request.decimals?.let { currency.decimals = it }
        return ApiResponse.success(currencyRepository.save(currency))
    }

    // ─── Exchange Rates ────────────────────────────────────────────────────────

    @PostMapping("/exchange-rates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create or update an exchange rate",
        description = "Persists a SPOT, CLOSING, or AVERAGE exchange rate for a currency pair on a specific date."
    )
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun createExchangeRate(
        @Valid @RequestBody command: CreateExchangeRateCommand
    ): ApiResponse<ExchangeRate> {
        SecurityUtils.requireOwnEntity(command.entityId)
        val rate = ExchangeRate(
            entityId = command.entityId,
            fromCurrency = command.fromCurrency,
            toCurrency = command.toCurrency,
            rateDate = command.rateDate,
            rateValue = command.rateValue,
            rateType = command.rateType
        )
        return ApiResponse.success(exchangeRateService.saveRate(rate))
    }

    @GetMapping("/exchange-rates/all")
    @Operation(
        summary = "List all exchange rates for an entity",
        description = "Returns all exchange rate records for the entity, ordered by date descending then from-currency ascending."
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun listExchangeRates(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID
    ): ApiResponse<List<ExchangeRate>> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(exchangeRateRepository.findAllByEntityIdOrderByRateDateDescFromCurrencyAsc(entityId))
    }

    @GetMapping("/exchange-rates")
    @Operation(
        summary = "Look up a specific exchange rate",
        description = "Returns the exchange rate for the given currency pair, date, and type. Defaults to SPOT if type is omitted."
    )
    @PreAuthorize(RoleSets.BROAD_READ)
    fun getRate(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID,
        @RequestParam @Parameter(description = "Source currency (ISO 4217)", required = true) fromCurrency: String,
        @RequestParam @Parameter(description = "Target currency (ISO 4217)", required = true) toCurrency: String,
        @RequestParam
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        @Parameter(description = "Rate date (ISO 8601)", required = true) date: LocalDate,
        @RequestParam(required = false, defaultValue = "SPOT")
        @Parameter(description = "Rate type: SPOT, CLOSING, or AVERAGE") rateType: RateType
    ): ApiResponse<BigDecimal> {
        SecurityUtils.requireOwnEntity(entityId)
        val rate = exchangeRateService.getRate(entityId, fromCurrency, toCurrency, date, rateType)
        return ApiResponse.success(rate)
    }

    @PutMapping("/exchange-rates/{id}")
    @Operation(summary = "Update exchange rate value")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun updateExchangeRate(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateExchangeRateRequest
    ): ApiResponse<ExchangeRate> {
        val rate = exchangeRateRepository.findById(id).orElseThrow {
            ResourceNotFoundException("EXCHANGE_RATE_NOT_FOUND", id, "ExchangeRate")
        }
        SecurityUtils.requireOwnEntity(rate.entityId)
        rate.rateValue = request.rateValue
        return ApiResponse.success(exchangeRateService.saveRate(rate))
    }

    // ─── Revaluation ──────────────────────────────────────────────────────────

    @GetMapping("/revaluation/preview")
    @Operation(
        summary = "Preview FX revaluation",
        description = "Returns indicative revaluation data per monetary-item account — balances, prior rate, closing rate, and P&L delta — without posting any journal entries."
    )
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun previewRevaluation(
        @RequestParam entityId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate
    ): ApiResponse<RevaluationPreviewResponse> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(fxRevaluationService.previewRevaluation(entityId, date))
    }

    @PostMapping("/revaluation")
    @Operation(
        summary = "Run IAS 21 FX revaluation",
        description = """
Re-translates all foreign-currency monetary-item account balances to the closing rate
as of the given date. For each account with a material difference (> 0.000001), a
journal entry is posted debiting/crediting the account and offsetting the provided
gainLossAccountId.

Requires CLOSING rates to be loaded for all foreign currencies before calling this
endpoint.
"""
    )
    @PreAuthorize(RoleSets.APPROVER)
    fun runRevaluation(
        @Valid @RequestBody command: RunRevaluationCommand
    ): ApiResponse<Unit> {
        SecurityUtils.requireOwnEntity(command.entityId)
        fxRevaluationService.runRevaluation(
            entityId = command.entityId,
            periodId = command.periodId,
            date = command.date,
            gainLossAccountId = command.gainLossAccountId
        )
        return ApiResponse.success(Unit)
    }
}

// ─── Request DTOs ──────────────────────────────────────────────────────────────

data class CreateCurrencyCommand(
    @field:NotNull(message = "Entity ID is required")
    val entityId: UUID,

    @field:NotBlank(message = "Currency code is required")
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must be a 3-letter ISO 4217 code")
    val currencyCode: String,

    @field:NotBlank(message = "Currency name is required")
    val currencyName: String,

    val isFunctional: Boolean = false,

    val symbol: String? = null,

    val decimals: Int = 2
)

data class CreateExchangeRateCommand(
    @field:NotNull(message = "Entity ID is required")
    val entityId: UUID,

    @field:NotBlank(message = "From-currency code is required")
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "From-currency code must be a 3-letter ISO 4217 code")
    val fromCurrency: String,

    @field:NotBlank(message = "To-currency code is required")
    @field:Pattern(regexp = "^[A-Z]{3}$", message = "To-currency code must be a 3-letter ISO 4217 code")
    val toCurrency: String,

    @field:NotNull(message = "Rate date is required")
    val rateDate: LocalDate,

    @field:NotNull(message = "Rate value is required")
    @field:Positive(message = "Rate value must be positive")
    val rateValue: BigDecimal,

    @field:NotNull(message = "Rate type is required")
    val rateType: RateType
)

data class RunRevaluationCommand(
    @field:NotNull(message = "Entity ID is required")
    val entityId: UUID,

    @field:NotNull(message = "Period ID is required")
    val periodId: UUID,

    @field:NotNull(message = "Revaluation date is required")
    val date: LocalDate,

    @field:NotNull(message = "Gain/loss account ID is required")
    val gainLossAccountId: UUID
)

data class UpdateCurrencyRequest(
    @field:NotBlank(message = "Currency name is required")
    val currencyName: String,

    val symbol: String? = null,

    val decimals: Int? = null
)

data class UpdateExchangeRateRequest(
    @field:NotNull(message = "Rate value is required")
    @field:Positive(message = "Rate value must be positive")
    val rateValue: BigDecimal
)

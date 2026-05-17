package com.qesuite.accounting.tax.service

import com.qesuite.accounting.tax.domain.TaxCode
import com.qesuite.accounting.tax.domain.TaxRate
import com.qesuite.accounting.tax.repository.TaxCodeRepository
import com.qesuite.accounting.tax.repository.TaxRateRepository
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.*

@Service
@Transactional
class TaxService(
    private val taxCodeRepository: TaxCodeRepository,
    private val taxRateRepository: TaxRateRepository
) {

    /**
     * §13.1 — Tax Calculation Engine
     * Calculates the tax amount for a given base amount and tax code string.
     */
    @Transactional(readOnly = true)
    fun calculateTax(entityId: UUID, taxCodeStr: String, baseAmount: BigDecimal, date: LocalDate): BigDecimal {
        val taxCode = taxCodeRepository.findByEntityIdAndCode(entityId, taxCodeStr)
            .orElseThrow { ResourceNotFoundException("TAX_CODE_NOT_FOUND", taxCodeStr, "Tax Code") }

        val rate = taxRateRepository.findEffectiveRate(taxCode.id!!, date)
            ?: throw ResourceNotFoundException("TAX_RATE_NOT_FOUND", taxCodeStr, "Tax Rate for $date")

        return baseAmount.multiply(rate.rate).setScale(6, RoundingMode.HALF_EVEN)
    }

    /**
     * Returns the tax code entity for validation by code string.
     */
    @Transactional(readOnly = true)
    fun getTaxCode(entityId: UUID, code: String): TaxCode {
        return taxCodeRepository.findByEntityIdAndCode(entityId, code)
            .orElseThrow { ResourceNotFoundException("TAX_CODE_NOT_FOUND", code, "Tax Code") }
    }

    /**
     * Returns the tax code entity by primary key.
     */
    @Transactional(readOnly = true)
    fun getTaxCodeById(id: UUID): TaxCode =
        taxCodeRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("TAX_CODE_NOT_FOUND", id, "TaxCode") }

    /**
     * §13.1, §14.1 — Calculate tax by tax rate primary key.
     *
     * Used by the invoicing module where invoice lines reference a `tax_rate_id` UUID
     * directly rather than the tax-code string. Returns `base × rate.rate`.
     * Throws `TAX_RATE_NOT_FOUND` (HTTP 404) if the rate is absent or inactive.
     */
    @Transactional(readOnly = true)
    fun calculateTaxByRateId(taxRateId: UUID, baseAmount: BigDecimal): BigDecimal {
        val rate: TaxRate = taxRateRepository.findById(taxRateId)
            .orElseThrow { ResourceNotFoundException("TAX_RATE_NOT_FOUND", taxRateId, "Tax Rate") }
        return baseAmount.multiply(rate.rate).setScale(6, RoundingMode.HALF_EVEN)
    }

    /**
     * §13.1, §14.1 — Resolve and apply tax for a single invoice line.
     *
     * Accepts either a direct [taxRateId] or a [taxCodeId] + [invoiceDate] pair:
     * - [taxRateId]: used as-is; entity ownership is validated against [entityId].
     * - [taxCodeId]: the effective rate on [invoiceDate] is looked up automatically.
     *   Throws [ValidationException] with TAX_RATE_NOT_FOUND if no rate covers that date.
     *
     * Returns a pair of (taxAmount, resolvedTaxRateId) so the caller can persist the
     * resolved rate UUID on the invoice line for audit purposes.
     */
    @Transactional(readOnly = true)
    fun resolveLineTax(
        entityId: UUID,
        taxRateId: UUID?,
        taxCodeId: UUID?,
        baseAmount: BigDecimal,
        invoiceDate: LocalDate
    ): Pair<BigDecimal, UUID?> {
        if (taxRateId == null && taxCodeId == null) {
            return Pair(BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN), null)
        }

        val rate: TaxRate = when {
            taxRateId != null -> {
                val r = taxRateRepository.findById(taxRateId)
                    .orElseThrow { ResourceNotFoundException("TAX_RATE_NOT_FOUND", taxRateId, "Tax Rate") }
                if (r.entityId != entityId) throw ValidationException(
                    errorCode = "TAX_RATE_ENTITY_MISMATCH",
                    message = "Tax rate $taxRateId does not belong to entity $entityId."
                )
                r
            }
            else -> {
                // taxCodeId is non-null here; find the rate effective on the invoice date.
                val taxCode = taxCodeRepository.findById(taxCodeId!!)
                    .orElseThrow { ResourceNotFoundException("TAX_CODE_NOT_FOUND", taxCodeId, "TaxCode") }
                if (taxCode.entityId != entityId) throw ValidationException(
                    errorCode = "TAX_CODE_ENTITY_MISMATCH",
                    message = "Tax code $taxCodeId does not belong to entity $entityId."
                )
                taxRateRepository.findEffectiveRate(taxCode.id!!, invoiceDate)
                    ?: throw ValidationException(
                        errorCode = "TAX_RATE_NOT_FOUND",
                        message = "No effective tax rate for code '${taxCode.code}' on $invoiceDate. " +
                                "Create a tax rate with effectiveFrom <= $invoiceDate."
                    )
            }
        }

        val taxAmount = baseAmount.multiply(rate.rate).setScale(6, RoundingMode.HALF_EVEN)
        return Pair(taxAmount, rate.id)
    }

    /**
     * §13.2 — Create a new tax code for the given entity.
     * Validates code uniqueness within the entity scope.
     */
    fun createTaxCode(command: CreateTaxCodeCommand): TaxCode {
        if (taxCodeRepository.existsByEntityIdAndCode(command.entityId, command.code)) {
            throw ConflictException(
                errorCode = "TAX_CODE_DUPLICATE",
                message = "Tax code '${command.code}' already exists for entity ${command.entityId}.",
                context = mapOf("entityId" to command.entityId.toString(), "code" to command.code)
            )
        }

        val taxCode = TaxCode(
            entityId = command.entityId,
            code = command.code,
            description = command.description,
            isRecoverable = command.isRecoverable
        )

        return taxCodeRepository.save(taxCode)
    }

    /**
     * §13.2 — Create a new tax rate effective from a given date.
     * Rate must be between 0 and 1 inclusive (e.g., 0.1600 for 16%).
     */
    fun createTaxRate(command: CreateTaxRateCommand): TaxRate {
        val taxCode = taxCodeRepository.findById(command.taxCodeId)
            .orElseThrow { ResourceNotFoundException("TAX_CODE_NOT_FOUND", command.taxCodeId, "TaxCode") }

        if (command.rate < BigDecimal.ZERO || command.rate > BigDecimal.ONE) {
            throw ValidationException(
                errorCode = "INVALID_TAX_RATE",
                message = "Tax rate must be between 0 and 1 (e.g., 0.1600 for 16%). Provided: ${command.rate}"
            )
        }

        val taxRate = TaxRate(
            entityId = command.entityId,
            taxCode = taxCode,
            rate = command.rate,
            effectiveFrom = command.effectiveFrom
        )

        return taxRateRepository.save(taxRate)
    }

    /**
     * §13.2 — List all tax codes for a given entity.
     */
    @Transactional(readOnly = true)
    fun listTaxCodes(entityId: UUID): List<TaxCode> =
        taxCodeRepository.findByEntityId(entityId)

    /**
     * §13.2 — List all tax rates for a given tax code.
     */
    @Transactional(readOnly = true)
    fun listRates(taxCodeId: UUID): List<TaxRate> =
        taxRateRepository.findByTaxCodeId(taxCodeId)

    /**
     * §13.2 — Update an existing tax code's metadata.
     */
    fun updateTaxCode(id: UUID, command: UpdateTaxCodeCommand): TaxCode {
        val taxCode = getTaxCodeById(id)

        // Only allow updating description and recoverability
        // Code string is typically a business key and shouldn't change
        taxCode.description = command.description
        taxCode.isRecoverable = command.isRecoverable

        return taxCodeRepository.save(taxCode)
    }
}

data class CreateTaxCodeCommand(
    val entityId: UUID,
    val code: String,
    val description: String?,
    val isRecoverable: Boolean = true
)

data class CreateTaxRateCommand(
    val entityId: UUID,
    val taxCodeId: UUID,
    val rate: BigDecimal,  // e.g., 0.1600 for 16%
    val effectiveFrom: LocalDate
)

data class UpdateTaxCodeCommand(
    val description: String?,
    val isRecoverable: Boolean
)

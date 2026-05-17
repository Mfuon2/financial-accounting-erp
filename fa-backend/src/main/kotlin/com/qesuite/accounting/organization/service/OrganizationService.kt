package com.qesuite.accounting.organization.service

import com.qesuite.accounting.organization.domain.Organization
import com.qesuite.accounting.organization.domain.OrganizationStatus
import com.qesuite.accounting.organization.repository.OrganizationRepository
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional
class OrganizationService(
    private val organizationRepository: OrganizationRepository
) {

    private val log = LoggerFactory.getLogger(OrganizationService::class.java)

    private val CURRENCY_CODE_REGEX = Regex("^[A-Z]{3}$")

    // ─────────────────────────────────────────────────────────────────────────
    // Create
    // ─────────────────────────────────────────────────────────────────────────

    fun createOrganization(command: CreateOrganizationCommand): Organization {
        // Name uniqueness
        if (organizationRepository.existsByName(command.name)) {
            throw ConflictException(
                errorCode = "DUPLICATE_ORGANIZATION_NAME",
                message = "An organization with name '${command.name}' already exists.",
                context = mapOf("name" to command.name)
            )
        }

        // Registration number uniqueness (only when provided)
        if (command.registrationNumber != null &&
            organizationRepository.existsByRegistrationNumber(command.registrationNumber)
        ) {
            throw ConflictException(
                errorCode = "DUPLICATE_REGISTRATION_NUMBER",
                message = "An organization with registration number '${command.registrationNumber}' already exists.",
                context = mapOf("registration_number" to command.registrationNumber)
            )
        }

        // Functional currency format
        if (!CURRENCY_CODE_REGEX.matches(command.functionalCurrency)) {
            throw ValidationException(
                errorCode = "INVALID_CURRENCY_CODE",
                message = "Functional currency '${command.functionalCurrency}' is not a valid 3-letter ISO 4217 currency code.",
                context = mapOf("functional_currency" to command.functionalCurrency)
            )
        }

        // Reporting currency format
        if (!CURRENCY_CODE_REGEX.matches(command.reportingCurrency)) {
            throw ValidationException(
                errorCode = "INVALID_CURRENCY_CODE",
                message = "Reporting currency '${command.reportingCurrency}' is not a valid 3-letter ISO 4217 currency code.",
                context = mapOf("reporting_currency" to command.reportingCurrency)
            )
        }

        // Fiscal year start month
        if (command.fiscalYearStartMonth !in 1..12) {
            throw ValidationException(
                errorCode = "INVALID_FISCAL_YEAR_START_MONTH",
                message = "fiscalYearStartMonth must be between 1 and 12, got ${command.fiscalYearStartMonth}.",
                context = mapOf("fiscal_year_start_month" to command.fiscalYearStartMonth)
            )
        }

        // Timezone validation
        validateTimezone(command.timezone)

        val organization = Organization(
            name = command.name,
            legalName = command.legalName,
            registrationNumber = command.registrationNumber,
            taxIdentificationNumber = command.taxIdentificationNumber,
            functionalCurrency = command.functionalCurrency,
            reportingCurrency = command.reportingCurrency,
            countryCode = command.countryCode,
            timezone = command.timezone,
            fiscalYearStartMonth = command.fiscalYearStartMonth,
            addressLine1 = command.addressLine1,
            addressLine2 = command.addressLine2,
            city = command.city,
            postalCode = command.postalCode,
            phone = command.phone,
            email = command.email,
            website = command.website,
            createdBy = command.createdBy,
            modifiedBy = command.createdBy
        )

        val saved = organizationRepository.save(organization)
        log.info("Organization created: id={} name={} by={}", saved.id, saved.name, command.createdBy)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Update
    // ─────────────────────────────────────────────────────────────────────────

    fun updateOrganization(id: UUID, command: UpdateOrganizationCommand, updatedBy: UUID): Organization {
        val organization = findById(id)

        command.name?.let { organization.name = it }
        command.legalName?.let { organization.legalName = it }
        command.taxIdentificationNumber?.let { organization.taxIdentificationNumber = it }
        command.functionalCurrency?.let {
            if (!CURRENCY_CODE_REGEX.matches(it)) {
                throw ValidationException(
                    errorCode = "INVALID_CURRENCY_CODE",
                    message = "Functional currency '$it' is not a valid 3-letter ISO 4217 currency code.",
                    context = mapOf("functional_currency" to it)
                )
            }
            organization.functionalCurrency = it
        }
        command.reportingCurrency?.let {
            if (!CURRENCY_CODE_REGEX.matches(it)) {
                throw ValidationException(
                    errorCode = "INVALID_CURRENCY_CODE",
                    message = "Reporting currency '$it' is not a valid 3-letter ISO 4217 currency code.",
                    context = mapOf("reporting_currency" to it)
                )
            }
            organization.reportingCurrency = it
        }
        command.countryCode?.let { organization.countryCode = it }
        command.timezone?.let {
            validateTimezone(it)
            organization.timezone = it
        }
        command.fiscalYearStartMonth?.let {
            if (it !in 1..12) {
                throw ValidationException(
                    errorCode = "INVALID_FISCAL_YEAR_START_MONTH",
                    message = "fiscalYearStartMonth must be between 1 and 12, got $it.",
                    context = mapOf("fiscal_year_start_month" to it)
                )
            }
            organization.fiscalYearStartMonth = it
        }
        command.addressLine1?.let { organization.addressLine1 = it }
        command.addressLine2?.let { organization.addressLine2 = it }
        command.city?.let { organization.city = it }
        command.postalCode?.let { organization.postalCode = it }
        command.phone?.let { organization.phone = it }
        command.email?.let { organization.email = it }
        command.website?.let { organization.website = it }
        command.logoUrl?.let { organization.logoUrl = it }

        organization.modifiedAt = Instant.now()
        organization.modifiedBy = updatedBy

        val saved = organizationRepository.save(organization)
        log.info("Organization updated: id={} by={}", saved.id, updatedBy)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status transitions
    // ─────────────────────────────────────────────────────────────────────────

    fun suspendOrganization(id: UUID, reason: String, suspendedBy: UUID): Organization {
        val organization = findById(id)

        if (organization.status != OrganizationStatus.ACTIVE) {
            throw ValidationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Only ACTIVE organizations can be suspended. Current status: ${organization.status}.",
                context = mapOf("current_status" to organization.status.name, "organization_id" to id.toString())
            )
        }

        organization.status = OrganizationStatus.SUSPENDED
        organization.modifiedAt = Instant.now()
        organization.modifiedBy = suspendedBy

        val saved = organizationRepository.save(organization)
        log.info("Organization suspended: id={} by={} reason={}", saved.id, suspendedBy, reason)
        return saved
    }

    fun activateOrganization(id: UUID, activatedBy: UUID): Organization {
        val organization = findById(id)

        if (organization.status != OrganizationStatus.SUSPENDED) {
            throw ValidationException(
                errorCode = "INVALID_STATUS_TRANSITION",
                message = "Only SUSPENDED organizations can be activated. Current status: ${organization.status}.",
                context = mapOf("current_status" to organization.status.name, "organization_id" to id.toString())
            )
        }

        organization.status = OrganizationStatus.ACTIVE
        organization.modifiedAt = Instant.now()
        organization.modifiedBy = activatedBy

        val saved = organizationRepository.save(organization)
        log.info("Organization activated: id={} by={}", saved.id, activatedBy)
        return saved
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun findById(id: UUID): Organization =
        organizationRepository.findById(id).orElseThrow {
            ResourceNotFoundException(
                errorCode = "ORGANIZATION_NOT_FOUND",
                resourceId = id,
                resourceType = "Organization"
            )
        }

    @Transactional(readOnly = true)
    fun findAll(pageable: Pageable): Page<Organization> =
        organizationRepository.findAll(pageable)

    @Transactional(readOnly = true)
    fun findByStatus(status: OrganizationStatus, pageable: Pageable): Page<Organization> =
        organizationRepository.findByStatus(status, pageable)

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun validateTimezone(timezone: String) {
        try {
            ZoneId.of(timezone)
        } catch (e: Exception) {
            throw ValidationException(
                errorCode = "INVALID_TIMEZONE",
                message = "Timezone '$timezone' is not a valid IANA timezone identifier.",
                context = mapOf("timezone" to timezone)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Command objects
// ─────────────────────────────────────────────────────────────────────────────

data class CreateOrganizationCommand(
    val name: String,
    val legalName: String? = null,
    val registrationNumber: String? = null,
    val taxIdentificationNumber: String? = null,
    val functionalCurrency: String = "USD",
    val reportingCurrency: String = "USD",
    val countryCode: String = "KE",
    val timezone: String = "Africa/Nairobi",
    val fiscalYearStartMonth: Int = 1,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val createdBy: UUID
)

data class UpdateOrganizationCommand(
    val name: String? = null,
    val legalName: String? = null,
    val taxIdentificationNumber: String? = null,
    val functionalCurrency: String? = null,
    val reportingCurrency: String? = null,
    val countryCode: String? = null,
    val timezone: String? = null,
    val fiscalYearStartMonth: Int? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val logoUrl: String? = null
)

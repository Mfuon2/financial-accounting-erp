package com.qesuite.accounting.organization.domain

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "organizations")
class Organization(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, length = 255) var name: String,
    @Column(name = "legal_name", length = 255) var legalName: String? = null,
    @Column(name = "registration_number", length = 100) var registrationNumber: String? = null,
    @Column(name = "tax_identification_number", length = 100) var taxIdentificationNumber: String? = null,
    @Column(name = "functional_currency", nullable = false, length = 3) var functionalCurrency: String = "USD",
    @Column(name = "reporting_currency", nullable = false, length = 3) var reportingCurrency: String = "USD",
    @Column(name = "country_code", nullable = false, length = 2) var countryCode: String = "KE",
    @Column(nullable = false, length = 100) var timezone: String = "Africa/Nairobi",
    @Column(name = "fiscal_year_start_month", nullable = false) var fiscalYearStartMonth: Int = 1,
    @Column(name = "address_line1", length = 255) var addressLine1: String? = null,
    @Column(name = "address_line2", length = 255) var addressLine2: String? = null,
    @Column(length = 100) var city: String? = null,
    @Column(name = "postal_code", length = 20) var postalCode: String? = null,
    @Column(length = 30) var phone: String? = null,
    @Column(length = 255) var email: String? = null,
    @Column(length = 255) var website: String? = null,
    @Column(name = "logo_url", length = 500) var logoUrl: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: OrganizationStatus = OrganizationStatus.ACTIVE,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "created_by", nullable = false, updatable = false) val createdBy: UUID,
    @Column(name = "modified_at", nullable = false) var modifiedAt: Instant = Instant.now(),
    @Column(name = "modified_by", nullable = false) var modifiedBy: UUID,
    @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
    @Column(name = "deactivated_at") var deactivatedAt: Instant? = null,
    @Column(name = "deactivated_by") var deactivatedBy: UUID? = null,
    @Column(name = "deactivation_reason") var deactivationReason: String? = null,
    @Version @Column(nullable = false) var version: Long = 0
)

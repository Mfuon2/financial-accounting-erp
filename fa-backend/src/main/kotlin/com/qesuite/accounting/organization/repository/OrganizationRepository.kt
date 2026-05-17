package com.qesuite.accounting.organization.repository

import com.qesuite.accounting.organization.domain.Organization
import com.qesuite.accounting.organization.domain.OrganizationStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface OrganizationRepository : JpaRepository<Organization, UUID> {
    fun findByStatus(status: OrganizationStatus, pageable: Pageable): Page<Organization>
    fun findByRegistrationNumber(registrationNumber: String): Optional<Organization>
    fun existsByName(name: String): Boolean
    fun existsByRegistrationNumber(registrationNumber: String): Boolean
}

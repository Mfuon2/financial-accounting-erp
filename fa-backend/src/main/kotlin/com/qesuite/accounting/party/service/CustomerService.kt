package com.qesuite.accounting.party.service

import com.qesuite.accounting.party.domain.Customer
import com.qesuite.accounting.party.dto.CreateCustomerCommand
import com.qesuite.accounting.party.dto.UpdateCustomerCommand
import com.qesuite.accounting.party.repository.CustomerRepository
import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.codegen.service.CodeGeneratorService
import com.qesuite.accounting.shared.codegen.service.EntityNumberConfigService
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val codeGeneratorService: CodeGeneratorService,
    private val numberConfigService: EntityNumberConfigService,
) {

    @Auditable(action = AuditAction.CREATE, resourceType = "CUSTOMER")
    fun create(command: CreateCustomerCommand): Customer {
        val cfg = numberConfigService.resolveConfig(command.entityId, "CUSTOMER")
        val customerCode = command.customerCode.takeIf { it.isNotBlank() }
            ?: codeGeneratorService.nextUniqueForConfig(command.entityId, cfg) { code ->
                !customerRepository.existsByEntityIdAndCustomerCode(command.entityId, code)
            }
        if (customerRepository.existsByEntityIdAndCustomerCode(command.entityId, customerCode)) {
            throw ConflictException(
                errorCode = "DUPLICATE_ACCOUNT_CODE",
                message = "Customer code $customerCode already exists for this entity.",
                context = mapOf("customer_code" to customerCode, "entity_id" to command.entityId),
            )
        }
        val customer = Customer(
            entityId = command.entityId,
            periodId = command.periodId,
            customerCode = customerCode,
            name = command.name,
            taxNumber = command.taxNumber,
            email = command.email,
            phone = command.phone,
            creditLimit = command.creditLimit ?: BigDecimal.ZERO,
            paymentTerms = command.paymentTerms,
            defaultArAccountId = command.defaultArAccountId
        )
        return customerRepository.save(customer)
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "CUSTOMER")
    fun update(@AuditResourceId customerId: UUID, command: UpdateCustomerCommand): Customer {
        val customer = findById(customerId)
        if (command.creditLimit != null) customer.creditLimit = command.creditLimit
        if (command.paymentTerms != null) customer.paymentTerms = command.paymentTerms
        if (command.email != null) customer.email = command.email
        if (command.phone != null) customer.phone = command.phone
        return customerRepository.save(customer)
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "CUSTOMER")
    fun deactivate(@AuditResourceId customerId: UUID, reason: String, deactivatedBy: UUID): Customer {
        val customer = findById(customerId)
        if (!customer.isActive) {
            throw ConflictException(
                errorCode = "CUSTOMER_ALREADY_DEACTIVATED",
                message = "Customer $customerId is already deactivated.",
                context = mapOf("customer_id" to customerId),
            )
        }
        customer.isActive = false
        customer.deactivatedAt = java.time.Instant.now()
        customer.deactivatedBy = deactivatedBy
        customer.deactivationReason = reason
        return customerRepository.save(customer)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): Customer = customerRepository.findById(id)
        .orElseThrow {
            ResourceNotFoundException(
                errorCode = "CUSTOMER_NOT_FOUND",
                resourceId = id,
                resourceType = "Customer"
            )
        }

    @Transactional(readOnly = true)
    fun findByEntityActive(entityId: UUID, pageable: Pageable): Page<Customer> =
        customerRepository.findByEntityIdAndIsActiveTrue(entityId, pageable)

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<Customer> =
        customerRepository.findByEntityId(entityId, pageable)

    @Transactional(readOnly = true)
    fun findByEntityAndCode(entityId: UUID, customerCode: String): Customer? =
        customerRepository.findByEntityIdAndCustomerCode(entityId, customerCode)
}

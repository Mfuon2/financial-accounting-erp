package com.qesuite.accounting.party.service

import com.qesuite.accounting.party.domain.Supplier
import com.qesuite.accounting.party.dto.CreateSupplierCommand
import com.qesuite.accounting.party.dto.UpdateSupplierCommand
import com.qesuite.accounting.party.repository.SupplierRepository
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
import java.time.Instant
import java.util.UUID

/**
 * §14.3 — Supplier master-data service. Mirrors [CustomerService] for the procurement
 * (AP) cycle. Soft-delete only (Rule 03).
 */
@Service
@Transactional
class SupplierService(
    private val supplierRepository: SupplierRepository,
    private val codeGeneratorService: CodeGeneratorService,
    private val numberConfigService: EntityNumberConfigService,
) {

    @Auditable(action = AuditAction.CREATE, resourceType = "SUPPLIER")
    fun create(command: CreateSupplierCommand): Supplier {
        val cfg = numberConfigService.resolveConfig(command.entityId, "SUPPLIER")
        val supplierCode = command.supplierCode.takeIf { it.isNotBlank() }
            ?: codeGeneratorService.nextUniqueForConfig(command.entityId, cfg) { code ->
                !supplierRepository.existsByEntityIdAndSupplierCode(command.entityId, code)
            }
        if (supplierRepository.existsByEntityIdAndSupplierCode(command.entityId, supplierCode)) {
            throw ConflictException(
                errorCode = "DUPLICATE_ACCOUNT_CODE",
                message = "Supplier code $supplierCode already exists for this entity.",
                context = mapOf(
                    "supplier_code" to supplierCode,
                    "entity_id" to command.entityId,
                ),
            )
        }
        val supplier = Supplier(
            entityId = command.entityId,
            periodId = command.periodId,
            supplierCode = supplierCode,
            name = command.name,
            taxNumber = command.taxNumber,
            email = command.email,
            phone = command.phone,
            paymentTerms = command.paymentTerms,
            defaultApAccountId = command.defaultApAccountId,
        )
        return supplierRepository.save(supplier)
    }

    /**
     * §14.3 — Update mutable supplier fields: email, phone, paymentTerms.
     * supplierCode and name are immutable after creation (audit-trail preservation).
     * Only non-null fields in the command are applied.
     */
    @Auditable(action = AuditAction.UPDATE, resourceType = "SUPPLIER")
    fun update(@AuditResourceId supplierId: UUID, command: UpdateSupplierCommand): Supplier {
        val supplier = findById(supplierId)
        if (command.email != null)        supplier.email        = command.email
        if (command.phone != null)        supplier.phone        = command.phone
        if (command.paymentTerms != null) supplier.paymentTerms = command.paymentTerms
        return supplierRepository.save(supplier)
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "SUPPLIER")
    fun deactivate(
        @AuditResourceId supplierId: UUID,
        reason: String,
        deactivatedBy: UUID,
    ): Supplier {
        val supplier = findById(supplierId)
        if (!supplier.isActive) {
            throw ConflictException(
                errorCode = "SUPPLIER_ALREADY_DEACTIVATED",
                message = "Supplier $supplierId is already deactivated.",
                context = mapOf("supplier_id" to supplierId),
            )
        }
        supplier.isActive = false
        supplier.deactivatedAt = Instant.now()
        supplier.deactivatedBy = deactivatedBy
        supplier.deactivationReason = reason
        return supplierRepository.save(supplier)
    }

    @Transactional(readOnly = true)
    fun findById(id: UUID): Supplier = supplierRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("SUPPLIER_NOT_FOUND", id, "Supplier") }

    @Transactional(readOnly = true)
    fun findByEntityActive(entityId: UUID, pageable: Pageable): Page<Supplier> =
        supplierRepository.findByEntityIdAndIsActiveTrue(entityId, pageable)

    @Transactional(readOnly = true)
    fun findByEntity(entityId: UUID, pageable: Pageable): Page<Supplier> =
        supplierRepository.findByEntityId(entityId, pageable)

    @Transactional(readOnly = true)
    fun findByEntityAndCode(entityId: UUID, supplierCode: String): Supplier? =
        supplierRepository.findByEntityIdAndSupplierCode(entityId, supplierCode)
}

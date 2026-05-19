package com.qesuite.accounting.party.service

import com.qesuite.accounting.party.domain.Supplier
import com.qesuite.accounting.party.dto.CreateSupplierCommand
import com.qesuite.accounting.party.dto.SupplierStatementLine
import com.qesuite.accounting.party.dto.SupplierStatementResponse
import com.qesuite.accounting.party.dto.UpdateSupplierCommand
import com.qesuite.accounting.party.repository.SupplierRepository
import com.qesuite.accounting.payables.repository.BillPaymentRepository
import com.qesuite.accounting.payables.repository.BillRepository
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
    private val billRepository: BillRepository,
    private val billPaymentRepository: BillPaymentRepository,
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

    @Transactional(readOnly = true)
    fun getStatement(supplierId: UUID): SupplierStatementResponse {
        val supplier = findById(supplierId)
        val bills = billRepository.findBySupplierIdAndIsActiveTrueOrderByBillDateAsc(supplierId)

        val billIds = bills.map { it.id }
        val paymentsByBill = if (billIds.isEmpty()) emptyMap()
        else billPaymentRepository.findByBillIdIn(billIds).groupBy { it.billId }

        val lines = mutableListOf<SupplierStatementLine>()
        var running = BigDecimal.ZERO

        // Bills and debit notes both live in the Bill table; isDebitNote distinguishes them
        for (bill in bills) {
            if (bill.isDebitNote) {
                running = running.subtract(bill.totalAmount)
                lines += SupplierStatementLine(
                    date        = bill.billDate,
                    type        = "DEBIT_NOTE",
                    reference   = bill.billNumber,
                    description = bill.description ?: "Debit note — ${bill.supplierName}",
                    debit       = BigDecimal.ZERO,
                    credit      = bill.totalAmount,
                    balance     = running,
                    status      = bill.status.name,
                    documentId  = bill.id,
                )
            } else {
                running = running.add(bill.totalAmount)
                lines += SupplierStatementLine(
                    date        = bill.billDate,
                    type        = "BILL",
                    reference   = bill.billNumber,
                    description = bill.description ?: "Bill — ${bill.supplierName}",
                    debit       = bill.totalAmount,
                    credit      = BigDecimal.ZERO,
                    balance     = running,
                    status      = bill.status.name,
                    documentId  = bill.id,
                )
                // Inline the payments for this bill sorted by date
                val pmts = paymentsByBill[bill.id]?.sortedBy { it.paymentDate } ?: emptyList()
                for (pmt in pmts) {
                    running = running.subtract(pmt.amount)
                    lines += SupplierStatementLine(
                        date        = pmt.paymentDate,
                        type        = "PAYMENT",
                        reference   = pmt.reference ?: pmt.id.toString().takeLast(8).uppercase(),
                        description = "Payment — ${pmt.paymentMethod?.name?.replace('_', ' ') ?: "Bank transfer"}",
                        debit       = BigDecimal.ZERO,
                        credit      = pmt.amount,
                        balance     = running,
                        status      = null,
                        documentId  = pmt.id,
                    )
                }
            }
        }

        // Re-sort everything by date then type (bills before payments on same day)
        val sorted = lines.sortedWith(compareBy({ it.date }, { if (it.type == "PAYMENT") 1 else 0 }))

        // Recompute running balance on sorted list
        var bal = BigDecimal.ZERO
        val finalLines = sorted.map { line ->
            bal = bal.add(line.debit).subtract(line.credit)
            line.copy(balance = bal)
        }

        val totalDebits  = finalLines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.debit) }
        val totalCredits = finalLines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.credit) }

        return SupplierStatementResponse(
            supplierId     = supplier.id,
            supplierName   = supplier.name,
            supplierCode   = supplier.supplierCode,
            currency       = bills.firstOrNull()?.currencyCode ?: "KES",
            totalDebits    = totalDebits,
            totalCredits   = totalCredits,
            closingBalance = totalDebits.subtract(totalCredits),
            lines          = finalLines,
        )
    }
}

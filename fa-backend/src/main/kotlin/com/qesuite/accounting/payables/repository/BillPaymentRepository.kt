package com.qesuite.accounting.payables.repository

import com.qesuite.accounting.payables.domain.BillPayment
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BillPaymentRepository : JpaRepository<BillPayment, UUID> {
    fun findByBillId(billId: UUID): List<BillPayment>
    fun findByEntityId(entityId: UUID): List<BillPayment>
}

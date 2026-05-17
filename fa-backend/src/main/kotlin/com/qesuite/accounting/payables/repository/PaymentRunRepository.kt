package com.qesuite.accounting.payables.repository

import com.qesuite.accounting.payables.domain.PaymentRun
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PaymentRunRepository : JpaRepository<PaymentRun, UUID> {
    fun findByEntityIdOrderByPaymentDateDesc(entityId: UUID): List<PaymentRun>
}

package com.qesuite.accounting.expenses.repository

import com.qesuite.accounting.expenses.domain.ExpenseClaim
import com.qesuite.accounting.expenses.domain.ExpenseClaimStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ExpenseClaimRepository : JpaRepository<ExpenseClaim, UUID> {
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<ExpenseClaim>
    fun findByEntityIdAndStatus(entityId: UUID, status: ExpenseClaimStatus, pageable: Pageable): Page<ExpenseClaim>
    fun findByEntityIdAndEmployeeId(entityId: UUID, employeeId: UUID, pageable: Pageable): Page<ExpenseClaim>
}

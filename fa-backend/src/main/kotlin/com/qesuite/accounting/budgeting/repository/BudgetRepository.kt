package com.qesuite.accounting.budgeting.repository

import com.qesuite.accounting.budgeting.domain.Budget
import com.qesuite.accounting.budgeting.domain.BudgetStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BudgetRepository : JpaRepository<Budget, UUID> {
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<Budget>
    fun findByEntityIdAndStatus(entityId: UUID, status: BudgetStatus, pageable: Pageable): Page<Budget>
}

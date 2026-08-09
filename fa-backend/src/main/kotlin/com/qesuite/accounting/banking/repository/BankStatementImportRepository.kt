package com.qesuite.accounting.banking.repository

import com.qesuite.accounting.banking.domain.BankStatementImport
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
interface BankStatementImportRepository : JpaRepository<BankStatementImport, UUID> {
    fun findByEntityId(entityId: UUID, pageable: Pageable): Page<BankStatementImport>

    fun existsByEntityIdAndAccountIdAndStatementDateAndClosingBalance(
        entityId: UUID,
        accountId: UUID,
        statementDate: LocalDate,
        closingBalance: BigDecimal,
    ): Boolean
}

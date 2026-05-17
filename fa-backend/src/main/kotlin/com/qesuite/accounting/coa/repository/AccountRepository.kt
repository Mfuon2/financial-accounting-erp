package com.qesuite.accounting.coa.repository

import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByEntityIdAndAccountCode(entityId: UUID, accountCode: String): Optional<Account>
    fun findAllByEntityId(entityId: UUID): List<Account>
    fun findAllByEntityIdAndAccountType(entityId: UUID, accountType: AccountType): List<Account>
    fun findAllByEntityIdAndAccountSubtype(entityId: UUID, accountSubtype: AccountSubtype): List<Account>
    fun existsByEntityIdAndAccountCode(entityId: UUID, accountCode: String): Boolean
    fun existsByEntityIdAndAccountName(entityId: UUID, accountName: String): Boolean
}

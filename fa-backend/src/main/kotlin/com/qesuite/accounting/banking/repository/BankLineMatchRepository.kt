package com.qesuite.accounting.banking.repository

import com.qesuite.accounting.banking.domain.BankLineMatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BankLineMatchRepository : JpaRepository<BankLineMatch, UUID> {

    /** Whether [ledgerEntryId] is already matched to some bank line (any entity/account). */
    fun existsByLedgerEntryId(ledgerEntryId: UUID): Boolean

    /**
     * Every `ledgerEntryId` currently matched to some bank statement line for [accountId] —
     * used by [com.qesuite.accounting.banking.service.BankStatementService] to compute which GL
     * entries for the account are still "outstanding" (recorded in the books, not yet matched to
     * any statement line) for the reconciliation tie-out, and to exclude already-matched entries
     * from auto-match candidate suggestions. A JPQL join across this module's own three entities
     * (line -> import) — not a cross-bounded-context repository access.
     */
    @Query(
        """
        SELECT m.ledgerEntryId FROM BankLineMatch m
        JOIN m.bankStatementLine l
        JOIN l.bankStatementImport i
        WHERE i.accountId = :accountId
        """
    )
    fun findMatchedLedgerEntryIdsByAccountId(accountId: UUID): List<UUID>
}

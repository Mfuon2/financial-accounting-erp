package com.qesuite.accounting.ledger.repository

import com.qesuite.accounting.ledger.domain.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*
import java.math.BigDecimal
import java.time.LocalDate

@Repository
interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {

    fun existsByAccountId(accountId: UUID): Boolean

    // -------------------------------------------------------------------------
    // Existing aggregate queries
    // -------------------------------------------------------------------------

    @Query("SELECT SUM(le.functionalDebit) FROM LedgerEntry le WHERE le.accountId = :accountId AND le.transDate <= :asOfDate")
    fun sumFunctionalDebits(accountId: UUID, asOfDate: LocalDate): BigDecimal?

    @Query("SELECT SUM(le.functionalCredit) FROM LedgerEntry le WHERE le.accountId = :accountId AND le.transDate <= :asOfDate")
    fun sumFunctionalCredits(accountId: UUID, asOfDate: LocalDate): BigDecimal?

    @Query("SELECT SUM(le.functionalDebit) FROM LedgerEntry le WHERE le.accountId = :accountId AND le.transDate >= :startDate AND le.transDate <= :endDate")
    fun sumFunctionalDebitsRange(accountId: UUID, startDate: LocalDate, endDate: LocalDate): BigDecimal?

    @Query("SELECT SUM(le.functionalCredit) FROM LedgerEntry le WHERE le.accountId = :accountId AND le.transDate >= :startDate AND le.transDate <= :endDate")
    fun sumFunctionalCreditsRange(accountId: UUID, startDate: LocalDate, endDate: LocalDate): BigDecimal?

    // -------------------------------------------------------------------------
    // Account ledger listing — full history, chronological
    // -------------------------------------------------------------------------

    /** All entries for an account ordered oldest-first; ties broken by insertion order. */
    fun findByAccountIdOrderByTransDateAscCreatedAtAsc(accountId: UUID): List<LedgerEntry>

    // -------------------------------------------------------------------------
    // Date-range slice for T-Account / sub-ledger views
    // -------------------------------------------------------------------------

    /**
     * Entries for a single account within [startDate, endDate], oldest-first.
     * Used by getTAccount and subsidiary ledger range queries.
     */
    @Query(
        """
        SELECT le FROM LedgerEntry le
        WHERE le.accountId = :accountId
          AND le.transDate >= :startDate
          AND le.transDate <= :endDate
        ORDER BY le.transDate ASC, le.createdAt ASC
        """
    )
    fun findByAccountIdAndDateRange(
        accountId: UUID,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<LedgerEntry>

    // -------------------------------------------------------------------------
    // Traceability — back-trace from journal entry line to ledger entries
    // -------------------------------------------------------------------------

    /** Find all ledger entries that were generated from a specific journal entry line. */
    fun findByJournalEntryLineId(journalEntryLineId: UUID): List<LedgerEntry>

    // -------------------------------------------------------------------------
    // Entity-level aggregates for trial balance
    // (No JPA relationship to Account, so callers resolve accountIds first.)
    // -------------------------------------------------------------------------

    /**
     * Sum of functional debits for a set of accounts up to and including asOfDate.
     * Callers obtain the accountId list from AccountRepository.findAllByEntityId().
     */
    @Query(
        """
        SELECT COALESCE(SUM(le.functionalDebit), 0)
        FROM LedgerEntry le
        WHERE le.accountId IN :accountIds
          AND le.transDate <= :asOfDate
        """
    )
    fun sumFunctionalDebitsByAccountIds(accountIds: List<UUID>, asOfDate: LocalDate): BigDecimal

    /**
     * Sum of functional credits for a set of accounts up to and including asOfDate.
     * Callers obtain the accountId list from AccountRepository.findAllByEntityId().
     */
    @Query(
        """
        SELECT COALESCE(SUM(le.functionalCredit), 0)
        FROM LedgerEntry le
        WHERE le.accountId IN :accountIds
          AND le.transDate <= :asOfDate
        """
    )
    fun sumFunctionalCreditsByAccountIds(accountIds: List<UUID>, asOfDate: LocalDate): BigDecimal

    /** Credits for a set of accounts in [startDate, endDate] — used by the dashboard expense/revenue sparkline. */
    @Query("""
        SELECT COALESCE(SUM(le.functionalCredit), 0)
        FROM LedgerEntry le
        WHERE le.accountId IN :accountIds
          AND le.transDate >= :startDate
          AND le.transDate <= :endDate
    """)
    fun sumCreditsByAccountIdsAndRange(accountIds: List<UUID>, startDate: LocalDate, endDate: LocalDate): BigDecimal

    /** Debits for a set of accounts in [startDate, endDate] — used by the dashboard expense/revenue sparkline. */
    @Query("""
        SELECT COALESCE(SUM(le.functionalDebit), 0)
        FROM LedgerEntry le
        WHERE le.accountId IN :accountIds
          AND le.transDate >= :startDate
          AND le.transDate <= :endDate
    """)
    fun sumDebitsByAccountIdsAndRange(accountIds: List<UUID>, startDate: LocalDate, endDate: LocalDate): BigDecimal
}

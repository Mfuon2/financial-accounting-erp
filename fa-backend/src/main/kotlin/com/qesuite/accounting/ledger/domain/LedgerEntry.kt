package com.qesuite.accounting.ledger.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * §5.1 — General Ledger Entry
 */
@Entity
@Table(name = "ledger_entries")
class LedgerEntry(
    entityId: UUID,
    
    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Column(name = "journal_entry_line_id", nullable = false)
    val journalEntryLineId: UUID,

    @Column(name = "trans_date", nullable = false)
    val transDate: LocalDate,

    @Column(name = "functional_debit", precision = 20, scale = 6, nullable = false)
    val functionalDebit: BigDecimal = BigDecimal.ZERO,

    @Column(name = "functional_credit", precision = 20, scale = 6, nullable = false)
    val functionalCredit: BigDecimal = BigDecimal.ZERO,

    @Column(name = "running_balance", precision = 20, scale = 6, nullable = false)
    var runningBalance: BigDecimal

) : BaseFinancialEntity(entityId = entityId)

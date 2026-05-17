package com.qesuite.accounting.journal.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

/**
 * §4.1 — Journal Entry Engine
 */
@Entity
@Table(name = "journal_entries")
class JournalEntry(
    entityId: UUID,
    periodId: UUID,

    @Column(name = "reference", length = 30, unique = false)
    var reference: String? = null,

    @Column(name = "trans_date", nullable = false)
    var transDate: LocalDate,

    @Column(name = "description")
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: JournalEntryStatus = JournalEntryStatus.DRAFT,

    @Column(name = "source_type")
    var sourceType: String? = null,

    @Column(name = "source_id")
    var sourceId: UUID? = null,

    @OneToMany(mappedBy = "journalEntry", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    var lines: MutableList<JournalEntryLine> = mutableListOf()

) : BaseFinancialEntity(entityId = entityId, periodId = periodId) {
    
    fun addLine(line: JournalEntryLine) {
        lines.add(line)
        line.journalEntry = this
    }

    fun clearLines() {
        lines.clear()
    }
}

@Entity
@Table(name = "journal_entry_lines")
class JournalEntryLine(
    @Id
    val id: UUID = UUID.randomUUID(),

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    var journalEntry: JournalEntry? = null,

    @Column(name = "account_id", nullable = false)
    var accountId: UUID,

    @Column(name = "description")
    var description: String? = null,

    /**
     * §3.1 — Monetary Precision: DECIMAL(20,6)
     */
    @Column(name = "debit_amount", precision = 20, scale = 6, nullable = false)
    var debitAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "credit_amount", precision = 20, scale = 6, nullable = false)
    var creditAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "currency_code", length = 3, nullable = false)
    var currencyCode: String,

    @Column(name = "exchange_rate", precision = 20, scale = 6, nullable = false)
    var exchangeRate: BigDecimal = BigDecimal.ONE,

    @Column(name = "functional_debit", precision = 20, scale = 6, nullable = false)
    var functionalDebit: BigDecimal = BigDecimal.ZERO,

    @Column(name = "functional_credit", precision = 20, scale = 6, nullable = false)
    var functionalCredit: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_code", length = 20)
    @Schema(example = "VAT_16", description = "Tax code for automated tax reporting (§13)")
    var taxCode: String? = null,

    @Column(name = "tax_amount", precision = 20, scale = 6)
    @Schema(type = "number", format = "decimal", example = "400.000000")
    var taxAmount: BigDecimal? = null
)

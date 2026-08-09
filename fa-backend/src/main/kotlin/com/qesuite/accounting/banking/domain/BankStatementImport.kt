package com.qesuite.accounting.banking.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Cash & Bank Management, workplan.md Phase 1
 * item 2. A single imported bank statement (header) for one GL cash/bank [accountId], covering
 * [statementDate] with the bank's own [openingBalance]/[closingBalance] for that statement.
 * `periodId` (inherited from [BaseFinancialEntity]) is left `null` on the header, same reasoning
 * as [com.qesuite.accounting.budgeting.domain.Budget]: a statement's [lines] each carry their own
 * `transDate`, the header itself doesn't belong to one period.
 *
 * Never posts a journal entry — reconciliation compares this statement's lines against existing
 * [com.qesuite.accounting.ledger.domain.LedgerEntry] rows for [accountId] at read time
 * ([com.qesuite.accounting.banking.service.BankStatementService.reconciliationSummary]), the same
 * non-posting philosophy as Budgeting's variance report. This is also why creation does not
 * require `@RequireIdempotencyKey` (CLAUDE.md §4): importing a statement has no double-posting
 * risk (nothing is posted), and a defensive DB-level de-dupe constraint on
 * (entity_id, account_id, statement_date, closing_balance) already guards against an accidental
 * duplicate re-import of the exact same statement.
 */
@Entity
@Table(name = "bank_statement_imports")
class BankStatementImport(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,

    @Column(name = "account_id", nullable = false)
    @Schema(description = "FK to the GL cash/bank account (must be a non-header, Cash & Equivalents asset account)")
    val accountId: UUID,

    @Column(name = "statement_date", nullable = false)
    @Schema(example = "2026-03-31", description = "The statement's own closing/as-of date")
    var statementDate: LocalDate,

    @Column(name = "opening_balance", nullable = false, precision = 20, scale = 6)
    @Schema(example = "1250000.000000", description = "Opening balance per the bank statement")
    var openingBalance: BigDecimal,

    @Column(name = "closing_balance", nullable = false, precision = 20, scale = 6)
    @Schema(example = "1318400.000000", description = "Closing balance per the bank statement")
    var closingBalance: BigDecimal,

    @Column(nullable = true, columnDefinition = "TEXT")
    var notes: String? = null,

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = null) {

    /** Imported statement lines — one row per transaction on the statement. */
    @OneToMany(mappedBy = "bankStatementImport", fetch = FetchType.EAGER, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JsonIgnoreProperties("bankStatementImport")
    val lines: MutableList<BankStatementLine> = mutableListOf()

    /** Optimistic locking. */
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0

    fun addLine(line: BankStatementLine) {
        lines.add(line)
        line.bankStatementImport = this
    }
}

package com.qesuite.accounting.shared.codegen.service

import com.qesuite.accounting.shared.codegen.domain.CodeSequence
import com.qesuite.accounting.shared.codegen.repository.CodeSequenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Year
import java.util.UUID

/**
 * §15 — Generic business-code generator.
 *
 * Two format families:
 *
 *  Master-data (no year, never resets):
 *    CU0001   — Customer
 *    SUPP0001 — Supplier
 *    FA0001   — Fixed Asset
 *
 *  Transactional (year-scoped, resets to 0001 each January):
 *    INV-2026-0001   — Sales Invoice
 *    BILL-2026-0001  — Purchase Bill
 *    JE-2026-0001    — Journal Entry
 *    SD-2026-0001    — Source Document
 *    PAY-2026-0001   — Payment
 *    RCT-2026-0001   — Receipt
 *    CN-2026-0001    — Credit Note
 *    DN-2026-0001    — Debit Note
 */
@Service
class CodeGeneratorService(
    private val repo: CodeSequenceRepository
) {
    companion object {
        private val YEAR_SCOPED = setOf("INV", "BILL", "JE", "SD", "PAY", "RCT", "CN", "DN", "PO", "QT")
        private val PAD_WIDTH   = mapOf(
            "CU" to 4, "SUPP" to 4, "FA" to 4,
            "INV" to 4, "BILL" to 4, "JE" to 4,
            "SD" to 4, "PAY" to 4, "RCT" to 4,
            "CN" to 4, "DN" to 4, "PO" to 4, "QT" to 4
        )
    }

    /**
     * Atomically consumes the next sequence and returns the formatted code.
     * REQUIRES_NEW so the increment commits independently of the caller's transaction.
     * Pass [yearScoped] and [padWidth] explicitly when using a dynamic/configured prefix
     * that may not be in the built-in YEAR_SCOPED / PAD_WIDTH maps.
     * Pass [fiscalYear] to override the default (calendar year) for year-scoped sequences —
     * used by journal entries to scope references to the period's fiscal year rather than
     * the current wall-clock year.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun next(
        entityId: UUID,
        rawPrefix: String,
        yearScoped: Boolean = rawPrefix.uppercase() in YEAR_SCOPED,
        padWidth: Int = PAD_WIDTH[rawPrefix.uppercase()] ?: 4,
        customFormat: String? = null,
        fiscalYear: Int? = null,
    ): String {
        val prefix = rawPrefix.uppercase()
        return if (yearScoped) nextYearScoped(entityId, prefix, padWidth, customFormat, fiscalYear)
               else nextSimple(entityId, prefix, padWidth, customFormat)
    }

    /**
     * Keeps calling [next] until the produced code passes [isUnique].
     * Handles legacy data (records created before the sequence table existed)
     * and any other gap in the sequence.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun nextUnique(
        entityId: UUID,
        rawPrefix: String,
        yearScoped: Boolean = rawPrefix.uppercase() in YEAR_SCOPED,
        padWidth: Int = PAD_WIDTH[rawPrefix.uppercase()] ?: 4,
        customFormat: String? = null,
        fiscalYear: Int? = null,
        isUnique: (String) -> Boolean,
    ): String {
        var code: String
        var attempts = 0
        do {
            code = next(entityId, rawPrefix, yearScoped, padWidth, customFormat, fiscalYear)
            attempts++
            if (attempts > 50) break  // safety valve — should never happen in practice
        } while (!isUnique(code))
        return code
    }

    /**
     * Returns what the next code WOULD be without consuming it.
     * Safe to call freely (read-only). Used for UI hints — may be off by 1
     * under concurrent creation, which is acceptable.
     */
    @Transactional(readOnly = true)
    fun peek(
        entityId: UUID,
        rawPrefix: String,
        yearScoped: Boolean = rawPrefix.uppercase() in YEAR_SCOPED,
        padWidth: Int = PAD_WIDTH[rawPrefix.uppercase()] ?: 4,
        customFormat: String? = null,
    ): String {
        val prefix = rawPrefix.uppercase()
        return if (yearScoped) {
            val year    = Year.now().value
            val lastSeq = repo.findByEntityIdAndPrefixAndYear(entityId, prefix, year)?.lastSeq ?: 0
            format(prefix, year, lastSeq + 1, padWidth, customFormat)
        } else {
            val lastSeq = repo.findByEntityIdAndPrefixNoYear(entityId, prefix)?.lastSeq ?: 0
            format(prefix, null, lastSeq + 1, padWidth, customFormat)
        }
    }

    @Transactional(readOnly = true)
    fun peekAll(entityId: UUID): Map<String, String> =
        (YEAR_SCOPED + setOf("CU", "SUPP", "FA")).associateWith { peek(entityId, it) }

    /** Convenience — unpacks a [PrefixConfig] and calls [nextUnique]. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun nextUniqueForConfig(
        entityId: UUID,
        config: PrefixConfig,
        fiscalYear: Int? = null,
        isUnique: (String) -> Boolean,
    ): String =
        nextUnique(entityId, config.prefix, config.yearScoped, customFormat = config.customFormat, fiscalYear = fiscalYear, isUnique = isUnique)

    // ── internals ─────────────────────────────────────────────────────────────

    private fun nextYearScoped(entityId: UUID, prefix: String, padWidth: Int, customFormat: String?, fiscalYear: Int? = null): String {
        val year = fiscalYear ?: Year.now().value
        val seq  = repo.findForUpdateWithYear(entityId, prefix, year)
            ?: CodeSequence(entityId = entityId, prefix = prefix, year = year)
        seq.lastSeq++
        repo.saveAndFlush(seq)
        return format(prefix, year, seq.lastSeq, padWidth, customFormat)
    }

    private fun nextSimple(entityId: UUID, prefix: String, padWidth: Int, customFormat: String?): String {
        val seq = repo.findForUpdateNoYear(entityId, prefix)
            ?: CodeSequence(entityId = entityId, prefix = prefix, year = null)
        seq.lastSeq++
        repo.saveAndFlush(seq)
        return format(prefix, null, seq.lastSeq, padWidth, customFormat)
    }

    private fun format(prefix: String, year: Int?, seq: Int, padWidth: Int, customFormat: String? = null): String {
        if (customFormat != null) {
            val seqWidth = Regex("\\{(0+)\\}").find(customFormat)?.groupValues?.get(1)?.length ?: padWidth
            val seqStr   = seq.toString().padStart(seqWidth, '0')
            return customFormat
                .replace("{PREFIX}", prefix)
                .replace("{YYYY}",   year?.toString() ?: "")
                .replace("{YY}",     year?.let { (it % 100).toString().padStart(2, '0') } ?: "")
                .replace(Regex("\\{0+\\}"), seqStr)
        }
        val seqStr = seq.toString().padStart(padWidth, '0')
        return if (year != null) "$prefix-$year-$seqStr" else "$prefix$seqStr"
    }
}

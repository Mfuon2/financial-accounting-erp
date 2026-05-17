package com.qesuite.accounting.invoicing.service

import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.domain.InvoiceLine
import com.qesuite.accounting.invoicing.domain.PerformanceObligationType
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * §14.4, §11 — IFRS 15 Revenue Recognition Service
 * Implements 5-step revenue recognition model:
 * (1) Identify contract — customer exists, terms approved
 * (2) Identify performance obligations — goods/services to be delivered
 * (3) Determine transaction price — fixed consideration
 * (4) Allocate transaction price — proportional to standalone selling price
 * (5) Recognize revenue — upon satisfaction of obligation
 *
 * This service implements the POINT_IN_TIME path fully (goods delivered).
 * The OVER_TIME path (services) is structured but deferred to a future period-end event.
 */
@Service
class Ifrs15RecognitionService {

    /**
     * Validate and compute IFRS 15 recognition for invoice lines.
     * For POINT_IN_TIME obligations, recognizes full amount on invoice approval.
     * For OVER_TIME obligations, stores the obligation for later period-end recognition.
     *
     * @param invoice The invoice being approved
     * @param lines The invoice lines with performance obligation types
     * @return Map of line ID to recognized amount (may be zero for OVER_TIME)
     */
    fun computeRecognition(invoice: Invoice, lines: List<InvoiceLine>): Map<java.util.UUID, BigDecimal> {
        val recognition = mutableMapOf<java.util.UUID, BigDecimal>()

        lines.forEach { line ->
            val recognizedAmount = when (line.recognitionType) {
                // §14.4, §11.2 — POINT_IN_TIME: Recognize full amount on delivery
                PerformanceObligationType.POINT_IN_TIME -> {
                    line.lineTotal.setScale(6, RoundingMode.HALF_EVEN)
                }

                // §14.4, §11.2 — OVER_TIME: Will be recognized over service period
                // For now, store obligation with 0 recognized; period-end job will recognize incrementally
                PerformanceObligationType.OVER_TIME -> {
                    BigDecimal.ZERO.setScale(6, RoundingMode.HALF_EVEN)
                }

                // Default to POINT_IN_TIME if not specified (assume goods)
                null -> {
                    line.lineTotal.setScale(6, RoundingMode.HALF_EVEN)
                }
            }

            recognition[line.id] = recognizedAmount
        }

        return recognition
    }

    /**
     * Determine if an invoice qualifies for revenue recognition at approval.
     * Returns TRUE if all performance obligations are POINT_IN_TIME or NULL.
     * Returns FALSE if any obligation is OVER_TIME (deferred to period-end).
     *
     * @param lines Invoice lines with obligation types
     * @return TRUE if can recognize now, FALSE if deferred
     */
    fun canRecognizeNow(lines: List<InvoiceLine>): Boolean {
        return !lines.any { it.recognitionType == PerformanceObligationType.OVER_TIME }
    }

    /**
     * Calculate total deferred revenue for an invoice.
     * This is the amount that must be credited to deferred revenue account instead of sales revenue.
     *
     * @param lines Invoice lines
     * @return Total deferred amount (sum of OVER_TIME line totals)
     */
    fun calculateDeferredRevenue(lines: List<InvoiceLine>): BigDecimal {
        return lines
            .filter { it.recognitionType == PerformanceObligationType.OVER_TIME }
            .map { it.lineTotal }
            .fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }
            .setScale(6, RoundingMode.HALF_EVEN)
    }
}

package com.qesuite.accounting.analytics.service

import com.qesuite.accounting.analytics.dto.DashboardSummaryResponse
import com.qesuite.accounting.analytics.dto.RecentAuditItem
import com.qesuite.accounting.analytics.dto.SparklineResponse
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.repository.AccountRepository
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.repository.InvoiceRepository
import com.qesuite.accounting.journal.domain.JournalEntryStatus
import com.qesuite.accounting.journal.repository.JournalEntryRepository
import com.qesuite.accounting.ledger.repository.LedgerEntryRepository
import com.qesuite.accounting.payments.repository.PaymentRepository
import com.qesuite.accounting.shared.audit.repository.AuditLogRepository
import com.qesuite.accounting.users.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val accountRepository: AccountRepository,
    private val ledgerRepository: LedgerEntryRepository,
    private val invoiceRepository: InvoiceRepository,
    private val paymentRepository: PaymentRepository,
    private val journalRepository: JournalEntryRepository,
    private val auditLogRepository: AuditLogRepository,
    private val userRepository: UserRepository,
) {

    fun getSummary(entityId: UUID): DashboardSummaryResponse {
        val today = LocalDate.now()
        val startOfMonth = today.withDayOfMonth(1)
        val twelveMonthsAgo = today.minusMonths(11).withDayOfMonth(1)

        // Resolve account IDs by type/subtype
        val cashIds = accountRepository
            .findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CASH_AND_EQUIVALENTS)
            .map { it.id }
        val expenseIds = accountRepository
            .findAllByEntityIdAndAccountType(entityId, AccountType.EXPENSE)
            .map { it.id }

        // KPI — cash balance (debit-normal: debits - credits up to today)
        val cash = if (cashIds.isEmpty()) BigDecimal.ZERO else
            ledgerRepository.sumFunctionalDebitsByAccountIds(cashIds, today)
                .subtract(ledgerRepository.sumFunctionalCreditsByAccountIds(cashIds, today))
                .max(BigDecimal.ZERO)

        // KPI — AR outstanding (sum of outstanding invoice amounts)
        val ar = invoiceRepository.findOutstandingByEntity(entityId)
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.outstandingAmount) }

        // KPI — MTD revenue from approved invoices in current month
        val mtdRevenue = invoiceRepository
            .findRevenueInRange(entityId, startOfMonth, today)
            .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.totalAmount) }

        // KPI — MTD expenses from expense ledger entries in current month
        val mtdExpenses = if (expenseIds.isEmpty()) BigDecimal.ZERO else
            ledgerRepository.sumDebitsByAccountIdsAndRange(expenseIds, startOfMonth, today)
                .subtract(ledgerRepository.sumCreditsByAccountIdsAndRange(expenseIds, startOfMonth, today))
                .max(BigDecimal.ZERO)

        // Sparklines — 12 monthly buckets
        val (sparkLabels, sparkRev, sparkExp, sparkAr, sparkCash) = buildSparklines(
            entityId, today, cashIds, expenseIds
        )

        // Pending approvals count
        val pendingJe = journalRepository
            .findByEntityIdAndStatus(entityId, JournalEntryStatus.PENDING_APPROVAL).size
        val pendingInv = invoiceRepository
            .findByEntityIdAndStatus(entityId, InvoiceStatus.DRAFT, org.springframework.data.domain.Pageable.unpaged())
            .totalElements.toInt()
        val pendingApprovals = pendingJe + pendingInv

        // Recent audit activity
        val recentLogs = auditLogRepository.findTop10ByEntityIdOrderByCreatedAtDesc(entityId)
        val actorIds = recentLogs.mapNotNull { it.userId }.toSet()
        val actorMap = userRepository.findAllById(actorIds).associateBy { it.id }

        val recentAudit = recentLogs.map { log ->
            val actor = actorMap[log.userId]?.fullName ?: "System"
            val ts = log.createdAt.atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            RecentAuditItem(
                ts = ts,
                detail = "${log.action.name.lowercase().replaceFirstChar { it.uppercase() }} ${
                    log.resourceType.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
                } …${log.resourceId.toString().takeLast(6)}",
                actor = actor
            )
        }

        return DashboardSummaryResponse(
            cashAndEquivalents = cash,
            accountsReceivable = ar,
            mtdRevenue = mtdRevenue,
            mtdExpenses = mtdExpenses,
            sparkRev = sparkRev,
            sparkExp = sparkExp,
            sparkAr = sparkAr,
            sparkCash = sparkCash,
            sparkLabels = sparkLabels,
            pendingApprovals = pendingApprovals,
            recentAudit = recentAudit
        )
    }

    fun getSparklines(entityId: UUID): SparklineResponse {
        val today = LocalDate.now()
        val cashIds = accountRepository
            .findAllByEntityIdAndAccountSubtype(entityId, AccountSubtype.CASH_AND_EQUIVALENTS)
            .map { it.id }
        val expenseIds = accountRepository
            .findAllByEntityIdAndAccountType(entityId, AccountType.EXPENSE)
            .map { it.id }
        val (labels, rev, exp, ar, cash) = buildSparklines(entityId, today, cashIds, expenseIds)
        return SparklineResponse(revenue = rev, expenses = exp, ar = ar, cash = cash, labels = labels)
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private data class Sparklines(
        val labels: List<String>,
        val rev: List<BigDecimal>,
        val exp: List<BigDecimal>,
        val ar: List<BigDecimal>,
        val cash: List<BigDecimal>
    )

    private fun buildSparklines(
        entityId: UUID,
        today: LocalDate,
        cashIds: List<UUID>,
        expenseIds: List<UUID>
    ): Sparklines {
        val monthFmt = DateTimeFormatter.ofPattern("MMM yy").withLocale(java.util.Locale.ENGLISH)

        // Fetch raw data for the whole 12-month window once, then group in-memory
        val windowStart = today.minusMonths(11).withDayOfMonth(1)
        val windowEnd = today.withDayOfMonth(today.lengthOfMonth())

        val allInvoices = invoiceRepository.findRevenueInRange(entityId, windowStart, windowEnd)
        val allPayments = paymentRepository.findPostedInRange(entityId, windowStart, windowEnd)

        // Build 12 monthly buckets (oldest → newest)
        val months = (11 downTo 0).map { n -> today.minusMonths(n.toLong()) }

        val labels = months.map { it.format(monthFmt) }

        val sparkRev = months.map { month ->
            val start = month.withDayOfMonth(1)
            val end = month.withDayOfMonth(month.lengthOfMonth())
            allInvoices.filter { it.issueDate in start..end }
                .fold(BigDecimal.ZERO) { acc, inv -> acc.add(inv.totalAmount) }
        }

        val sparkCash = months.map { month ->
            val start = month.withDayOfMonth(1)
            val end = month.withDayOfMonth(month.lengthOfMonth())
            allPayments.filter { it.paymentDate in start..end }
                .fold(BigDecimal.ZERO) { acc, p -> acc.add(p.functionalAmount) }
        }

        // Expense sparkline from ledger (one query per month — bounded to 12)
        val sparkExp = if (expenseIds.isEmpty()) List(12) { BigDecimal.ZERO } else
            months.map { month ->
                val start = month.withDayOfMonth(1)
                val end = month.withDayOfMonth(month.lengthOfMonth())
                ledgerRepository.sumDebitsByAccountIdsAndRange(expenseIds, start, end)
                    .subtract(ledgerRepository.sumCreditsByAccountIdsAndRange(expenseIds, start, end))
                    .max(BigDecimal.ZERO)
            }

        // AR sparkline — use revenue as proxy (new AR created per month)
        val sparkAr = sparkRev

        return Sparklines(labels, sparkRev, sparkExp, sparkAr, sparkCash)
    }
}

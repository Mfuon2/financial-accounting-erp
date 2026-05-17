package com.qesuite.accounting.approvals.service

import com.qesuite.accounting.approvals.dto.PendingApprovalItem
import com.qesuite.accounting.invoicing.domain.InvoiceStatus
import com.qesuite.accounting.invoicing.repository.InvoiceRepository
import com.qesuite.accounting.invoicing.service.InvoiceService
import com.qesuite.accounting.journal.domain.JournalEntryStatus
import com.qesuite.accounting.journal.repository.JournalEntryRepository
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.users.repository.UserRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Service
class GlobalApprovalService(
    private val journalRepository: JournalEntryRepository,
    private val invoiceRepository: InvoiceRepository,
    private val journalService: JournalService,
    private val invoiceService: InvoiceService,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun listPending(entityId: UUID): List<PendingApprovalItem> {
        val pendingJournals = journalRepository.findByEntityIdAndStatus(entityId, JournalEntryStatus.PENDING_APPROVAL)
        val draftInvoices = invoiceRepository.findByEntityIdAndStatus(entityId, InvoiceStatus.DRAFT, Pageable.unpaged()).content

        // Batch-load user names for the submittedBy column
        val userIds = (pendingJournals.mapNotNull { it.createdBy } + draftInvoices.mapNotNull { it.createdBy }).toSet()
        val userMap = userRepository.findAllById(userIds).associateBy { it.id }
        fun userName(id: UUID?) = id?.let { userMap[it]?.fullName } ?: "System"

        val tsFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withLocale(java.util.Locale.ENGLISH)

        val journalItems = pendingJournals.map { je ->
            val totalAmount = je.lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.debitAmount) }
            PendingApprovalItem(
                id = je.id,
                type = "Journal Entry",
                ref = "JE-${je.id.toString().takeLast(8).uppercase()}",
                title = je.description ?: "Journal Entry",
                amount = totalAmount,
                currency = je.lines.firstOrNull()?.currencyCode ?: "KES",
                submittedBy = userName(je.createdBy),
                waitingFor = "SENIOR_ACCOUNTANT",
                submittedAt = je.createdAt.atZone(ZoneOffset.UTC).format(tsFmt),
                entityId = je.entityId
            )
        }

        val invoiceItems = draftInvoices.map { inv ->
            PendingApprovalItem(
                id = inv.id,
                type = "Invoice",
                ref = inv.invoiceNumber,
                title = "Invoice ${inv.invoiceNumber}",
                amount = inv.totalAmount,
                currency = inv.currencyCode,
                submittedBy = userName(inv.createdBy),
                waitingFor = "CONTROLLER_CFO",
                submittedAt = inv.createdAt.atZone(ZoneOffset.UTC).format(tsFmt),
                entityId = inv.entityId
            )
        }

        return (journalItems + invoiceItems).sortedByDescending { it.submittedAt }
    }

    @Transactional
    fun approve(id: UUID, type: String) {
        when (type.uppercase()) {
            "JOURNAL_ENTRY" -> journalService.postEntry(id)
            "INVOICE"       -> invoiceService.approve(id)
            else -> throw IllegalArgumentException("Unknown approval type: $type")
        }
    }

    @Transactional
    fun reject(id: UUID, type: String, reason: String) {
        val r = reason.ifBlank { "Rejected via global approvals queue" }
        when (type.uppercase()) {
            "JOURNAL_ENTRY" -> journalService.rejectEntry(id, r)
            "INVOICE"       -> invoiceService.void(id, r)
            else -> throw IllegalArgumentException("Unknown approval type: $type")
        }
    }
}

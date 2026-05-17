package com.qesuite.accounting.payables.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "bills")
class Bill(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false, updatable = false)
    val entityId: UUID,

    @Column(name = "period_id")
    var periodId: UUID? = null,

    @Column(name = "bill_number", nullable = false, length = 50)
    val billNumber: String,

    @Column(name = "supplier_id")
    val supplierId: UUID? = null,

    @Column(name = "supplier_name", nullable = false, length = 255)
    var supplierName: String,

    @Column(name = "bill_date", nullable = false)
    var billDate: LocalDate,

    @Column(name = "due_date")
    var dueDate: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: BillStatus = BillStatus.DRAFT,

    @Column(nullable = false, precision = 19, scale = 6)
    var subtotal: BigDecimal = BigDecimal.ZERO,

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 6)
    var taxAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 6)
    var totalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 6)
    var paidAmount: BigDecimal = BigDecimal.ZERO,

    @Column(name = "currency_code", nullable = false, length = 3)
    val currencyCode: String = "KES",

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 6)
    var exchangeRate: BigDecimal = BigDecimal.ONE,

    @Column(name = "functional_amount", nullable = false, precision = 19, scale = 6)
    var functionalAmount: BigDecimal = BigDecimal.ZERO,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    @Column(name = "journal_entry_id")
    var journalEntryId: UUID? = null,

    @Column(name = "is_debit_note", nullable = false)
    var isDebitNote: Boolean = false,

    @Column(name = "original_bill_id")
    var originalBillId: UUID? = null,

    @Column(name = "source_document_id")
    var sourceDocumentId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "modified_at", nullable = false)
    var modifiedAt: Instant = Instant.now(),

    @Column(name = "created_by")
    val createdBy: UUID? = null,

    @Column(name = "modified_by")
    var modifiedBy: UUID? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @OneToMany(mappedBy = "bill", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    val items: MutableList<BillItem> = mutableListOf(),
) {
    val outstandingAmount: BigDecimal get() = totalAmount.subtract(paidAmount)
    val isOverdue: Boolean get() = dueDate != null && dueDate!!.isBefore(LocalDate.now()) && status != BillStatus.PAID && status != BillStatus.VOID
}

@Entity
@Table(name = "bill_items")
class BillItem(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id", nullable = false)
    var bill: Bill? = null,

    @Column(nullable = false, length = 500)
    var description: String,

    @Column(nullable = false, precision = 19, scale = 6)
    var quantity: BigDecimal = BigDecimal.ONE,

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 6)
    var unitPrice: BigDecimal,

    @Column(name = "tax_code", length = 20)
    var taxCode: String? = null,

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    var taxRate: BigDecimal = BigDecimal.ZERO,

    @Column(name = "line_total", nullable = false, precision = 19, scale = 6)
    var lineTotal: BigDecimal,

    @Column(name = "account_code", length = 20)
    var accountCode: String? = null,
)

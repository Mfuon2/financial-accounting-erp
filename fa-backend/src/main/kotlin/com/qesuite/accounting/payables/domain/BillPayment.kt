package com.qesuite.accounting.payables.domain

import com.qesuite.accounting.payments.domain.PaymentMethod
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "bill_payments")
class BillPayment(
    @Id
    @Column(nullable = false, updatable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false, updatable = false)
    val entityId: UUID,

    @Column(name = "bill_id", nullable = false, updatable = false)
    val billId: UUID,

    @Column(name = "payment_date", nullable = false)
    val paymentDate: LocalDate,

    @Column(nullable = false, precision = 19, scale = 6)
    val amount: BigDecimal,

    @Column(name = "currency_code", nullable = false, length = 3)
    val currencyCode: String = "KES",

    @Column(length = 100)
    val reference: String? = null,

    @Column(columnDefinition = "TEXT")
    val notes: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    val paymentMethod: PaymentMethod? = null,

    @Column(name = "cash_account_id")
    val cashAccountId: UUID? = null,

    @Column(name = "payment_run_id")
    val paymentRunId: UUID? = null,

    @Column(name = "journal_entry_id")
    var journalEntryId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by")
    val createdBy: UUID? = null,
)

package com.qesuite.accounting.source.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.util.*
import java.math.BigDecimal

@Entity
@Table(name = "source_documents")
class SourceDocument(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: SourceDocumentType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SourceDocumentStatus = SourceDocumentStatus.DRAFT,

    @Column(nullable = false)
    var docDate: LocalDate,

    @Column(nullable = false)
    var referenceNumber: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column
    var amount: BigDecimal? = null,

    @Column
    var currencyCode: String? = "USD",

    entityId: UUID,
    periodId: UUID? = null
) : BaseFinancialEntity(entityId = entityId, periodId = periodId)

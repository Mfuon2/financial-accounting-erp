package com.qesuite.accounting.party.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.util.UUID

/**
 * §14.3 — Customer Master Data
 * Represents a customer in the revenue cycle. Links to invoices and accounts receivable.
 */
@Entity
@Table(name = "customers")
class Customer(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,
    periodId: UUID? = null,

    @Column(nullable = false, length = 50)
    @Schema(example = "CUST-00042", description = "Unique customer code within entity")
    val customerCode: String,

    @Column(nullable = false, length = 255)
    @Schema(example = "Acme Corporation", description = "Customer's legal name")
    val name: String,

    @Column(nullable = true, length = 50)
    @Schema(example = "A001234567A", description = "Tax identification number (e.g., KRA PIN)")
    val taxNumber: String? = null,

    @Column(nullable = true, length = 255)
    @Schema(example = "accounts@acme.com", description = "Primary email address")
    var email: String? = null,

    @Column(nullable = true, length = 20)
    @Schema(example = "+254712345678", description = "Primary phone number")
    var phone: String? = null,

    @Column(nullable = false, precision = 20, scale = 6)
    @Schema(example = "50000.000000", description = "Maximum credit line")
    var creditLimit: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = true, length = 50)
    @Schema(example = "NET_30", description = "Default payment terms (e.g., NET_30, NET_60, DUE_ON_RECEIPT)")
    var paymentTerms: String? = null,

    @Column(nullable = true)
    @Schema(description = "Reference to the customer's default AR sub-account in COA")
    val defaultArAccountId: UUID? = null

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = periodId) {

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
}

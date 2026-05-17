package com.qesuite.accounting.party.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.util.UUID

/**
 * §14.3 — Supplier Master Data (for future use in Accounts Payable)
 * Represents a supplier in the procurement cycle. Links to purchase invoices and accounts payable.
 */
@Entity
@Table(name = "suppliers")
class Supplier(
    id: UUID = UUID.randomUUID(),
    entityId: UUID,
    periodId: UUID? = null,

    @Column(nullable = false, length = 50)
    @Schema(example = "SUPP-00001", description = "Unique supplier code within entity")
    val supplierCode: String,

    @Column(nullable = false, length = 255)
    @Schema(example = "Widget Manufacturing Ltd", description = "Supplier's legal name")
    val name: String,

    @Column(nullable = true, length = 50)
    @Schema(example = "P001234567B", description = "Tax identification number")
    val taxNumber: String? = null,

    @Column(nullable = true, length = 255)
    @Schema(example = "orders@widgets.com", description = "Primary email address")
    var email: String? = null,

    @Column(nullable = true, length = 20)
    @Schema(example = "+254712345678", description = "Primary phone number")
    var phone: String? = null,

    @Column(nullable = true, length = 50)
    @Schema(example = "NET_30", description = "Default payment terms")
    var paymentTerms: String? = null,

    @Column(nullable = true)
    @Schema(description = "Reference to the supplier's default AP sub-account in COA")
    val defaultApAccountId: UUID? = null

) : BaseFinancialEntity(id = id, entityId = entityId, periodId = periodId) {

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
}

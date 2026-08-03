package com.qesuite.accounting.shared.categories.domain

import com.qesuite.accounting.shared.domain.BaseFinancialEntity
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

/**
 * A single entity-owned, dynamically managed category value — e.g. one payment term
 * ("Net 30") or one payment method ("M-Pesa"). See [CategoryType] for the generic-system
 * rationale.
 *
 * Deactivation follows the existing party (Customer/Supplier) soft-delete convention via
 * [BaseFinancialEntity]'s `isActive`/`deactivatedAt`/`deactivatedBy`/`deactivationReason` —
 * a category referenced by historical documents is never hard-deleted.
 */
@Entity
@Table(
    name = "categories",
    uniqueConstraints = [UniqueConstraint(columnNames = ["entity_id", "category_type", "code"])],
)
@Schema(description = "Entity-owned, dynamically managed category value (CLAUDE.md §2).")
class Category(
    entityId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "category_type", length = 30, nullable = false, updatable = false)
    val categoryType: CategoryType,

    @Column(name = "code", length = 40, nullable = false, updatable = false)
    @Schema(example = "NET_30", description = "Stable machine value stored on the referencing record — immutable once created so historical documents keep resolving correctly.")
    var code: String,

    @Column(name = "label", length = 100, nullable = false)
    @Schema(example = "Net 30", description = "Human-readable display label — editable without affecting stored references.")
    var label: String,

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0,

) : BaseFinancialEntity(entityId = entityId)

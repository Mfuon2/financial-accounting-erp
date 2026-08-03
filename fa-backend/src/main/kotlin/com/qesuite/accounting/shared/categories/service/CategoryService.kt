package com.qesuite.accounting.shared.categories.service

import com.qesuite.accounting.shared.audit.annotation.AuditResourceId
import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.domain.AuditAction
import com.qesuite.accounting.shared.categories.domain.Category
import com.qesuite.accounting.shared.categories.domain.CategoryType
import com.qesuite.accounting.shared.categories.repository.CategoryRepository
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class CategoryDto(
    val id: UUID,
    val entityId: UUID,
    val categoryType: CategoryType,
    val code: String,
    val label: String,
    val sortOrder: Int,
    val isActive: Boolean,
) {
    companion object {
        fun from(c: Category) = CategoryDto(
            id = c.id,
            entityId = c.entityId,
            categoryType = c.categoryType,
            code = c.code,
            label = c.label,
            sortOrder = c.sortOrder,
            isActive = c.isActive,
        )
    }
}

@Service
@Transactional
class CategoryService(
    private val categoryRepository: CategoryRepository,
) {

    /**
     * Returns every category value for the (entityId, categoryType) pair, seeding
     * [CategoryType.defaultSeed] on first access if the entity has none yet for this type.
     *
     * This lazy bootstrap is what makes both pre-existing entities (backfilled by the Flyway
     * migration too, belt-and-suspenders) and any entity created after this feature shipped
     * (there is no "on entity create" hook in this codebase today — COA templates are applied
     * the same explicit-not-automatic way) resolve to the exact same codes the old hard-coded
     * frontend arrays used, so no existing Customer/Supplier/Bill/Payment/Invoice reference goes
     * stale.
     */
    fun listByType(entityId: UUID, categoryType: CategoryType, activeOnly: Boolean = false): List<CategoryDto> {
        seedIfEmpty(entityId, categoryType)
        val rows = if (activeOnly) {
            categoryRepository.findByEntityIdAndCategoryTypeAndIsActiveTrueOrderBySortOrderAscLabelAsc(entityId, categoryType)
        } else {
            categoryRepository.findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(entityId, categoryType)
        }
        return rows.map { CategoryDto.from(it) }
    }

    private fun seedIfEmpty(entityId: UUID, categoryType: CategoryType) {
        if (categoryRepository.countByEntityIdAndCategoryType(entityId, categoryType) > 0L) return
        val seeded = categoryType.defaultSeed().mapIndexed { index, (code, label) ->
            Category(entityId = entityId, categoryType = categoryType, code = code, label = label, sortOrder = index)
        }
        categoryRepository.saveAll(seeded)
    }

    @Auditable(action = AuditAction.CREATE, resourceType = "CATEGORY")
    fun create(entityId: UUID, categoryType: CategoryType, code: String, label: String, sortOrder: Int?): CategoryDto {
        val normalizedCode = code.trim().uppercase().replace(Regex("\\s+"), "_")
        if (normalizedCode.isBlank()) {
            throw ValidationException(errorCode = "INVALID_CODE", message = "Category code must not be blank.")
        }
        if (label.isBlank()) {
            throw ValidationException(errorCode = "INVALID_LABEL", message = "Category label must not be blank.")
        }
        // Make sure the bootstrap defaults exist first so a brand-new entity's first custom
        // addition sorts after them, not before.
        seedIfEmpty(entityId, categoryType)
        if (categoryRepository.existsByEntityIdAndCategoryTypeAndCodeIgnoreCase(entityId, categoryType, normalizedCode)) {
            throw ConflictException(
                errorCode = "DUPLICATE_CATEGORY_CODE",
                message = "A $categoryType category with code '$normalizedCode' already exists for this entity.",
                context = mapOf("entity_id" to entityId, "category_type" to categoryType.name, "code" to normalizedCode),
            )
        }
        val nextSort = sortOrder ?: run {
            val existing = categoryRepository.findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(entityId, categoryType)
            (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        }
        val saved = categoryRepository.save(
            Category(entityId = entityId, categoryType = categoryType, code = normalizedCode, label = label.trim(), sortOrder = nextSort)
        )
        return CategoryDto.from(saved)
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "CATEGORY")
    fun update(@AuditResourceId id: UUID, label: String?, sortOrder: Int?): CategoryDto {
        val category = findEntityById(id)
        if (label != null) {
            if (label.isBlank()) throw ValidationException(errorCode = "INVALID_LABEL", message = "Category label must not be blank.")
            category.label = label.trim()
        }
        if (sortOrder != null) category.sortOrder = sortOrder
        return CategoryDto.from(categoryRepository.save(category))
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "CATEGORY")
    fun deactivate(@AuditResourceId id: UUID, reason: String, deactivatedBy: UUID?): CategoryDto {
        val category = findEntityById(id)
        if (!category.isActive) {
            throw ConflictException(
                errorCode = "CATEGORY_ALREADY_DEACTIVATED",
                message = "Category $id is already deactivated.",
                context = mapOf("category_id" to id),
            )
        }
        category.isActive = false
        category.deactivatedAt = Instant.now()
        category.deactivatedBy = deactivatedBy
        category.deactivationReason = reason
        return CategoryDto.from(categoryRepository.save(category))
    }

    @Auditable(action = AuditAction.UPDATE, resourceType = "CATEGORY")
    fun activate(@AuditResourceId id: UUID): CategoryDto {
        val category = findEntityById(id)
        if (category.isActive) return CategoryDto.from(category)
        category.isActive = true
        category.deactivatedAt = null
        category.deactivatedBy = null
        category.deactivationReason = null
        return CategoryDto.from(categoryRepository.save(category))
    }

    /** Used by the controller to look up the owning entityId before applying the ownership guard. */
    @Transactional(readOnly = true)
    fun findEntityById(id: UUID): Category = categoryRepository.findById(id)
        .orElseThrow { ResourceNotFoundException(errorCode = "CATEGORY_NOT_FOUND", resourceId = id, resourceType = "Category") }
}

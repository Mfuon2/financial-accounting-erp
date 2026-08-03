package com.qesuite.accounting.shared.categories.repository

import com.qesuite.accounting.shared.categories.domain.Category
import com.qesuite.accounting.shared.categories.domain.CategoryType
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryRepository : JpaRepository<Category, UUID> {
    fun findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(
        entityId: UUID,
        categoryType: CategoryType,
    ): List<Category>

    fun findByEntityIdAndCategoryTypeAndIsActiveTrueOrderBySortOrderAscLabelAsc(
        entityId: UUID,
        categoryType: CategoryType,
    ): List<Category>

    fun existsByEntityIdAndCategoryTypeAndCodeIgnoreCase(
        entityId: UUID,
        categoryType: CategoryType,
        code: String,
    ): Boolean

    fun countByEntityIdAndCategoryType(entityId: UUID, categoryType: CategoryType): Long
}

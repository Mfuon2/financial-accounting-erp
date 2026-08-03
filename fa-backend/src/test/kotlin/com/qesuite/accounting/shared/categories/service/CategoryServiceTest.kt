package com.qesuite.accounting.shared.categories.service

import com.qesuite.accounting.shared.categories.domain.Category
import com.qesuite.accounting.shared.categories.domain.CategoryType
import com.qesuite.accounting.shared.categories.repository.CategoryRepository
import com.qesuite.accounting.shared.exceptions.ConflictException
import com.qesuite.accounting.shared.exceptions.ResourceNotFoundException
import com.qesuite.accounting.shared.exceptions.ValidationException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.UUID

class CategoryServiceTest {

    private val categoryRepository = mockk<CategoryRepository>()
    private val categoryService = CategoryService(categoryRepository)

    private val entityId = UUID.randomUUID()
    private val otherEntityId = UUID.randomUUID()

    // ── Seeding ──────────────────────────────────────────────────────────────────

    @Test
    fun `listByType seeds the built-in defaults when the entity has none yet for this type`() {
        every { categoryRepository.countByEntityIdAndCategoryType(entityId, CategoryType.PAYMENT_TERM) } returns 0L
        val savedSlot = slot<List<Category>>()
        every { categoryRepository.saveAll(capture(savedSlot)) } returns emptyList()
        every {
            categoryRepository.findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(entityId, CategoryType.PAYMENT_TERM)
        } returns emptyList()

        categoryService.listByType(entityId, CategoryType.PAYMENT_TERM)

        // Then — the exact 6 codes the hard-coded Suppliers.vue/Customers.vue array used,
        // in the same order, so BillService.parsePaymentTermsDays keeps resolving them.
        val seeded = savedSlot.captured
        assertEquals(6, seeded.size)
        assertEquals(
            listOf("DUE_ON_RECEIPT", "NET_15", "NET_30", "NET_45", "NET_60", "NET_90"),
            seeded.map { it.code },
        )
        assertTrue(seeded.all { it.entityId == entityId && it.categoryType == CategoryType.PAYMENT_TERM })
    }

    @Test
    fun `listByType does not reseed when the entity already has rows for this type`() {
        every { categoryRepository.countByEntityIdAndCategoryType(entityId, CategoryType.PAYMENT_METHOD) } returns 5L
        every {
            categoryRepository.findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(entityId, CategoryType.PAYMENT_METHOD)
        } returns emptyList()

        categoryService.listByType(entityId, CategoryType.PAYMENT_METHOD)

        verify(exactly = 0) { categoryRepository.saveAll(any<List<Category>>()) }
    }

    @Test
    fun `listByType is scoped strictly to the requested entity — no cross-entity leakage`() {
        val mine = Category(entityId = entityId, categoryType = CategoryType.PAYMENT_METHOD, code = "CASH", label = "Cash")
        every { categoryRepository.countByEntityIdAndCategoryType(entityId, CategoryType.PAYMENT_METHOD) } returns 1L
        every {
            categoryRepository.findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(entityId, CategoryType.PAYMENT_METHOD)
        } returns listOf(mine)
        // A different entity's repository call is stubbed to return something else entirely —
        // proves the service passes the caller's own entityId straight through and never mixes it up.
        every { categoryRepository.countByEntityIdAndCategoryType(otherEntityId, CategoryType.PAYMENT_METHOD) } returns 1L
        every {
            categoryRepository.findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(otherEntityId, CategoryType.PAYMENT_METHOD)
        } returns listOf(Category(entityId = otherEntityId, categoryType = CategoryType.PAYMENT_METHOD, code = "MPESA", label = "M-Pesa"))

        val result = categoryService.listByType(entityId, CategoryType.PAYMENT_METHOD)

        assertEquals(1, result.size)
        assertEquals("CASH", result[0].code)
        assertTrue(result.all { it.entityId == entityId })
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    @Test
    fun `create rejects a duplicate code within the same entity and type with a 409`() {
        every { categoryRepository.countByEntityIdAndCategoryType(entityId, CategoryType.PAYMENT_TERM) } returns 6L
        every {
            categoryRepository.existsByEntityIdAndCategoryTypeAndCodeIgnoreCase(entityId, CategoryType.PAYMENT_TERM, "NET_30")
        } returns true

        val ex = assertThrows<ConflictException> {
            categoryService.create(entityId, CategoryType.PAYMENT_TERM, "net 30", "Net 30 (dup)", null)
        }
        assertEquals("DUPLICATE_CATEGORY_CODE", ex.errorCode)
        assertEquals(409, ex.httpStatus)
    }

    @Test
    fun `create normalizes the code and assigns the next sort order`() {
        every { categoryRepository.countByEntityIdAndCategoryType(entityId, CategoryType.PAYMENT_TERM) } returns 6L
        every {
            categoryRepository.existsByEntityIdAndCategoryTypeAndCodeIgnoreCase(entityId, CategoryType.PAYMENT_TERM, "NET_120")
        } returns false
        every {
            categoryRepository.findByEntityIdAndCategoryTypeOrderBySortOrderAscLabelAsc(entityId, CategoryType.PAYMENT_TERM)
        } returns listOf(
            Category(entityId = entityId, categoryType = CategoryType.PAYMENT_TERM, code = "NET_90", label = "Net 90", sortOrder = 5),
        )
        val savedSlot = slot<Category>()
        every { categoryRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val created = categoryService.create(entityId, CategoryType.PAYMENT_TERM, "net 120", "Net 120", null)

        assertEquals("NET_120", created.code)
        assertEquals(6, created.sortOrder)
    }

    @Test
    fun `create rejects a blank label`() {
        assertThrows<ValidationException> {
            categoryService.create(entityId, CategoryType.PAYMENT_TERM, "NET_30", "   ", null)
        }
    }

    // ── Update / deactivate / activate ────────────────────────────────────────────

    @Test
    fun `update changes label and sort order without touching the immutable code`() {
        val existing = Category(entityId = entityId, categoryType = CategoryType.PAYMENT_TERM, code = "NET_30", label = "Net 30", sortOrder = 2)
        every { categoryRepository.findById(existing.id) } returns Optional.of(existing)
        every { categoryRepository.save(existing) } returns existing

        val updated = categoryService.update(existing.id, "Net 30 Days", 9)

        assertEquals("Net 30 Days", updated.label)
        assertEquals(9, updated.sortOrder)
        assertEquals("NET_30", updated.code)
    }

    @Test
    fun `deactivate sets isActive false and records the reason`() {
        val existing = Category(entityId = entityId, categoryType = CategoryType.PAYMENT_METHOD, code = "CASH", label = "Cash")
        val userId = UUID.randomUUID()
        every { categoryRepository.findById(existing.id) } returns Optional.of(existing)
        every { categoryRepository.save(existing) } returns existing

        val result = categoryService.deactivate(existing.id, "Cash payments no longer accepted", userId)

        assertFalse(result.isActive)
        assertEquals("Cash payments no longer accepted", existing.deactivationReason)
        assertEquals(userId, existing.deactivatedBy)
        assertNotNull(existing.deactivatedAt)
    }

    @Test
    fun `deactivate rejects an already-deactivated category`() {
        val existing = Category(entityId = entityId, categoryType = CategoryType.PAYMENT_METHOD, code = "CASH", label = "Cash")
        existing.isActive = false
        every { categoryRepository.findById(existing.id) } returns Optional.of(existing)

        val ex = assertThrows<ConflictException> {
            categoryService.deactivate(existing.id, "already gone", null)
        }
        assertEquals("CATEGORY_ALREADY_DEACTIVATED", ex.errorCode)
    }

    @Test
    fun `activate reverses a deactivation`() {
        val existing = Category(entityId = entityId, categoryType = CategoryType.PAYMENT_METHOD, code = "CASH", label = "Cash")
        existing.isActive = false
        existing.deactivationReason = "temp"
        every { categoryRepository.findById(existing.id) } returns Optional.of(existing)
        every { categoryRepository.save(existing) } returns existing

        val result = categoryService.activate(existing.id)

        assertTrue(result.isActive)
        assertNull(existing.deactivationReason)
    }

    @Test
    fun `findEntityById throws 404 for an unknown id`() {
        val id = UUID.randomUUID()
        every { categoryRepository.findById(id) } returns Optional.empty()

        assertThrows<ResourceNotFoundException> { categoryService.findEntityById(id) }
    }
}

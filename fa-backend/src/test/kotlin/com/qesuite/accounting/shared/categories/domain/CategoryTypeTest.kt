package com.qesuite.accounting.shared.categories.domain

import com.qesuite.accounting.payments.domain.PaymentMethod
import com.qesuite.accounting.source.domain.SourceDocumentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * §2 — PAYMENT_METHOD and DOCUMENT_TYPE are curated relabelings of fixed backend enums, not
 * freely-extensible lists: their `defaultSeed()` codes deserialize directly into
 * [PaymentMethod]/[SourceDocumentType] on the actual request DTOs (Payment/BillPayment,
 * SourceDocument). If a future change adds/renames an enum constant without updating the
 * matching `defaultSeed()`, the two silently drift — this test is the guard.
 */
class CategoryTypeTest {

    @Test
    fun `DOCUMENT_TYPE defaultSeed codes match SourceDocumentType exactly, same order`() {
        val seedCodes = CategoryType.DOCUMENT_TYPE.defaultSeed().map { it.first }
        val enumNames = SourceDocumentType.entries.map { it.name }
        assertEquals(enumNames, seedCodes)
    }

    @Test
    fun `PAYMENT_METHOD defaultSeed codes are all valid PaymentMethod enum constants`() {
        val seedCodes = CategoryType.PAYMENT_METHOD.defaultSeed().map { it.first }
        val enumNames = PaymentMethod.entries.map { it.name }.toSet()
        assertEquals(seedCodes, seedCodes.filter { it in enumNames })
    }
}

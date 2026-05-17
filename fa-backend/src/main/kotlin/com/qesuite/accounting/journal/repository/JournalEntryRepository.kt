package com.qesuite.accounting.journal.repository

import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.journal.domain.JournalEntryStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface JournalEntryRepository : JpaRepository<JournalEntry, UUID> {
    @EntityGraph(attributePaths = ["lines"])
    fun findByEntityId(entityId: UUID): List<JournalEntry>

    @EntityGraph(attributePaths = ["lines"])
    fun findByEntityIdAndStatus(entityId: UUID, status: JournalEntryStatus): List<JournalEntry>

    fun existsByEntityIdAndReference(entityId: UUID, reference: String): Boolean
}

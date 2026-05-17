package com.qesuite.accounting.source.repository

import com.qesuite.accounting.source.domain.SourceDocument
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SourceDocumentRepository : JpaRepository<SourceDocument, UUID> {
    fun findByEntityId(entityId: UUID): List<SourceDocument>
}

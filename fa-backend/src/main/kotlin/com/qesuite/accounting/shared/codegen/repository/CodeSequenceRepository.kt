package com.qesuite.accounting.shared.codegen.repository

import com.qesuite.accounting.shared.codegen.domain.CodeSequence
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CodeSequenceRepository : JpaRepository<CodeSequence, UUID> {

    // ── Year-scoped (transactional codes: INV, JE, BILL, …) ──────────────────

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CodeSequence s WHERE s.entityId = :entityId AND s.prefix = :prefix AND s.year = :year")
    fun findForUpdateWithYear(
        @Param("entityId") entityId: UUID,
        @Param("prefix")   prefix:   String,
        @Param("year")     year:     Int
    ): CodeSequence?

    @Query("SELECT s FROM CodeSequence s WHERE s.entityId = :entityId AND s.prefix = :prefix AND s.year = :year")
    fun findByEntityIdAndPrefixAndYear(
        @Param("entityId") entityId: UUID,
        @Param("prefix")   prefix:   String,
        @Param("year")     year:     Int
    ): CodeSequence?

    // ── Non-year-scoped (master data: CU, SUPP, FA, …) ───────────────────────

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM CodeSequence s WHERE s.entityId = :entityId AND s.prefix = :prefix AND s.year IS NULL")
    fun findForUpdateNoYear(
        @Param("entityId") entityId: UUID,
        @Param("prefix")   prefix:   String
    ): CodeSequence?

    @Query("SELECT s FROM CodeSequence s WHERE s.entityId = :entityId AND s.prefix = :prefix AND s.year IS NULL")
    fun findByEntityIdAndPrefixNoYear(
        @Param("entityId") entityId: UUID,
        @Param("prefix")   prefix:   String
    ): CodeSequence?
}

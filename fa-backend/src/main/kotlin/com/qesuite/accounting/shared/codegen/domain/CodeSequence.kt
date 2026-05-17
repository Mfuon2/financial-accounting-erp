package com.qesuite.accounting.shared.codegen.domain

import jakarta.persistence.*
import java.util.UUID

/**
 * Tracks the last-used sequence number for each (entity, prefix, year) combination.
 * Intentionally NOT extending BaseFinancialEntity — this is an operational sequence
 * table, not a financial record. No soft-delete, no auditing columns.
 */
@Entity
@Table(
    name = "code_sequences",
    uniqueConstraints = [UniqueConstraint(
        name  = "uq_code_sequences",
        columnNames = ["entity_id", "prefix", "year"]
    )]
)
class CodeSequence(
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false, updatable = false)
    val entityId: UUID,

    @Column(name = "prefix", nullable = false, updatable = false, length = 20)
    val prefix: String,

    // NULL for master-data codes; calendar year (e.g. 2026) for transactional codes
    @Column(name = "year", nullable = true, updatable = false)
    val year: Int? = null,

    @Column(name = "last_seq", nullable = false)
    var lastSeq: Int = 0
)

package com.qesuite.accounting.shared.codegen.domain

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(
    name = "entity_number_configs",
    uniqueConstraints = [UniqueConstraint(columnNames = ["entity_id", "module_key"])]
)
class EntityNumberConfig(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,

    @Column(name = "module_key", nullable = false, length = 30)
    val moduleKey: String,

    @Column(name = "prefix", nullable = false, length = 20)
    var prefix: String,

    /** Custom format template using {PREFIX}, {YYYY}, {YY}, {0000} tokens. Null = system default. */
    @Column(name = "custom_format", length = 60)
    var customFormat: String? = null,
)

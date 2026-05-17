package com.qesuite.accounting.shared.audit.annotation

import com.qesuite.accounting.shared.audit.domain.AuditAction

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Auditable(
    val action: AuditAction,
    val resourceType: String
)

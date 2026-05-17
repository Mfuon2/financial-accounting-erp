package com.qesuite.accounting.shared.audit.annotation

/**
 * Marks a parameter as the Entity/Tenant ID for forensic auditing.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditEntityId

/**
 * Marks a parameter as the Primary Resource ID for forensic auditing.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditResourceId

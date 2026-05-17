package com.qesuite.accounting.shared.audit.aop

import com.qesuite.accounting.shared.audit.annotation.Auditable
import com.qesuite.accounting.shared.audit.service.AuditService
import com.qesuite.accounting.shared.security.SecurityAuditorAware
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.AfterReturning
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component
import java.util.*

@Aspect
@Component
class AuditAspect(
    private val auditService: AuditService,
    private val securityAuditorAware: SecurityAuditorAware
) {

    companion object {
        /**
         * System actor UUID — used when audit operations are triggered by automated
         * processes (batch jobs, M-Pesa callbacks, system-initiated postings) that
         * run outside a user session. Distinct from the zero UUID — deliberately
         * named so it is traceable in audit queries.
         */
        val SYSTEM_ACTOR_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    }

    private val log = org.slf4j.LoggerFactory.getLogger(AuditAspect::class.java)

    @AfterReturning("@annotation(auditable)", returning = "result")
    fun auditMethod(joinPoint: JoinPoint, auditable: Auditable, result: Any?) {
        val signature = joinPoint.signature as org.aspectj.lang.reflect.MethodSignature
        val method = signature.method
        val parameterAnnotations = method.parameterAnnotations
        val args = joinPoint.args

        var entityId: UUID? = null
        var resourceId: UUID? = null

        for (i in args.indices) {
            val annotations = parameterAnnotations[i]
            if (annotations.any { it is com.qesuite.accounting.shared.audit.annotation.AuditEntityId }) {
                entityId = args[i] as? UUID
            }
            if (annotations.any { it is com.qesuite.accounting.shared.audit.annotation.AuditResourceId }) {
                resourceId = args[i] as? UUID
            }
        }

        // Fallback: extract entityId from a command/DTO argument that carries an entityId field
        if (entityId == null) {
            entityId = extractEntityIdFromArgs(args)
        }
        // Fallback: extract entityId from the returned entity itself
        if (entityId == null) {
            entityId = extractFieldFromObject(result, "entityId")
        }

        // Fallback for resourceId from the result if not found in params
        if (resourceId == null) {
            resourceId = extractResourceIdFromResult(result)
        }

        val userId = securityAuditorAware.currentAuditor.orElseGet {
            log.warn(
                "audit.aspect: no authenticated user in security context for {}.{} — recording as SYSTEM",
                joinPoint.signature.declaringTypeName, joinPoint.signature.name
            )
            SYSTEM_ACTOR_ID
        }

        auditService.log(
            entityId   = entityId   ?: run { log.warn("audit.aspect: no @AuditEntityId found, using SYSTEM_ACTOR_ID"); SYSTEM_ACTOR_ID },
            userId     = userId,
            action     = auditable.action,
            resourceType = auditable.resourceType,
            resourceId = resourceId ?: run { log.warn("audit.aspect: no @AuditResourceId found for {}, using SYSTEM_ACTOR_ID", auditable.resourceType); SYSTEM_ACTOR_ID },
            payloadAfter = sanitisePayload(result)
        )
    }

    private fun sanitisePayload(result: Any?): Any? {
        if (result == null) return null
        // For User objects, never serialise the password hash
        return try {
            val clazz = result.javaClass
            // Use reflection to check if this is a User-like entity with a passwordHash field
            val sensitiveFields = setOf("passwordHash", "password", "secret", "tokenHash", "keyHash")
            val fields = clazz.declaredFields.filter { it.name in sensitiveFields }
            if (fields.isEmpty()) return result
            // Convert to a safe map representation
            val map = mutableMapOf<String, Any?>()
            clazz.declaredFields.forEach { field ->
                field.isAccessible = true
                if (field.name !in sensitiveFields) {
                    map[field.name] = field.get(result)
                } else {
                    map[field.name] = "[REDACTED]"
                }
            }
            map
        } catch (e: Exception) {
            "[SERIALIZATION_ERROR: ${e.javaClass.simpleName}]"
        }
    }

    private fun extractEntityIdFromArgs(args: Array<Any?>): UUID? {
        for (arg in args) {
            val found = extractFieldFromObject(arg, "entityId")
            if (found != null) return found
        }
        return null
    }

    private fun extractFieldFromObject(obj: Any?, fieldName: String): UUID? {
        if (obj == null) return null
        return try {
            val getter = obj.javaClass.getMethod("get${fieldName.replaceFirstChar { it.uppercase() }}")
            getter.invoke(obj) as? UUID
        } catch (_: NoSuchMethodException) {
            try {
                val field = obj.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.get(obj) as? UUID
            } catch (_: Exception) { null }
        } catch (_: Exception) { null }
    }

    private fun extractResourceIdFromResult(result: Any?): UUID? {
        if (result is UUID) return result
        return try {
            val idField = result?.javaClass?.getMethod("getId")
            idField?.invoke(result) as? UUID
        } catch (e: Exception) {
            null
        }
    }
}

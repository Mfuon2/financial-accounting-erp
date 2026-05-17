package com.qesuite.accounting.ap.config

import com.qesuite.accounting.ap.domain.PeriodStatus
import com.qesuite.accounting.ap.service.PeriodService
import com.qesuite.accounting.shared.exceptions.BaseAccountingException
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * §5.3 — Period Lock Enforcement (AOP Interceptor).
 *
 * Methods annotated with [RequireOpenPeriod] receive a pre-invocation check that the
 * accounting period referenced by a parameter marked with [PeriodId] is in one of the
 * states permitted for posting: OPEN, ADJUSTING or CLOSING. If the period is in any
 * other state (FUTURE, CLOSED, REOPENED with restrictions) a [PeriodLockedException]
 * is raised before the target method runs.
 *
 * Usage:
 * ```
 * @RequireOpenPeriod
 * fun postEntry(@PeriodId periodId: UUID, command: JournalCommand) { ... }
 * ```
 *
 * §11.04 — Never post to a CLOSED period without elevated approval; that override
 * path is handled by a separate service that bypasses [RequireOpenPeriod] explicitly.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireOpenPeriod

/**
 * Marks a parameter as the accounting period identifier for [PeriodLockInterceptor].
 *
 * Either a `UUID` or a domain object exposing `getPeriodId(): UUID` may carry the
 * annotation; the interceptor reflects on the parameter to extract the value.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class PeriodId

@Aspect
@Component
class PeriodLockInterceptor(private val periodService: PeriodService) {

    private companion object {
        // §5.3 — States in which financial postings are permitted.
        val POSTING_PERMITTED: Set<PeriodStatus> =
            setOf(PeriodStatus.OPEN, PeriodStatus.ADJUSTING, PeriodStatus.CLOSING)
    }

    /**
     * §5.3 — Intercepts every call to a method annotated with [RequireOpenPeriod] and
     * validates the referenced period is in a posting-permitted state.
     *
     * Throws [PeriodLockedException] (HTTP 422 / `PERIOD_LOCKED`) if the period is
     * locked. Throws [PeriodIdResolutionException] (HTTP 500 / `PERIOD_ID_RESOLUTION_FAILED`)
     * if no `@PeriodId` parameter is present — this is a coding defect, not a runtime
     * business condition, so it surfaces to the developer via a 5xx response.
     */
    @Before("@annotation(com.qesuite.accounting.ap.config.RequireOpenPeriod)")
    fun checkPeriodOpen(joinPoint: JoinPoint) {
        val periodId = resolvePeriodId(joinPoint)
            ?: throw PeriodIdResolutionException(
                "Method ${joinPoint.signature.toShortString()} is annotated @RequireOpenPeriod " +
                    "but no @PeriodId parameter was found or resolved to a non-null UUID."
            )

        val period = periodService.findById(periodId)

        if (period.status !in POSTING_PERMITTED) {
            throw PeriodLockedException(
                periodId = periodId,
                currentStatus = period.status.name,
                message = "Period ${period.periodName} is ${period.status}. Posting is not permitted."
            )
        }
    }

    /**
     * Walks parameter annotations to locate `@PeriodId`. The parameter may be a `UUID`
     * directly, or any object exposing a `getPeriodId(): UUID` getter (e.g. a command
     * DTO carrying a `periodId` property).
     */
    private fun resolvePeriodId(joinPoint: JoinPoint): UUID? {
        val signature = joinPoint.signature as MethodSignature
        val parameterAnnotations = signature.method.parameterAnnotations
        val args = joinPoint.args

        for (i in args.indices) {
            val hasPeriodId = parameterAnnotations[i].any { it is PeriodId }
            if (!hasPeriodId) continue
            val arg = args[i] ?: continue
            return when (arg) {
                is UUID -> arg
                else -> extractPeriodIdFromObject(arg)
            }
        }
        return null
    }

    private fun extractPeriodIdFromObject(arg: Any): UUID? = try {
        // Kotlin properties expose JavaBean getters; getPeriodId() is the canonical name.
        val getter = arg.javaClass.getMethod("getPeriodId")
        getter.invoke(arg) as? UUID
    } catch (_: NoSuchMethodException) {
        null
    } catch (_: ReflectiveOperationException) {
        null
    }
}

/**
 * §6.6 — `PERIOD_LOCKED` (HTTP 422). Raised when a posting operation targets a period
 * not in OPEN / ADJUSTING / CLOSING state.
 */
class PeriodLockedException(
    errorCode: String = "PERIOD_LOCKED",
    periodId: UUID,
    currentStatus: String,
    message: String,
) : BaseAccountingException(
    errorCode,
    message,
    422,
    mapOf(
        "period_id" to periodId.toString(),
        "current_status" to currentStatus,
    ),
)

/**
 * §6.8 — `PERIOD_ID_RESOLUTION_FAILED` (HTTP 500). A developer-facing defect signal:
 * the interceptor was applied but no `@PeriodId` parameter was provided.
 */
class PeriodIdResolutionException(
    message: String,
) : BaseAccountingException(
    errorCode = "PERIOD_ID_RESOLUTION_FAILED",
    message = message,
    httpStatus = 500,
    context = null,
)

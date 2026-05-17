package com.qesuite.accounting.shared.idempotency.aspect

/**
 * §6.2, §11.06 — Marks a controller endpoint as requiring an `Idempotency-Key` HTTP
 * header. The companion [IdempotencyAspect] enforces presence + UUID format and
 * short-circuits duplicate calls via [com.qesuite.accounting.shared.idempotency.service.IdempotencyService].
 *
 * Example:
 * ```
 * @PostMapping("/invoices")
 * @RequireIdempotencyKey
 * fun create(@RequestBody body: CreateInvoiceCommand) { ... }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireIdempotencyKey

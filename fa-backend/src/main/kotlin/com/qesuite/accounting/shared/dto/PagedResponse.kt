package com.qesuite.accounting.shared.dto

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/**
 * §7.3 — Standard paged response envelope. Single source of truth used by every list
 * endpoint in the system. Modules MUST NOT redefine this type; use `Page.toPagedResponse`
 * to convert a Spring Data page to this envelope.
 */
@Schema(description = "Standard paged response (§7.3)")
data class PagedResponse<T>(
    val content: List<T>,
    @Schema(example = "0") val page: Int,
    @Schema(example = "50") val size: Int,
    @Schema(example = "237") val totalElements: Long,
    @Schema(example = "5") val totalPages: Int,
    @Schema(example = "true") val hasNext: Boolean,
    @Schema(example = "false") val hasPrevious: Boolean,
)

/**
 * §7.3 — Convert a Spring Data [Page] to a [PagedResponse], applying [mapFn] to each row.
 */
fun <T, R> Page<T>.toPagedResponse(mapFn: (T) -> R): PagedResponse<R> = PagedResponse(
    content = content.map(mapFn),
    page = number,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages,
    hasNext = hasNext(),
    hasPrevious = hasPrevious(),
)

package com.qesuite.accounting.invoicing.controller

import com.qesuite.accounting.invoicing.domain.Invoice
import com.qesuite.accounting.invoicing.service.InvoiceService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * §14.2 — Standalone read-only listing of credit notes.
 *
 * Credit notes are not a separate entity/table — they are `Invoice` rows with
 * `status = CREDIT_NOTE` (negative subtotal/tax/total, terminal state; see
 * [com.qesuite.accounting.invoicing.controller.InvoiceController.createCreditNote]).
 * This controller exposes them as a first-class `/api/v1/credit-notes` resource for the
 * dedicated Credit Notes screen, matching this codebase's `/api/v1/<resource>` naming
 * convention, without duplicating [InvoiceController]'s create/void/list logic.
 */
@RestController
@RequestMapping("/api/v1/credit-notes")
@Tag(
    name = "Module 14: Invoicing — Credit Notes",
    description = """
Read-only listing of credit notes for an entity.

Credit notes are issued via `POST /api/v1/invoices/{id}/credit-note` against a posted
invoice; this endpoint lists the resulting credit-note rows (Invoice rows with
`status = CREDIT_NOTE`) scoped to an entity, paginated the same way `GET /api/v1/invoices`
is.
"""
)
class CreditNoteController(
    private val invoiceService: InvoiceService
) {

    @GetMapping
    @Operation(
        summary = "List credit notes for an entity",
        description = "Returns a paginated list of credit notes (Invoice rows with status=CREDIT_NOTE) for the given entityId."
    )
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun list(
        @RequestParam @Parameter(description = "Tenant/entity UUID", required = true) entityId: UUID,
        @PageableDefault(size = 50) pageable: Pageable
    ): ApiResponse<PagedResponse<Invoice>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = invoiceService.findCreditNotesByEntity(entityId, pageable)
        return ApiResponse.success(page.toPagedResponse { it })
    }
}

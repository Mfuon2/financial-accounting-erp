package com.qesuite.accounting.banking.controller

import com.qesuite.accounting.banking.dto.BankReconciliationSummaryResponse
import com.qesuite.accounting.banking.dto.BankStatementImportResponse
import com.qesuite.accounting.banking.dto.CreateBankStatementImportCommand
import com.qesuite.accounting.banking.dto.IgnoreLineCommand
import com.qesuite.accounting.banking.dto.MatchCandidateResponse
import com.qesuite.accounting.banking.dto.MatchLineCommand
import com.qesuite.accounting.banking.service.BankStatementService
import com.qesuite.accounting.shared.dto.PagedResponse
import com.qesuite.accounting.shared.dto.toPagedResponse
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.RoleSets
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Project.md Domain 1 (Financial Operations) — Cash & Bank Management REST endpoints.
 * workplan.md Phase 1 item 2.
 *
 * Every id-scoped endpoint resolves the resource first and checks
 * [SecurityUtils.requireOwnEntity] against the resource's OWN `entityId` before acting — never a
 * client-supplied one, and never skipped for a "read-only" action. For the `/lines/{lineId}/...`
 * endpoints, `entityId` is read off the line's parent import (`BankStatementLine.entityId`, which
 * throws rather than silently defaulting if the parent is somehow missing — see its KDoc). This
 * codebase has twice found real IDOR gaps from an endpoint that skipped this exact check (see
 * MEMORY.md's Known Issues), so it is applied here without exception, matching
 * `BudgetController`'s reference pattern exactly.
 *
 * Role gates use [RoleSets]: list/get/reconciliation/suggestions = `ACCOUNTING_READ` (a formal
 * report / downstream artifact read, matches `TrialBalanceController`'s precedent); import =
 * `ACCOUNTING_OP` (matches `BudgetController.create`); match/auto-match/unmatch/ignore/unignore =
 * `ACCOUNTING_OP` (a financial-judgment action that isn't itself a final sign-off — matches
 * `BillController.recordPayment`'s precedent, per this module's assignment brief).
 *
 * No `@RequireIdempotencyKey` on import — a bank statement import never posts to the ledger, so
 * there is no double-posting risk (same reasoning as `BudgetController.create`); a DB-level
 * de-dupe constraint guards against an accidental duplicate re-import instead.
 */
@Tag(name = "Cash & Bank Management", description = "Bank statement import, GL matching, and reconciliation tie-out — Project.md Domain 1")
@RestController
@RequestMapping("/api/v1/bank-statements")
class BankStatementController(
    private val bankStatementService: BankStatementService,
) {

    @Operation(summary = "List bank statement imports for an entity (paginated)")
    @GetMapping
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun list(
        @RequestParam @Parameter(description = "Entity (tenant) ID", required = true) entityId: UUID,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<ApiResponse<PagedResponse<BankStatementImportResponse>>> {
        SecurityUtils.requireOwnEntity(entityId)
        val page = bankStatementService.findByEntity(entityId, pageable)
        return ResponseEntity.ok(ApiResponse.success(page.toPagedResponse { bankStatementService.toResponse(it) }))
    }

    @Operation(summary = "Get a bank statement import by ID, with its lines and match status")
    @GetMapping("/{id}")
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun getById(@PathVariable id: UUID): ResponseEntity<ApiResponse<BankStatementImportResponse>> {
        val import = bankStatementService.findImportById(id)
        SecurityUtils.requireOwnEntity(import.entityId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.toResponse(import)))
    }

    @Operation(summary = "Import a bank statement (header + lines) as a JSON array")
    @PostMapping
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun import(@Valid @RequestBody command: CreateBankStatementImportCommand): ResponseEntity<ApiResponse<BankStatementImportResponse>> {
        SecurityUtils.requireOwnEntity(command.entityId)
        val import = bankStatementService.importStatement(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(bankStatementService.toResponse(import)))
    }

    @Operation(
        summary = "Reconciliation summary/tie-out report",
        description = "GL balance, statement balance, outstanding items on each side, and whether the reconciliation ties out.",
    )
    @GetMapping("/{id}/reconciliation")
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun reconciliation(@PathVariable id: UUID): ResponseEntity<ApiResponse<BankReconciliationSummaryResponse>> {
        val import = bankStatementService.findImportById(id)
        SecurityUtils.requireOwnEntity(import.entityId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.reconciliationSummary(id)))
    }

    @Operation(summary = "Auto-match candidate ledger entries for a bank line (read-only — does not commit a match)")
    @GetMapping("/lines/{lineId}/suggestions")
    @PreAuthorize(RoleSets.ACCOUNTING_READ)
    fun suggestions(@PathVariable lineId: UUID): ResponseEntity<ApiResponse<List<MatchCandidateResponse>>> {
        val line = bankStatementService.findLineById(lineId)
        SecurityUtils.requireOwnEntity(line.entityId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.suggestions(lineId)))
    }

    @Operation(summary = "Manually match a bank line to one or more ledger entries")
    @PostMapping("/lines/{lineId}/match")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun match(
        @PathVariable lineId: UUID,
        @Valid @RequestBody command: MatchLineCommand,
    ): ResponseEntity<ApiResponse<BankStatementImportResponse>> {
        val line = bankStatementService.findLineById(lineId)
        SecurityUtils.requireOwnEntity(line.entityId)
        val importId = line.bankStatementImportId
        bankStatementService.match(lineId, command.ledgerEntryIds, SecurityUtils.currentUser().userId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.toResponse(bankStatementService.findImportById(importId))))
    }

    @Operation(summary = "One-click auto-match a bank line (commits only when exactly one candidate matches)")
    @PostMapping("/lines/{lineId}/auto-match")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun autoMatch(@PathVariable lineId: UUID): ResponseEntity<ApiResponse<BankStatementImportResponse>> {
        val line = bankStatementService.findLineById(lineId)
        SecurityUtils.requireOwnEntity(line.entityId)
        bankStatementService.autoMatch(lineId, SecurityUtils.currentUser().userId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.toResponse(bankStatementService.findImportById(line.bankStatementImportId))))
    }

    @Operation(summary = "Remove all matches from a bank line, returning it to UNMATCHED")
    @PostMapping("/lines/{lineId}/unmatch")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun unmatch(@PathVariable lineId: UUID): ResponseEntity<ApiResponse<BankStatementImportResponse>> {
        val line = bankStatementService.findLineById(lineId)
        SecurityUtils.requireOwnEntity(line.entityId)
        bankStatementService.unmatch(lineId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.toResponse(bankStatementService.findImportById(line.bankStatementImportId))))
    }

    @Operation(summary = "Set a bank line aside (IGNORED) without matching it")
    @PostMapping("/lines/{lineId}/ignore")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun ignore(
        @PathVariable lineId: UUID,
        @Valid @RequestBody command: IgnoreLineCommand,
    ): ResponseEntity<ApiResponse<BankStatementImportResponse>> {
        val line = bankStatementService.findLineById(lineId)
        SecurityUtils.requireOwnEntity(line.entityId)
        bankStatementService.ignore(lineId, command.reason, SecurityUtils.currentUser().userId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.toResponse(bankStatementService.findImportById(line.bankStatementImportId))))
    }

    @Operation(summary = "Revert an IGNORED bank line back to UNMATCHED")
    @PostMapping("/lines/{lineId}/unignore")
    @PreAuthorize(RoleSets.ACCOUNTING_OP)
    fun unignore(@PathVariable lineId: UUID): ResponseEntity<ApiResponse<BankStatementImportResponse>> {
        val line = bankStatementService.findLineById(lineId)
        SecurityUtils.requireOwnEntity(line.entityId)
        bankStatementService.unignore(lineId)
        return ResponseEntity.ok(ApiResponse.success(bankStatementService.toResponse(bankStatementService.findImportById(line.bankStatementImportId))))
    }
}

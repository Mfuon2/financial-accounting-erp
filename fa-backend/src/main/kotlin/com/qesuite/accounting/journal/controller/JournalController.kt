package com.qesuite.accounting.journal.controller

import com.qesuite.accounting.journal.domain.JournalEntry
import com.qesuite.accounting.journal.service.CreateJournalEntryCommand
import com.qesuite.accounting.journal.service.JournalService
import com.qesuite.accounting.shared.audit.domain.AuditLog
import com.qesuite.accounting.shared.exceptions.ApiResponse
import com.qesuite.accounting.shared.security.SecurityUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/journal-entries")
@Tag(
    name = "Module 3: Journal Entry Engine",
    description = """
The Journal Entry Engine is the core of the double-entry bookkeeping system. Every
financial event in the system is recorded as a `JournalEntry` containing two or more
`JournalEntryLine` items that must balance (total debits = total credits in functional
currency) before the entry can be posted to the General Ledger.

**Entry Lifecycle (State Machine):**
```
DRAFT → PENDING_APPROVAL → POSTED
              ↓
           (REJECTED → DRAFT)
              ↓
         POSTED → REVERSED
```

- **DRAFT**: Entry is being prepared. Lines can be added/modified freely.
- **PENDING_APPROVAL**: Entry has been submitted. Double-entry balance is validated 
  at this point. No further edits permitted.
- **POSTED**: Entry has been approved and written to the General Ledger. Immutable.
- **REJECTED**: Entry was rejected during review. Returns to DRAFT for correction.
- **REVERSED**: A counter-entry has been created to null out this posting (IFRS-compliant
  reversal — original entry is preserved for audit trail).

**Double-Entry Invariant:**
`SUM(line.debitAmount × exchangeRate) == SUM(line.creditAmount × exchangeRate)`  
in functional currency. Enforced by `DoubleEntryValidator` at submission and posting.

**Multi-Currency:** Each line carries its own `currencyCode` and `exchangeRate`. 
Functional amounts (`functionalDebit`, `functionalCredit`) are pre-calculated on 
creation and stored on `JournalEntryLine` for immutable audit trail purposes.

**Audit Trail:** All state transitions are recorded in the `AuditLog` table with 
the acting user, timestamp, and entry payload snapshot.

**Source Traceability:** Entries may carry `sourceType` and `sourceId` linking back 
to a `SourceDocument` for end-to-end evidence chain (IAS 1 §35).
"""
)
class JournalController(private val journalService: JournalService) {

    @GetMapping
    @PreAuthorize("hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "List journal entries for an entity",
        description = """
Returns all journal entries for the given entity in any status (DRAFT, PENDING_APPROVAL,
POSTED, REVERSED). Entries are not filtered by status — use the `status` field in the
response to filter client-side, or extend with query parameters as needed.

Each entry includes its full list of `lines` with functional currency amounts.
"""
    )
    fun getAll(
        @RequestParam @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000") entityId: UUID
    ): ApiResponse<List<JournalEntry>> {
        SecurityUtils.requireOwnEntity(entityId)
        return ApiResponse.success(journalService.getAllEntries(entityId))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Create a new journal entry in DRAFT status",
        description = """
Creates a new journal entry with one or more lines. The entry is created in **DRAFT** 
status and is NOT yet validated for double-entry balance — that validation occurs at
submission time (`POST /{id}/submit`).

**Request body key fields:**
- `entityId` — Tenant UUID (must match an active entity)
- `periodId` — Must reference an **OPEN** or **ADJUSTING** accounting period
- `transDate` — Transaction date (must fall within the period's date range)
- `lines` — Minimum 2 lines; each line must specify `accountId`, at least one of 
  `debitAmount` or `creditAmount`, `currencyCode`, and `exchangeRate`
- `sourceType` / `sourceId` — Optional link to a `SourceDocument`

**Note:** `functionalDebit` and `functionalCredit` on each line are calculated 
automatically (`amount × exchangeRate`) and cannot be set directly.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Journal entry created in DRAFT status"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid period, account, or line structure"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account or period not found")
    )
    fun create(
        @Valid @RequestBody command: CreateJournalEntryCommand
    ): ApiResponse<JournalEntry> {
        SecurityUtils.requireOwnEntity(command.entityId)
        return ApiResponse.success(journalService.createEntry(command))
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Retrieve a journal entry with all its lines",
        description = """
Returns the complete journal entry record including all `JournalEntryLine` items with
their original currency amounts, exchange rates, and calculated functional currency amounts.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Journal entry found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Journal entry not found")
    )
    fun getById(
        @PathVariable @Parameter(description = "Journal entry UUID", example = "990e8400-e29b-41d4-a716-446655440004") id: UUID
    ): ApiResponse<JournalEntry> {
        val entry = journalService.findById(id)
        SecurityUtils.requireOwnEntity(entry.entityId)
        return ApiResponse.success(entry)
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Update a journal entry",
        description = """
Replaces all lines on the entry and updates mutable header fields (`description`, 
`transDate`). The entry must be in **DRAFT** status.

**Blocked if** the entry is in PENDING_APPROVAL, POSTED, or REVERSED status — returns
`422` with error code `IMMUTABLE_RECORD`.

All existing lines are cleared and replaced atomically. The new set of lines is
not yet validated for double-entry balance.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entry updated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Entry not found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Entry is not in DRAFT status")
    )
    fun update(
        @PathVariable @Parameter(description = "Journal entry UUID to update") id: UUID,
        @Valid @RequestBody command: CreateJournalEntryCommand
    ): ApiResponse<JournalEntry> {
        SecurityUtils.requireOwnEntity(journalService.findById(id).entityId)
        return ApiResponse.success(journalService.updateEntry(id, command))
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Submit entry for approval (DRAFT → PENDING_APPROVAL)",
        description = """
Validates the double-entry balance invariant and transitions the entry to 
**PENDING_APPROVAL**, placing it in the approver's review queue.

**Validation at submission:**
- `SUM(functionalDebit) == SUM(functionalCredit)` across all lines (within 0.000001 tolerance)
- At least 2 lines
- Entry is in DRAFT status

If the invariant fails, a `422` is returned with error code `DOUBLE_ENTRY_VIOLATION`
and the amounts of the imbalance.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entry submitted for approval"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Double-entry balance violation or invalid status")
    )
    fun submit(
        @PathVariable @Parameter(description = "Journal entry UUID") id: UUID
    ): ApiResponse<Unit> {
        SecurityUtils.requireOwnEntity(journalService.findById(id).entityId)
        journalService.submitEntry(id)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Approve and post entry to the General Ledger (PENDING_APPROVAL → POSTED)",
        description = """
Posts the approved journal entry to the General Ledger. This is the critical step
that creates `LedgerEntry` records and updates account running balances.

**What happens on posting:**
1. Double-entry balance is re-validated.
2. One `LedgerEntry` row is inserted per journal line.
3. Running balance on each affected `Account` is recalculated.
4. Entry status is set to **POSTED** (immutable thereafter).
5. An `AuditLog` record is written with action `POST`.

**Required role:** SENIOR_ACCOUNTANT or CONTROLLER_CFO.

**Note:** If the entry is already POSTED, returns `422` with `ALREADY_POSTED`.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Entry posted to General Ledger"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Entry already posted or not in PENDING_APPROVAL status")
    )
    fun approve(
        @PathVariable @Parameter(description = "Journal entry UUID to post") id: UUID
    ): ApiResponse<Unit> {
        SecurityUtils.requireOwnEntity(journalService.findById(id).entityId)
        journalService.postEntry(id)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Reject a pending entry back to DRAFT (PENDING_APPROVAL → DRAFT)",
        description = """
Rejects the entry and returns it to **DRAFT** status for correction by the originator.

A mandatory `reason` parameter must be supplied — this is recorded in the audit log
to provide the reviewer's justification. The originator can then edit the entry and
resubmit.

**Required role:** SENIOR_ACCOUNTANT, CONTROLLER_CFO, or AUDITOR.
"""
    )
    fun reject(
        @PathVariable @Parameter(description = "Journal entry UUID to reject") id: UUID,
        @RequestParam @Parameter(description = "Reason for rejection (recorded in audit log)", example = "Incorrect account allocation — should use 6100 not 6000") reason: String
    ): ApiResponse<Unit> {
        SecurityUtils.requireOwnEntity(journalService.findById(id).entityId)
        journalService.rejectEntry(id, reason)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')")
    @Operation(
        summary = "Create a reversing counter-entry against a POSTED entry",
        description = """
Creates an IFRS-compliant reversal against a POSTED journal entry. The original entry
is **not deleted or modified** — its status changes to **REVERSED** and a new mirror
entry with all debit/credit amounts swapped is created in POSTED status.

This maintains a complete, immutable audit trail (IAS 8 — accounting policy changes
and correction of errors require reversal, not deletion).

**Common use cases:**
- Reversing an accrual at the start of the new period
- Correcting a mis-posted entry
- Unwinding a prepayment after the service has been rendered

The reversing entry is linked back to the original via `sourceType = "REVERSAL"` 
and `sourceId = original.id`.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reversing entry created and posted"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Entry is not in POSTED status")
    )
    fun reverse(
        @PathVariable @Parameter(description = "Journal entry UUID to reverse") id: UUID
    ): ApiResponse<JournalEntry> {
        SecurityUtils.requireOwnEntity(journalService.findById(id).entityId)
        return ApiResponse.success(journalService.reverseEntry(id))
    }

    @GetMapping("/{id}/audit-trail")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')")
    @Operation(
        summary = "Retrieve the audit trail for a journal entry",
        description = "Returns all AuditLog records for this journal entry, ordered newest-first. " +
            "Each record captures who performed the action, when, and the resulting payload state."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Audit trail returned"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Journal entry not found")
    )
    fun getAuditTrail(
        @PathVariable @Parameter(description = "Journal entry UUID") id: UUID
    ): ApiResponse<List<AuditLog>> {
        SecurityUtils.requireOwnEntity(journalService.findById(id).entityId)
        return ApiResponse.success(journalService.getAuditTrail(id))
    }
}

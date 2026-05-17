package com.qesuite.accounting.coa.controller

import com.qesuite.accounting.coa.domain.Account
import com.qesuite.accounting.coa.domain.AccountSubtype
import com.qesuite.accounting.coa.domain.AccountType
import com.qesuite.accounting.coa.domain.CoaTemplate
import com.qesuite.accounting.coa.service.AccountService
import com.qesuite.accounting.coa.service.CreateAccountCommand
import com.qesuite.accounting.coa.service.UpdateAccountCommand
import com.qesuite.accounting.shared.exceptions.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1/coa")
@Tag(
    name = "Module 1: Chart of Accounts",
    description = """
The Chart of Accounts (COA) is the foundational classification framework for the entire
accounting system. Every financial transaction is ultimately categorised against one or
more accounts in this hierarchy.

**Account Hierarchy:**
Accounts support up to **5 levels** of parent-child nesting. A child account inherits
its `accountType` and `normalBalance` from its `accountSubtype`. Circular references
are detected and rejected.

**IFRS Classification:**
Each account carries an `ifrsCategory` (IAS 1 taxonomy) which is used directly by the
Financial Statement engine to classify balances on the Balance Sheet and P&L — no
heuristics or account-code prefixes are used.

**Account Subtypes → Account Type mapping:**
| Subtype | Parent Type |
|---|---|
| CASH_AND_EQUIVALENTS | ASSET |
| CURRENT_RECEIVABLE | ASSET |
| NON_CURRENT_PPE | ASSET |
| OPERATING_REVENUE | REVENUE |
| OPERATING_EXPENSES | EXPENSE |
| CURRENT_PAYABLE | LIABILITY |
| RETAINED_EARNINGS | EQUITY |
| ... | ... |

**Immutability Rules:**
- `accountCode` cannot be changed once ledger entries exist against the account.
- Deactivation is blocked if any ledger history exists (soft-delete only).
- `accountType` is derived from `accountSubtype` and is read-only.

**Templates:** Pre-built COA templates (e.g., SERVICE, RETAIL) can be applied to 
bootstrap a new entity's chart of accounts in a single call.
"""
)
class AccountController(private val accountService: AccountService) {

    @GetMapping("/accounts")
    @Operation(
        summary = "List all accounts",
        description = """
Returns all accounts scoped to the given `entityId`, with optional filters.

All filters are applied as AND conditions. Omitting a filter means "any value".
Use `isActive=true` to exclude deactivated accounts from reporting views.
Use `parentAccountId` to retrieve only direct children of a specific parent node.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "List of accounts (may be empty)")
    )
    fun getAllAccounts(
        @RequestParam @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000") entityId: UUID,
        @RequestParam(required = false) @Parameter(description = "Filter by account type (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE)") type: AccountType?,
        @RequestParam(required = false) @Parameter(description = "Filter by IFRS-aligned account subtype") subtype: AccountSubtype?,
        @RequestParam(required = false) @Parameter(description = "Filter by active status. true = active accounts only") isActive: Boolean?,
        @RequestParam(required = false) @Parameter(description = "Return only direct children of this parent account UUID") parentAccountId: UUID?
    ): ApiResponse<List<Account>> {
        return ApiResponse.success(accountService.getAllAccounts(entityId, type, subtype, isActive, parentAccountId))
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new ledger account",
        description = """
Creates a new account in the Chart of Accounts.

**Validation rules:**
- `accountCode` must be unique within the entity.
- `accountName` must be unique within the entity.
- `currencyCode` must reference a currency already registered for the entity.
- If `parentAccountId` is supplied, the parent must belong to the same entity and 
  the resulting hierarchy depth must not exceed 5 levels.
- Circular parent-child references are detected and rejected.
- `ifrsCategory` is mandatory for financial statement classification. Defaulting to
  `OPERATING_EXPENSES` is allowed but should be set explicitly for accurate reporting.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Account created successfully"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failure (duplicate code/name, invalid currency, hierarchy depth exceeded)"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Circular reference detected or entity mismatch")
    )
    fun createAccount(@Valid @RequestBody command: CreateAccountCommand): ApiResponse<Account> {
        return ApiResponse.success(accountService.createAccount(command))
    }

    @GetMapping("/accounts/{id}")
    @Operation(
        summary = "Retrieve a single account by ID",
        description = "Returns the full account record including its IFRS category, normal balance direction, and current balance totals."
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found")
    )
    fun getAccount(
        @PathVariable @Parameter(description = "Account UUID", example = "770e8400-e29b-41d4-a716-446655440002") id: UUID
    ): ApiResponse<Account> {
        return ApiResponse.success(accountService.findById(id))
    }

    @PutMapping("/accounts/{id}")
    @Operation(
        summary = "Update an existing account",
        description = """
Updates mutable fields on an account. 

**Immutability constraint:** `accountCode` cannot be changed if any `LedgerEntry`
records exist against this account. Attempting to do so returns `422` with error code
`IMMUTABLE_ACCOUNT_CODE`.

The `accountType` and `normalBalance` are automatically re-derived from the 
updated `accountSubtype` — they cannot be set directly.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account updated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Account code is immutable (has ledger history)")
    )
    fun updateAccount(
        @PathVariable @Parameter(description = "Account UUID to update") id: UUID,
        @Valid @RequestBody command: UpdateAccountCommand
    ): ApiResponse<Account> {
        return ApiResponse.success(accountService.updateAccount(id, command))
    }

    @PostMapping("/accounts/{id}/deactivate")
    @Operation(
        summary = "Deactivate an account",
        description = """
Soft-deactivates an account, setting `isActive = false`. The account remains in the
database and all historical ledger data is preserved.

**Blocked if:** The account has any associated `LedgerEntry` records. This prevents
deactivating accounts with transaction history, which would corrupt historical reporting.

Deactivated accounts are excluded from new journal entry line selections but remain
visible in historical reports with `isActive=false` filter.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account deactivated"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Account not found"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Deactivation blocked — account has ledger history")
    )
    fun deactivateAccount(
        @PathVariable @Parameter(description = "Account UUID to deactivate") id: UUID
    ): ApiResponse<Unit> {
        accountService.deactivateAccount(id)
        return ApiResponse.success(Unit)
    }

    @GetMapping("/accounts/{id}/hierarchy")
    @Operation(
        summary = "Retrieve the full parent-chain hierarchy for an account",
        description = """
Returns the ordered list of accounts from the root ancestor down to the requested
account, representing the full COA hierarchy path.

Example response for account `1100 - Trade Receivables` might return:
```
[
  { accountCode: "1000", accountName: "Assets" },
  { accountCode: "1100", accountName: "Trade Receivables" }
]
```

Useful for rendering breadcrumb navigation in the COA tree UI.
"""
    )
    fun getHierarchy(
        @PathVariable @Parameter(description = "Account UUID to retrieve the hierarchy for") id: UUID
    ): ApiResponse<List<Account>> {
        return ApiResponse.success(accountService.getHierarchy(id))
    }

    @GetMapping("/accounts/{id}/balance")
    @Operation(
        summary = "Get the running balance for an account",
        description = """
Returns the net functional-currency balance for an account as of a given date.

The balance is computed directly from `LedgerEntry` records using point-in-time
aggregation (`SUM(functionalDebit) - SUM(functionalCredit)` for DEBIT-normal accounts,
inverted for CREDIT-normal accounts).

If `asOfDate` is omitted, today's date is used.

**Note:** This reflects posted entries only. DRAFT or PENDING_APPROVAL journal entries
are not included.
"""
    )
    fun getBalance(
        @PathVariable @Parameter(description = "Account UUID") id: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Point-in-time date for balance calculation (ISO 8601, e.g. 2026-03-31). Defaults to today.", example = "2026-03-31")
        asOfDate: LocalDate?
    ): ApiResponse<BigDecimal> {
        return ApiResponse.success(accountService.getBalance(id, asOfDate))
    }

    @GetMapping("/templates")
    @Operation(
        summary = "List all available COA templates",
        description = """
Returns the names of all built-in Chart of Accounts templates. 
Templates are used to bootstrap a new entity's COA with a standard set of accounts.

Available templates:
- **SERVICE** — Suitable for professional services businesses (Cash, Revenue, Expenses).
- Additional industry templates (RETAIL, MANUFACTURING) can be added as enum values.
"""
    )
    fun listTemplates(): ApiResponse<Array<CoaTemplate>> {
        return ApiResponse.success(CoaTemplate.values())
    }

    @PostMapping("/templates/{template_id}/apply")
    @Operation(
        summary = "Apply a COA template to an entity",
        description = """
Applies a pre-built account set to the specified entity. Each template creates a 
standard set of accounts with appropriate IFRS categories, subtypes, and normal balances.

This is a **non-idempotent** operation — calling it multiple times will create 
duplicate accounts. It is intended for initial entity setup only.

Requires a functional currency (e.g., USD) to be registered for the entity first.
"""
    )
    @ApiResponses(
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Template applied"),
        io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid currency or entity")
    )
    fun applyTemplate(
        @RequestParam @Parameter(description = "Tenant/company UUID", example = "550e8400-e29b-41d4-a716-446655440000") entityId: UUID,
        @PathVariable("template_id") @Parameter(description = "Template identifier", schema = Schema(implementation = CoaTemplate::class)) template: CoaTemplate
    ): ApiResponse<Unit> {
        accountService.applyTemplate(entityId, template)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/import")
    @Operation(
        summary = "Bulk-import a custom COA from JSON",
        description = """
Accepts a JSON array of `CreateAccountCommand` objects and creates all accounts in 
sequence. All accounts are scoped to the `entityId` query parameter, overriding any
`entityId` field in the request body.

Validation is applied to each account individually. If any account fails validation,
the entire import is rolled back (transactional).

**Recommended usage:** Migrating from a legacy accounting system or provisioning a 
complex multi-level hierarchy in a single API call.
"""
    )
    fun importCoa(
        @RequestParam @Parameter(description = "Tenant/company UUID for all imported accounts") entityId: UUID,
        @RequestBody commands: List<CreateAccountCommand>
    ): ApiResponse<Unit> {
        accountService.importAccounts(entityId, commands)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/rebuild-hierarchy")
    @Operation(
        summary = "Rebuild account hierarchy from account codes",
        description = "Infers parent_account_id for all accounts with null parents using the X-ABCD code structure."
    )
    fun rebuildHierarchy(
        @RequestParam @Parameter(description = "Tenant/company UUID") entityId: UUID
    ): ApiResponse<Unit> {
        accountService.rebuildHierarchy(entityId)
        return ApiResponse.success(Unit)
    }

    @PostMapping("/accounts/validate-code")
    @Operation(
        summary = "Validate an account code for uniqueness",
        description = """
Checks whether a given account code is available for use within the entity's COA.  
Returns `true` if the code is **available** (not yet in use), `false` if already taken.

Use this endpoint to provide real-time validation feedback in the COA creation UI
before submitting the full `POST /accounts` request.
"""
    )
    fun validateCode(
        @RequestParam @Parameter(description = "Tenant/company UUID") entityId: UUID,
        @RequestParam @Parameter(description = "Account code to validate (e.g. '1100')", example = "1100") code: String
    ): ApiResponse<Boolean> {
        return ApiResponse.success(accountService.validateAccountCode(entityId, code))
    }
}

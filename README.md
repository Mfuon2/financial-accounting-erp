# QeSuite — Open-Source IFRS Financial Accounting System

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Status: Active](https://img.shields.io/badge/Status-Active-success.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](https://vuejs.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF.svg)](https://kotlinlang.org)
[![PRs Welcome](https://img.shields.io/badge/PRs-Welcome-brightgreen.svg)](CONTRIBUTING.md)

**QeSuite FA** is a production-ready, self-hosted financial accounting platform built to International Financial Reporting Standards (IFRS). It delivers a complete general ledger engine, full AP/AR lifecycle, multi-currency FX processing, IFRS 15 revenue recognition, fixed-asset management (IAS 16), and a high-density terminal-grade UI — all in a single open-source repository.

---

## Table of Contents

- [Key Capabilities](#key-capabilities)
- [Modules and Feature Set](#modules-and-feature-set)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [IFRS Compliance](#ifrs-compliance)
- [API Reference](#api-reference)
- [Contributing](#contributing)
- [Security](#security)
- [Roadmap](#roadmap)
- [License](#license)
- [Disclaimer](#disclaimer)

---

## Key Capabilities

- **Fully open-source and self-hosted** — financial data stays on infrastructure you control, with no vendor lock-in and no per-seat licensing.
- **IFRS-first double-entry ledger** — every transaction is validated for debit/credit equality before it is accepted. The trial balance engine throws a hard error on any mismatch. The ledger is immutable; corrections go through reversals only.
- **Complete 9-step accounting cycle** — source document capture through post-closing trial balance, with each step tracked and validated by the accounting cycle controller.
- **Full AP and AR lifecycle** — vendor bills with batch payment runs on the payables side; customer invoicing, credit notes, and payment matching on the receivables side.
- **API-first architecture** — every function is accessible via a documented REST API (OpenAPI 3.0), suitable for embedding QeSuite FA into existing platforms or integrating with external systems.
- **Demo / Production mode** — a built-in toggle lets teams explore the system with static sample data before connecting to the live backend, requiring no code changes.
- **Configurable document numbering** — 13 document types (invoices, bills, journal entries, payments, receipts, credit notes, debit notes, etc.) each have independently configurable prefix, year-scope, and zero-padding rules per entity.
- **Forensic, insert-only audit trail** — an AOP-driven aspect captures every create, update, approval, posting, reversal, and closing event with payload-before and payload-after snapshots. Records are never modified or deleted.
- **Multi-entity support** — each entity has its own chart of accounts, fiscal periods, document sequences, currencies, tax codes, and user access. Entities are fully isolated.
- **Idempotency protection** — critical mutating endpoints are guarded by Redis-backed idempotency keys that prevent duplicate submissions.

---

## Modules and Feature Set

### 1. Authentication and User Management

**Authentication**
- JWT authentication with access and refresh token rotation (RFC 6749 pattern). Refresh tokens are stored as SHA-256 hashes, never in plaintext.
- Endpoints: login, register, refresh, logout, change password, forgot password (sends email reset link), reset password (single-use token, 1-hour expiry), email verification.
- Password reset revokes all active sessions on completion. Password change also revokes all sessions, requiring the user to re-authenticate on all devices.
- Account lockout: 5 consecutive failed login attempts trigger a 30-minute lock. The lock clears automatically when the window expires.

**Password Policy**
- Minimum 8 characters, at least 1 uppercase letter, 1 digit, and 1 special character (non-alphanumeric). Enforced at the service layer on registration and password changes.

**User Lifecycle**
- Statuses: `PENDING_VERIFICATION` → `ACTIVE`, with `LOCKED`, `SUSPENDED`, and `DEACTIVATED` states.
- Self-registered users start as `PENDING_VERIFICATION` until email is verified. Admin-created users are activated immediately. The first user in a new entity is automatically bootstrapped as `SYSTEM_ADMIN` with email pre-verified.
- Role assignment, profile updates, activate/deactivate/reactivate. The last `SYSTEM_ADMIN` in an entity cannot be demoted.

**Roles (6)**
`DATA_ENTRY` · `ACCOUNTANT` · `SENIOR_ACCOUNTANT` · `CONTROLLER_CFO` · `AUDITOR` · `SYSTEM_ADMIN`

**Sessions**
- List all active sessions (non-revoked, non-expired refresh tokens) with device info (user-agent, IP address, issued/expiry timestamps).
- Revoke a specific session by ID; or revoke all sessions except the current one.

**API Keys**
- Named API keys for programmatic integrations. Key material is shown once on creation; only the SHA-256 hash is stored.
- Operations: create, list, revoke, rotate. API keys authenticate via a separate filter chain alongside JWT.

---

### 2. Organisation Management

Each entity holds: name, legal name, registration number, tax identification number, functional currency, reporting currency, country code, timezone, fiscal year start month, address (lines, city, postal code), phone, email, website, and logo URL.

- Organisation lifecycle: `ACTIVE`. Suspension and deactivation are available for system-admin management.
- Fiscal year start month is configurable (1–12), supporting non-calendar fiscal years.
- Timezone-aware: accounting dates are stored in UTC and converted using the entity's configured timezone.
- Optimistic locking (versioned) prevents lost-update race conditions on concurrent edits.

---

### 3. Chart of Accounts (COA)

**Account Master**
Each account stores: account code (unique per entity), account name (unique per entity), type, subtype, normal balance (derived but stored explicitly), temporary flag, parent account ID, IFRS category, IFRS classification, currency code, running totals (total debits, total credits, current balance in functional currency, original-currency balance), active flag, and header flag.

- **Header accounts**: when a child account is added, the parent is automatically promoted to `isHeader = true`. Header accounts cannot receive direct journal postings (IAS 1 §29). Only leaf accounts accept new entries.
- Account codes are locked once any ledger entries exist against them; renaming or deactivation is still permitted.
- Inactive accounts are rejected at the application layer before a journal line reaches the posting engine.
- Account code must be unique per entity; account name must be unique per entity.
- Circular references in the hierarchy are rejected. Maximum depth: 5 levels.

**Account Types and Subtypes (IFRS-aligned)**
- Assets: Cash and Equivalents, Current Receivable, Current Inventory, Current Prepaid, Non-current PPE, Non-current Intangible, Non-current Investment, Non-current Other, Accumulated Depreciation (contra-asset, credit-normal).
- Liabilities: Current Payable, Current Accrued, Current Deferred Revenue, Current Tax, Non-current Long-term Debt, Non-current Provision, Non-current Deferred Tax.
- Equity: Share Capital, Retained Earnings, Other Comprehensive Income, Dividends/Drawings.
- Revenue: Operating Revenue, Other Income, Finance Income.
- Expenses: Cost of Goods Sold, Operating Expenses, Depreciation, Amortisation, Finance Costs, Tax Expense.

**COA Templates**
Pre-built templates (service, merchandising, manufacturing, financial services, non-profit) that pre-populate account codes, names, types, IFRS categories, and parent hierarchy. Import from template in one operation.

**Currency Resolution**
When creating an account without specifying a currency, the system resolves the entity's functional currency and assigns it automatically. Creating an account with a currency code that is not registered for the entity is rejected.

---

### 4. Period Management

- Fiscal year generation: creates all 12 monthly periods for a given year in one operation. New periods start as `FUTURE` — this prevents a historical year generation from auto-switching the active accounting context.
- Period lifecycle: `FUTURE` → `OPEN` → `ADJUSTING` → `CLOSED`.
- Period-lock interceptor: a Spring MVC interceptor rejects all mutating requests targeting a `CLOSED` period before the request reaches any service logic.
- Date-range lookup: find the period that covers a specific date.

---

### 5. Accounting Cycle Controller (9-Step Orchestration)

Tracks and enforces the complete 9-step IFRS accounting cycle per period per entity:

1. Journalise transactions (continuous, via journal entry endpoints)
2. Post to the general ledger (continuous)
3. Generate unadjusted trial balance
4. Record and post adjusting entries (accruals, deferrals, prepayment amortisation, unearned revenue recognition)
5. Generate adjusted trial balance
6. Prepare Income Statement / Profit & Loss
7. Prepare Balance Sheet
8. Post closing entries (revenues and expenses → retained earnings)
9. Generate post-closing trial balance

- Step validation: a dry-run endpoint confirms prerequisites before a step transition is attempted.
- Full cycle run: a single endpoint orchestrates all remaining steps for a period.

---

### 6. Source Documents

**Supported types**: Sales Invoice, Purchase Invoice, Receipt, Payment Voucher, Bank Statement, Payroll Summary, Credit Note, Asset Purchase Order, Loan Agreement.

**Lifecycle**: `DRAFT` → `SUBMITTED` → `REVIEWED` → `APPROVED` → `POSTED` → `ARCHIVED`. Void and restore (un-void) operations are also supported.

**Transaction classification**: a classify endpoint evaluates a source document against the four-test IFRS recognition gate — past event, economic impact, probability, and measurability.

**File attachments**: each source document supports file attachments. File references are stored in the database; the binary content is stored in MinIO object storage (or local disk in development).

---

### 7. Journal Entry Engine

- **Double-entry validation**: enforced before any entry is persisted. Sum of debits must equal sum of credits; the engine will not write a partially balanced entry.
- **Entry lifecycle**: `DRAFT` → `PENDING_APPROVAL` → `APPROVED` → `POSTED`. Rejected entries return to `DRAFT` with a reason.
- **Reversal**: any posted entry can be reversed. A mirror-image entry is created and immediately posted, with a back-reference to the original.
- **Reference numbers**: generated by the code generator, scoped to the fiscal year of the period (not the current calendar year). A journal in period January 2026 receives reference `JE-2026-0001`, and a journal in a historical 2025 period receives `JE-2025-NNNN`, independently.
- **Journal lines carry**: account ID, description, debit amount, credit amount, currency code, exchange rate, computed functional-currency debit, computed functional-currency credit, tax code string, and pre-computed tax amount.
- **Tax on lines**: each journal line can carry a tax code and amount for VAT-inclusive or tax-exclusive entry recording.

---

### 8. Adjusting Entries

- **Accruals**: creates a journal entry tagged with `sourceType = ACCRUAL`.
- **Deferrals**: creates a journal entry tagged with `sourceType = DEFERRAL`.
- **Prepayment amortisation**: batch-processes all accounts with subtype `CURRENT_PREPAID` that carry a positive balance. For each, posts DR Operating Expenses / CR Prepaid Account for the full balance. Failures on individual accounts are caught and logged without aborting the batch.
- **Unearned revenue recognition**: releases balances from deferred revenue accounts into earned revenue.

---

### 9. General Ledger and Posting Engine

- Immutable ledger: entries in `ledger_entries` are never deleted or updated. All corrections go through reversal journal entries.
- Balance computation respects normal-balance direction so contra accounts (e.g., Accumulated Depreciation, which is credit-normal) compute the correct book value without sign errors.
- **T-account view**: side-by-side debit and credit ledger entries for any account over any date range.
- **Subsidiary ledger**: drill into postings scoped to a specific customer, supplier, or fixed asset.

---

### 10. Trial Balance

- **Unadjusted**: balances derived from the live ledger before period-end adjustments.
- **Adjusted**: balances after adjusting entries have been posted.
- **Mismatch validation**: the trial balance engine sums all raw debits and raw credits across the entity. If they do not match, a `TRIAL_BALANCE_FAILURE` exception is thrown — this indicates a data integrity problem that must be resolved before proceeding.
- Accounts with abnormal balances (balance on the opposite side from their normal) are placed in the opposite column of the trial balance display to clearly flag the anomaly.
- Each row carries a hierarchy depth indicator for indented display, and an `isHeader` flag to visually separate summary accounts.
- **Comparative trial balance**: side-by-side presentation across two periods. (See Roadmap.)

---

### 11. Financial Statements

**Profit and Loss (Income Statement)**
Revenue accounts minus expense accounts, grouped by IFRS category, for a specified start and end date.

**Balance Sheet (Statement of Financial Position)**
Assets, liabilities, and equity as at a specified date, classified into current and non-current categories per IAS 1.

**Statement of Cash Flows — IAS 7 Indirect Method**
- Operating activities: net income + non-cash add-backs (depreciation, amortisation) + working-capital movements (receivables, payables, inventory, prepayments).
- Investing activities: net movement in non-current asset accounts.
- Financing activities: net movement in non-current liability and equity accounts.
- Closing cash = opening cash + net change in cash.

**PDF export**: all three financial statements can be exported as formatted PDF documents using OpenPDF. The PDF renderer uses the entity's branding accent colour and produces a print-ready A4 layout.

---

### 12. Period Closing

**Closing entries (4 steps)**:
1. Close revenue accounts → Income Summary (DR Revenue / CR Income Summary per account).
2. Close expense accounts → Income Summary (DR Income Summary / CR Expense per account).
3. Close Income Summary → Retained Earnings (DR or CR Income Summary, opposite to Retained Earnings).
4. Close Dividends → Retained Earnings.

Balances are computed from live ledger entries within the period's date range — not from the cached `currentBalance` field — so the closing amounts are always accurate.

On completion, the period status transitions to `CLOSED`. A reopen operation is available under elevated authorisation.

---

### 13. Accounts Receivable (AR) — Revenue Cycle

**Invoices**
- Lifecycle: `DRAFT` → `APPROVED` → `SENT` → `PARTIALLY_PAID` → `PAID`, with `VOID` and `CREDIT_NOTE` exits.
- On approval, the system auto-posts the AR journal: DR Accounts Receivable / CR Revenue, with a separate CR to the Tax Payable account for any tax on the line.
- Line items carry quantity, unit price, tax rate reference, and computed totals. Tax is computed by the Tax module using the effective rate on the invoice date.
- Automatic due date: calculated from NET-N payment terms stored on the customer record.
- Duplicate guard: duplicate invoice numbers within an entity are rejected.

**IFRS 15 Revenue Recognition**
The 5-step model is applied on invoice approval:
- Point-in-time obligations (goods delivered): the full line amount is recognised immediately as revenue.
- Over-time obligations (services): the obligation is stored against the Deferred Revenue account. Incremental period-end recognition is structured and ready for activation.

**Credit Notes**
- Full or partial credit notes against an approved invoice.
- The system reverses the corresponding portion of the AR journal (CR Accounts Receivable / DR Revenue) and adjusts the original invoice's outstanding balance.

**Payments**
- Lifecycle: `PENDING` → `MATCHED` → `APPROVED` → `POSTED` → `REVERSED`.
- Multiple payment methods tracked per payment.
- One payment can be matched against multiple invoices with partial match support.
- On posting, the GL journal is created automatically (DR Cash / CR Accounts Receivable) and a receipt is auto-generated.

**Receipts**
- Auto-generated when a payment is posted. Manual generation also supported.
- Lifecycle: `POSTED` → `ISSUED` → `VOID`.
- Receipt delivery by email is supported when email is enabled.

**AR Ageing Report**
Multi-bucket aging (current, 1–30, 31–60, 61–90, 90+ days overdue) with customer-level detail.

---

### 14. Accounts Payable (AP)

**Vendor Bills**
- Lifecycle: `DRAFT` → `APPROVED` → `PARTIALLY_PAID` → `PAID` → `VOID`.
- On approval, the system auto-posts the AP journal: DR Expense / CR Accounts Payable.
- Due date is auto-populated from the supplier's NET-N payment terms.
- Duplicate bill detection: warns when the same supplier, date, and amount appear within a configurable monetary tolerance (default ±1.00 in the bill's currency).

**Debit Notes (Purchase Credit Notes)**
- Reduce the AP balance with proportional expense reversal.
- Debit notes and bills share the same table, distinguished by the `isDebitNote` flag.

**Payments**
- Single payment recording: record a payment against a bill, selecting the cash account and payment method.
- **Batch payment runs**: consolidate payments across multiple vendor bills into a single journal entry for efficient bank processing.

**Supplier Statement**
A running-balance statement for a supplier showing all bills, debit notes, and payments in chronological order, with a cumulative balance column. Totals for debits, credits, and closing balance are included.

**AP Ageing Report**
Aging report bucketed by current, 1–30, 31–60, 61–90, and 90+ days overdue.

---

### 15. Fixed Assets — IAS 16

**Asset Register**
Each asset records: asset code, name, category, assigned-to, acquisition date, acquisition cost, salvage value, useful life (months), depreciation method, accumulated depreciation, status, and three mandatory COA account references (cost account, accumulated depreciation account, depreciation expense account).

Asset codes can be auto-generated by the code generator or provided manually. Duplicate asset codes within an entity are rejected.

**Depreciation**
- **Straight-Line (SL)**: `(cost − salvage) ÷ useful life in months` per month.
- **Double-Declining Balance (DDB)**: `(2 ÷ useful life in months) × book value` per month, capped at `book value − salvage`.
- Both methods cap the final run at the remaining depreciable balance, preventing over-depreciation.
- Assets with `usefulLifeMonths ≤ 0` are skipped with a warning log entry.

**Batch Depreciation Run**
Processes all `ACTIVE` assets for a given entity and period in one operation. Each asset posts DR Depreciation Expense / CR Accumulated Depreciation. Individual asset failures are logged and the batch continues.

**Asset Disposal**
Posts: DR Proceeds Received / DR Accumulated Depreciation / CR Asset Cost / CR or DR Gain or Loss on Disposal (depending on whether proceeds exceed net book value).

**Asset Lifecycle**: `ACTIVE` → `FULLY_DEPRECIATED` → `DISPOSED`.

---

### 16. Multi-Currency and FX — IAS 21

- **Currency registry**: ISO 4217 currency codes with name, symbol, decimal precision, and a `isFunctional` flag. Exactly one currency per entity is designated as functional.
- **Functional currency**: all journal lines store both the transaction-currency amount and a computed functional-currency amount (transaction amount × exchange rate).
- **Exchange rates**: date-effective rates with type (`SPOT`, `CLOSING`). Lookup falls back from `CLOSING` to `SPOT` if no closing rate exists for the requested date.
- **FX revaluation preview**: calculates the unrealised gain or loss on all monetary accounts denominated in a foreign currency, comparing the rate at which they were recorded against the current closing rate.
- **FX revaluation posting**: posts the net gain or loss as a journal entry to the configured FX Gain/Loss account.
- **Realised FX on settlement**: exchange differences between the invoice rate and the payment rate are captured as realised FX gain/loss journal entries when a payment is posted.

---

### 17. Tax Module

- **Tax codes**: code (unique per entity), name, description, tax type (`OUTPUT`, `INPUT`, `EXEMPT`, `WHT`), linked GL account code, and `isRecoverable` flag (for input VAT reclaim).
- **Tax rates**: effective-dated rates per code, allowing rate changes (e.g., a VAT rate change from 14% to 16%) without losing historical data.
- **Tax calculation**: given a base amount and either a tax code string + date (resolves the effective rate automatically) or a direct tax rate ID, returns the computed tax amount (`base × rate`, rounded `HALF_EVEN` to 6 decimal places).
- Input VAT: recoverable taxes on AP lines are tracked separately for VAT reclaim purposes.

---

### 18. Analytics Dashboard

Live data is loaded in parallel, independently — a slow data source does not block other cards.

- **4 KPI cards**: cash balance (sum of Cash and Equivalents account balances), accounts receivable outstanding, month-to-date revenue, and month-to-date operating expenses. Each card includes a delta vs the prior month.
- **12-month sparklines**: trailing 12-month rolling series for cash, AR, revenue, and expenses.
- **Revenue vs expenses chart**: 12-month bar/line comparison.
- **Recent activity feed**: last N audit log entries with actor name, action, and resource reference.
- **Pending approvals count**: items awaiting approval surfaced alongside the approvals queue widget.
- **AR ageing widget**: high-level ageing summary visible on the dashboard.
- **Active period indicator**: the current open period is displayed and auto-refreshes every 30 seconds.

---

### 19. Global Approvals Queue

- Aggregated view of all items awaiting approval across journal entries, customer invoices, and vendor bills — from a single endpoint.
- Each item in the queue carries: type, reference number, title/description, amount, currency, submitting user, the role required to approve, and timestamps.
- Approve and reject actions route to the correct module endpoint for the document type.
- Filterable by document type.

---

### 20. Audit Trail

- **Insert-only**: audit log records are never updated or deleted.
- **AOP-driven**: the `@Auditable` annotation on service methods captures transitions automatically. The audit writes in a `REQUIRES_NEW` transaction so a rollback of the main transaction does not suppress the audit entry.
- **Captured fields**: actor user ID (resolved to full name in the API response), action (`CREATE`, `UPDATE`, `DELETE`, `POST`, `REVERSE`, `APPROVE`, `REJECT`, `CLOSE`, `REOPEN`, `EXPORT`, `TAX_ADJUSTMENT`), resource type, resource ID, JSON payload before, JSON payload after, client IP address, and timestamp.
- **Indexed** on `(resource_type, resource_id)` and `entity_id` for performant lookups at scale.
- Filterable API with pagination: filter by resource type, actor, action, and date range.
- Access restricted to `AUDITOR`, `SENIOR_ACCOUNTANT`, and `SYSTEM_ADMIN` roles.

---

### 21. IAS 1 Compliance Checker

Runs four automated checks and returns a PASS / WARN / FAIL per check:

1. **Account classification consistency**: every account's IFRS category must be consistent with its account type (e.g., a CURRENT_ASSETS category cannot belong to an EXPENSE account).
2. **Functional currency disclosure**: confirms that exactly one currency is registered as functional for the entity. Required under IAS 21.
3. **COA coverage**: all five account types (Asset, Liability, Equity, Revenue, Expense) must be present in the chart of accounts.
4. **Postable accounts**: at least one non-header (leaf) account must exist so that journal entries can be posted.

---

### 22. Configurable Document Numbering

13 document types are supported, each with an independently configurable number format per entity:

| Module | Default Prefix | Format Family |
|---|---|---|
| Customer | `CU` | Master-data (no year, never resets) |
| Supplier | `SUPP` | Master-data |
| Fixed Asset | `FA` | Master-data |
| Sales Invoice | `INV` | Transactional (year-scoped, resets annually) |
| Purchase Bill | `BILL` | Transactional |
| Journal Entry | `JE` | Transactional |
| Source Document | `SD` | Transactional |
| Payment | `PAY` | Transactional |
| Receipt | `RCT` | Transactional |
| Credit Note | `CN` | Transactional |
| Debit Note | `DN` | Transactional |
| Purchase Order | `PO` | Transactional |
| Quotation | `QT` | Transactional |

Each module allows alternative prefixes (e.g., `SINV`, `SI` instead of `INV`). Sequence increments are committed in an independent `REQUIRES_NEW` transaction so a rollback of the parent transaction does not recycle a number. The code generator guarantees uniqueness within the entity before returning a code.

---

### 23. Idempotency

- Redis-backed idempotency key store with PostgreSQL persistence for durability.
- Applied via `@RequireIdempotencyKey` AOP annotation on mutating endpoints.
- A duplicate request (same idempotency key within the TTL) returns the cached response without re-executing the operation.

---

### 24. PDF Report Export

Generated using OpenPDF with a branded A4 layout (accent colour, font hierarchy, and formatted monetary values). Three reports are currently exportable:

- Statement of Financial Position (Balance Sheet)
- Profit and Loss Statement
- Statement of Cash Flows

---

### 25. Email

Asynchronous email dispatch via Spring Mail (SMTP). Three email types:

- **Password reset**: sends a one-time link (valid 1 hour) to the user's registered email.
- **Receipt delivery**: sends a formatted HTML receipt to the customer's email when a payment receipt is issued.
- **User invite**: sent when an admin creates a new user account.

Email dispatch is feature-flagged via the `MAIL_ENABLED` environment variable (defaults to `false`). When disabled, the email intent is logged without sending.

---

### 26. Setup Health

Seven automated setup checks run against the live API:

| Check | Severity | Condition |
|---|---|---|
| Active accounting period | Critical | At least one period is `OPEN` or `ADJUSTING` |
| Functional currency registered | Critical | One currency is marked as functional |
| Chart of accounts populated | Critical | At least one account exists |
| Tax codes configured | Warning | At least one tax code exists |
| FX closing rates for current period | Warning | If foreign currencies are registered, closing rates must be present |
| Customers created | Info | At least one active customer exists |
| Suppliers created | Info | At least one active supplier exists |

Results cache for 5 minutes. Critical failures block most transaction workflows.

---

### 27. Budgeting (Project.md Domain 1)

- **Budgets**: entity-scoped plans (name, status, notes) made up of **budget lines** — one row per
  (GL account, accounting period, amount). A budget's total is always derived from its lines, never
  client-supplied.
- **Lifecycle**: `DRAFT → APPROVED` or `→ VOID` (from either status). Corrections are void-and-recreate,
  matching this codebase's immutability-after-approval convention — a budget is never edited once
  `APPROVED`.
- **Validation**: every line's account must belong to the same entity, be active, and not be a
  header/summary account (IAS 1 §29 — the same rule journal posting enforces); every line's period
  must belong to the same entity.
- **Budget-vs-actual variance report**: for each line, "actual" is the net ledger movement for that
  account over that line's period date range, signed per the account's normal balance (the same
  signed-actual convention the Analytics Dashboard's trial-balance summary uses), so a budgeted
  amount and its actual are directly comparable regardless of whether the account is debit-normal
  (assets/expenses) or credit-normal (liabilities/equity/revenue). A budget never posts a journal
  entry — it is a planning artifact only, compared against the ledger at read time.

---

### 28. Cash & Bank Management (Project.md Domain 1)

- **Bank statement import**: a statement (bank account, statement date, opening/closing balance)
  made up of **statement lines** (date, description, signed amount — positive is a deposit/credit
  to the bank, negative a withdrawal/debit — reference). Import is a JSON array today; a full
  CSV/OFX parsing pipeline is out of scope for this first cut.
- **Matching**: each line is matched, manually or via a date/amount-tolerance auto-match, against
  one or more existing `LedgerEntry` rows for the statement's bank account — never against a
  duplicated copy of ledger data. A line's status is `UNMATCHED`, `MATCHED`, or `IGNORED`
  (with a mandatory reason).
- **Bank account validation**: the account a statement is imported against must be non-header,
  active, and `AccountSubtype.CASH_AND_EQUIVALENTS` — enforced server-side, not just in the
  frontend picker.
- **Reconciliation tie-out report**: the standard two-sided bank-reconciliation identity —
  `adjustedBookBalance = glBalance + bankOutstandingTotal` and
  `adjustedBankBalance = closingBalance + glOutstandingTotal` — both reduce to the same figure when
  every matched pair's amounts genuinely agree, so a non-zero difference is a real, surfaced gap,
  never silently adjusted away. This module never posts a journal entry — it compares existing
  ledger activity against a statement, it doesn't create new ledger activity.

### 29. Expense Management / T&E (Project.md Domain 1)

- **Expense claims**: an employee claim (employee, claim date, notes) made up of **claim lines**
  (expense account, description, amount, date incurred, optional receipt reference — a plain
  string/URL, not a full OCR/upload pipeline in this first cut).
- **Lifecycle**: `DRAFT → SUBMITTED → APPROVED → REIMBURSED`, or `SUBMITTED → REJECTED → DRAFT`
  (reopen for correction and resubmission, rather than cloning a new claim, since a rejected claim
  never posted anything).
- **Reimbursement posting**: on approval, posts a real, balanced journal entry — DR each line's
  expense account (merged by account, so two lines against the same account produce one journal
  line) / CR an Employee Reimbursements Payable account, reusing the existing `CURRENT_PAYABLE`
  account subtype (IAS 1 §54(k) — an approved-but-unpaid employee reimbursement is a current
  liability, economically identical in nature to a trade payable) rather than inventing a new one.
- **Segregation of duties, two independent checks**: the nominal beneficiary (`employeeId`) may not
  approve their own reimbursement, *and* the actual submitter (`createdBy`) may not approve a claim
  they filed — the second check still applies even when the claim was filed under a different
  employee's name (delegated submission — e.g. an assistant filing for an executive — stays
  allowed; only the maker-checker identity, not the nominal claimant, is enforced at approval).

### 30. Segregation of Duties — Maker-Checker (cross-cutting)

Every approval action in the system — journal entry posting, invoice approval, bill approval, budget
approval, and expense claim approval (see above) — requires that the approver be a **different
person** from whoever created the record, not merely someone holding an approver-tier role. Role
gating alone (`SENIOR_ACCOUNTANT`, `CONTROLLER_CFO`, etc.) never checked this: two users sharing the
same role could otherwise approve each other's work, or worse, their own. Enforced by a single
shared check (`SecurityUtils.requireNotSelfApproval`) compared against each record's
audit-populated `createdBy` (Spring Data JPA auditing — never a client-suppliable field), reused
everywhere rather than hand-rolled per module. The Global Approvals Queue (§19) inherits this
automatically, since it routes to the same underlying service methods.

---

## Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          QeSuite FA                                  │
│                                                                      │
│  ┌──────────────────┐    ┌─────────────────────────────────────────┐ │
│  │  fa-frontend     │    │  fa-backend                             │ │
│  │                  │    │                                         │ │
│  │  Vue 3 + Vite 6  │◄──►│  Kotlin + Spring Boot 3.3               │ │
│  │  Vue Router 4    │    │  Spring Security (JWT + API Key)        │ │
│  │  Tailwind CSS 4  │    │  Spring Data JPA + Hibernate            │ │
│  │  Custom UI kit   │    │  Spring Batch (background jobs)         │ │
│  └──────────────────┘    │  OpenAPI 3.0 / Swagger UI               │ │
│                          │  Resilience4j · Micrometer              │ │
│                          └──────────────┬──────────────────────────┘ │
│                                         │                            │
│         ┌────────────────────┬──────────┴──────────┐                │
│         │                   │                      │                │
│  ┌──────▼──────┐   ┌─────────▼──────┐   ┌──────────▼──────┐        │
│  │ PostgreSQL  │   │     Redis       │   │     MinIO        │        │
│  │ (Primary DB)│   │ (Cache /        │   │ (Object storage) │        │
│  │ + Flyway    │   │  Idempotency)   │   │ S3-compatible    │        │
│  └─────────────┘   └────────────────┘   └─────────────────┘        │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Nginx  (reverse proxy · load balancer · static file serving) │ │
│  └────────────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────────┘
```

### Backend Module Structure

```
com.qesuite.accounting
├── analytics          Dashboard KPIs, sparklines, revenue chart
├── ap                 Period management and 9-step cycle controller
├── approvals          Global approvals aggregation queue
├── assets             IAS 16 fixed assets, depreciation, disposal
├── banking            Bank statement import, GL matching, reconciliation tie-out
├── budgeting          Budgets, budget lines, budget-vs-actual variance reporting
├── coa                Chart of accounts, hierarchy, templates
├── expenses           Expense claims, approval routing, reimbursement posting
├── fx                 Currencies, exchange rates, FX revaluation (IAS 21)
├── invoicing          Customer invoices, credit notes, AR lifecycle, IFRS 15
├── journal            Journal entries, adjustments, period closing
├── ledger             GL posting, financial statements, trial balance
├── organization       Multi-entity organisation management
├── party              Customers and suppliers
├── payables           Vendor bills, debit notes, payment runs (AP)
├── payments           Payment lifecycle, matching, GL posting, M-Pesa hook
├── receipts           Receipt generation and delivery
├── source             Source document capture and classification
├── tax                Tax codes, effective-dated rates, calculation engine
├── users              Users, auth, sessions, password management, API keys
└── shared
    ├── audit          Forensic audit log (AOP @Auditable, insert-only)
    ├── categories     Configurable category values (payment terms/methods, document types)
    ├── codegen        Configurable entity number generation (13 modules)
    ├── compliance     IAS 1 compliance checker
    ├── docs           OpenAPI / Scalar API documentation controller
    ├── domain         BaseFinancialEntity (entityId, periodId, timestamps)
    ├── exceptions     Typed business-rule exceptions + global handler
    ├── idempotency    Redis-backed duplicate-request prevention
    ├── pdf            PDF report generation (OpenPDF)
    ├── security       JWT, RBAC, API key filter chain
    └── storage        MinIO / local file storage abstraction
```

### Frontend Module Structure

```
src/views
├── auth          Login, signup, forgot password, reset password
├── overview      Dashboard (KPIs, charts, approvals, activity feed)
├── ledger        Chart of accounts, periods, journal entries, source documents
├── parties       Customers, suppliers
├── assets        Fixed asset register, depreciation run
├── banking       Bank statement import and reconciliation
├── planning      Budgets and budget-vs-actual variance reporting
├── expenses      Expense claims and approval routing
├── revenue       Invoices, credit notes, payments, receipts, AR ageing
├── payables      Vendor bills, AP ageing
├── period-end    Trial balance, period-end workflow, FX revaluation
├── statements    Profit & loss, balance sheet, cash flow, close period
├── reports       T-account, sub-ledgers, audit trail, IAS 1, comparative TB
└── setup         Profile, organisation, users, API keys, tax & currency,
                  categories, security (sessions), system health
```

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Frontend Framework | Vue.js | 3.5 |
| Frontend Build Tool | Vite | 6.x |
| Frontend Styling | Tailwind CSS | 4.x |
| Frontend Routing | Vue Router | 4.5 |
| Backend Language | Kotlin | 1.9 |
| Backend Framework | Spring Boot | 3.3 |
| Security | Spring Security + JJWT | 6.x / 0.11.5 |
| Persistence | Spring Data JPA + Hibernate | 6.x |
| Database | PostgreSQL | 15+ |
| Database Migrations | Flyway | — |
| Cache / Idempotency | Redis (Spring Data Redis) | 7.x |
| Object Storage | MinIO (S3-compatible) | — |
| PDF Generation | OpenPDF | 1.3.38 |
| Email Dispatch | Spring Boot Mail (SMTP) | — |
| Background Jobs | Spring Batch | — |
| API Documentation | SpringDoc OpenAPI 3.0 / Swagger UI | 2.5.0 |
| Observability | Micrometer + Prometheus | — |
| Resilience | Resilience4j | 2.2.0 |
| Containerisation | Docker + Docker Compose | — |
| Reverse Proxy | Nginx | stable-alpine |
| DB Admin UI | pgAdmin 4 | — |
| Test Framework | MockK + Spring Security Test | — |

---

## Quick Start

### Prerequisites

- Docker and Docker Compose (recommended — no local Java or Node required)
- Or: Java 17+, Node.js 20+ (or Bun), PostgreSQL 15+, Redis 7+ for local development

### Full Stack with Docker Compose

```bash
git clone https://github.com/Mfuon2/financial-accounting-erp.git
cd financial-accounting-erp
docker compose up --build
```

Services started:

| Service | URL | Purpose |
|---|---|---|
| Application (SPA + API) | `http://localhost` | Vue frontend + REST API via Nginx |
| Swagger UI | `http://localhost/swagger-ui.html` | Interactive API documentation |
| MinIO Console | `http://localhost:9001` | Object storage administration |
| pgAdmin | `http://localhost:5050` | PostgreSQL database UI |

### Local Development

**Backend**

```bash
cd fa-backend
cp src/main/resources/application.example.properties \
   src/main/resources/application.properties
# Edit application.properties — set DB, Redis, storage, JWT, and mail config
./mvnw spring-boot:run
```

**Frontend**

```bash
cd fa-frontend
npm install        # or: bun install
npm run dev        # or: bun run dev
```

Frontend dev server: `http://localhost:5173`  
Backend API: `http://localhost:8080`

### Environment Variables

| Variable | Description | Default |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://db:5432/accounting` |
| `DB_USERNAME` | Database username | `accounting_user` |
| `DB_PASSWORD` | Database password | `accounting_pass` |
| `JWT_SECRET` | HS256 signing secret (32+ chars) | — |
| `JWT_EXPIRATION_MS` | Access token TTL in milliseconds | `86400000` (24 h) |
| `JWT_REFRESH_EXPIRATION_MS` | Refresh token TTL in milliseconds | `2592000000` (30 d) |
| `SPRING_DATA_REDIS_HOST` | Redis hostname | `redis` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `STORAGE_TYPE` | `minio` or `local` | `minio` |
| `MINIO_ACCESS_KEY` | MinIO access key | `minioadmin` |
| `MINIO_SECRET_KEY` | MinIO secret key | `minioadmin` |
| `MINIO_BUCKET` | MinIO bucket name | `qesuite-documents` |
| `MAIL_ENABLED` | Enable SMTP email | `false` |
| `MAIL_HOST` | SMTP server hostname | `smtp.gmail.com` |
| `MAIL_PORT` | SMTP port | `587` |
| `MAIL_USERNAME` | SMTP username | — |
| `MAIL_PASSWORD` | SMTP password | — |
| `MAIL_FROM` | From address for outbound email | `noreply@qesuite.com` |
| `APP_BASE_URL` | Public base URL (used in email links) | `http://localhost` |
| `VITE_API_BASE_URL` | Backend base URL for the Vue frontend | `http://localhost:8080` |

---

## IFRS Compliance

| Standard | Implementation |
|---|---|
| IAS 1 | Balance Sheet, P&L, and Cash Flow statements; IAS 1 classification consistency checker |
| IAS 7 | Statement of Cash Flows — indirect method with operating/investing/financing classifications |
| IAS 16 | Fixed asset register, Straight-Line and Double-Declining Balance depreciation, asset disposal with gain/loss posting |
| IAS 21 | Multi-currency transaction recording, FX revaluation (unrealised gains/losses), realised FX differences on payment settlement |
| IFRS 15 | 5-step revenue recognition model on invoice approval; point-in-time recognition fully implemented; over-time (deferred revenue) structured for period-end activation |

QeSuite FA provides a technical framework intended to support IFRS compliance. Actual compliance depends on correct configuration, data entry, and qualified accounting oversight. See the [Disclaimer](#disclaimer) section.

---

## API Reference

Full interactive documentation via OpenAPI 3.0 once the backend is running:

```
http://localhost:8080/swagger-ui.html
```

A Postman collection is included in the repository:
`fa-backend/QeSuite IFRS Financial Accounting API.postman_collection.json`

### Endpoint Summary

```
Authentication
  POST   /api/v1/auth/login                       Authenticate — returns access + refresh tokens
  POST   /api/v1/auth/register                    Register a new user
  POST   /api/v1/auth/refresh                     Rotate refresh token
  POST   /api/v1/auth/logout                      Revoke all sessions
  POST   /api/v1/auth/change-password             Change password (revokes all sessions)
  POST   /api/v1/auth/forgot-password             Initiate password reset email
  POST   /api/v1/auth/reset-password              Complete reset via one-time token
  POST   /api/v1/auth/verify-email/{userId}       Verify email address
  GET    /api/v1/auth/sessions                    List active sessions
  DELETE /api/v1/auth/sessions/{id}               Revoke a specific session
  POST   /api/v1/auth/sessions/revoke-all-others  Keep current session, revoke all others

Users
  GET    /api/v1/users                  List users (admin)
  POST   /api/v1/users                  Create user (admin)
  GET    /api/v1/users/{id}             Get user by ID
  PUT    /api/v1/users/{id}/role        Assign role
  PUT    /api/v1/users/{id}/profile     Update profile
  POST   /api/v1/users/{id}/deactivate  Deactivate user
  POST   /api/v1/users/{id}/reactivate  Reactivate user

API Keys
  POST   /api/v1/api-keys               Create API key
  GET    /api/v1/api-keys               List API keys
  GET    /api/v1/api-keys/{id}          Get API key
  POST   /api/v1/api-keys/{id}/revoke   Revoke key
  POST   /api/v1/api-keys/{id}/rotate   Rotate key (revoke + issue new)

Organisation
  POST   /api/v1/organizations          Create organisation (system admin)
  GET    /api/v1/organizations          List organisations (system admin)
  GET    /api/v1/organizations/me       Get own organisation
  PUT    /api/v1/organizations/me       Update organisation profile
  POST   /api/v1/organizations/{id}/suspend   Suspend
  POST   /api/v1/organizations/{id}/activate  Reactivate

Chart of Accounts
  POST   /api/v1/coa/accounts                    Create account
  GET    /api/v1/coa/accounts                    List accounts (filterable by type/subtype/status)
  GET    /api/v1/coa/accounts/{id}               Get account
  PUT    /api/v1/coa/accounts/{id}               Update account
  POST   /api/v1/coa/accounts/{id}/deactivate    Deactivate account
  GET    /api/v1/coa/accounts/hierarchy          Full account tree
  GET    /api/v1/coa/accounts/templates          Available COA templates
  POST   /api/v1/coa/accounts/import             Import from template
  POST   /api/v1/coa/accounts/validate-code      Validate proposed code
  GET    /api/v1/coa/accounts/{id}/balance       Live account balance

Periods
  GET    /api/v1/periods                            List periods
  GET    /api/v1/periods/{id}                       Get period
  POST   /api/v1/periods/generate-fiscal-year       Generate 12 periods for a year
  POST   /api/v1/periods/{id}/transition            Advance period status

Accounting Cycle
  POST   /api/v1/accounting-cycle/run               Run full 9-step cycle for a period
  POST   /api/v1/accounting-cycle/transition        Manually advance cycle step
  GET    /api/v1/accounting-cycle/validate-step     Dry-run step validation

Source Documents
  POST   /api/v1/source-documents                   Create source document
  GET    /api/v1/source-documents                   List documents
  GET    /api/v1/source-documents/{id}              Get document
  PUT    /api/v1/source-documents/{id}              Update draft document
  POST   /api/v1/source-documents/{id}/submit       Submit for review
  POST   /api/v1/source-documents/{id}/review       Mark reviewed
  POST   /api/v1/source-documents/{id}/approve      Approve
  POST   /api/v1/source-documents/{id}/archive      Archive
  POST   /api/v1/source-documents/{id}/void         Void
  POST   /api/v1/source-documents/{id}/restore      Restore from void/archive
  POST   /api/v1/source-documents/{id}/classify     Classify transaction

Journal Entries
  POST   /api/v1/journal-entries                    Create journal entry
  GET    /api/v1/journal-entries                    List entries (filterable)
  GET    /api/v1/journal-entries/{id}               Get entry
  PUT    /api/v1/journal-entries/{id}               Update draft entry
  POST   /api/v1/journal-entries/{id}/submit        Submit for approval
  POST   /api/v1/journal-entries/{id}/approve       Approve
  POST   /api/v1/journal-entries/{id}/reject        Reject back to draft
  POST   /api/v1/journal-entries/{id}/post          Post to general ledger
  POST   /api/v1/journal-entries/{id}/reverse       Reverse a posted entry
  GET    /api/v1/journal-entries/{id}/audit-trail   Entry-level audit history

Adjusting Entries
  POST   /api/v1/adjustments/accruals               Post accrual entry
  POST   /api/v1/adjustments/deferrals              Post deferral entry
  POST   /api/v1/adjustments/prepayments/amortize   Batch-amortise prepaid accounts
  POST   /api/v1/adjustments/unearned/recognize     Recognise unearned revenue

Closing
  POST   /api/v1/closing/run                        Run period closing entries
  POST   /api/v1/closing/reopen                     Reopen a closed period

Customers
  POST   /api/v1/customers                          Create customer
  GET    /api/v1/customers                          List customers
  GET    /api/v1/customers/{id}                     Get customer
  PUT    /api/v1/customers/{id}                     Update customer
  POST   /api/v1/customers/{id}/deactivate          Deactivate customer

Suppliers
  POST   /api/v1/suppliers                          Create supplier
  GET    /api/v1/suppliers                          List suppliers
  GET    /api/v1/suppliers/{id}                     Get supplier
  PUT    /api/v1/suppliers/{id}                     Update supplier
  POST   /api/v1/suppliers/{id}/deactivate          Deactivate supplier
  GET    /api/v1/suppliers/{id}/statement           Supplier account statement

Invoices (AR)
  POST   /api/v1/invoices                           Create invoice (DRAFT)
  GET    /api/v1/invoices                           List invoices
  GET    /api/v1/invoices/{id}                      Get invoice
  POST   /api/v1/invoices/{id}/approve              Approve and post AR journal
  POST   /api/v1/invoices/{id}/void                 Void invoice
  POST   /api/v1/invoices/{id}/credit-note          Issue credit note
  POST   /api/v1/invoices/{id}/payment              Record payment against invoice
  GET    /api/v1/invoices/ar-ageing                 AR aging report

Payments
  POST   /api/v1/payments                           Create payment (PENDING)
  GET    /api/v1/payments                           List payments
  GET    /api/v1/payments/{id}                      Get payment
  POST   /api/v1/payments/{id}/match                Match to invoice(s)
  POST   /api/v1/payments/{id}/approve              Approve matched payment
  POST   /api/v1/payments/{id}/post                 Post to GL (auto-generates receipt)
  POST   /api/v1/payments/{id}/reverse              Reverse a posted payment
  POST   /api/v1/payments/mpesa/callback            M-Pesa STK push callback

Receipts
  POST   /api/v1/receipts/generate                  Generate receipt for a posted payment
  GET    /api/v1/receipts                           List receipts
  GET    /api/v1/receipts/{id}                      Get receipt
  POST   /api/v1/receipts/{id}/issue                Issue (trigger email delivery)
  POST   /api/v1/receipts/{id}/void                 Void receipt
  GET    /api/v1/receipts/by-payment/{paymentId}    Get receipt for a payment

Vendor Bills (AP)
  POST   /api/v1/bills                              Create vendor bill (DRAFT)
  GET    /api/v1/bills                              List bills
  GET    /api/v1/bills/{id}                         Get bill
  POST   /api/v1/bills/{id}/approve                 Approve and post AP journal
  POST   /api/v1/bills/{id}/void                    Void bill
  POST   /api/v1/bills/{id}/payment                 Record single bill payment
  POST   /api/v1/bills/payment-run                  Batch payment run (multiple vendors)
  GET    /api/v1/bills/ageing                       AP aging report

Fixed Assets
  POST   /api/v1/assets                             Create fixed asset
  GET    /api/v1/assets                             List assets
  GET    /api/v1/assets/{id}                        Get asset
  PUT    /api/v1/assets/{id}                        Update asset
  POST   /api/v1/assets/{id}/dispose                Dispose asset (posts disposal journal)
  POST   /api/v1/assets/batch-depreciate            Run batch depreciation for a period

Ledger
  GET    /api/v1/ledger/accounts/{id}/entries       Ledger entries for an account
  GET    /api/v1/ledger/accounts/{id}/t-account     T-account view
  GET    /api/v1/ledger/entries/{id}                Get ledger entry
  GET    /api/v1/ledger/subsidiary/customer/{id}    Customer sub-ledger
  GET    /api/v1/ledger/subsidiary/supplier/{id}    Supplier sub-ledger
  GET    /api/v1/ledger/subsidiary/asset/{id}       Asset sub-ledger
  POST   /api/v1/ledger/depreciate                  Post depreciation entry

Trial Balance
  GET    /api/v1/trial-balance                      Unadjusted trial balance
  GET    /api/v1/trial-balance/adjusted             Adjusted trial balance
  GET    /api/v1/trial-balance/comparative          Comparative trial balance (two periods)

Financial Statements
  GET    /api/v1/statements/balance-sheet           Balance sheet (as at date)
  GET    /api/v1/statements/profit-loss             Profit and loss (date range)
  GET    /api/v1/statements/cash-flow               Statement of cash flows (indirect method)
  GET    /api/v1/statements/balance-sheet/pdf       Balance sheet PDF export
  GET    /api/v1/statements/profit-loss/pdf         Profit and loss PDF export
  GET    /api/v1/statements/cash-flow/pdf           Cash flow PDF export

FX
  POST   /api/v1/fx/currencies                      Register currency
  GET    /api/v1/fx/currencies                      List currencies
  PUT    /api/v1/fx/currencies/{id}                 Update currency
  POST   /api/v1/fx/exchange-rates                  Add exchange rate
  GET    /api/v1/fx/exchange-rates                  List exchange rates
  PUT    /api/v1/fx/exchange-rates/{id}             Update exchange rate
  GET    /api/v1/fx/revaluation/preview             Preview FX revaluation (unrealised gain/loss)
  POST   /api/v1/fx/revaluation                     Post FX revaluation journal

Tax
  POST   /api/v1/tax/codes                          Create tax code
  GET    /api/v1/tax/codes                          List tax codes
  GET    /api/v1/tax/codes/{id}                     Get tax code
  PUT    /api/v1/tax/codes/{id}                     Update tax code
  POST   /api/v1/tax/rates                          Add tax rate (effective-dated)
  GET    /api/v1/tax/rates                          List rates
  POST   /api/v1/tax/calculate                      Calculate tax for a line amount

Analytics and Compliance
  GET    /api/v1/analytics/dashboard                KPIs, sparklines, charts, activity
  GET    /api/v1/approvals                          Global pending approvals queue
  GET    /api/v1/compliance/ias1                    IAS 1 compliance checks

Audit
  GET    /api/v1/audit-logs                         Forensic audit trail (paginated, filterable)

Document Numbering
  GET    /api/v1/number-configs                     List number configurations per entity
  PUT    /api/v1/number-configs/{module}            Update number format for a document type
```

---

## Contributing

Contributions from accountants, developers, and finance technologists are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for:

- Development environment setup
- Code style guidelines
- How to submit pull requests
- How to report bugs and propose features

Issues tagged [`good first issue`](https://github.com/Mfuon2/financial-accounting-erp/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) are a good starting point for first-time contributors.

---

## Security

For responsible disclosure of security vulnerabilities, please read [SECURITY.md](SECURITY.md).

Do not file public GitHub Issues for security vulnerabilities.

---

## Roadmap

See [ROADMAP.md](ROADMAP.md) for the phased delivery plan, known gaps, and planned work.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

You are free to use, modify, and distribute this software under the terms of the GPL-3.0. Any derivative work must also be distributed under the same license. See [LICENSE](LICENSE) for the full license text.

---

## Disclaimer

QeSuite FA is open-source software provided "as is", without warranty of any kind.

This software is a technical accounting framework and is not a substitute for professional accounting, legal, tax, or financial advice. Organisations using this software bear sole responsibility for:

- Ensuring the system is correctly configured for their jurisdiction and applicable accounting standards
- Verifying the accuracy of all financial data entered into the system
- Engaging qualified accounting professionals to review financial statements produced by the system
- Compliance with local tax laws, reporting requirements, and audit obligations

The authors, contributors, and maintainers of QeSuite FA shall not be liable for any direct, indirect, incidental, special, exemplary, or consequential damages arising in any way from the use of this software.

Use in production financial systems is entirely at your own risk.

---

## Contact and Community

- **Repository:** [github.com/Mfuon2/financial-accounting-erp](https://github.com/Mfuon2/financial-accounting-erp)
- **Issues and Feature Requests:** [GitHub Issues](https://github.com/Mfuon2/financial-accounting-erp/issues)
- **Discussions:** [GitHub Discussions](https://github.com/Mfuon2/financial-accounting-erp/discussions)
- **Security:** See [SECURITY.md](SECURITY.md)

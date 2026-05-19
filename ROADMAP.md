# QeSuite FA — Roadmap

This document describes what is currently implemented, what gaps are confirmed in the codebase, and the planned delivery phases for resolving them.

---

## Current State — What Is Implemented

The following modules and capabilities are fully implemented in the current codebase:

**Core Accounting Engine**
- Immutable double-entry general ledger with hard mismatch enforcement
- 9-step accounting cycle controller with step validation
- Hierarchical chart of accounts with IFRS category mapping and header-account enforcement
- COA templates (service, merchandising, manufacturing, financial services, non-profit)
- Journal entry engine: draft → approve → post with reversal support and fiscal-year scoped reference numbers
- Adjusting entries: accruals, deferrals, batch prepayment amortisation, unearned revenue recognition
- Trial balance: unadjusted and adjusted, with abnormal-balance flagging and mismatch validation
- Period management: FUTURE → OPEN → ADJUSTING → CLOSED, with period-lock interceptor
- Period closing: 4-step closing entries (Revenue, Expenses, Income Summary, Dividends → Retained Earnings)
- Financial statements: Profit and Loss, Balance Sheet, Statement of Cash Flows (IAS 7 indirect method)
- PDF export for all three financial statements

**Revenue Cycle (AR)**
- Customer invoices: full lifecycle from DRAFT through PAID, VOID, and CREDIT_NOTE
- IFRS 15 revenue recognition: 5-step model; point-in-time recognition fully implemented; over-time (deferred revenue) structured
- Credit notes: full and partial, with GL reversal
- Payments: PENDING → MATCHED → APPROVED → POSTED, with partial matching and reversal
- Automatic receipt generation on payment posting; receipt issue and void lifecycle
- AR aging report (5 buckets)
- M-Pesa STK push callback endpoint with suspense-payment handling

**Accounts Payable (AP)**
- Vendor bills: full lifecycle from DRAFT through PAID and VOID
- AP journal auto-posted on bill approval
- Debit notes (purchase credit notes) with AP balance reversal
- Single and batch payment runs
- AP aging report (5 buckets)
- Supplier account statement (running-balance view of bills, debit notes, and payments)
- Duplicate bill detection within configurable monetary tolerance

**Fixed Assets (IAS 16)**
- Asset register with three COA account mappings (cost, accumulated depreciation, depreciation expense)
- Straight-Line and Double-Declining Balance depreciation; capped at remaining depreciable balance
- Batch depreciation run for a period; asset disposal with gain/loss posting
- Asset lifecycle: ACTIVE → FULLY_DEPRECIATED → DISPOSED

**Multi-Currency and FX (IAS 21)**
- Currency registry with functional currency designation
- Date-effective exchange rates (SPOT, CLOSING) with fallback logic
- FX revaluation preview (unrealised gain/loss calculation) and posting
- Realised FX differences on payment settlement

**Parties, Tax, and Configuration**
- Customer and supplier master data with auto-generated codes, credit limits, and payment terms
- Tax codes (OUTPUT, INPUT, EXEMPT, WHT) with effective-dated rates and recoverable flag
- Line-level tax computation by tax code or direct rate ID
- Configurable document numbering for 13 document types
- Source document capture, classification, and file attachments (MinIO or local)

**Authentication, Users, and Security**
- JWT authentication with refresh token rotation (RFC 6749); tokens stored as SHA-256 hashes
- 5-attempt account lockout with 30-minute auto-expiry
- Password policy enforcement (min 8 chars, uppercase, digit, special character)
- Six RBAC roles with per-endpoint enforcement
- Session listing and per-session revocation
- API key management (create, revoke, rotate) with separate filter chain
- Forget/reset password with single-use token and session revocation on reset

**Analytics, Approvals, Compliance, and Audit**
- Dashboard: live KPIs, 12-month sparklines, revenue/expense chart, activity feed, AR ageing widget
- Global approvals queue aggregating journals, invoices, and bills into one endpoint
- IAS 1 compliance checker (4 automated checks)
- Insert-only forensic audit trail with AOP-driven capture and full payload snapshots
- Setup health checks (7 checks across periods, currencies, COA, tax, FX, customers, suppliers)

**Infrastructure**
- Docker Compose stack: PostgreSQL, Redis, MinIO, Nginx, pgAdmin, backend, frontend
- Flyway schema migrations (37 versioned migrations)
- Idempotency protection (Redis-backed) on critical mutating endpoints
- Email service: password reset, receipt delivery, user invite (feature-flagged, async)
- Prometheus metrics and Resilience4j circuit breaker
- OpenAPI 3.0 documentation with Swagger UI

---

## Confirmed Gaps — Current Codebase

The following items have been verified as missing or incomplete through direct source code inspection:

### Tier 1 — Blocks Core UI Functionality

| # | Gap | Detail |
|---|---|---|
| 1 | Comparative trial balance stub | `GET /api/v1/trial-balance/comparative` endpoint returns an empty/stub response. The UI view exists; the service logic is not yet implemented. |
| 2 | `PUT /api/v1/fx/currencies/{id}` missing | The FX currencies edit button in the UI has no target endpoint. |
| 3 | `PUT /api/v1/fx/exchange-rates/{id}` missing | Exchange rate inline editing has no target endpoint. |
| 4 | `PUT /api/v1/tax/codes/{id}` missing | Tax code edit button has no target endpoint. |
| 5 | `PUT /api/v1/assets/{id}` missing | Fixed asset edit form has no target endpoint. |
| 6 | RBAC gap on `/api/v1/organizations/me` | The `GET` and `PUT` for a regular admin's own organisation are blocked by an overly restrictive RBAC guard that was written for the system-admin listing endpoint. Regular `SYSTEM_ADMIN` users within an entity cannot access their own organisation profile. |

### Tier 2 — Significant UX Gaps

| # | Gap | Detail |
|---|---|---|
| 7 | Standalone credit notes list | `GET /api/v1/credit-notes` does not exist as a standalone endpoint. Credit notes are issued against invoices but cannot be listed independently. The `CreditNotes.vue` view cannot be populated. |
| 8 | Closing preview | `GET /api/v1/closing/preview` is not implemented. The period-close gate check before committing closing entries is missing. |
| 9 | Source document restore | `POST /api/v1/source-documents/{id}/restore` is defined in the design but not yet implemented in the controller or service. |
| 10 | OVER_TIME revenue recognition | The IFRS 15 over-time recognition path stores obligations in Deferred Revenue but the period-end job that incrementally recognises them has not been built. Point-in-time works correctly. |
| 11 | M-Pesa session lookup | The M-Pesa callback suspense logic uses hardcoded sentinel UUIDs for entityId/customerId/periodId. A production STK push session table (keyed on `checkoutRequestId`) is needed so callbacks can be matched to the originating entity and customer. |

### Tier 3 — Infrastructure Gaps

| Feature | Status | Unblocks |
|---|---|---|
| External FX rate feed | Not implemented | "Refresh rates" button in FX UI — currently rates are entered manually. |
| Bulk source document upload | Not implemented | `POST /api/v1/source-documents/bulk-upload` — requires file-upload pipeline. |
| AR statement email send | Not implemented | `POST /api/v1/invoices/ar-ageing/send-statements` — requires email infra to be enabled and a statement PDF renderer. |
| Receipt resend | Not implemented | `POST /api/v1/receipts/{id}/resend` — blocked on email being enabled. |
| Background job queue | Partial | Depreciation and FX revaluation currently run synchronously in the request thread; Spring Batch is declared as a dependency but the job scheduling pipeline is not wired. At scale, long-running batch operations should be offloaded. |
| Historical period balance snapshots | Not implemented | Comparative statements currently re-compute from live ledger entries. A snapshot table would make multi-year comparisons faster and protect against retroactive data changes. |
| COA template seed data | Not implemented | The template list endpoint returns structure but COA templates need seed data in the database for the signup wizard to be functional end-to-end. |

---

## Phase Plan

### Phase 1 — CRUD Completeness (unblock all UI edit buttons)

All Tier 1 gaps. Small, bounded changes to existing controllers.

- `PUT /api/v1/assets/{id}` — add to `AssetController`
- `PUT /api/v1/tax/codes/{id}` — add to `TaxController`
- `PUT /api/v1/fx/currencies/{id}` — add to `FxController`
- `PUT /api/v1/fx/exchange-rates/{id}` — add to `FxController`
- Fix RBAC on `GET|PUT /api/v1/organizations/me` for entity-scoped `SYSTEM_ADMIN` role
- Implement `GET /api/v1/trial-balance/comparative` (service logic against two periods)

**Estimated scope**: 6 targeted changes to existing files. No new modules or infrastructure.

---

### Phase 2 — Workflow Completeness

Tier 2 gaps that block specific user workflows.

- Standalone credit notes: add `GET /api/v1/credit-notes` (and optionally `GET /api/v1/credit-notes/{id}`) by querying invoices where `type = CREDIT_NOTE`.
- Closing preview: implement `GET /api/v1/closing/preview` to calculate what the closing entries would be without committing them.
- Source document restore: implement `POST /api/v1/source-documents/{id}/restore` in service and controller.
- OVER_TIME revenue recognition: build the period-end recognition job that reads obligations from the Deferred Revenue account and posts the earned portion.

---

### Phase 3 — Infrastructure Hardening

Enables features that currently require manual workarounds.

- **COA seed data**: populate the database with the five pre-built COA template datasets so the signup wizard works end-to-end.
- **External FX rate feed**: integrate with a public exchange rate API (e.g., Open Exchange Rates, Fixer.io) to allow one-click rate refresh in the FX UI.
- **Background job pipeline**: wire Spring Batch properly so depreciation runs, FX revaluations, and future batch operations are queued and executed outside the HTTP request thread. Expose job status endpoints.
- **Historical period snapshots**: add a `period_balances` snapshot table populated at period close, enabling accurate multi-year comparative reporting without re-computing from the full ledger history.

---

### Phase 4 — M-Pesa Production Readiness

Applicable to deployments using the M-Pesa Daraja API.

- Build the STK push session table: when a push is initiated, store `checkoutRequestId → (entityId, customerId, periodId)`.
- Update the M-Pesa callback handler to look up the session record and replace the suspense sentinel IDs with the real values.
- Add an M-Pesa callback log view to the UI (`GET /api/v1/payments/mpesa/callbacks`).
- Expose the STK push initiation endpoint (`POST /api/v1/payments/mpesa/stk-push`).

---

### Phase 5 — Communication and Document Delivery

Depends on Phase 3 infrastructure being enabled.

- AR statement email: `POST /api/v1/invoices/ar-ageing/send-statements` — generates a per-customer statement PDF and sends it via email.
- Receipt resend: `POST /api/v1/receipts/{id}/resend` — re-sends the receipt email to the delivery address on record.
- Bulk source document upload: `POST /api/v1/source-documents/bulk-upload` — allows batch ingestion of supporting documents.

---

### Phase 6 — Extended Standards Coverage (Future)

Larger scope items planned for future milestones. None of these are currently partially implemented.

- **IFRS 16 Lease accounting**: right-of-use asset, lease liability, amortisation schedule, interest computation.
- **IAS 36 Impairment**: impairment indicators, recoverable amount calculation, impairment loss posting.
- **IAS 37 Provisions**: probable obligations, provision creation, utilisation, and reversal.
- **IAS 38 Intangible assets**: amortisation of intangible assets with finite useful lives.
- **Consolidation**: inter-company elimination, minority interest, consolidated financial statements across entities.
- **Bank reconciliation**: import bank statement transactions, match against ledger entries, flag unreconciled items.
- **Payroll journal import**: map payroll system outputs to journal entries automatically.
- **Role-level data filters**: restrict which accounts, cost centres, or business units a user can view, beyond the current entity-level isolation.

---

## How to Contribute to a Phase

1. Pick an item from the phase you want to work on.
2. Open a GitHub Issue referencing the item.
3. Follow [CONTRIBUTING.md](CONTRIBUTING.md) for branch naming, PR format, and test requirements.
4. Each API change requires an updated Postman collection entry and an OpenAPI annotation update.
5. Bug reports specific to implemented functionality should use the [BUG_REPORT.md](BUG_REPORT.md) template.

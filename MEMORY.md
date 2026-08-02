# MEMORY.md — Project Memory

**Last updated:** 2026-08-02 (Phase 0 period-management fix + ApiKeys wiring taken through full
governance gate — first module to complete Engineering + Accounting sign-off under this model)

This is the single source of truth for "where things stand." Every completed unit of work updates
this file per AGENTS.md's Delivery Manager responsibilities. See [workplan.md](workplan.md) for the
phase plan this memory tracks progress against, and [Project.md](Project.md) for the full six-domain
vision this project is working toward.

---

## Current Milestone

**Phase 0 — Stabilize & Govern.** The repository already contains a substantial, working product
(QeSuite FA). Before any Project.md-scale expansion, the immediate milestone is: reconcile
uncommitted work, resolve confirmed critical bugs, and put the three-agent governance model into
effect for all subsequent work. See `workplan.md` Phase 0.

## Current Sprint

Not yet started under the new governance model — this baseline was just established. First sprint
should be scoped by the Delivery Manager from Phase 0's task list in `workplan.md`.

---

## Product Baseline — What's Already Built

QeSuite FA is a Kotlin/Spring Boot 3.3 + Vue 3 IFRS financial accounting system. Full detail in
`README.md`. Condensed inventory:

- **Core ledger:** immutable double-entry GL, 9-step accounting cycle controller, hierarchical COA
  with IFRS categories and 5 pre-built templates, journal entries (draft→approve→post→reverse),
  adjusting entries (accruals/deferrals/prepayment amortisation/unearned revenue), trial balance
  (unadjusted/adjusted) with mismatch enforcement, period management with lock enforcement, 4-step
  period closing, financial statements (P&L, Balance Sheet, Cash Flow/IAS 7 indirect) with PDF
  export.
- **AR (revenue cycle):** customer invoices with IFRS 15 point-in-time recognition (over-time is
  structured but not yet posting — see Known Gaps), credit notes, payments with partial matching,
  auto-generated receipts, AR ageing, M-Pesa STK push hook (session lookup still sentinel-based).
- **AP:** vendor bills, debit notes, single + batch payment runs, supplier statements, AP ageing,
  duplicate-bill detection.
- **Fixed Assets (IAS 16):** asset register, straight-line + double-declining depreciation (capped
  correctly), batch depreciation runs, disposal with gain/loss posting.
- **Multi-currency/FX (IAS 21):** currency registry, functional currency, date-effective rates
  (spot/closing with fallback), revaluation preview + posting, realised FX on settlement.
- **Tax:** codes (output/input/exempt/WHT), effective-dated rates, line-level calculation.
- **Platform basics:** JWT + refresh rotation auth, 6 RBAC roles, sessions, API keys, forensic
  insert-only audit trail (AOP `@Auditable`), global approvals queue, IAS 1 compliance checker (4
  checks), 7-check setup health, configurable document numbering (13 types), Redis idempotency,
  MinIO/local file storage, async email (feature-flagged), OpenAPI/Swagger docs, Docker Compose
  full stack.
- **Frontend:** dashboard with live KPIs/sparklines/activity feed, full views for every module
  above, custom high-density UI kit (primitives, data-display, overlays, tables), demo/production
  mode toggle throughout.

## Gap Analysis vs. Project.md's Six Domains

| Domain | Status | Notes |
|---|---|---|
| 1. Financial Operations | **~80% built** | Core GL/AP/AR/Assets/FX/Tax solid. Missing: dedicated Budgeting module, dedicated Cash/Bank Management (bank statement import + reconciliation matching), dedicated Expense Management (T&E). |
| 2. Enterprise Finance | **Not started** | Multi-entity isolation exists; no consolidation, no intercompany elimination, no treasury, no cost/project accounting, no shared services, no FP&A. Single-entity financial close only. |
| 3. Operational Finance | **Not started** | No procurement/purchasing module, no 3-way PO match, no contracts, no inventory accounting, no subscription billing. Billing exists only as customer invoicing (AR). |
| 4. Governance & Compliance | **Partial** | Forensic audit trail and IAS 1 checker exist. No configurable workflow engine (approvals are status-based, not dynamically routed), no formal SoD enforcement, no risk management module, no regulatory reporting framework beyond financial statements. |
| 5. Intelligence | **Minimal** | Dashboard KPIs/sparklines exist. No AI capability of any kind yet — no copilot, forecasting, anomaly/fraud detection, or auto-reconciliation. |
| 6. Platform Services | **Partial** | REST API + OpenAPI, JWT/RBAC security, multi-currency, MinIO document storage exist. Missing: true multi-tenant SaaS control plane, event bus, GraphQL, integration hub/webhooks beyond one M-Pesa hook, notification engine (email only), OCR, localization/multi-language, extensibility/plugin framework. |

**Reality check:** Project.md describes a multi-year, large-team commercial ERP effort comparable
to Oracle Fusion / SAP S/4HANA. This repository is a strong, correctly-built single-domain MVP —
roughly the foundation of Domain 1. The honest read is: harden and complete Domain 1, then extend
outward domain by domain per `workplan.md`, rather than attempting all six domains at once.

---

## Modules In Progress — RESOLVED

The uncommitted-work reconciliation flagged at baseline is resolved: everything that was sitting in
the working tree has now been committed to `main`, in this order:
- `c78b701` — governance docs adopted (`CLAUDE.md`, `AGENTS.md`, `MEMORY.md`, `SKILLS.md`,
  `workplan.md`, `Project.md` added; `ROADMAP.md`/`BUG_REPORT.md`/`BUG_REPORT_V2.md` deletions
  committed — content already folded forward, recoverable via `git show f75f61b:<file>` if deeper
  historical detail is ever needed).
- `84535d9` — auth pages redesign (`Login.vue`, `Signup.vue`, `ForgotPassword.vue`, `base.css`, new
  `auth-bg.jpg`) — cosmetic only, no business/API logic touched, so per CLAUDE.md §16.5 this needed
  only Engineering sign-off, which it has (build verified clean).
- `f9801ca` — ApiKeys view wired to real endpoints with revoke-reason and filtering (`users.js`,
  `ApiKeys.vue`) — Financial Systems Architect independently re-derived the wiring and confirmed it
  is real (**APPROVED**).
- `7084460`, `6cf3261`, `dde9c5c`, `5e2f87b` — period-management fix, see "Known Issues" and
  "Outstanding Reviews" below for the full account.

Working tree is clean as of this update (aside from an untracked, out-of-scope
`fa-frontend/package-lock.json`).

## Known Issues / Technical Debt

Carried forward from the (uncommitted-deleted) internal gap analysis and bug reports, re-verified
where noted below. First Phase 0 task was to confirm which are still open — see the
period-management line below for the result.

**RESOLVED — Periods module (BUG-24/27/34), confirmed fixed and verified end-to-end:**
Fiscal-year generation defaulting to a nonsensical year (BUG-24), silent working-context switching
on historical fiscal-year generation (BUG-27), and the missing fiscal-year switcher UI (BUG-34) are
fixed as of commits `7084460`/`dde9c5c`/`5e2f87b`, confirmed by the Financial Systems Architect's
two-pass review (first pass rejected the initial fix; second pass, after corrections, independently
re-derived every claim from current file contents and re-ran `mvn clean test` + `npm run build`,
verdict **APPROVED end-to-end**). Detail:
- The Swagger docs claiming January auto-opens were themselves stale/false — the actual code already
  correctly set all 12 periods to `FUTURE` (a pre-existing fix predating this session); the docs were
  corrected to match reality, not the behavior.
- `Periods.vue`'s fiscal-year default made data-driven instead of hardcoded (BUG-24); a
  dynamically-populated fiscal-year filter plus a persistent "Active period" banner added (BUG-34);
  misleading modal copy fixed.
- First review pass caught 3 real defects and did not approve: `PeriodServiceTest.kt` and
  `PeriodControllerTest.kt` still asserted the **old buggy** behavior (a genuine pre-existing
  test-suite gap, only caught because the reviewer ran `mvn test`, not just `mvn compile`);
  `PeriodService.kt`'s duplicate-fiscal-year guard threw `ValidationException` (400) instead of the
  documented `ConflictException` (409); and a real regression in `useActivePeriod.js`, which returned
  a `FUTURE` period as "active" when none is actually `OPEN`/`ADJUSTING`/`CLOSING` — unreachable
  before this fix (January always auto-opened) but exposed by the correct BUG-27 fix. All three were
  corrected and re-verified in the second pass; `Dashboard.vue`'s fallback copy was corrected to
  match the `useActivePeriod.js` fix.

**RESOLVED — BUG-25 (single-open-period enforcement), verified by actually running it, not
read-through:** In its first review pass, the Financial Systems Architect wrote and ran temporary
MockK tests directly against the real `PeriodService` (not against mocks of the rule itself) and
confirmed: attempting to transition a second period to `OPEN` while one is already `OPEN` for the
same entity throws `BusinessRuleViolationException` with `errorCode=PERIOD_ALREADY_OPEN`,
`httpStatus=422`, and leaves the target period unmutated — surefire reported `Tests run: 3,
Failures: 0`. This confirms the single-open-period control genuinely works today, independent of and
prior to the BUG-24/27/34 fixes above. **Caveat:** the verification tests were temporary and were
deleted after the run (per the reviewer's own report) — the permanent `PeriodServiceTest.kt` suite
(6/6 passing after this session's fixes) has not been confirmed to include an equivalent *permanent*
regression test for this specific scenario. Treat the *behavior* as verified-correct today, but log
"add a permanent `PERIOD_ALREADY_OPEN` regression test to `PeriodServiceTest.kt`" as a small
follow-up so a future change can't silently regress this without the suite catching it.

**Critical (from BUG_REPORT_V2, still unverified against current code):**
- Journal Entries: critical issue logged as BUG-29 (detail not preserved in this summary — see
  `git show HEAD:BUG_REPORT_V2.md` for full text).
- Comparative Trial Balance: critical issue BUG-33, consistent with the confirmed stub gap below.
- Dashboard stepper: misleading progress display (BUG-HC-01, confirmed with visual evidence).
- Several medium/low findings: hardcoded default tax codes, hardcoded demo data (513-line
  `data/index.js`), hardcoded sparkline label format, English-only step names, stale period status
  and mixed-fiscal-year data on the dashboard, deceptive revenue chart spike.

**Confirmed backend gaps (from the internal integration/gap-analysis notes, backend assessed ~82%
ready overall):**
- *Tier 1 (blocks UI edit buttons):* comparative trial balance endpoint is a stub; missing `PUT`
  endpoints for FX currencies, exchange rates, tax codes, and fixed assets; overly restrictive RBAC
  blocks a regular entity `SYSTEM_ADMIN` from viewing/editing their own organization profile.
- *Tier 2 (workflow gaps):* no standalone credit-notes list endpoint; no closing-preview endpoint
  (can't preview closing entries before committing); source-document restore (un-void) not wired;
  IFRS 15 over-time revenue recognition has no period-end recognition job yet (point-in-time works).
- *Tier 3 (infrastructure):* no external FX rate feed; no bulk source-document upload; no AR
  statement email send / receipt resend; Spring Batch declared but not wired — depreciation and FX
  revaluation run synchronously in the request thread; no historical period-balance snapshot table
  for fast multi-year comparatives; COA templates lack seed data for the signup wizard.
- *Larger future items explicitly out of scope until later phases:* IFRS 16 leases, IAS 36
  impairment, IAS 37 provisions, IAS 38 intangibles amortisation, consolidation/intercompany
  elimination, bank reconciliation, payroll journal import, role-level data filters below
  entity-level isolation.

**New — hardcoded-category violations of CLAUDE.md §2's configuration-driven principle (surfaced
during this session's work, not fixed, not blocking, candidates for a future dynamic-management
screen):**
- Duplicated `PAYMENT_TERMS` arrays hardcoded in `fa-frontend/src/views/parties/Suppliers.vue` and
  `fa-frontend/src/views/parties/Customers.vue`.
- Duplicated/inconsistent `PAYMENT_METHODS` arrays hardcoded in
  `fa-frontend/src/views/payables/Bills.vue`, `fa-frontend/src/views/revenue/Payments.vue`, and
  `fa-frontend/src/views/revenue/Invoices.vue`.
- Hardcoded `DOC_TYPES` array in `fa-frontend/src/views/ledger/SourceDocs.vue` — should follow the
  existing configurable document-numbering pattern (`shared/codegen`) instead of a literal array.

**New — pre-existing backend test-suite issues (surfaced by this session's full `mvn clean test`
runs, not caused by this session's work, confirmed present before and after):**
- Two pre-existing, unrelated test failures: `UserServiceTest.registerUser creates
  PENDING_VERIFICATION user for non-first registration`, and `CoreAccountingIntegrationTest` (H2
  rejects the reserved SQL word `year` in the `code_sequences` table — an H2-test-dialect issue, not
  necessarily a production Postgres issue, but unverified either way).
- Two backend test files entirely commented out end-to-end, including their `package` declarations,
  silently contributing 0 tests to every run:
  `fa-backend/src/test/kotlin/com/qesuite/accounting/invoicing/service/InvoiceServiceTest.kt` and
  `fa-backend/src/test/kotlin/com/qesuite/accounting/receipts/service/ReceiptServiceTest.kt`.

## Architectural Decisions

- Modular monolith (Kotlin/Spring Boot), not microservices — correct for current scale; see
  CLAUDE.md §7–8 for when/if to revisit.
- Multi-entity via row-level isolation in one PostgreSQL database — not multi-tenant SaaS. A tenant
  control plane is target-state (Project.md Platform Services, Phase 6).
- Demo/production mode toggle (`isDemo.value`) is a first-class, permanent product feature, not
  scaffolding to remove — new API clients must support it.
- Configurable document numbering (13 document types via `shared/codegen`) is the established
  pattern for any new transactional document type — never hardcode a new prefix/sequence.

## Accounting Decisions

- IFRS-first, double-entry, immutable ledger — corrections are always reversals, never edits.
- Header accounts never accept postings (IAS 1 §29) — enforced at creation time (auto-promotion of
  parent to header) and at posting time.
- Functional currency is mandatory and singular per entity (IAS 21) — enforced by both the currency
  registry and the IAS 1 compliance checker.
- Tax computed at the effective rate for the transaction date, not the current date (supports rate
  changes like 14%→16% VAT without corrupting history).
- Single-open-period control is the intended design (per BUG-25's severity as *critical*) — multiple
  concurrently open periods is a defect, not a feature. Confirmed genuinely enforced today, verified
  by actually running it under Financial Systems Architect review (see Known Issues); recommend
  adding a permanent regression test to lock it in, since the verification test used was temporary.

## Open Risks / Blockers

- Fiscal-year default/context-switch/switcher-UI bugs (BUG-24/27/34) are now fixed and verified (see
  Known Issues) — **removed** from this list. BUG-25 (multiple concurrent `OPEN` periods) is also
  **removed** from this list — verified genuinely enforced by an actual (if temporary) test run; the
  residual risk is narrower than "is it broken" — it's "no permanent regression test locks this in
  yet" (see Known Issues follow-up item).
- Synchronous batch operations (depreciation, FX revaluation) will not scale and block the request
  thread — a real constraint once transaction volume grows, tracked for Phase 0/3.
- M-Pesa callback handling uses sentinel UUIDs in place of a real session table — **not production
  safe** for real M-Pesa integration until Phase 0/workplan item is resolved.
- Two backend test files (`InvoiceServiceTest.kt`, `ReceiptServiceTest.kt`) are entirely commented
  out, contributing 0 tests silently — invoicing and receipts have no automated regression coverage
  today despite `mvn test` reporting green; do not read a passing test run as proof those modules are
  covered.

## Outstanding Reviews

- **Period management (BUG-24/25/27/34) and ApiKeys wiring: both now have recorded Engineering +
  Accounting sign-off**, per AGENTS.md's Definition of Completion — this is the first module actually
  taken through the full governance gate end to end (two Financial Systems Architect review passes:
  first pass found 3 real defects and did not approve, but *also* independently verified BUG-25's
  single-open-period enforcement by running a real test against it; second pass, after fixes,
  independently re-derived every claim from current file contents, re-ran `mvn clean test` (JDK 21)
  and `npm run build`, and gave final verdict **APPROVED end-to-end** for both).
- Follow-up owed (small, non-blocking): add a permanent `PERIOD_ALREADY_OPEN` regression test to
  `PeriodServiceTest.kt` — the behavior is verified correct today, but the test that proved it was
  temporary and was deleted after the review run.
- No formal Engineering or Accounting sign-off exists yet for any *other* module under the new
  governance model (the product was built before this framework was adopted) — treat those modules as
  **provisionally accepted** (working, documented, IFRS-cited) but not formally gated per AGENTS.md
  until they're touched again, at which point the full gate applies.

## Next Recommended Action

Uncommitted-work reconciliation and the BUG-24/25/27/34 period-management fixes are done and
verified (see above). Real remaining Phase 0 work:

1. Add a permanent `PERIOD_ALREADY_OPEN` regression test to `PeriodServiceTest.kt` — quick, closes
   the one residual gap from the BUG-25 verification (behavior confirmed correct, but only by a
   temporary test).
2. Close the confirmed Tier 1 gaps: missing `PUT` endpoints for FX currencies, exchange rates, tax
   codes, and fixed assets; comparative trial balance implementation; organization RBAC fix for
   entity-scoped `SYSTEM_ADMIN`.
3. Close Tier 2 workflow gaps: standalone credit-notes list endpoint, closing-preview endpoint,
   source-document restore (un-void), IFRS 15 over-time revenue recognition period-end job.
4. Close Tier 3 infrastructure gaps: wire the Spring Batch pipeline so depreciation/FX revaluation
   run asynchronously; seed COA template data for the signup wizard; build the M-Pesa STK-push
   session table (replace sentinel UUIDs).
5. New backlog surfaced this session (not urgent, but log-and-track per CLAUDE.md §2): consolidate
   the three hardcoded-category violations (`PAYMENT_TERMS`, `PAYMENT_METHODS`, `DOC_TYPES` — see
   Known Issues) into a dynamic-management screen; restore or delete the two fully-commented-out test
   files (`InvoiceServiceTest.kt`, `ReceiptServiceTest.kt`); investigate/fix the two pre-existing
   backend test failures (`UserServiceTest`, `CoreAccountingIntegrationTest`).

## Lessons Learned

- The repository already had strong internal gap-analysis discipline (the now-superseded
  `ROADMAP.md`/`BUG_REPORT*.md`/`integration-roadmap`) — that habit is worth preserving inside
  `MEMORY.md` and `workplan.md` rather than as separate ad hoc documents that drift out of sync.
- The gap between "well-built accounting core" and "Oracle Fusion competitor" is enormous — framing
  every future request against the six-domain gap table above prevents overpromising scope in any
  single session.
- The period-management review process caught a real gap that a compile-only check would have
  missed: `PeriodServiceTest.kt`/`PeriodControllerTest.kt` still asserted the *old, buggy* behavior
  after the fix landed, and `mvn compile` alone would have shown green. It was only caught because the
  Financial Systems Architect insisted on actually running `mvn test`, not reading the diff and
  reasoning about it. This is exactly why AGENTS.md's "run it, don't read it" mandate for Agent 2
  exists, and this session is the concrete case that validates keeping it non-negotiable.

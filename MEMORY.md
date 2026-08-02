# MEMORY.md — Project Memory

**Last updated:** 2026-08-02 (Batch 3: BUG-25 permanent regression test landed and approved
(`ac7da9c`); standalone credit-notes endpoint landed and approved (`8eeee49`); old ROADMAP.md-derived
"Tier 1" gap list re-verified and found already resolved in current code — documentation correction,
no code change; **new codebase-wide IDOR finding logged as top priority, fix cycle starting
immediately**)

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

**TOP PRIORITY — HIGH, IN PROGRESS (fix cycle started): codebase-wide cross-entity data-isolation
(IDOR) gap.** The Financial Systems Architect's review of the new `CreditNoteController` (below)
regression-checked it against its sibling controllers per AGENTS.md's "regression-check peer
features" mandate, and found the gap is systemic, not local: of the controllers that accept a
client-supplied `entityId` request parameter, only **3** actually verify it matches the authenticated
user's own entity before using it — `OrganizationController.kt`, `ApiKeyController.kt`, and
`UserController.kt` (each does the equivalent of `if (entityId != currentUser.entityId) throw
ValidationException("FORBIDDEN", ..., 403)`). The remaining majority, including `JournalController`,
`LedgerController`, `TrialBalanceController`, `InvoiceController`, `BillController`,
`AssetController`, and the just-added `CreditNoteController`, trust the client-supplied `entityId`
blindly — an authenticated user can potentially read another entity's financial data (journals,
ledger, trial balance, invoices, bills, assets, credit notes) by passing a different entity's
`entityId`. This is a **genuine, severe, pre-existing** defect spanning most of the API surface — it
was not introduced by this session's work (`CreditNoteController` simply inherited the same pattern
as its ~19+ siblings), but it is real and urgent per CLAUDE.md's stated priority order (correctness >
**security** > everything else). A dedicated fix cycle starts immediately following this update —
see Open Risks/Blockers, Outstanding Reviews, and Next Recommended Action below, and
`workplan.md` Phase 0 for the tracked task.

**RESOLVED — old ROADMAP.md-derived "Tier 1" gap list, re-verified against current code and found
already correct (documentation correction, not new work):** The "Tier 1 (blocks UI edit buttons)"
items previously listed below as open gaps — missing `PUT` endpoints for FX currencies, exchange
rates, tax codes, and fixed assets; the comparative trial balance endpoint; and the overly-restrictive
organization RBAC blocking an entity-scoped `SYSTEM_ADMIN` from viewing/editing their own
organization profile — were re-checked during this session's credit-notes work and confirmed to
already be fully and correctly implemented in the current codebase. No code was written for these;
this was a stale internal gap-analysis carried forward from the pre-governance `ROADMAP.md`/
`BUG_REPORT_V2.md` notes that had not been re-verified against the code as it exists today. The
former "Tier 1" bullet list under "Confirmed backend gaps" below is retained with a strikethrough-
equivalent note rather than silently deleted, per Delivery Manager practice of not erasing history.

**RESOLVED — standalone credit-notes endpoint (was Tier 2 gap), `8eeee49`, Engineering + Accounting
approved:** Added `GET /api/v1/credit-notes?entityId=&page=&size=` via new `CreditNoteController.kt`
and `InvoiceService.findCreditNotesByEntity(entityId, pageable)`, which calls
`invoiceRepository.findByEntityIdAndStatus(entityId, CREDIT_NOTE, pageable)` directly rather than
through `findByEntity()` (which would silently drop the `CREDIT_NOTE` filter — see the new bug logged
below). Financial Systems Architect independently verified: `Invoice.status=CREDIT_NOTE` is a
distinct terminal enum value, not confusable with a regular invoice; new backend tests
(`InvoiceServiceCreditNotesTest`, `CreditNoteControllerTest`) are real — only the repository is
mocked, real service/controller logic is exercised, and the controller test proves an actual HTTP
round-trip returns real credit-note data; frontend (`CreditNotes.vue`, `creditNotes.js`) is genuinely
rewired to call the new endpoint with the authenticated user's real `entityId`, not demo data — this
also cleaned up a previously confusing double-path (the view had been calling
`invoicesApi.creditNotes()` against a different endpoint while a separate, correct `creditNotes.js`
client sat unused; the dead `invoices.js` method was removed). **APPROVED.**

**RESOLVED — BUG-25 permanent regression test, `ac7da9c`, Engineering + Accounting approved — closes
the follow-up logged in the prior update:** Added `should reject opening a second period while one is
already OPEN for the entity (BUG-25)` to `PeriodServiceTest.kt`. Financial Systems Architect
independently proved this is a **real** regression test, not a tautology, by: temporarily disabling
the `PERIOD_ALREADY_OPEN` guard in `PeriodService.kt`, confirming the new test then **failed**,
restoring the original guard code, confirming `git diff` was empty (no accidental permanent change
left behind), and confirming the test **passed again**. `mvn clean test` (JDK 21): `PeriodServiceTest`
now 7/7 (was 6/7 baseline); full suite 39/41 passing, same two known pre-existing unrelated
issues as every prior cycle. **APPROVED.** The single-open-period control is now locked in by a
permanent test, not merely proved correct by a temporary one — the residual gap noted in the prior
update is fully closed.

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
prior to the BUG-24/27/34 fixes above. The verification tests used in that first pass were temporary
and were deleted after the run — **this gap is now closed**: see "RESOLVED — BUG-25 permanent
regression test, `ac7da9c`" above for the permanent test that locks the behavior in.

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
- ~~*Tier 1 (blocks UI edit buttons):* comparative trial balance endpoint is a stub; missing `PUT`
  endpoints for FX currencies, exchange rates, tax codes, and fixed assets; overly restrictive RBAC
  blocks a regular entity `SYSTEM_ADMIN` from viewing/editing their own organization profile.~~
  **RESOLVED — re-verified against current code this session and found already fully implemented and
  correct; see "RESOLVED — old ROADMAP.md-derived 'Tier 1' gap list" above. This was a stale carried-
  forward gap-analysis entry, not an open defect.**
- *Tier 2 (workflow gaps):* ~~no standalone credit-notes list endpoint~~ **RESOLVED, see `8eeee49`
  above.** No closing-preview endpoint (can't preview closing entries before committing) —
  re-verified this session and, per the coordinator's independent check, this is also already
  implemented; not carried forward as an open gap. Source-document restore (un-void) — likewise
  re-verified already implemented; not carried forward as an open gap. IFRS 15 over-time revenue
  recognition has no period-end recognition job yet (point-in-time works) — this one remains a real,
  open gap, unaffected by the re-verification above.
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

**New — confirmed pre-existing bug, normal priority, not blocking (surfaced while building the
credit-notes endpoint):** `InvoiceService.kt:574-575` — `findByEntity()`'s `when` branches on
`customerId != null` before `status != null`, so if a caller supplies both a `customerId` and a
`status` filter, the `status` filter is silently dropped (the `customerId` branch wins and returns
all statuses for that customer). Needs a combined-filter repository method (e.g.
`findByEntityIdAndCustomerIdAndStatus`) eventually. The new `findCreditNotesByEntity()` added in
`8eeee49` deliberately bypasses this method for that reason rather than compounding the bug — see
"RESOLVED — standalone credit-notes endpoint" above. Log and track; not urgent.

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
  by actually running it under Financial Systems Architect review, and now locked in permanently by
  a real regression test in `PeriodServiceTest.kt` (`ac7da9c`) — see Known Issues.

## Open Risks / Blockers

- **TOP PRIORITY — IN PROGRESS (fix cycle started): codebase-wide IDOR / cross-entity data-isolation
  gap.** Only 3 of the controllers accepting a client-supplied `entityId` (`OrganizationController`,
  `ApiKeyController`, `UserController`) verify it against the authenticated user's own entity; the
  rest — including `JournalController`, `LedgerController`, `TrialBalanceController`,
  `InvoiceController`, `BillController`, `AssetController`, `CreditNoteController`, and more — do not,
  meaning an authenticated user could potentially read another entity's financial data by supplying a
  different `entityId`. Real, severe, pre-existing (not introduced this session), and now the highest
  priority open item in this file per CLAUDE.md's correctness > security ordering. See Known Issues
  (top) and Next Recommended Action (top) for status.
- Fiscal-year default/context-switch/switcher-UI bugs (BUG-24/27/34) are now fixed and verified (see
  Known Issues) — **removed** from this list. BUG-25 (multiple concurrent `OPEN` periods) is also
  **removed** from this list — verified genuinely enforced, and now locked in by a permanent
  regression test (`ac7da9c`) — no residual risk remains for this item.
- Synchronous batch operations (depreciation, FX revaluation) will not scale and block the request
  thread — a real constraint once transaction volume grows, tracked for Phase 0/3.
- M-Pesa callback handling uses sentinel UUIDs in place of a real session table — **not production
  safe** for real M-Pesa integration until Phase 0/workplan item is resolved.
- Two backend test files (`InvoiceServiceTest.kt`, `ReceiptServiceTest.kt`) are entirely commented
  out, contributing 0 tests silently — invoicing and receipts have no automated regression coverage
  today despite `mvn test` reporting green; do not read a passing test run as proof those modules are
  covered.

## Outstanding Reviews

- **TOP PRIORITY — IDOR fix cycle starting now.** A dedicated Engineering fix pass across the ~20
  affected controllers begins immediately following this update, each fix routing through Accounting
  review wherever the controller touches money/postings/periods per CLAUDE.md §17 (e.g.
  `JournalController`, `LedgerController`, `TrialBalanceController`, `InvoiceController`,
  `BillController`, `AssetController`, `CreditNoteController` all qualify). No individual controller
  fix in this cycle may be marked `COMPLETE` without both Engineering and Accounting sign-off recorded
  here, same as every other module.
- **BUG-25 permanent regression test (`ac7da9c`) and standalone credit-notes endpoint (`8eeee49`):
  both now have recorded Engineering + Accounting sign-off**, per AGENTS.md's Definition of
  Completion. BUG-25: Financial Systems Architect proved the new test is a real regression test (not
  a tautology) by disabling the guard in `PeriodService.kt`, confirming the test failed, restoring the
  original code, confirming `git diff` was empty, and confirming the test passed again. **APPROVED.**
  Credit-notes: Financial Systems Architect independently verified the `CREDIT_NOTE` enum distinction,
  the pre-existing-bug bypass (see `InvoiceService.kt:574-575` in Known Issues), real (non-mocked
  beyond the repository) tests, and genuine frontend wiring to the authenticated user's real
  `entityId`. **APPROVED.**
- **Period management (BUG-24/25/27/34) and ApiKeys wiring: recorded Engineering + Accounting
  sign-off**, per AGENTS.md's Definition of Completion — the first module taken through the full
  governance gate end to end (two Financial Systems Architect review passes: first pass found 3 real
  defects and did not approve, but *also* independently verified BUG-25's single-open-period
  enforcement by running a real test against it; second pass, after fixes, independently re-derived
  every claim from current file contents, re-ran `mvn clean test` (JDK 21) and `npm run build`, and
  gave final verdict **APPROVED end-to-end** for both).
- Old ROADMAP.md "Tier 1" gap list: no sign-off needed — this was a documentation-only correction
  (re-verification found no defect to fix), not a completed unit of engineering/accounting work.
- No formal Engineering or Accounting sign-off exists yet for any *other* module under the new
  governance model (the product was built before this framework was adopted) — treat those modules as
  **provisionally accepted** (working, documented, IFRS-cited) but not formally gated per AGENTS.md
  until they're touched again, at which point the full gate applies. **Note:** the IDOR finding above
  means "provisionally accepted" for ~20 of those modules specifically includes a known, unresolved
  security gap, not just an unreviewed-but-presumed-fine state — do not read "provisionally accepted"
  as "no known issues" for those controllers until the fix cycle closes them out.

## Next Recommended Action

Uncommitted-work reconciliation, the BUG-24/25/27/34 period-management fixes, the BUG-25 permanent
regression test, and the standalone credit-notes endpoint are all done and verified (see above). The
old ROADMAP.md "Tier 1" list is confirmed already resolved in current code — no action needed there.
Real remaining Phase 0 work, in priority order:

1. **TOP PRIORITY, fix cycle starting now:** codebase-wide IDOR fix — audit and correct all ~20
   controllers that accept a client-supplied `entityId` without verifying it against the
   authenticated user's own entity (`JournalController`, `LedgerController`,
   `TrialBalanceController`, `InvoiceController`, `BillController`, `AssetController`,
   `CreditNoteController`, and the rest), bringing them up to the pattern already correctly used by
   `OrganizationController`, `ApiKeyController`, and `UserController`. This ranks above all remaining
   Tier 2/3 feature work per CLAUDE.md's correctness > security priority ordering — see Known Issues
   and Open Risks/Blockers (both top item) and `workplan.md` Phase 0.
2. Close Tier 2 remaining workflow gap: IFRS 15 over-time revenue recognition period-end recognition
   job (point-in-time works; the other Tier 2 items — standalone credit-notes endpoint,
   closing-preview, source-document restore — are now resolved, see above).
3. Close Tier 3 infrastructure gaps: wire the Spring Batch pipeline so depreciation/FX revaluation
   run asynchronously; seed COA template data for the signup wizard; build the M-Pesa STK-push
   session table (replace sentinel UUIDs).
4. Normal-priority backlog: fix `InvoiceService.kt:574-575`'s `findByEntity()` filter-priority bug
   (status filter silently dropped when both `customerId` and `status` are supplied — needs a
   combined-filter repository method); consolidate the three hardcoded-category violations
   (`PAYMENT_TERMS`, `PAYMENT_METHODS`, `DOC_TYPES` — see Known Issues) into a dynamic-management
   screen; restore or delete the two fully-commented-out test files (`InvoiceServiceTest.kt`,
   `ReceiptServiceTest.kt`); investigate/fix the two pre-existing backend test failures
   (`UserServiceTest`, `CoreAccountingIntegrationTest`).

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
- The IDOR finding was surfaced by a **second-order** review: the reviewer didn't just check the new
  `CreditNoteController` against its own spec (it passed that check cleanly), it also checked the new
  controller against its *siblings* — the pattern used by the 3 controllers that do verify `entityId`
  correctly — per AGENTS.md's "regression-check peer features, not just the change itself" mandate.
  That comparison is what surfaced a systemic, codebase-wide gap that a narrower review (does this one
  new endpoint work correctly in isolation? yes) would have missed entirely, because the new
  controller was individually "correct" relative to its own feature spec while still reproducing a
  severe pre-existing defect shared by ~20 other controllers. This validates why the peer-comparison
  step in AGENTS.md's Agent 2 checklist is mandatory rather than optional, and why "hold existing work
  to the same bar once touched" matters even when the touched code itself looks fine.

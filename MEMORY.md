# MEMORY.md — Project Memory

**Last updated:** 2026-08-09 (Batch 5: **Phase 0 governance bookkeeping closed** — see Batch 4 detail
below — **and Phase 1 started**: built the Budgeting module (Project.md Domain 1's last major gap
alongside Cash/Bank Management and Expense Management), the first phase 1 item in `workplan.md`.
Engineering-complete and Financial-Systems-Architect-**APPROVED** end to end (see "Budgeting module"
under Known Issues/Current Milestone below) — the first brand-new financial-domain module built
since the governance model + `AGENTS.md` three-persona review process was adopted. **Read "Handover
— Budgeting Module" below before touching this module further**, it records exactly what's built,
what's verified, and what a future agent should do next.)

**Batch 4 (same day, recorded for continuity):** this update closed a real gap in Delivery Manager
bookkeeping, not new engineering from scratch — the codebase-wide IDOR fix cycle logged 2026-08-02 as
"starting immediately" actually ran to completion across `ac16f42`..`3953672` and was never recorded
here as done; likewise the dynamic category-management work for `PAYMENT_TERM`/`PAYMENT_METHOD`
(`986fd63`..`aa43b7a`) merged and was Financial-Systems-Architect-approved but also never recorded.
Both are now recorded below. In addition, that session's independent re-verification pass fixed one
real, live bug found in the earlier sweep but left un-fixed (`'CFO'` vs `'CONTROLLER_CFO'` role
string in `PaymentController`/`ReceiptController`) and found + fixed **one new live IDOR/SoD gap the
original sweep missed entirely**: `GlobalApprovalController` had no entity-ownership check and no
role gate at all. Also closed: `DOCUMENT_TYPE`, the third and last of the three hardcoded-category
violations logged 2026-08-02, following the exact `PAYMENT_TERM`/`PAYMENT_METHOD` precedent.

This is the single source of truth for "where things stand." Every completed unit of work updates
this file per AGENTS.md's Delivery Manager responsibilities. See [workplan.md](workplan.md) for the
phase plan this memory tracks progress against, and [Project.md](Project.md) for the full six-domain
vision this project is working toward.

---

## Current Milestone

**Phase 1 — Financial Operations Completion** (Project.md Domain 1), just started. Phase 0's
governance/stabilization work is bookkeeping-complete (see below); Phase 0's remaining items are
normal-priority infrastructure backlog, not blockers, and continue in parallel. Phase 1's goal per
`workplan.md`: finish the domain the product already mostly covers (Budgeting, Cash & Bank
Management, Expense Management, historical period-balance snapshots, FX rate feed, communication
gaps) before expanding to Project.md's other five domains.

**Phase 0 recap (bookkeeping-complete 2026-08-09):** The codebase-wide IDOR/RBAC fix cycle and the
three hardcoded-category violations (Phase 0's two largest concrete items) are **all fully resolved
and recorded** — see Known Issues and Open Risks/Blockers below. Phase 0 is not formally "closed"
(that requires the Delivery Manager to explicitly re-sequence `workplan.md`, not implied by starting
Phase 1 work) — its remaining items (IFRS 15 over-time revenue-recognition job, Spring Batch pipeline
wiring, COA template seed data, M-Pesa session table, `InvoiceService.kt:574-575` filter-priority
bug, two commented-out test files) stay open and should be picked up opportunistically alongside
Phase 1 feature work, not treated as blocking it.

## Current Sprint

**Budgeting module (Phase 1, item 1) — Engineering + Financial Systems Architect APPROVED, see
"Handover — Budgeting Module" below for full detail and what's next.** This is the first
new-financial-domain module built end-to-end under the three-persona governance model (previous
Phase 0 work was fixes/hardening to existing modules, not a new domain). Next sprint should be
scoped by the Delivery Manager from Phase 1's remaining items (Cash & Bank Management, Expense
Management, ...) in `workplan.md`, picking up the Handover section's "Not done" list first.

---

## Handover — Budgeting Module (Phase 1, item 1)

**Status: Engineering-complete, Financial Systems Architect APPROVED, not yet committed to git,
frontend not visually verified in a browser (environment limitation, see below). Read this whole
section before touching the module.**

### What this module is

Project.md Domain 1 (Financial Operations) lists Budgeting as a core capability; `workplan.md`
Phase 1 lists it as item 1 (alongside Cash & Bank Management and Expense Management, both still
unbuilt). A `Budget` (header: name, status, total, notes) has `BudgetLine`s (account + period +
amount, one row per account-per-period). It never posts a journal entry — it's a planning artifact,
compared against actual ledger activity only at read time via a variance report endpoint. Lifecycle:
`DRAFT → APPROVED` or `→ VOID` (from either) — corrections are void-and-recreate, not edits, matching
this codebase's immutability-after-approval convention elsewhere.

### Files added/changed (none committed yet — see "Not done" below)

- Backend, new: `fa-backend/src/main/kotlin/com/qesuite/accounting/budgeting/{domain,repository,service,controller,dto}/*`
  (`Budget.kt`, `BudgetLine.kt`, `BudgetRepository.kt`, `BudgetService.kt`, `BudgetController.kt`,
  `BudgetingDtos.kt`), `V43__Create_budgets_and_budget_lines_tables.sql`.
- Backend, new tests: `BudgetServiceTest.kt` (16 tests), `BudgetControllerSecurityTest.kt` (7 tests).
- Frontend, new: `fa-frontend/src/api/budgets.js`, `fa-frontend/src/views/planning/Budgets.vue`.
- Frontend, changed: `api/index.js` (registered `budgets`), `data/index.js` (added `BUDGETS` demo
  fixture), `router/index.js` (added `/budgets` route + breadcrumb), `AppSidebar.vue` (added
  `Budgeting` nav group).
- Unrelated-but-same-session changes also sitting uncommitted: the Phase 0 governance bookkeeping
  (MEMORY.md/workplan.md edits), the `CFO`→`CONTROLLER_CFO` fix, the `GlobalApprovalController`
  IDOR/SoD fix (+ its own new test file under `test/kotlin/.../approvals/`), the `DOCUMENT_TYPE`
  category work, and `CategoryTypeTest.kt` — see Batch 4 above for that detail. All of it is one
  uncommitted working tree right now; a future agent should treat these as logically separate units
  when committing (don't squash a security fix and a new feature into one commit).

### What's verified, and how (per AGENTS.md's "run it, don't read it" bar)

- `mvn clean test`: **118 tests, only the 2 known pre-existing failures** (`UserServiceTest`,
  `CoreAccountingIntegrationTest` — both predate this work, documented in Known Issues below), zero
  regressions. Run this again after any further change before trusting the count.
- `npm run build`: clean. This caught one real bug during development (wrong `Kpi.vue` import path —
  fixed).
- **Independent Financial Systems Architect review (separate subagent, not self-review) — APPROVED**,
  and did real mutation-testing, not read-through: (1) flipped the `NormalBalance.DEBIT` branch in
  `BudgetService.actualForAccountInPeriod`, confirmed exactly the 2 variance-netting tests failed,
  reverted, confirmed 16/16 pass again — proves the variance sign convention (matches
  `DashboardService.getTbSummary`'s exact pattern) is real, not a tautological test. (2) Removed the
  `SecurityUtils.requireOwnEntity(...)` call from `BudgetController.approve`, confirmed the
  cross-entity security test failed (500 instead of 403), reverted, confirmed 7/7 pass again — proves
  the IDOR check is load-bearing. (3) Confirmed header-account rejection and entity-isolation
  validation in `validateLine()`, and `MONEY_SCALE=6`/`HALF_EVEN` rounding consistency with
  `InvoiceService`. One non-blocking note from that review: each mutating controller action calls
  `budgetService.findById(id)` twice (once for the ownership check, once inside the service method)
  — a redundant read, not a defect, and consistent with the existing codebase pattern elsewhere; fix
  opportunistically if this controller is touched again, not urgent enough to block on.

### Update 2026-08-09 (later same day): committed, and live-verified in a real browser

Both blockers below (as originally written) are now resolved:

- **Committed and pushed.** Split into 8 separate commits on `main` (`00a61fc`..`1d1ce3b`): the
  `.gitignore`/demo-data fix, the Budgeting feature itself, the `CFO`→`CONTROLLER_CFO` fix, the
  `GlobalApprovalController` IDOR/SoD fix, the `DOCUMENT_TYPE` category work, docs, and two more
  bug fixes found during live UI verification (next bullet). Pushed to `origin/main`.
- **Frontend live-verified in a real browser, and this caught two real bugs `npm run build` could
  never have caught.** The originally-downloaded `playwright-core` Chromium build kept getting
  SIGKILL'd on launch in this sandbox (as first written below) — the actual fix was to drive the
  **already-installed, properly signed/notarized Google Chrome** instead: `open -a "Google Chrome"
  --args --headless=new --disable-gpu --remote-debugging-port=9222`, then
  `chromium.connectOverCDP('http://localhost:9222')` from `playwright-core`. This works because the
  restriction was specifically about launching an ad-hoc-signed downloaded binary, not about
  headless Chrome or sandboxing in general — **note this for any future browser-verification need in
  this environment.** One more gotcha worth recording: the app uses `createWebHashHistory` (main.js),
  so `page.goto('.../budgets')` silently does nothing — the correct URL is `.../#/budgets`.
  - Confirmed the list view renders both demo budgets with correct data, the variance report modal
    shows correct budgeted/actual/variance math (cross-checked by hand against the demo fixture),
    and there were zero console errors throughout.
  - Also confirmed demo mode's `useAuth` never populates `currentUser` at all by design (`init()`
    early-returns before reading `sessionStorage` when `isDemo.value`), so role-gated UI
    (`canManage`/`canApprove`) is invisible-by-default in every demo session, app-wide — not a
    Budgeting-specific issue. Verified the role-gated paths anyway by temporarily patching
    `useAuth.js`'s `init()` to seed a demo user, screenshotting, then **fully reverting the patch**
    (confirmed via `git diff` returning clean) — same mutate-then-revert technique the earlier
    independent Financial Systems Architect review used.
  - **Bug found and fixed (`1d1ce3b`):** the "Total approved (budgeted)" KPI showed `—` instead of a
    number. Root cause: `Kpi.vue` formats its `value` prop internally via its own `fmt()` call;
    `Budgets.vue` was passing an *already-formatted* string (`fmt(totalBudgeted)`, e.g.
    `"2,780,000.00"`) into that prop, so `Kpi`'s internal `fmt()` received a comma-containing string,
    failed `isNaN()`, and fell back to its placeholder. Fixed by passing the raw number, matching
    every other `Kpi` usage in the same component.
  - **Bug found and fixed (`faf39f0`), NOT specific to Budgeting — a pre-existing, silent, app-wide
    gap:** the account-picker dropdown showed header accounts (e.g. `1-0000 · ASSETS`) as selectable,
    which the backend correctly rejects. Root cause: `fa-frontend/src/api/accounts.js`'s
    `demoAccounts()` never mapped the raw COA fixture's `type` (`HEADER`/`POST`) into the API-shaped
    `isHeader` field, so it read as `undefined` for every account in demo mode — meaning
    `!a.isHeader`-style filters (mine, and presumably any other consumer) silently did nothing.
    Fixed with one line; **also confirmed this restores `ChartOfAccounts.vue`'s "Header" badge**,
    which had presumably never rendered in demo mode either — a second, independent regression this
    one gap caused, found by checking a sibling view once the root cause was known (the same
    "regression-check peer features" habit this session's earlier IDOR work established).

### What is still NOT done — pick up here

1. **No OpenAPI/Swagger manual check.** `@Operation`/`@Tag` annotations are in place on
   `BudgetController` following the exact pattern of `PaymentController`/`BillController`, but nobody
   has loaded `/docs` (Scalar UI) or `/v3/api-docs.json` and eyeballed the generated schema for the
   new endpoints. Quick check, not done yet.
2. **Real Phase 1 items still fully unbuilt** (Budgeting was only item 1 of the list in
   `workplan.md` Phase 1): Cash & Bank Management (bank statement import + reconciliation matching),
   Expense Management (T&E), historical period-balance snapshots, external FX rate feed integration,
   communication/document-delivery gaps (AR statement email, receipt resend, bulk source-doc upload).
3. **Deliberately out of scope for this first cut, candidates for later iteration on Budgeting
   itself** (not blocking, just not built): CSV/Excel budget import, multi-year budget
   copy-forward/rollup, a cost-center/department dimension on `BudgetLine` (this depends on Phase 2's
   Cost Accounting, per `workplan.md` — don't build it early), budget revision history beyond simple
   void-and-recreate, and a Dashboard KPI tile surfacing budget utilization (the existing
   `DashboardService`/`Dashboard.vue` were not touched — Budgeting is currently only reachable via its
   own nav item, not summarized on the main dashboard).
4. **Not re-audited: does `accounts.js`'s `isHeader` gap (just fixed) have siblings?** The fix above
   was found by accident while testing an unrelated feature. Worth a quick, deliberate check of
   whether any other demo-mode API client silently drops a field that a real consumer filters on —
   the same class of bug, not yet swept for systematically.

---

## Handover — Phase 1 Continued: Cash & Bank Management, Expense Management, Maker-Checker

**Status as of 2026-08-09, same day as the Budgeting handover above.** Two more Phase 1 modules were
built in parallel by two independently-spawned "senior software architect" agents, each in an
isolated git worktree, following the Budgeting module's exact conventions (briefed explicitly on all
of them: header+lines JPA shape, `RoleSets`/`SecurityUtils.requireOwnEntity`, money discipline,
mutation-tested tests, the live-browser-verification technique). Mid-flight, both were also briefed
on the `LazyInitializationException` finding (see above) before finishing their own entities.

### Expense Management (T&E) — Phase 1 item 3 — DONE, merged, pushed

`com.qesuite.accounting.expenses.*` — `ExpenseClaim`/`ExpenseClaimLine`, lifecycle
`DRAFT→SUBMITTED→APPROVED→REIMBURSED` (or `→REJECTED→DRAFT` reopen), posts a real journal entry on
approval (DR each line's expense account / CR an Employee Reimbursements Payable account reusing
`AccountSubtype.CURRENT_PAYABLE` — IAS 1 §54(k), defensible, independently confirmed). Merged to
`main` at `139a10a`. **163/163 backend tests pass** (only the 2 known pre-existing failures),
`npm run build` clean.

Independently reviewed — **APPROVED WITH CONDITIONS**, every claim verified by actually running
things (not read-through): the debit=credit posting test, the self-approval guard, and IDOR checks
were each mutation-tested (temporarily broke the logic, confirmed the specific test caught it,
reverted, confirmed clean). The one condition: `employeeId` (who the claim is for) was fully
client-suppliable at creation with no check against the actual submitter — any `PREPARER`-tier user
could file a claim under a colleague's name, and only the *approval* step checked identity
(`claim.employeeId == approverId`), not creation. **User's explicit decision: keep delegated
submission allowed (a legitimate workflow — an assistant filing for an executive), but require true
maker-checker regardless of whose name is on the claim.** Implemented by adding
`SecurityUtils.requireNotSelfApproval(claim.createdBy)` alongside the existing `employeeId` check —
two independent guards, not one replacing the other. New test proves they're independent (a claim
filed by user A under a different employeeId, approved by user A, is caught by the `createdBy` check
specifically — the `employeeId` check alone would have missed it). Mutation-tested; merged as
`139a10a` (after first merging latest `main` into the worktree branch to pick up the
`SecurityUtils.requireNotSelfApproval` helper this fix depends on).

**Real gap Expense Management's own build surfaced, not yet acted on** (from its build report,
independently confirmed by the reviewer): a `PREPARER`-tier user can still name *any* employee as
`employeeId` at creation — the fix above closes the maker-checker gap but does not restrict
delegation to an authorized/audited list. Not blocking (delegation is intentionally allowed per the
decision above), but worth a future call on whether delegation should be role-gated or logged more
visibly than the existing audit trail already does.

### Codebase-wide maker-checker (segregation of duties) — DONE, merged, pushed

The Expense Management delegation question above led directly to a much bigger finding: **every
approval flow in this codebase — Journal, Invoice, Bill, and the Budgeting module built earlier
today — checked only ROLE (`RoleSets.APPROVER`), never IDENTITY.** A `SENIOR_ACCOUNTANT` could
approve their own journal entry, invoice, bill, or budget; role-gating alone never prevented it. This
is a systemic, real internal-control gap across the entire application, found by the user directly
("I haven't seen the implementation of maker checkers"), not by an agent.

Fixed with one shared helper, `SecurityUtils.requireNotSelfApproval(createdBy)` (mirrors
`requireOwnEntity`'s pattern from the original IDOR sweep — one reusable check, not a hand-rolled
comparison duplicated per service), wired into `JournalService.postEntry`, `InvoiceService.approve`,
`BillService.approveBill`, and `BudgetService.approve`. `GlobalApprovalController`/`-Service`, which
routes to all three of the first group directly, inherits the check automatically — no duplicate
logic needed there. Fails open when `createdBy` is `null` (unknown maker) — every real persisted
entity has it populated by JPA auditing in production, so this only matters for synthetic/legacy
data, where blocking outright would be a worse failure mode than allowing it.

9 new tests (`SecurityUtilsTest` ×3 for the helper itself, `BudgetServiceTest`/`JournalServiceTest`
×2 each, new `BillServiceApprovalTest` ×2 — Bill has no prior test coverage of `approveBill`'s full
happy path at all, a separate pre-existing gap not solved here, so its second test proves the guard
lets a different approver *reach* the next step, not that the whole method succeeds end to end).
Proven load-bearing by mutation testing (Budget's check specifically: disabled it, confirmed the test
failed by hitting an unmocked repository call instead of the expected exception, reverted, confirmed
clean). `mvn clean test`: 127 tests at the time of this commit, same 2 known pre-existing failures.
Merged at `8976e5a`, pushed.

### Cash & Bank Management — Phase 1 item 2 — DONE, merged, pushed

`com.qesuite.accounting.banking.*` — bank statement import, GL matching (manual + a simple
date/amount-tolerance auto-match), reconciliation tie-out report (`adjustedBookBalance =
adjustedBankBalance`, the standard two-sided bank-rec identity). Never posts a journal entry — a
comparison/reporting tool over existing ledger activity, same non-posting design as Budgeting.
Built in its own isolated worktree (commits `677a838`/`0932d53`), merged into `main` (`e297632`,
resolving three purely-additive merge conflicts with the parallel Expense Management module in
`router/index.js`/`AppSidebar.vue`/`data/index.js` — both modules had added entries at the same
insertion points) and pushed. **191/191 backend tests pass** (only the 2 known pre-existing
failures), `npm run build` clean.

Found and fixed two of its own real bugs during the build: (1) a `LazyInitializationException`-class
risk it would have inherited from copying `BudgetLine`/`InvoiceLine`'s original shape (fixed before
finishing, once flagged); (2) a *different* instance of the same underlying problem — this app runs
`spring.jpa.open-in-view: false`, so a lazy field read after the read-only transaction closes throws
the same way — fixed with a `JOIN FETCH` repository query (`BankStatementLineRepository.findByIdWithImport`).
Also found a demo-mode-only gap (the COA fixture had no `accountSubtype` field at all, so no demo
account could ever match a `CASH_AND_EQUIVALENTS` filter — same *class* of gap as the `isHeader` fix
in the Budgeting handover above, found independently by a different agent). Live-verified in a real
browser, catching and fixing a KPI-grid overflow bug along the way.

**Independent Financial Systems Architect review: APPROVED, no conditions.** (First attempt hit a
transient API disconnect mid-run; resumed to completion — see below, this is the completed verdict.)
Every claim independently verified by actually running things, not read-through: confirmed the
155-test count directly; proved the reconciliation math is a genuine two-sided bank-rec identity
(not coincidentally-correct-looking code) by deriving it algebraically AND by mutation-testing
(flipped `.add`→`.subtract` on the adjustment formula, confirmed both reconciliation tests failed
with wrong numbers, reverted, confirmed clean); confirmed `BankLineMatch` is a plain class with
id-based `equals`/`hashCode` matching the established pattern (noted one cosmetic-only inconsistency:
`BankStatementLine` has no explicit `equals`/`hashCode` override at all, which is safe since it never
touches its lazy field, but not stylistically uniform — not fixed, not blocking); confirmed the
`JOIN FETCH` fix is a genuine fix for a real reachable code path, not defensive-but-unnecessary code;
mutation-tested the IDOR check on `match` (removed it, confirmed the cross-entity test failed 500
instead of 403, reverted, confirmed clean); confirmed the `CASH_AND_EQUIVALENTS` account-picker
validation is enforced server-side, not just in the frontend dropdown; confirmed money discipline
(`HALF_EVEN`/scale 6) matches `BudgetService`'s convention. On whether this module needed
Agent-2-level review at all given it never posts a journal entry: reviewer's own judgment was yes —
"reconciliation" and "account balances" are both explicit CLAUDE.md §17 triggers, and the accounting
judgment involved (sign conventions, treating `IGNORED` lines as still counting toward the tie-out
rather than silently excluding them, which side each outstanding total adjusts) is exactly the kind
of subtlety an engineer could get backward without independent accounting confirmation — this
review discharged that gate, it wasn't satisfiable by engineering self-review alone.

Both isolated worktrees (`agent-a31b4f89eb4373724`, `agent-aad07028cec60e4cc`) and their branches
have been removed post-merge — nothing left to clean up.

### What a future agent should do next

1. **README.md's module inventory still not updated** for any of the three new Phase 1 modules
   (Budgeting, Expense Management, Cash & Bank Management) — CLAUDE.md §12 requirement, open since
   the Budgeting handover, still open now. Do this before starting new feature work.
2. Remaining real Phase 1 items, still fully unbuilt: historical period-balance snapshots, external
   FX rate feed integration, communication/document-delivery gaps.
3. OpenAPI/Swagger docs not manually eyeballed for any of the three new modules — annotations are in
   place following established patterns, but nobody has loaded `/docs` and checked the rendered
   schema. Quick check, not done for any of the three.
4. **Not re-audited: does `accounts.js`'s `isHeader`/`accountSubtype` gap have other siblings?** Two
   independent agents found two different instances of "demo-mode API client silently defaults a
   field a real consumer filters on" today (`isHeader` in the Budgeting handover, `accountSubtype`
   here). Worth a deliberate sweep of every `src/api/*.js` demo-mode mapping function against what
   its real consumers actually filter/branch on, rather than waiting for a third accidental find.
5. **Not re-audited: does `BankStatementLine`'s missing `equals`/`hashCode` override have siblings?**
   Every entity fixed for the `LazyInitializationException` risk (`BudgetLine`, `InvoiceLine`,
   `BankLineMatch`) got an explicit id-based override; entities that never held a lazy back-reference
   in the first place (like `BankStatementLine`) were never required to, but doing so anyway would
   be a reasonable consistency pass if anyone touches entity design conventions next.

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

**FOUND AND FIXED 2026-08-09 (`00a61fc`) — `fa-frontend/src/data/index.js` (the entire
demo-mode fixture dataset: COA, customers, suppliers, invoices, bills, journals, periods, payments,
receipts, AR/AP ageing, trial balance, P&L/BS/cash-flow, everything) has *never been committed to
git, in this repository's entire history*.** Found while adding the `BUDGETS` demo fixture and
noticing `git status` didn't pick up the change. Root cause: `.gitignore`'s `data/` pattern (line 39,
meant to ignore a Docker Compose bind-mount that doesn't actually exist — `docker-compose.yml` uses
named volumes, not host bind-mounts, so nothing ever needed that line) has no leading `/`, so it
matches `fa-frontend/src/data/` too, silently un-tracking a real, load-bearing source file since
whenever that `.gitignore` line was added. **Practical impact: a fresh `git clone` of this repository
today produces a frontend where every demo-mode view has empty/broken fixture data** — this is a
real, severe, previously-undetected defect, not a hypothetical one; only local working trees that
happened to have the file on disk before the ignore rule took effect were ever unaffected. Fixed by
removing the offending `.gitignore` line (kept `postgres-data/`, unrelated and harmless). Committed
as its own fix (`00a61fc`), separate from the Budgeting feature commit, and pushed to `origin/main`.

**FOUND AND FIXED 2026-08-09 (`a04dd7f`, `c0040b9`) — `BudgetLine`/`InvoiceLine` risked
`LazyInitializationException`.** User feedback flagged this exact bug class ("a lazy `@ManyToOne`
would throw `LazyInitializationException`"); a whole-application audit followed. Root cause pattern:
a Kotlin `data class` entity with a lazy `@ManyToOne` back-reference (e.g. `BudgetLine.budget`,
`InvoiceLine.invoice`) in its primary constructor gets a compiler-generated `equals`/`hashCode`/
`toString` that touches that field — calling any of them on a managed-but-uninitialized proxy outside
an active Hibernate session (a log line, a `Set`/`contains()` check, a test assertion on a detached
instance) throws. Audited every `@ManyToOne` usage in the codebase (`JournalEntryLine`, `TaxRate`,
`BudgetLine`, `BillItem`, `InvoiceLine`) — only two were actually at risk: `BudgetLine` (introduced
this session, fixed immediately) and `InvoiceLine` (pre-existing, already-shipped, since before this
session — a real latent defect in the live Invoicing module, not hypothetical).
`JournalEntryLine`/`BillItem` are already plain classes; `TaxRate` uses `EAGER` fetch (no lazy proxy
involved). Both fixed by converting to a plain `class` with explicit id-based `equals`/`hashCode` (the
textbook-correct approach for JPA entities regardless of the laziness issue — field-based equality on
a mutable entity also breaks hash-based collection contracts once a field changes post-insertion).
No `.copy()`/destructuring usage found anywhere for either type, so the conversion was safe; `mvn
clean test` confirmed no regressions both times. **Standing rule going forward**: never make a JPA
entity holding a lazy `@ManyToOne`/`@OneToOne` a Kotlin `data class` — use a plain `class` with
explicit id-based `equals`/`hashCode` instead, every time, on every new header+lines entity
(Budgeting's `BankStatementLine`/`ExpenseClaimLine`-style children currently being built for Phase 1
item 2/3 must follow this from the start, not need a follow-up fix).

**RESOLVED — codebase-wide cross-entity data-isolation (IDOR) gap, plus a segregation-of-duties
fast-follow, `ac16f42`..`3953672`, Engineering-owned and approved per AGENTS.md's Ownership Matrix
(security/RBAC is an area Agent 1 owns & approves outright — no Agent 2 gate applies).** This item
was logged below as "IN PROGRESS, fix cycle started" on 2026-08-02 and the fix cycle did in fact run
to completion the same day; it was simply never marked resolved here until this update. Sequence:
- `ac16f42`/`b91def1`/`93f5ff3`/`1d1a1de` — added `SecurityUtils.requireOwnEntity(...)` ownership
  checks to every one of the ~20 affected controllers (`ledger`/`journal`/`period`,
  `party`/`invoicing`/`payables`, `assets`/`fx`/`tax`, `coa`/`compliance`/`codegen`/`source-doc`),
  bringing them up to the pattern `OrganizationController`/`ApiKeyController`/`UserController`
  already used correctly.
- `626fc6d` — a **second** Financial Systems Architect audit pass (peer-comparison, not just
  read-through) found a **different, equally severe** gap in the same 13 controllers: zero
  role-based access control at all — any authenticated role, including `DATA_ENTRY`, could close/
  reopen periods, dispose fixed assets, approve/void invoices, run FX revaluation, or rebuild the
  COA. Introduced `RoleSets.kt` (named `@PreAuthorize` constants derived from already-correct
  precedent in the codebase, not invented independently) and applied it. This same commit also found
  and fixed a **pre-existing, unrelated bug**: `AccessDeniedException` had no handler more specific
  than the generic 500 catch-all, so every `@PreAuthorize` denial in the **entire application**
  (not just this sweep) returned 500 instead of 403 — fixed in `GlobalExceptionHandler.kt`.
- `e14c455`/`bb33b3d`/`e32a070`/`3953672` — applied `RoleSets` to the remaining controller groups
  (`period`/`accounting-cycle`, `party`/`invoicing`, `assets`/`fx`/`tax`, `coa`/`codegen`/
  `source-document`). `3953672`'s commit message documents the exact role assigned to every one of
  the ~40+ gated endpoints and the precedent each was matched to.
- **Flagged but deliberately deferred at the time, fixed 2026-08-09 (this update):** `626fc6d`'s own
  commit message flagged that `PaymentController` and `ReceiptController` hand-rolled the SpEL string
  `hasAnyRole(...,'CFO',...)` instead of using the real `UserRole` enum value `'CONTROLLER_CFO'` —
  meaning every `CONTROLLER_CFO` user was silently denied on every gated Payment/Receipt endpoint
  (create, match, approve, post, reverse, generate, issue, void). Migrated both controllers onto
  `RoleSets` constants (all 12 endpoints map exactly to existing constants/precedent, no new roles
  invented). Verified via `mvn clean test`: 93 tests, same 2 known pre-existing failures as baseline,
  no regressions.
- **New finding, not in the original sweep's scope, found and fixed 2026-08-09 during independent
  re-verification:** `GlobalApprovalController` (`/api/v1/approvals/{id}/approve|reject`) was missed
  by the entire sweep above — it has no `@PreAuthorize` at all and performed no entity-ownership
  check before dispatching to `JournalService.postEntry`/`InvoiceService.approve`/
  `BillService.approveBill` (and their reject/void counterparts), none of which check ownership
  themselves (that check normally lives in the bypassed entity-specific controller). Concretely: any
  authenticated user of **any role**, from **any entity**, could approve or reject another entity's
  pending journal entry, invoice, or bill by guessing/enumerating its UUID through this one global
  queue endpoint — a full IDOR **and** a complete segregation-of-duties bypass, hiding behind a
  controller that every peer controller in the sweep above was individually checked but this one was
  never enumerated. Fixed by adding `GlobalApprovalService.resolveEntityId(id, type)` (looked up
  before dispatch, matching the pattern every other controller in the sweep uses) plus
  `SecurityUtils.requireOwnEntity(...)` and `@PreAuthorize(RoleSets.APPROVER)` on both endpoints —
  `APPROVER` matches the exact gate already used by the three actions this queue routes to
  (`JournalController.approve`/`InvoiceController.approve`/`BillController.approveBill`, all
  `SENIOR_ACCOUNTANT`/`CONTROLLER_CFO`/`SYSTEM_ADMIN`). New regression test
  `GlobalApprovalControllerSecurityTest` (4 tests: cross-entity 403, role-gate 403, happy path 200)
  proves both the IDOR and the SoD gate, not just read-through. `mvn clean test`: 93 tests, same 2
  known pre-existing failures, no regressions.
- This confirms the exact lesson already recorded below under Lessons Learned — peer-comparison
  review surfaces systemic gaps a narrower "does this one endpoint work" check misses — applies
  recursively: the second audit pass that found the RBAC gap in `626fc6d` was itself not exhaustive
  enough to catch `GlobalApprovalController`, which required a **third**, independent pass to find.
  Any future security review of this codebase should explicitly enumerate every `@RestController`
  and check it against this list, not assume "the sweep already covered controllers of this kind."

**Historical record of the original finding (2026-08-02), preserved for context:** The Financial
Systems Architect's review of the new `CreditNoteController` (below)
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

**RESOLVED — all three hardcoded-category violations of CLAUDE.md §2's configuration-driven
principle, now closed via one generic `Category`/`CategoryType` system (`shared/categories`,
`V41`/`V42` migrations, `setup/Categories.vue` admin screen, `useCategoryCache.js`):**
- `PAYMENT_TERM` (was: duplicated `PAYMENT_TERMS` arrays in `Suppliers.vue`/`Customers.vue`) —
  `986fd63`..`aa43b7a`, Financial Systems Architect **APPROVED** (seed-data fidelity vs. the old
  hardcoded arrays confirmed, `BillService.parsePaymentTermsDays` due-date calculation unaffected).
  This and the next item merged 2026-08-03 but were never recorded as resolved here until this
  update — pure Delivery Manager bookkeeping gap, not new work.
- `PAYMENT_METHOD` (was: duplicated/inconsistent `PAYMENT_METHODS` arrays in `Bills.vue`/
  `Payments.vue`/`Invoices.vue`) — same commits, same approval; codes verified to still match
  `PaymentMethod` enum constant names so `Payment`/`BillPayment` creation keeps deserializing.
- `DOCUMENT_TYPE` (was: hardcoded `DOC_TYPES` array in `SourceDocs.vue`) — closed 2026-08-09, third
  `CategoryType` added following the exact same pattern. New `CategoryTypeTest` proves
  `DOCUMENT_TYPE.defaultSeed()` codes match `SourceDocumentType` enum constants exactly, same order
  (the load-bearing correctness claim — a value added here with no matching enum constant would fail
  at document-creation time, same accepted tradeoff as `PAYMENT_METHOD`). `SourceDocs.vue`'s
  `typeLabel()` now shows the curated label (e.g. "Sales Invoice") instead of the old raw
  underscore-replaced enum name ("SALES INVOICE"). No Agent 2 review required — `SourceDocument.type`
  is a classification tag, not a posting/balance/tax/period calculation (AGENTS.md Agent 2 scope).
  `mvn clean test`: 95 tests (93 + 2 new), same 2 known pre-existing failures; `npm run build`: clean.

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
- **Standing principle, user-mandated 2026-08-02: security and scale are considered for every
  feature, on both backend and frontend, at design/build time — not audited in afterward.** This
  is now written into CLAUDE.md §13–14 and §16.4, and into AGENTS.md's Agent 1 review checklist, as
  a permanent, non-optional gate, not a one-time cleanup pass. Concrete trigger: the codebase-wide
  IDOR gap below existed because security was being reasoned about per-feature instead of as a
  standing architectural property that should have been checked on every one of the ~24 affected
  controllers as it was written. Every future module (Phase 1 onward) must be designed against this
  from the start — do not repeat the pattern of building first and discovering the gap later.

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

- **RESOLVED — codebase-wide IDOR / cross-entity data-isolation gap, plus SoD role gates.** Fixed
  `ac16f42`..`3953672` (2026-08-02, but never marked resolved here until 2026-08-09); a live gap the
  original sweep missed (`GlobalApprovalController`, no ownership check and no role gate at all) and
  a live, deferred bug it found but didn't fix (`CFO`→`CONTROLLER_CFO` role string in
  `PaymentController`/`ReceiptController`) were both found and fixed 2026-08-09 during independent
  re-verification. See Known Issues (top) for full detail. No residual risk known at this time, but
  given this is the **second** time a peer-comparison pass found something a prior pass missed, a
  future session should still do one more independent enumeration of every `@RestController` against
  the ownership-check + role-gate pattern before treating this as permanently closed.
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

- **IDOR/RBAC sweep (`ac16f42`..`3953672`) plus the 2026-08-09 fast-follow (CFO role-string fix,
  `GlobalApprovalController` fix): Engineering-owned and approved, no Accounting gate applies.**
  Per AGENTS.md's Ownership Matrix, "Security/RBAC, infra, CI/CD, performance" is a row Agent 1
  "Owns & approves" outright (N/A for Agent 2) — unlike a posting-logic or tax-calculation change,
  this class of fix only requires Engineering to actually run it (AGENTS.md's "run it, don't just
  read it" bar), not a separate Accounting sign-off. Verified per that bar, not by reading the diff:
  full `mvn clean test` re-run after each fix (93 → 95 tests across this session, only the same 2
  known pre-existing failures both times), and a new real regression test for each specific
  gap — `GlobalApprovalControllerSecurityTest` (4 tests: cross-entity 403, DATA_ENTRY role-gate 403,
  SENIOR_ACCOUNTANT happy-path 200) proves the fix, not just documents intent. **APPROVED
  (Engineering).**
- **Dynamic category management, all three kinds (`PAYMENT_TERM`/`PAYMENT_METHOD`: `986fd63`..
  `aa43b7a`; `DOCUMENT_TYPE`: 2026-08-09): Engineering + Accounting sign-off recorded.**
  `PAYMENT_TERM`/`PAYMENT_METHOD` — Financial Systems Architect **APPROVED** at merge time (seed-data
  fidelity, `BillService.parsePaymentTermsDays` due-date calculation preserved, migration race-safety
  via unique constraint, security guard + role gate correct) — this sign-off existed since
  2026-08-03 but was never transcribed into this file until now. `DOCUMENT_TYPE` — no Accounting
  gate needed (source-document classification is not a posting/balance/tax/period calculation per
  AGENTS.md's Agent 2 scope list); Engineering-verified via the new `CategoryTypeTest` (proves the
  seed codes match `SourceDocumentType` exactly) plus full `mvn clean test` (95 tests, same 2 known
  failures) and `npm run build` (clean). **APPROVED.**
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
regression test, the standalone credit-notes endpoint, the codebase-wide IDOR/RBAC fix cycle (plus
its 2026-08-09 fast-follow), and all three hardcoded-category violations (`PAYMENT_TERM`/
`PAYMENT_METHOD`/`DOCUMENT_TYPE`) are all done and verified (see above). The old ROADMAP.md "Tier 1"
list is confirmed already resolved in current code — no action needed there. Real remaining Phase 0
work, in priority order:

1. Close Tier 2 remaining workflow gap: IFRS 15 over-time revenue recognition period-end recognition
   job (point-in-time works; the other Tier 2 items — standalone credit-notes endpoint,
   closing-preview, source-document restore — are now resolved, see above).
2. Close Tier 3 infrastructure gaps: wire the Spring Batch pipeline so depreciation/FX revaluation
   run asynchronously; seed COA template data for the signup wizard; build the M-Pesa STK-push
   session table (replace sentinel UUIDs).
3. Normal-priority backlog: fix `InvoiceService.kt:574-575`'s `findByEntity()` filter-priority bug
   (status filter silently dropped when both `customerId` and `status` are supplied — needs a
   combined-filter repository method); restore or delete the two fully-commented-out test files
   (`InvoiceServiceTest.kt`, `ReceiptServiceTest.kt`); investigate/fix the two pre-existing backend
   test failures (`UserServiceTest`, `CoreAccountingIntegrationTest`).
4. Recommended, not yet scheduled: one more independent enumeration of every `@RestController` in the
   codebase against the ownership-check + role-gate pattern (see Open Risks/Blockers) — two
   consecutive independent review passes each found something the previous pass missed
   (`GlobalApprovalController` was missed by both the original IDOR sweep and its own RBAC
   fast-follow), so treat "the sweep is complete" as a hypothesis to keep testing, not a settled fact.

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
- **Completed engineering work is not "done" until Delivery Manager records it — a full sweep sat
  unrecorded in this file for a week.** The IDOR/RBAC fix cycle and the `PAYMENT_TERM`/
  `PAYMENT_METHOD` category work both landed and were verified/approved on 2026-08-02/03, but this
  file kept describing the IDOR gap as "IN PROGRESS" until 2026-08-09 simply because no session
  closed the loop. A user asking "what's the state of X" or "proceed with the plan" between those
  dates would have been given stale, overly-alarming information from this file directly. AGENTS.md
  already assigns this to the Delivery Manager persona ("keeps `workplan.md`'s current phase and
  `MEMORY.md`'s current milestone/sprint in sync with actual repo state") — the gap here was a
  process miss (the persona wasn't adopted at the end of that session), not a gap in the rule itself.
  Practical takeaway: at the end of any unit of work, explicitly check "did I update MEMORY.md" as
  its own step, the same way `mvn test`/`npm run build` are checked, rather than assuming the fix
  commits speak for themselves.
- **A completed security sweep is a hypothesis, not a settled fact, until something enumerates the
  full controller list against it.** Two independent review passes (the original IDOR sweep, then
  its own RBAC fast-follow in `626fc6d`) each individually found a real, severe gap the *other* had
  missed — and a **third** pass (this session) still found `GlobalApprovalController`, missed by
  both. The common failure mode: each pass reasoned from "controllers of this kind" (REST
  controllers exposing `entityId`-scoped resources) rather than mechanically listing every
  `@RestController` in the codebase and checking each one off. The check that actually worked this
  session was a blunt, mechanical `grep`/loop over every `*Controller.kt` file counting
  `@PreAuthorize`/`SecurityUtils` occurrences against its endpoint count — cheap, exhaustive, and
  more reliable here than reasoning about which controllers "should" already be covered.

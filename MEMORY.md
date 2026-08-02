# MEMORY.md — Project Memory

**Last updated:** 2026-08-02 (baseline established — governance framework adopted this date)

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

## Modules In Progress (Uncommitted, as of 2026-08-02)

Working tree has uncommitted changes that predate this governance baseline — **do not discard
without review**:
- Auth pages redesign: `Login.vue`, `Signup.vue`, `ForgotPassword.vue` substantially rewritten; new
  `fa-frontend/src/assets/auth-bg.jpg` (untracked) and `base.css` changes — appears to be a visual
  redesign of the auth flow, not yet committed.
- `fa-frontend/src/api/users.js`: `apiKeys()` now accepts query params, `revokeApiKey()` now accepts
  a `reason` parameter — paired with `ApiKeys.vue` changes (212 lines changed) that likely add a
  revoke-reason UI and key filtering.
- Deleted (uncommitted) from working tree: `ROADMAP.md`, `BUG_REPORT.md`, `BUG_REPORT_V2.md`. Their
  content has been folded into this file and `workplan.md` below — nothing is lost; still
  recoverable via `git show HEAD:<file>` if deeper detail is needed. These three files should be
  considered **superseded** by `MEMORY.md`/`workplan.md` going forward, not restored.

**Recommended first action:** review and commit (or intentionally discard) the auth redesign and
ApiKeys.vue changes before starting new Phase 0 work, so the working tree is clean.

## Known Issues / Technical Debt

Carried forward from the (uncommitted-deleted) internal gap analysis and bug reports. Not yet
re-verified against current `HEAD` — first Phase 0 task is to confirm which are still open.

**Critical (from BUG_REPORT_V2, unverified against current code):**
- Periods module: fiscal-year generation defaulted to a nonsensical year (BUG-24); system allowed
  multiple concurrent `OPEN` periods, violating the single-open-period accounting control (BUG-25);
  generating a historical fiscal year silently switched the user's working context away from the
  current year (BUG-27); no explicit fiscal-year switcher UI (BUG-34).
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
  concurrently open periods is a defect, not a feature, and must be re-verified/fixed under
  Financial Systems Architect review before being marked resolved.

## Open Risks / Blockers

- Uncommitted working-tree changes (see above) risk being lost or silently overwritten if not
  reconciled before other work starts.
- Critical accounting-control bugs (single-open-period, fiscal-year defaults) are unverified against
  current `HEAD` — until re-confirmed, treat period management as **not yet trustworthy** for
  production use.
- Synchronous batch operations (depreciation, FX revaluation) will not scale and block the request
  thread — a real constraint once transaction volume grows, tracked for Phase 0/3.
- M-Pesa callback handling uses sentinel UUIDs in place of a real session table — **not production
  safe** for real M-Pesa integration until Phase 0/workplan item is resolved.

## Outstanding Reviews

- Financial Systems Architect review has not yet been run against current `HEAD` for the period
  management critical bugs above — first accounting review owed once Phase 0 fixes land.
- No formal Engineering or Accounting sign-off exists yet for any module under the new governance
  model (the product was built before this framework was adopted) — treat existing modules as
  **provisionally accepted** (working, documented, IFRS-cited) but not formally gated per AGENTS.md
  until they're touched again, at which point the full gate applies.

## Next Recommended Action

1. Reconcile uncommitted working-tree changes (commit or discard the auth redesign + ApiKeys.vue
   changes, with user confirmation).
2. Re-verify the critical period-management bugs (BUG-24/25/27/34) against current `HEAD` — Agent 2
   review required given these are accounting-control violations, not cosmetic issues.
3. Begin Phase 0 of `workplan.md`: close the confirmed Tier 1 API gaps (missing PUT endpoints,
   comparative trial balance, organization RBAC fix).

## Lessons Learned

- The repository already had strong internal gap-analysis discipline (the now-superseded
  `ROADMAP.md`/`BUG_REPORT*.md`/`integration-roadmap`) — that habit is worth preserving inside
  `MEMORY.md` and `workplan.md` rather than as separate ad hoc documents that drift out of sync.
- The gap between "well-built accounting core" and "Oracle Fusion competitor" is enormous — framing
  every future request against the six-domain gap table above prevents overpromising scope in any
  single session.

# workplan.md — Phased Delivery Plan

**Approach basis:** This plan implements the vision in [@Project.md](Project.md) — a next-generation
Enterprise Financial ERP spanning Financial Operations, Enterprise Finance, Operational Finance,
Governance & Compliance, Intelligence, and Platform Services — adapted to the actual state of this
repository. Governance for how this plan is executed is defined in [CLAUDE.md](CLAUDE.md) (rules)
and [AGENTS.md](AGENTS.md) (who decides what); current status lives in [MEMORY.md](MEMORY.md).

## Reality Check

Project.md describes a multi-year, large-team effort to build something comparable to Oracle Fusion
Cloud ERP or SAP S/4HANA Finance. This repository — **QeSuite FA** — is already a real, working
IFRS-first accounting core (Kotlin/Spring Boot + Vue 3), roughly covering Project.md's Domain 1
(Financial Operations) at ~80% and touching pieces of Domains 4 and 6. See MEMORY.md's gap table.

This plan does not pretend the other five domains can be designed and built in one pass. It
sequences them, each phase ending in a state that is genuinely shippable and IFRS-correct, gated by
the three-agent approval process in AGENTS.md (Engineering approval + Accounting approval + Delivery
Manager memory update — no phase is "done" without all three).

---

## Phase 0 — Stabilize & Govern *(current phase)*

**Goal:** Clean baseline, governance in effect, confirmed defects resolved, before any new domain
work begins.

- [DONE] Reconcile uncommitted working-tree changes (auth page redesign, ApiKeys.vue enhancements) —
  all committed to `main`; see MEMORY.md "Modules In Progress — RESOLVED" for commit references.
- [DONE] Re-verify and fix fiscal-year generation defaults (BUG-24), fiscal-year context switching
  (BUG-27), fiscal-year switcher UI (BUG-34) — fixed and Engineering + Accounting approved
  (two-pass Financial Systems Architect review); see MEMORY.md "Known Issues" for detail.
- [DONE] BUG-25 (single-open-period enforcement) — verified genuinely correct today by an actual test
  run against the real `PeriodService` (not read-through), per the Financial Systems Architect's first
  review pass.
- [DONE] Add a permanent `PERIOD_ALREADY_OPEN` regression test to `PeriodServiceTest.kt` (`ac7da9c`) —
  closes the residual gap from the BUG-25 verification. Financial Systems Architect proved it's a
  real regression test, not a tautology, by disabling the guard in `PeriodService.kt`, confirming the
  test failed, restoring the original code, confirming `git diff` was empty, and confirming the test
  passed again. **Engineering + Accounting approved.**
- [DONE] Standalone credit-notes endpoint (`8eeee49`): `GET /api/v1/credit-notes`, new
  `CreditNoteController.kt` + `InvoiceService.findCreditNotesByEntity()`, real backend tests, and
  frontend rewired (`CreditNotes.vue`, `creditNotes.js`) to the real endpoint instead of a stale
  double-path. **Engineering + Accounting approved.**
- [DONE — documentation correction, no code change] Re-verified the old `ROADMAP.md`-derived "Tier 1"
  API gap list (`PUT` endpoints for FX currencies/exchange rates/tax codes/fixed assets; comparative
  trial balance; organization RBAC for entity-scoped `SYSTEM_ADMIN`) plus the Tier 2 closing-preview
  and source-document-restore items against current code and confirmed all are **already fully and
  correctly implemented** — this was a stale carried-forward gap-analysis entry, not an open defect.
  See MEMORY.md Known Issues for detail.
- **TOP PRIORITY, fix cycle starting now — codebase-wide IDOR / cross-entity data-isolation
  hardening:** only 3 of the controllers that accept a client-supplied `entityId`
  (`OrganizationController`, `ApiKeyController`, `UserController`) verify it against the authenticated
  user's own entity; the remaining ~20, including `JournalController`, `LedgerController`,
  `TrialBalanceController`, `InvoiceController`, `BillController`, `AssetController`, and the
  just-added `CreditNoteController`, do not — an authenticated user could potentially read another
  entity's financial data by supplying a different `entityId`. Real, severe, pre-existing (surfaced by
  regression-checking the new `CreditNoteController` against its siblings, not introduced by it). This
  item is sequenced **above** the remaining Tier 2/3 items below per CLAUDE.md's correctness >
  security priority ordering — a security-hardening pass across all ~20 affected controllers, adding
  the same entity-ownership check already used correctly by the 3 compliant controllers. Each fix
  routes through Accounting review wherever the controller touches money/postings/periods (most of
  them do) per CLAUDE.md §17. See MEMORY.md Known Issues (top) and Open Risks/Blockers (top).
- Remaining Tier 2 workflow gap: IFRS 15 over-time revenue recognition period-end recognition job
  (point-in-time works; the other Tier 2 items are now resolved above).
- Wire the Spring Batch background-job pipeline so depreciation and FX revaluation run
  asynchronously (prerequisite for Phase 2/3 batch-heavy work like consolidation).
- Seed COA template data so the signup wizard works end-to-end.
- Build the M-Pesa STK-push session table (replace sentinel UUIDs) if M-Pesa remains a target
  integration.
- **Configuration-driven-principle backlog (CLAUDE.md §2), first concrete instances found this
  session** — candidates for a dynamic-management screen, same pattern as `shared/codegen`:
  - Duplicated `PAYMENT_TERMS` arrays hardcoded in `Suppliers.vue` and `Customers.vue`.
  - Duplicated/inconsistent `PAYMENT_METHODS` arrays hardcoded in `Bills.vue`, `Payments.vue`, and
    `Invoices.vue`.
  - Hardcoded `DOC_TYPES` array in `SourceDocs.vue` — should follow the configurable
    document-numbering pattern instead.
- **Test-suite health backlog** — infrastructure hardening; arguably blocks confidently claiming any
  future module is fully verified until addressed:
  - Two pre-existing backend test failures (unrelated to any work this session): `UserServiceTest`
    (non-first-registration case) and `CoreAccountingIntegrationTest` (H2 rejects reserved word
    `year` in `code_sequences`).
  - Two backend test files entirely commented out end-to-end, silently contributing 0 tests:
    `InvoiceServiceTest.kt` and `ReceiptServiceTest.kt`.
- **Normal-priority backlog item, confirmed pre-existing bug (surfaced while building the
  credit-notes endpoint):** `InvoiceService.kt:574-575` — `findByEntity()`'s `when` branches on
  `customerId != null` before `status != null`, so supplying both filters silently drops the `status`
  filter. Needs a combined-filter repository method (e.g. `findByEntityIdAndCustomerIdAndStatus`)
  eventually. Not blocking, not urgent.

**Exit criteria:** All Phase 0 items have Engineering approval; every item touching postings,
periods, or reports additionally has Accounting approval; MEMORY.md reflects a clean, current
baseline with no unverified "unverified" critical bugs remaining.

---

## Phase 1 — Financial Operations Completion (Project.md Domain 1)

**Goal:** Finish the domain the product already mostly covers, so it's a genuinely complete
"Financial Operations" offering before expanding outward.

- **Budgeting module:** budget entities, budget lines by account/period, budget-vs-actual reporting.
- **Cash & Bank Management:** bank statement import, transaction matching against ledger entries,
  reconciliation status tracking (currently only a "Bank Statement" source-document type exists,
  with no reconciliation engine).
- **Expense Management (T&E):** employee expense claims, approval routing, reimbursement posting.
- **Historical period-balance snapshots:** for fast multi-year comparative statements (currently
  recomputed live from ledger entries every time).
- **External FX rate feed integration:** one-click rate refresh instead of manual entry only.
- Communication/document delivery gaps: AR statement email send, receipt resend, bulk
  source-document upload.

**Exit criteria:** Domain 1 module checklist in Project.md fully covered; Accounting Architect signs
off that Budgeting, Cash Management, and Expense Management follow the same posting-integrity rules
as the existing core (balanced entries, immutability, audit trail).

---

## Phase 2 — Enterprise Finance (Project.md Domain 2)

**Goal:** Multi-company financial management above the single-entity core.

- **Consolidation:** multi-entity consolidated financial statements with intercompany elimination
  (IFRS 10/IAS 27/28) — a first-class elimination engine, not a bolt-on report (see SKILLS.md
  anti-pattern note).
- **Intercompany accounting:** intercompany transactions and balances tracked distinctly from
  external ones.
- **Cost accounting & Project accounting:** cost centers, project-based costing (WIP, cost-to-
  complete).
- **Treasury basics:** cash positioning and forecasting building on Phase 1's Cash Management.
- **Financial close orchestration across entities:** extend the existing single-entity 9-step cycle
  controller to coordinate close across a group of entities.
- **Shared services / Financial planning (FP&A):** groundwork only — full scope depends on demand
  signal from actual users of Phase 1.

**Exit criteria:** Consolidated statements reconcile exactly to the sum of entity-level statements
net of eliminations, verified by Accounting Architect against a multi-entity test scenario set they
author before implementation begins (per AGENTS.md's "produces accounting scenarios" duty).

---

## Phase 3 — Operational Finance (Project.md Domain 3)

**Goal:** Extend from "record the transaction" to "run the business process that generates it."

- **Procurement & Purchasing:** purchase requisitions, purchase orders, 3-way match (PO ↔ goods
  receipt ↔ vendor bill) feeding into the existing AP `payables` module — procurement is its own
  bounded context that *feeds* AP, not an extension of it.
- **Contracts:** contract master data, terms, renewal tracking.
- **Inventory accounting:** stock valuation methods (FIFO/weighted average), COGS posting.
- **Subscription billing:** recurring billing schedules feeding the existing AR invoicing engine.
- **Order-to-Cash / Procure-to-Pay:** formalize these as tracked end-to-end workflows spanning
  existing AR/AP modules plus the new procurement/inventory pieces.

**Exit criteria:** 3-way match cannot post a bill inconsistent with its PO/receipt without an
explicit, audited override; inventory valuation reconciles to the GL inventory account balance.

---

## Phase 4 — Governance & Compliance (Project.md Domain 4)

**Goal:** Formalize controls the product currently only partially expresses.

- **Configurable workflow engine:** replace today's fixed status-based approvals with dynamic,
  rule-based routing supporting multi-level, parallel, conditional approvals, delegation, escalation,
  and SLA monitoring — built once, used by every module (SKILLS.md target skill).
- **Segregation of Duties (SoD):** formal SoD matrix and enforcement beyond RBAC role checks.
- **Risk management module:** risk register, control mapping.
- **Regulatory reporting framework:** generalize beyond the three financial statements toward
  jurisdiction-specific regulatory report templates.
- **Approval governance:** audit history and reporting over the new workflow engine's decisions.

**Exit criteria:** Every existing approval gate (journal, invoice, bill) is migrated onto the new
workflow engine with no loss of audit trail continuity; Accounting Architect confirms SoD rules match
real segregation requirements (e.g., preparer ≠ approver on journal entries).

---

## Phase 5 — Intelligence (Project.md Domain 5)

**Goal:** AI-powered finance capabilities layered on top of a now-mature transactional core.

- **AI Finance Copilot:** natural-language Q&A over financial statements and KPIs.
- **AI Forecasting:** cash flow, revenue, expense forecasting.
- **AI Reconciliation:** auto-matching for bank reconciliation (Phase 1) and intercompany
  reconciliation (Phase 2).
- **Anomaly/Fraud detection:** flagging unusual postings or approval patterns.
- **Executive insights / narrative generation:** month-end close summaries, KPI-change explanations.

**Exit criteria:** every AI output that could affect financial data flows through the existing
approval/audit gates — no AI action bypasses Engineering/Accounting review (CLAUDE.md, SKILLS.md
anti-pattern). Forecasts and anomaly flags are advisory until explicitly promoted to an approved
action by a human user.

---

## Phase 6 — Platform Services & Scale-Out (Project.md Domain 6)

**Goal:** The platform-level capabilities that make QeSuite FA a true multi-tenant, extensible,
globally deployable ERP rather than a single-deployment product.

- **Multi-tenant SaaS control plane:** tenant provisioning, isolation, billing above today's
  multi-entity model.
- **Event bus:** introduce in-process domain events first; external broker only when a concrete
  cross-service durability/replay need exists (CLAUDE.md §7).
- **Microservices decomposition:** extract only genuinely independent-scaling domains (Intelligence/
  AI, Integration Hub, Document/OCR) — the ledger/journal/GL core stays a single consistent unit.
- **GraphQL surface, Integration Hub/webhooks, notification engine (beyond email), OCR ingestion,
  localization/multi-language, extensibility/plugin framework.**
- **Deployment:** move off single-host Docker Compose to Kubernetes/multi-region once tenant count
  or traffic genuinely justifies it — not preemptively.

**Exit criteria:** New tenant onboarding is self-service and isolated; at least one domain has been
successfully extracted as an independently deployable service without breaking the core ledger's
transactional guarantees.

---

## Immediate Next Actions (Top of Backlog)

1. **TOP PRIORITY — codebase-wide IDOR fix cycle** (see Phase 0 above): add entity-ownership
   verification to the ~20 controllers that currently trust a client-supplied `entityId` without
   checking it against the authenticated user's own entity. Starts immediately; each fix requires
   Engineering + (where applicable) Accounting sign-off per AGENTS.md before being marked `[DONE]`.
2. Close the remaining Tier 2 gap: IFRS 15 over-time revenue recognition period-end recognition job.
3. Close Tier 3 infrastructure gaps (Spring Batch pipeline, COA template seed data, M-Pesa session
   table).
4. Delivery Manager to break down the remaining Phase 0 items (Tier 3 gaps, the three
   hardcoded-category backlog items, the `InvoiceService.kt` filter bug, and the test-suite-health
   backlog item) into individually trackable tasks in the next working session.

*(The permanent `PERIOD_ALREADY_OPEN` regression test and the standalone credit-notes endpoint that
were previously top of this list are now `[DONE]` — see Phase 0 above. The old Tier 1 API gap list
previously listed here is resolved as a documentation correction, not new work — also see Phase 0.)*

## How This Plan Is Maintained

Every phase transition, and every completed task within a phase, updates `MEMORY.md`'s current
milestone/sprint per AGENTS.md. This file's phase list may be re-sequenced if user priorities change
(e.g., a specific customer needs Procurement before Consolidation) — re-sequencing is a Delivery
Manager + user decision, not something any agent does unilaterally.

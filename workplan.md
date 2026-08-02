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

- Reconcile uncommitted working-tree changes (auth page redesign, ApiKeys.vue enhancements) — get
  user decision to commit or discard, don't leave them in limbo.
- Re-verify and fix the critical accounting-control bugs carried forward in MEMORY.md: single-open-
  period enforcement (BUG-25), fiscal-year generation defaults (BUG-24), fiscal-year context
  switching (BUG-27), fiscal-year switcher UI (BUG-34).
- Close the confirmed Tier 1 API gaps: `PUT` endpoints for FX currencies, exchange rates, tax codes,
  fixed assets; comparative trial balance implementation; organization RBAC fix for entity-scoped
  `SYSTEM_ADMIN`.
- Close Tier 2 workflow gaps: standalone credit-notes endpoint, closing-preview endpoint,
  source-document restore, IFRS 15 over-time revenue recognition period-end job.
- Wire the Spring Batch background-job pipeline so depreciation and FX revaluation run
  asynchronously (prerequisite for Phase 2/3 batch-heavy work like consolidation).
- Seed COA template data so the signup wizard works end-to-end.
- Build the M-Pesa STK-push session table (replace sentinel UUIDs) if M-Pesa remains a target
  integration.

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

1. User decision on the uncommitted auth-page redesign and ApiKeys.vue changes (commit vs. discard).
2. Agent 2 (Accounting) re-verification of the single-open-period and fiscal-year-default bugs —
   these are accounting-control defects, treated as highest priority in Phase 0.
3. Delivery Manager to break down the remaining Phase 0 items into individually trackable tasks in
   the next working session.

## How This Plan Is Maintained

Every phase transition, and every completed task within a phase, updates `MEMORY.md`'s current
milestone/sprint per AGENTS.md. This file's phase list may be re-sequenced if user priorities change
(e.g., a specific customer needs Procurement before Consolidation) — re-sequencing is a Delivery
Manager + user decision, not something any agent does unilaterally.

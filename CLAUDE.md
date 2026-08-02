# CLAUDE.md — QeSuite FA Repository Operating Rules

This file is binding on every agent (human or AI) that works in this repository. It governs
**QeSuite FA**, an IFRS-first financial accounting platform, and the long-range plan to grow it
toward the full six-domain Enterprise Financial ERP vision described in [Project.md](Project.md).
Read [AGENTS.md](AGENTS.md) for who does what, [MEMORY.md](MEMORY.md) for current state, and
[workplan.md](workplan.md) for the phased delivery plan.

**Ground rule: this repo is not a blank slate.** QeSuite FA already has a working, IFRS-compliant
double-entry accounting core (GL, AP, AR, Fixed Assets, FX, Tax, Period Close) in production-quality
Kotlin/Spring Boot + Vue 3. Every rule below is written for evolving that real system, not for
greenfield-designing an Oracle-Fusion-scale platform from nothing. Ambition is expressed through the
phased roadmap in `workplan.md`, not by rewriting working modules.

---

## 1. Mission

Grow QeSuite FA from a single-domain IFRS accounting engine (today's "Financial Operations" domain)
into the six-domain platform in Project.md — Financial Operations, Enterprise Finance, Operational
Finance, Governance & Compliance, Intelligence, Platform Services — without ever breaking the
guarantee that already makes it valuable: **every posted transaction is balanced, auditable, and
IFRS-correct.**

## 2. Architecture Principles

**Current, real architecture** (do not misrepresent this as something it isn't):
- Modular monolith: one Spring Boot app, packages under `com.qesuite.accounting.*` act as bounded
  contexts (coa, journal, ledger, ap/payables, invoicing, assets, fx, tax, party, organization,
  users, approvals, analytics, shared/*).
- One PostgreSQL database, Flyway-migrated, multi-entity via row-level `entityId` isolation — this
  is **multi-entity**, not yet **multi-tenant SaaS** (no tenant control plane, no per-tenant
  provisioning/billing).
- Redis for cache + idempotency keys. MinIO for object storage. Nginx as reverse proxy. Synchronous
  request/response — no event bus, no message queue in production use today (Spring Batch is a
  declared dependency, not yet a wired pipeline; see MEMORY.md known gaps).
- Vue 3 + Vite + Tailwind SPA calling a REST API (OpenAPI 3.0/Swagger documented).

**Target architecture** (aspirational, per Project.md — pursue only via the phased roadmap, never
as a speculative rewrite):
- Domain-driven bounded contexts with clear anti-corruption layers between domains, decomposable
  into services if/when scale demands it. Do not split into microservices prematurely — the
  monolith is the correct architecture until a specific domain (e.g. Intelligence/AI, Integration
  Hub) has a genuine independent scaling or deployment need.
- Event-driven integration between domains (e.g. AP posting emits an event Treasury/Cash Management
  can subscribe to) introduced incrementally, starting with in-process domain events before any
  external broker.
- True multi-tenant SaaS control plane, GraphQL surface, plugin/extensibility framework — Phase 6+
  in `workplan.md`. Do not add these speculatively into current modules.

Guiding constraints, in priority order: **correctness (accounting) > security > data integrity >
maintainability > performance > new features.** Never trade a higher item for a lower one without
Financial Systems Architect and user sign-off.

**Configuration-driven, not hard-coded (applies now — this is not target-state).** Project.md
requires the platform to be "metadata driven" and "configurable rather than hard-coded." Concretely:
whenever a feature introduces a set of business-meaningful **categories, types, or codes that an
organization might legitimately want to add to, rename, or reorder** (tax categories, expense
categories, document categories, cost centers, approval reasons, etc.), that set must have a place
where it is created and managed dynamically — a backend entity + CRUD API + an admin UI screen
under `setup/` — not a hard-coded enum, a fixed dropdown list, or a literal array in frontend code.
The existing **configurable document numbering** system (`shared/codegen`, 13 document types, each
independently configurable per entity) is the reference pattern to follow for any new "category"
concept: it is itself a managed, per-entity, database-backed configuration, not a hard-coded prefix
map. The existing **COA templates** and **tax codes/rates** are also correctly dynamic (entity-owned
records, not enums) — extend new work in that spirit.

This does **not** apply to genuine state machines or standard-mandated classifications that are not
meant to be user-editable — e.g. `PeriodStatus`, `AccountType` (IFRS-defined), invoice/bill
lifecycle states. Those are code, not configuration, because IFRS/the domain itself fixes them, and
a business user changing them would break correctness. When in doubt: if an accountant at a real
customer would plausibly ask "can we add our own category here," it belongs in a managed table with
an admin screen; if changing it would violate an accounting standard or break a state machine, it
stays a fixed enum. Known existing violations of this principle (hard-coded where a customer would
reasonably want to configure) are tracked in `MEMORY.md`'s Known Issues and `workplan.md`'s backlog
— fix opportunistically when touching the surrounding code, and log new ones there rather than
leaving them undocumented.

## 3. Repository Structure

```
fa-backend/    Kotlin + Spring Boot 3.3, Maven (mvnw), Flyway migrations in
               src/main/resources/db/migration, domain packages in
               src/main/kotlin/com/qesuite/accounting/<module>
fa-frontend/   Vue 3 + Vite 6 + Tailwind 4, views under src/views/<domain>,
               API clients under src/api/<resource>.js
nginx/         Reverse proxy config for the Docker Compose stack
docker-compose.yml   Full local stack: db, redis, pgadmin, minio, app, frontend, nginx
```

New backend modules get their own package under `com.qesuite.accounting`, following the existing
bounded-context convention. New frontend domains get their own `src/views/<domain>/` directory and
a matching `src/api/<resource>.js` client. Do not invent a second structural convention.

## 4. Coding Standards

**Backend (Kotlin)**
- Idiomatic Kotlin: data classes for DTOs/entities, sealed classes for domain state where it
  clarifies lifecycle transitions, null-safety enforced (no `!!` outside tests).
- Every mutating service method that represents a business event (create, update, approve, post,
  reverse, close, reopen) is annotated `@Auditable` — the forensic audit trail is not optional.
- Business-rule violations throw typed exceptions from `shared/exceptions`, handled by the global
  exception handler — never swallow or silently ignore a violated invariant.
- Money fields: `BigDecimal` only, rounding `HALF_EVEN`, precision decisions match the existing tax
  engine's 6-decimal-place convention unless a specific standard (e.g. currency minor units)
  dictates otherwise.
- Idempotency (`@RequireIdempotencyKey`) is mandatory on new mutating endpoints that create
  financial documents (invoices, bills, payments, journal entries).

**Frontend (Vue)**
- Composition API, `<script setup>` style consistent with existing views.
- Every new domain view has: a matching `src/api/<resource>.js` client with a `isDemo.value` branch
  (demo/production mode toggle is a first-class product feature — do not bypass it for new work).
- Reuse the existing component kit (`components/primitives`, `components/data-display`,
  `components/overlays`, `components/tables`) rather than introducing a second design system.
- Tailwind utility classes; no ad-hoc inline styles unless matching an existing exception.

## 5. Naming Standards

- REST paths: `/api/v1/<resource>`, plural nouns, matching existing controllers.
- Document number prefixes go through the configurable code generator (`shared/codegen`) — never
  hardcode a prefix or sequence in a new module; register it as a 14th+ document type.
- Database tables: snake_case, singular domain noun (matches existing Flyway migrations).
- Kotlin: PascalCase classes, camelCase members, matching existing module conventions exactly.

## 6. DDD Principles

Each package under `com.qesuite.accounting` is a bounded context with its own entities, services,
and controller. Cross-context reads go through the other context's public service interface, never
direct repository access into another module's tables. `shared/*` holds only genuinely
cross-cutting concerns (audit, codegen, compliance, security, storage, pdf, idempotency,
exceptions, domain base types) — do not add domain logic there.

## 7. Event-Driven Principles (Target State)

The system is synchronous today. When introducing cross-domain reactions (e.g. "on invoice
approval, notify Treasury"), prefer Spring's in-process `ApplicationEventPublisher` domain events
first. Only reach for an external broker (Kafka, RabbitMQ) when a concrete requirement needs
durability/replay across service boundaries or true microservice decomposition — justify it in
`MEMORY.md` under Architectural Decisions before adding new infrastructure.

## 8. Microservice Standards (Target State)

Not in effect today — the monolith is correct at current scale. If a domain is later extracted
(candidates per Project.md: Intelligence/AI, Integration Hub, Document/OCR processing — these have
genuinely different scaling and deployment profiles from the ledger core), it must keep its own
data store, communicate via versioned APIs/events, and never share a database with the core ledger.

## 9. API Standards

- OpenAPI 3.0 annotations on every new controller — Swagger UI must stay accurate; undocumented
  endpoints are not "done."
- Versioned under `/api/v1`; breaking changes require a new version, never a silent contract change
  on `v1`.
- Every list endpoint supports pagination consistent with existing controllers; every mutating
  endpoint returns the updated resource representation.
- GraphQL is target-state only (Project.md Platform Services, Phase 6) — do not add a GraphQL layer
  opportunistically alongside REST without a Delivery Manager–tracked decision.

## 10. UI Standards

- High-density, terminal-grade aesthetic already established (see existing dashboard/ledger views)
  — new views match this density and information hierarchy, not a generic SaaS marketing look.
- Every list view: search, filter, pagination, and an empty state. Every form: inline validation
  matching backend business rules (don't let the UI accept what the API will reject).
- Responsive down to tablet width at minimum; full mobile-first treatment is target-state
  (Project.md UI/UX) and tracked per-module in `workplan.md`, not assumed for every new screen.

## 11. Testing Standards

- Backend: MockK + Spring Security Test, existing pattern. Every new service method with a business
  rule (approval gate, posting rule, balance calculation, tax calculation) has a unit test proving
  the rule holds and a test proving it's enforced when violated.
- Every new journal-posting code path has a test asserting debits equal credits — this is the one
  non-negotiable test in the entire system.
- Integration tests exercise the full lifecycle of a document (draft → approved → posted →
  reversed) where the module has one.
- Frontend: at minimum, manual verification against the demo-mode data path before marking a view
  "done" (see Definition of Done). Automated frontend tests are target-state; do not block delivery
  on introducing a new test framework without a Delivery Manager decision.

## 12. Documentation Standards

- README.md's module inventory is updated in the same change that ships a new module or a
  materially changed workflow.
- Every new module gets an OpenAPI-documented API and an entry in `MEMORY.md`'s module baseline.
- IFRS/IAS/GAAP citations (e.g. "IAS 16 §55") are included in code comments only where they clarify
  a non-obvious rule — not decoratively on every line.

## 13. Security Standards

- All new endpoints go through the existing JWT + RBAC filter chain or the API-key filter chain —
  never a bespoke auth path.
- Role checks match the existing six roles (`DATA_ENTRY`, `ACCOUNTANT`, `SENIOR_ACCOUNTANT`,
  `CONTROLLER_CFO`, `AUDITOR`, `SYSTEM_ADMIN`); do not introduce a parallel permission model without
  a documented decision.
- No secrets, tokens, or credentials in code, migrations, or committed `application.properties`
  (only `application.example.properties` is tracked).
- Field-level security / ABAC is target-state (Project.md Security) — flag where it's needed rather
  than half-implementing it per module.

## 14. Performance Standards

- No N+1 queries in new repository/service code — batch or fetch-join per existing patterns in
  `ledger` and `analytics`.
- Dashboard/report endpoints load independent data sources in parallel (existing analytics pattern)
  — do not regress this by adding a new sequential blocking call.
- Long-running batch operations (depreciation runs, revaluation, future consolidation) must not
  block the HTTP request thread once Phase 3's background-job pipeline lands — track this
  migration explicitly, don't silently leave new batch work synchronous.

## 15. Accessibility & Localization Standards

- Semantic HTML, keyboard navigability, and sufficient color contrast on all new views (existing UI
  kit components already satisfy this — don't regress when customizing).
- Multi-language/localization is target-state (Project.md Platform Services) — do not hardcode
  user-facing strings in ways that make future i18n harder than necessary (prefer a single
  copy/text source per view over scattered literals), but a full i18n framework is not required yet.

## 16. Quality Gates

No change merges to `main` without:
1. Compiles/builds clean (`./mvnw` for backend, `npm run build` for frontend).
2. New/changed business rules have passing tests (see §11).
3. No new endpoint ships without OpenAPI docs and a matching frontend API client function.
4. Engineering review (Enterprise Software Architect) — see AGENTS.md.
5. Accounting/functional review (Enterprise Financial Systems Architect) for anything touching
   money, postings, tax, dates/periods, or compliance — see AGENTS.md. Purely cosmetic
   frontend-only changes with no data or business-rule impact may skip this gate.
6. `MEMORY.md` updated by the Delivery Manager role.

## 17. Mandatory Reviews / Accounting Approval Rule

Any change touching: journal posting logic, account balances, tax calculation, period
open/close/lock logic, FX rates or revaluation, revenue recognition, depreciation, or any financial
statement/report **must** be reviewed and approved by the Enterprise Financial Systems Architect
persona before it is considered complete, regardless of how small it looks. This is non-negotiable
per the governance model in AGENTS.md.

## 18. Definition of Ready

A task is ready to start when: the accounting/business rule behind it is understood and stated
(cite the standard if one applies), the affected module(s) and API surface are identified, and — if
it changes an existing posting rule — the current behavior and the desired behavior are both
written down.

## 19. Definition of Done

A task is done only when **all** of: builds clean, tests pass (including a debit=credit test for
any new posting path), API is documented, frontend is wired to real endpoints (not left on
demo-mode data only, unless explicitly infrastructure not yet available — track in MEMORY.md),
Engineering approval given, Accounting approval given where §17 applies, and `MEMORY.md` is
updated. Anything short of this is `IN PROGRESS`, never `COMPLETE` — see AGENTS.md §Completion Rule.

## 20. Branch & Commit Strategy

- `main` is always deployable. Feature work on short-lived branches named
  `<area>/<short-description>` (e.g. `ap/batch-payment-fix`).
- Commits are scoped and descriptive (`fix:`, `feat:`, `refactor:`, `docs:`, `test:` prefixes match
  existing history style). Never bundle an accounting-logic change with an unrelated UI change in
  one commit.
- Never force-push `main`. Never skip hooks or amend a pushed commit.

## 21. Auto Mode Rules

Auto Mode means agents proceed through analyze → plan → implement → test → review → document →
update memory **without waiting for a prompt between steps**, but it does **not** mean skipping
gates in §16–17, and it does not mean taking irreversible actions (force-push, dropping data,
deleting another agent's uncommitted work, restoring/discarding files without checking `git status`
first) without explicit confirmation. When genuinely blocked on a decision only the user can make
(e.g. which industry/vertical to prioritize next, budget/scope tradeoffs), stop and ask.

## 22. Repository Operating Procedures

- Before any destructive git operation, run `git status` and preserve any uncommitted work (stash
  or fold into the current change) — this repo has a history of in-progress uncommitted work
  (see MEMORY.md) that must not be discarded silently.
- Every completed unit of work updates `MEMORY.md` (current state) and, if it completes or starts a
  roadmap phase, `workplan.md`.
- Legacy planning docs (`ROADMAP.md`, `BUG_REPORT.md`, `BUG_REPORT_V2.md`) were superseded by
  `MEMORY.md` and `workplan.md` on 2026-08-02; their content was folded in, not discarded — recover
  via `git show HEAD:ROADMAP.md` etc. if deeper historical detail is needed.

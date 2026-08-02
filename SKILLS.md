# SKILLS.md — Skill Inventory

Skills are grouped **Active** (in real use in this codebase today) and **Target** (needed for the
Project.md vision, not yet applied — do not reach for these speculatively; they activate when
`workplan.md` reaches the phase that needs them). Each entry: Purpose, Best Practices, Patterns,
Anti-patterns, Reference Standards.

---

## Active Skills

### Accounting — IFRS Core (GL, AP, AR, Assets, FX, Tax)
- **Purpose:** Ground every posting, report, and business rule in correct financial accounting.
- **Best practices:** Double-entry always balances before persistence; corrections are reversals;
  header accounts never receive postings; functional currency is singular and mandatory per entity.
- **Patterns:** Draft→Approve→Post→Reverse lifecycle for every transactional document; auto-journal
  on approval (e.g. AR: DR Receivable/CR Revenue+Tax Payable); effective-dated rates for tax and FX.
- **Anti-patterns:** Editing a posted ledger entry directly; computing tax/FX at "today's rate"
  instead of the transaction date's effective rate; allowing postings to a parent/header account.
- **Reference standards:** IAS 1 (presentation), IAS 7 (cash flow, indirect method), IAS 16 (fixed
  assets), IAS 21 (FX/functional currency), IFRS 15 (revenue recognition, 5-step model).

### Financial Controls & Audit
- **Purpose:** Make every financial action traceable and every period boundary enforceable.
- **Best practices:** Insert-only audit records with before/after payload snapshots; audit write
  commits independently of the business transaction's rollback (`REQUIRES_NEW`); single-open-period
  discipline per entity.
- **Patterns:** AOP-driven `@Auditable` annotation on business-event service methods; period-lock
  interceptor rejecting mutations against `CLOSED` periods before they reach service logic.
- **Anti-patterns:** Auditing at the controller layer only (misses direct service invocations);
  allowing more than one `OPEN` period per entity (confirmed critical defect — see MEMORY.md).
- **Reference standards:** SOX-style internal control patterns; IAS 1 period presentation.

### Kotlin / Spring Boot Backend
- **Purpose:** Build correct, typed, testable business services.
- **Best practices:** Data classes for entities/DTOs, null-safety enforced, `BigDecimal` for all
  money with `HALF_EVEN` rounding, typed business exceptions over generic ones.
- **Patterns:** Bounded-context package per domain under `com.qesuite.accounting.*`; service layer
  owns business rules, controller layer stays thin; `@RequireIdempotencyKey` on financial mutations.
- **Anti-patterns:** Cross-module direct repository access (bypassing the owning module's service);
  `!!` non-null assertions outside test code; silently catching a business-rule exception.
- **Reference standards:** Spring Boot 3.3 conventions, Kotlin idiomatic style.

### PostgreSQL / Flyway
- **Purpose:** Durable, migration-controlled schema for financial data.
- **Best practices:** Every schema change is a new versioned Flyway migration, never an edit to an
  applied one; indexes on high-cardinality audit/lookup columns (`resource_type, resource_id`,
  `entity_id`).
- **Patterns:** snake_case tables, entity-scoped isolation via `entityId` foreign key on every
  financial table; optimistic locking (version column) on records subject to concurrent edits.
- **Anti-patterns:** Editing a shipped migration; unindexed foreign keys on tables queried at scale.
- **Reference standards:** Flyway migration discipline, PostgreSQL 15 features.

### Redis (Cache & Idempotency)
- **Purpose:** Prevent duplicate financial mutations and cache hot read paths.
- **Best practices:** Idempotency keys backed by Redis with PostgreSQL durability fallback; short
  TTLs on caches that front live financial balances (setup-health caches for 5 minutes, matching
  existing pattern).
- **Anti-patterns:** Caching a balance without a TTL or invalidation path tied to the posting event.

### MinIO / Object Storage
- **Purpose:** Store source-document attachments and generated PDFs outside the relational database.
- **Best practices:** Store only references (path/key) in PostgreSQL; binary content in MinIO
  (or local disk in development) behind the existing storage abstraction.
- **Anti-patterns:** Storing binary blobs in PostgreSQL columns; bypassing the storage abstraction
  with a direct S3 client call in a new module.

### Vue 3 / Vite / Tailwind Frontend
- **Purpose:** Build the high-density SPA UI consistent with the existing product.
- **Best practices:** Composition API/`<script setup>`; reuse the existing component kit; every view
  backed by a matching `src/api/<resource>.js` client with a demo-mode branch.
- **Patterns:** One `views/<domain>/` directory per business domain, mirroring backend bounded
  contexts; inline form validation matching backend business rules.
- **Anti-patterns:** A second, parallel component/design system; a view with no demo-mode data path;
  a form that accepts input the API is known to reject.
- **Reference standards:** Vue 3.5, Tailwind 4 conventions already established in the codebase.

### REST API Design (OpenAPI)
- **Purpose:** Keep the API contract documented, versioned, and predictable for integrators.
- **Best practices:** OpenAPI 3.0 annotations on every controller; `/api/v1` versioning; paginated
  list endpoints; mutating endpoints return the updated resource.
- **Anti-patterns:** Undocumented endpoints; silent breaking changes on an existing `v1` contract.
- **Reference standards:** OpenAPI 3.0 spec, REST maturity model (resource-oriented, not RPC-style).

### Security (JWT, RBAC, API Keys)
- **Purpose:** Protect financial data with authenticated, role-scoped access.
- **Best practices:** Access + refresh token rotation with refresh tokens stored as SHA-256 hashes;
  account lockout after repeated failures; API keys shown once, stored hashed; role checks at the
  service layer, not just route-guard level.
- **Anti-patterns:** Storing tokens or API keys in plaintext; a new auth path bypassing the existing
  filter chain; a permission model parallel to the six established roles.
- **Reference standards:** RFC 6749-pattern token rotation, OWASP authentication guidance.

### Resilience & Observability
- **Purpose:** Keep the system diagnosable and stable under partial failure.
- **Best practices:** Resilience4j circuit breakers on external calls (e.g., email, future FX feed
  integration); Micrometer/Prometheus metrics; OpenAPI/Swagger kept accurate as living documentation.
- **Anti-patterns:** An external integration with no timeout/circuit breaker; a metric added without
  a corresponding dashboard/alert consumer.

---

## Target Skills (Project.md Vision — Not Yet Active)

These activate only when `workplan.md` reaches the relevant phase. Listed now so the gap is
explicit and no agent quietly reaches for them early.

### Enterprise Consolidation & Intercompany Accounting
- **Purpose:** Multi-company consolidated financial statements (workplan Phase 2).
- **Reference standards:** IFRS 10 (Consolidated Financial Statements), IAS 27/28, elimination and
  minority-interest mechanics. **Anti-pattern to avoid when this starts:** building consolidation as
  a bolt-on report rather than a first-class elimination engine with its own audit trail.

### Treasury & Cost/Project Accounting
- **Purpose:** Cash forecasting, cost centers, project-based costing (workplan Phase 2).
- **Reference standards:** IAS 2 (inventory costing, where relevant), standard project-accounting
  patterns (WIP, cost-to-complete).

### Procurement / Order-to-Cash / Procure-to-Pay
- **Purpose:** PO-to-bill 3-way matching, contracts, inventory accounting, subscription billing
  (workplan Phase 3).
- **Anti-pattern to avoid:** modeling procurement as an extension of the AP `payables` module rather
  than its own bounded context that *feeds* AP on a matched, approved bill.

### Configurable Workflow Engine
- **Purpose:** Dynamic multi-level/parallel/conditional approval routing beyond today's fixed
  status-based approvals (workplan Phase 4).
- **Patterns:** Rule-based routing, delegation, escalation, SLA monitoring — as a reusable engine
  every module calls, not a per-module reimplementation.

### Risk Management & Segregation of Duties
- **Purpose:** Formal SoD enforcement and risk register beyond RBAC (workplan Phase 4).
- **Reference standards:** COSO internal control framework, SOX-style SoD matrices.

### AI Finance Copilot / Forecasting / Anomaly Detection
- **Purpose:** Natural-language finance Q&A, cash-flow/revenue forecasting, fraud/anomaly detection,
  auto-reconciliation (workplan Phase 5).
- **Anti-pattern to avoid:** bolting an LLM onto raw ledger data without going through the existing
  audited service layer — AI outputs affecting financial data must still flow through normal
  approval/audit gates, never bypass them.

### Event-Driven Architecture / Message Bus
- **Purpose:** Cross-domain reactions at scale (workplan Phase 6), once genuinely needed.
- **Reference standards:** Domain events first (in-process), broker (Kafka/RabbitMQ) only when a
  concrete durability/replay/service-boundary requirement exists — see CLAUDE.md §7.

### Microservices Decomposition
- **Purpose:** Extract genuinely independent-scaling domains (candidates: Intelligence/AI,
  Integration Hub, Document/OCR) from the monolith (workplan Phase 6).
- **Anti-pattern to avoid:** decomposing the ledger/journal/GL core — that stays a tightly
  consistent transactional unit.

### Multi-Tenant SaaS Control Plane
- **Purpose:** Tenant provisioning, isolation, and billing beyond today's multi-entity model
  (workplan Phase 6).

### GraphQL / Integration Hub / Webhooks / OCR / Localization
- **Purpose:** Platform Services breadth per Project.md (workplan Phase 6) — API surface expansion,
  third-party integration framework, document OCR ingestion, and multi-language support.

### Kubernetes / Cloud Deployment at Scale
- **Purpose:** Horizontal scaling and multi-region deployment once traffic/tenant count justifies
  moving off the current single-host Docker Compose stack (workplan Phase 6).

# `skills.md`
# AI AGENT REQUIRED SKILLS & COMPETENCIES
## IFRS Financial Accounting System — Kotlin + Spring Boot + PostgreSQL
## Version: 2.0 | Aligned to: System Design Prompt v3.0

> **AGENT DIRECTIVE:** This document defines the minimum required competency profile for any agent assigned to this system. Before accepting any task, an agent must self-assess against every section below. If a proficiency gap exists in a domain required by the assigned module, the agent must halt and escalate — partial proficiency is a build risk, not an acceptable trade-off.

---

## TABLE OF CONTENTS

1. [Technical Proficiency](#1-technical-proficiency)
2. [Financial & IFRS Domain Knowledge](#2-financial--ifrs-domain-knowledge)
3. [Module-Specific Domain Skills](#3-module-specific-domain-skills)
4. [Architectural & Cloud-Native Expertise](#4-architectural--cloud-native-expertise)
5. [Security & Compliance Skills](#5-security--compliance-skills)
6. [Testing & Quality Assurance Skills](#6-testing--quality-assurance-skills)
7. [Integration & Messaging Skills](#7-integration--messaging-skills)
8. [Agent Self-Assessment Protocol](#8-agent-self-assessment-protocol)

---

## 1. TECHNICAL PROFICIENCY

### 1.1 Core Language — Kotlin 1.9+

| Skill | Required Level | Application in This System |
|---|---|---|
| Null-safety (`?`, `!!`, `let`, `?.`) | Expert | All financial entities have nullable FKs; null must never silently pass validation |
| Data classes & sealed hierarchies | Expert | All domain models (`JournalEntry`, `LedgerEntry`, `Payment`, etc.) are data classes; state machines use sealed classes |
| Coroutines & `@Async` | Advanced | Webhook async processing (M-Pesa callbacks must return in <5s); background ledger posting |
| Extension functions | Advanced | Domain-level validators (e.g., `JournalEntry.validate()`, `BigDecimal.roundHalfEven()`) |
| Inline functions & reified types | Intermediate | Generic repository abstractions, type-safe query builders |
| Enum classes with `when` | Expert | All state machines (`PeriodStatus`, `JournalEntryStatus`, `PaymentStatus`, `ReceiptStatus`) must use exhaustive `when` |
| Kotlin DSL | Intermediate | Build scripts (`build.gradle.kts`), test fixtures |

### 1.2 Framework — Spring Boot 3.x

| Skill | Required Level | Application in This System |
|---|---|---|
| `@Transactional` semantics | Expert | Every posting operation (journal, payment, receipt) requires transaction boundaries; rollback on any validation failure |
| `@Async` + thread pool config | Advanced | M-Pesa webhook queuing; async financial statement generation |
| AOP interceptors | Advanced | Period-lock enforcement across all POST/PUT endpoints; audit log injection |
| `@ControllerAdvice` + exception mapping | Expert | All 60+ error codes must map to standardized HTTP responses (see `instructions.md §6`) |
| `@PreAuthorize` / Spring Security | Expert | Role-based method-level security on every financial endpoint |
| Spring Batch | Advanced | Bulk bank statement import; recurring journal generation; period-end batch jobs |
| Bean Validation (`@Valid`, custom validators) | Expert | Double-entry balance check; account type enforcement; period open check |
| Production-Grade AOP | Expert | Use of parameter-level annotations (`@AuditEntityId`, `@AuditResourceId`) for context extraction; zero reliance on positional heuristics |
| Import & Exception Hygiene | Expert | Mandatory use of clean imports; no qualified names in logic; proactive consolidation of exceptions to avoid redeclaration |
| Spring Data JPA / Hibernate | Expert | See §1.3 below |
| Actuator & Micrometer | Advanced | Custom metrics: `je_post_duration`, `payment_match_failures`, `period_lock_violations`, `ifrs_compliance_checks`, `receipt_delivery_failures` |
| OpenAPI / Swagger | Expert | All endpoints must have documented request/response schemas with high-fidelity financial examples |

### 1.3 ORM — Spring Data JPA / Hibernate

| Skill | Required Level | Application in This System |
|---|---|---|
| Entity lifecycle & `@EntityListeners` | Expert | Audit columns (`created_at`, `created_by`, `modified_at`, `modified_by`) auto-populated on all entities |
| `@Version` optimistic locking | Expert | `JournalEntry`, `LedgerEntry`, `Period` entities require optimistic locking to prevent concurrent posting conflicts |
| Custom type converters | Expert | `BigDecimal` → `DECIMAL(20,6)` precision mapping; `Enum` ↔ DB string converters |
| `@Column(updatable = false)` | Expert | All immutable fields (IDs, `posted_at`, `entry_date` on POSTED entries) |
| Query optimization (JPQL + native) | Advanced | Trial balance generation must complete in <5s; use indexed queries on `period_id`, `account_id`, `status` |
| `@SoftDelete` / `is_active` patterns | Expert | No hard deletes on any financial record |
| Lazy vs eager loading | Advanced | Avoid N+1 on ledger entry retrieval; use `JOIN FETCH` for journal entry lines |
| Projections & DTOs | Advanced | Financial statement queries return aggregated projections, not full entity graphs |

### 1.4 Database — PostgreSQL 15+

| Skill | Required Level | Application in This System |
|---|---|---|
| `DECIMAL(20,6)` precision | Expert | Mandatory for all monetary columns — never `NUMERIC` without precision, never `FLOAT` |
| DB-level constraints | Expert | `CHECK (debit_amount = 0 OR credit_amount = 0)`, `CHECK (debit_amount + credit_amount > 0)`, `UNIQUE(TransID)`, `UNIQUE(receipt_number)`, `UNIQUE(invoice_number)` |
| Foreign keys with `ON DELETE RESTRICT` | Expert | All financial record FK relationships |
| Indexes | Advanced | Required on: `status`, `period_id`, `entry_date`, `account_id`, `customer_id`, `supplier_id`, `TransID`, `idempotency_key` |
| JSONB | Advanced | `before_state` / `after_state` audit snapshots stored as JSONB |
| Row-level security (RLS) | Advanced | Multi-entity isolation — entities cannot access each other's ledger data |
| Partitioning | Intermediate | Optional: ledger entries partitioned by `period_id` for performance at scale |
| `pg_stat_statements` | Intermediate | Query profiling; CI/CD alert on queries >1s |
| Read replicas | Advanced | All reporting endpoints (`/trial-balance`, `/financial-statements`, `/reports/*`) route to read replica |
| Flyway / Liquibase migrations | Expert | All schema changes version-controlled; no manual DDL in production |

### 1.5 API Design

| Skill | Required Level | Application in This System |
|---|---|---|
| RESTful resource design | Expert | All 100+ endpoints follow noun-based resource paths (§17 of Design Doc) |
| Standardized response envelopes | Expert | Every response: `{ success, data, errors, warnings, metadata }` — no raw exceptions |
| Idempotency patterns | Expert | `Idempotency-Key` header required on all payment, receipt, and journal POST endpoints; store key + response with 24h TTL |
| Pagination & filtering | Expert | All list endpoints: `?page`, `?size`, `?sort` + domain filters; default `size=50`, max `size=500` |
| HTTP status code discipline | Expert | See full error code table in `instructions.md §6` |
| OpenAPI 3.x | Advanced | Auto-generated via SpringDoc; all request/response bodies, path params, and error responses documented |
| Versioning strategy | Intermediate | URI versioning (`/v1/`) — breaking changes require new version |

---

## 2. FINANCIAL & IFRS DOMAIN KNOWLEDGE

### 2.1 Core Accounting Principles

| Competency | Required Level | What the Agent Must Know |
|---|---|---|
| Double-entry bookkeeping | Expert | Every transaction: ≥1 debit + ≥1 credit; `SUM(debits) = SUM(credits)` with zero tolerance; normal balances per account type |
| Accrual basis accounting | Expert | Revenue recognised when earned (not when cash received); expenses recognised when incurred (not when paid) |
| Nine-step accounting cycle | Expert | Journalize → Post → Adjusting → Post Adjusting → Income Statement → Retained Earnings → Balance Sheet → Closing Entries → Post Closing; strict sequence enforcement |
| Account classification | Expert | ASSET (debit-normal), LIABILITY (credit-normal), EQUITY (credit-normal), REVENUE (credit-normal), EXPENSE (debit-normal); contra accounts opposite |
| Temporary vs permanent accounts | Expert | REVENUE, EXPENSE, DIVIDENDS are temporary (closed each period); ASSET, LIABILITY, EQUITY are permanent (carry forward) |
| Trial balance mechanics | Expert | Unadjusted TB: before adjustments; Adjusted TB: after adjustments; Post-Closing TB: permanent accounts only; all must balance |
| Closing entries (4-step) | Expert | (1) Close Revenue → Income Summary; (2) Close Expenses → Income Summary; (3) Close Income Summary → Retained Earnings; (4) Close Dividends → Retained Earnings |

### 2.2 IFRS Standards — Required Knowledge by Standard

| Standard | Competency Required | Key Implementation Points |
|---|---|---|
| **IAS 1** — Presentation | Expert | Minimum line items; current/non-current classification; comparative periods; going concern; single vs two-statement P&L format; function vs nature expense classification |
| **IAS 2** — Inventories | Advanced | FIFO or weighted average only (LIFO prohibited under IFRS); cost vs NRV write-down; reversal of write-down |
| **IAS 7** — Cash Flows | Advanced | Operating (direct and indirect method), investing, financing classification; interest and dividend policy choices; reconciliation to closing cash balance |
| **IAS 10** — Events After Reporting | Intermediate | Adjusting vs non-adjusting events; disclosure requirements |
| **IAS 16** — PPE | Advanced | Cost model vs revaluation model; componentisation; depreciation methods (straight-line, declining balance, units of production); derecognition; residual value review |
| **IAS 21** — Foreign Exchange | Advanced | Functional currency determination; transaction date rates; closing rates for monetary items; FX differences to P&L vs OCI; translation of foreign operations |
| **IAS 36** — Impairment | Advanced | Annual testing triggers; CGU identification; recoverable amount (higher of FVLCTD and VIU); impairment loss reversal rules |
| **IAS 37** — Provisions | Advanced | Three-criteria recognition test (present obligation + probable outflow + reliable estimate); contingent liability disclosure only; contingent asset: do not recognise |
| **IAS 38** — Intangibles | Advanced | Identifiability criterion; research (expense) vs development (capitalise if criteria met); useful life (finite vs indefinite); amortisation |
| **IFRS 9** — Financial Instruments | Advanced | Classification: amortised cost, FVOCI, FVTPL; ECL impairment model (3-stage); effective interest method; hedge accounting basics |
| **IFRS 13** — Fair Value | Advanced | Fair value hierarchy (Level 1/2/3); principal market; unit of account; required disclosures |
| **IFRS 15** — Revenue | Expert | 5-step model: (1) identify contract; (2) identify performance obligations; (3) determine transaction price; (4) allocate; (5) recognise; contract assets vs contract liabilities; variable consideration; over-time vs point-in-time recognition |
| **IFRS 16** — Leases | Advanced | Lessee: ROU asset + lease liability at present value; short-term and low-value exemptions; interest unwinding (effective interest); ROU depreciation; lease modification |

### 2.3 Revenue Cycle Domain (Modules 13–15)

| Competency | Required Level | Application |
|---|---|---|
| Invoice lifecycle management | Expert | DRAFT → APPROVED → SENT → PARTIALLY_PAID → PAID → VOID/CREDIT_NOTE; credit limit enforcement; tax calculation |
| Accounts receivable mechanics | Expert | Invoice posting creates AR debit; payment receipt clears AR; ageing analysis (0–30, 31–60, 61–90, 90+ days) |
| Payment channel accounting | Expert | Each channel (M-Pesa, bank transfer, cash, cheque, card) maps to a specific cash/bank account in the COA |
| M-Pesa webhook processing | Expert | Async acknowledgement (<5s); idempotency on `TransID`; match → post → receipt pipeline; reversal handling |
| Suspense account management | Advanced | Unmatched payments park in suspense; suspense ageing triggers manual review; clearing suspense must be audited |
| Receipt mechanics | Expert | Receipt is child of posted journal entry; receipt status: PENDING → POSTED → ISSUED → VOID; void triggers reversing entry |
| Credit notes | Advanced | Credit note is a negative invoice; creates reversing journal entry; applies against original AR balance |

---

## 3. MODULE-SPECIFIC DOMAIN SKILLS

Each agent assigned to a module must demonstrate **expert-level** knowledge of the following in addition to §1 and §2:

### Module 1 — Chart of Accounts
- COA hierarchy design (max 5 levels, circular reference prevention)
- IFRS account subtype classification table (all 22 subtypes)
- Normal balance derivation rules and contra account handling
- COA template design for service, merchandising, manufacturing, financial services, non-profit

### Module 2 — Transaction Capture
- Four-gate IFRS recognition test implementation
- Source document type taxonomy (all 10 document types)
- Document status lifecycle (`DRAFT → SUBMITTED → REVIEWED → APPROVED → POSTED → ARCHIVED`)
- Bank feed OFX/CSV parsing and duplicate detection algorithms

### Module 3 — Journal Entry Engine
- Journal entry data model (header + lines)
- Double-entry validator (§4.2 of Design Doc) — zero tolerance balance check
- Special journals auto-generation rules (5 types)
- Approval workflow thresholds and routing logic
- Reversing entry mechanics and status transitions

### Module 4 — General Ledger & Posting Engine
- Ledger posting atomicity requirements
- Running balance calculation per account type (debit-normal vs credit-normal)
- Subsidiary ledger reconciliation to control accounts (4 control accounts)
- Fixed asset register data model and depreciation engine (3 methods)
- T-account view query construction

### Module 5 — Trial Balance Engine
- Unadjusted vs adjusted TB distinction and generation logic
- Discrepancy detector algorithm (data corruption checks)
- Working trial balance worksheet (10-column format)
- Pre-conditions for financial statement generation

### Module 6 — Adjusting Entries Engine
- All five standard adjusting entry types (logic, journal entry, automation rules)
- All six IFRS-specific adjusting entries (provisions, impairment, FX revaluation, fair value, IFRS 16, IAS 21)
- Adjusting entry scheduler and period-end automation
- Adjusted trial balance lock logic

### Module 7 — Financial Statement Generator
- Four-statement generation sequence and interdependencies (net income → SOCE → retained earnings → balance sheet → cash balance validation)
- IAS 1 minimum line items per statement
- Cash flow indirect method reconciliation algorithm
- Notes and disclosures template engine (10+ note types)
- Comparative period column logic

### Module 8 — Closing Entries Engine
- Four-step closing entry sequence (exact order mandatory)
- Post-closing trial balance validation
- Period lock trigger and opening balance transfer to next period

### Module 9 — Period Management
- Fiscal calendar auto-generation
- Period state machine (`FUTURE → OPEN → ADJUSTING → CLOSING → CLOSED → REOPENED`)
- Nine-step cycle state machine with pre-condition enforcement
- Period-end checklist (14 items) and completion tracking

### Module 10 — IFRS Compliance Engine
- IFRS rules engine implementation per standard (13 standards)
- IFRS 15 five-step revenue recognition engine with contract tracking
- IFRS 16 lease amortisation schedule calculator
- Compliance checker output format and departure log

### Module 11 — Reporting & Audit Trail
- All 15 standard report types and their data sources
- Immutable audit log schema (`before_state`/`after_state` JSONB)
- Audit log access control (read-only for all roles including admin)
- Export format support (PDF, XLSX, CSV, JSON)

### Module 12 — Multi-Entity & Multi-Currency
- IAS 21 three-currency framework (functional, presentation, foreign)
- Exchange rate table management (spot, average, closing rates)
- Intercompany transaction recording and elimination logic
- Consolidation engine sequence (5 steps)
- Non-controlling interest (NCI) calculation

### Modules 13–15 — Revenue Cycle (Invoicing, Payments, Receipting)
- Full invoice lifecycle with IFRS 15 integration
- M-Pesa Daraja API webhook contract and async pipeline
- Payment matching algorithm (exact match, partial match, suspense routing)
- Auto-journal-entry generation from payment events
- Receipt immutability rules and void/reissue mechanics
- STK Push initiation and response handling

---

## 4. ARCHITECTURAL & CLOUD-NATIVE EXPERTISE

### 4.1 Application Architecture

| Pattern | Required Level | Application |
|---|---|---|
| 12-Factor App | Expert | Config in env vars; stateless services; backing services as attached resources; port binding; dev/prod parity |
| Layered architecture | Expert | `controller → service → repository → domain → config` — no layer skipping; domain logic never in controllers |
| Domain-driven design (DDD) | Advanced | Aggregates (`JournalEntry` owns its lines); repositories per aggregate root; domain events for cross-module triggers |
| Event-driven patterns | Advanced | Payment posted → triggers journal → triggers receipt generation (async event chain) |
| CQRS (light) | Intermediate | Read models for reports and trial balance separated from write models; route reads to replica |
| Modular package structure | Expert | One package per module: `module01_coa`, `module03_journal`, `module13_invoicing`, etc. No cross-module direct calls — use service interfaces |

### 4.2 Containerization & Deployment

| Skill | Required Level | Requirement |
|---|---|---|
| Docker multi-stage builds | Advanced | Final image: JRE slim (Alpine); build stage: JDK; image size target <200MB |
| Non-root container users | Advanced | Never run as root in container |
| Health checks | Expert | `/actuator/health` liveness + readiness probes; database connectivity check; message broker check |
| Resource limits | Advanced | CPU and memory limits defined in deployment manifests |
| Graceful shutdown | Expert | `server.shutdown=graceful`; drain in-flight requests; close DB connections cleanly on SIGTERM |
| Kubernetes / Docker Compose | Advanced | Manifests for local dev (Docker Compose) and production (K8s); secrets via K8s Secrets or Vault |

### 4.3 Observability

| Requirement | Implementation |
|---|---|
| Structured JSON logging | `logback-spring.xml` configured for JSON output in production; fields: `timestamp`, `level`, `correlation_id`, `entity_id`, `user_id`, `module`, `message` |
| Distributed tracing | Spring Cloud Sleuth / Micrometer Tracing; `trace_id` and `span_id` in all log lines and API responses |
| Custom business metrics | Micrometer counters/timers: `je_post_duration`, `payment_match_failures`, `period_lock_violations`, `ifrs_compliance_checks`, `receipt_delivery_failures`, `fx_revaluation_runs`, `suspense_unmatched_count` |
| Alerting thresholds | Query >1s: alert; Trial balance >5s: alert; Payment match failure rate >5%: alert; Unmatched suspense >48h: alert |
| Log PII masking | Never log full `TransID`, customer phone, email, or bank account in plaintext; use `[REDACTED]` or SHA-256 hash |

### 4.4 Resilience Engineering

| Pattern | Required Level | Application |
|---|---|---|
| Circuit breakers | Expert | Wrap M-Pesa STK Push API calls and exchange rate API calls with Resilience4j `CircuitBreaker` |
| Retry with backoff | Expert | Webhook retries: max 3 attempts; exponential backoff (1s, 3s, 9s) with jitter; dead-letter queue after max retries |
| Idempotency guards | Expert | All payment and journal posting endpoints; store `idempotency_key` in DB with TTL 24h; return cached response on duplicate |
| Graceful degradation | Advanced | If exchange rate API unavailable: use last known rate + alert; do not block posting |
| Timeout configuration | Advanced | External API calls timeout: 10s; DB query timeout: 30s; webhook acknowledgement: <5s |

### 4.5 Performance Requirements (Hard Targets — Enforced in CI)

| Operation | Target | Measurement Method |
|---|---|---|
| Trial balance generation | < 5 seconds | `@SpringBootTest` with 10 years of seeded data |
| Journal entry posting | < 500ms | Load test: 50 concurrent users |
| Financial statement generation | < 30 seconds | End-to-end cycle test |
| M-Pesa webhook acknowledgement | < 5 seconds | Integration test with WireMock |
| Receipt PDF generation | < 3 seconds | Unit test with 1000 line items |
| AR ageing report | < 10 seconds | Test with 100,000 invoice records |
| Concurrent users | ≥ 50 per entity | k6 / Gatling load test |

---

## 5. SECURITY & COMPLIANCE SKILLS

### 5.1 Authentication & Authorization

| Skill | Required Level | Implementation |
|---|---|---|
| JWT / OAuth2 | Expert | Stateless JWT; `Authorization: Bearer <token>`; token contains `user_id`, `entity_id`, `roles`, `exp` |
| Spring Security configuration | Expert | Stateless session; CSRF disabled for API; CORS configured per environment |
| Method-level security | Expert | `@PreAuthorize("hasRole('ACCOUNTANT') and #entityId == authentication.entityId")` on every financial endpoint |
| Role hierarchy | Expert | CFO > SENIOR_ACCOUNTANT > ACCOUNTANT > DATA_ENTRY; AUDITOR and SYSTEM_ADMIN are lateral roles |
| Approval threshold engine | Advanced | Thresholds configurable per entity in `config.approval_thresholds` table; dynamically loaded at runtime |
| Multi-tenancy isolation | Expert | All queries scoped by `entity_id`; RLS at DB level; no cross-entity data leakage possible |

### 5.2 Role Permissions Matrix

| Role | Can Create | Can Approve/Post | Can Lock Period | Can Override Lock | Can View Audit | Can Admin |
|---|---|---|---|---|---|---|
| DATA_ENTRY | ✅ Drafts only | ❌ | ❌ | ❌ | ❌ | ❌ |
| ACCOUNTANT | ✅ | ✅ (≤ threshold) | ❌ | ❌ | ❌ | ❌ |
| SENIOR_ACCOUNTANT | ✅ | ✅ (all values) | ✅ | ❌ | ✅ | ❌ |
| CFO | ✅ | ✅ (all values) | ✅ | ✅ (dual approval) | ✅ | ❌ |
| AUDITOR | ❌ | ❌ | ❌ | ❌ | ✅ (read-only) | ❌ |
| SYSTEM_ADMIN | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |

### 5.3 Data Protection

| Requirement | Implementation |
|---|---|
| Transport security | TLS 1.3 minimum; HSTS header enforced |
| Secrets management | No secrets in code or `application.yml`; use environment variables or Vault |
| PII in logs | `TransID`, phone numbers, email, bank account numbers masked in all log output |
| Data retention | Financial records: 7 years minimum (configurable); audit log: indefinite |
| Soft deletes only | `is_active = false` + `deactivated_at` timestamp; hard deletes blocked at repository layer |
| Encryption at rest | DB encryption enabled; object storage (receipts, source documents) encrypted |

---

## 6. TESTING & QUALITY ASSURANCE SKILLS

### 6.1 Testing Requirements

| Test Type | Tooling | Coverage Target | Scope |
|---|---|---|---|
| Unit tests | JUnit 5, MockK, AssertJ | ≥ 90% domain logic | Validators, state machines, IFRS engines, double-entry logic, rounding |
| Integration tests | `@SpringBootTest`, Testcontainers (PostgreSQL 15+, Redis 7+), WireMock | Full happy-path + error paths | Invoice → Payment → Journal → Ledger → TB → FS full cycle |
| Property-based tests | Kotest Property Testing | Rounding invariance, FX accuracy, balance invariance | 10,000 random amounts; concurrent posting race conditions |
| Load tests | k6 / Gatling | Meet all targets in §4.5 | Trial balance, journal posting, 50 concurrent users |
| Contract tests | Spring Cloud Contract / Pact | All external APIs | M-Pesa Daraja API; exchange rate providers |
| Static analysis | Detekt, Ktlint, SonarQube | 0 critical issues, A rating | Zero `Float`/`Double` on monetary fields; no missing `@Transactional`; no missing audit columns |

### 6.2 CI/CD Quality Gates (Build Fails If Any Gate Fails)

| Gate | Check | Tool |
|---|---|---|
| No floating-point money | `Float` or `Double` used for any monetary field | Detekt custom rule |
| Transactional coverage | `@Transactional` missing on any posting/approval service method | Detekt custom rule |
| Audit column presence | `created_at`, `created_by`, `modified_at`, `modified_by` missing from any `@Entity` | Custom annotation processor |
| Balance invariance | Any test where `SUM(debits) ≠ SUM(credits)` after posting | Integration test suite |
| Period lock enforcement | Any entry posts to a CLOSED period without override | Integration test suite |
| Immutable receipt | Receipt generated before `journal_entry_id.status == POSTED` | Integration test |
| Idempotency | Duplicate `Idempotency-Key` returns cached response, not a second posting | Integration test |
| SonarQube rating | Quality gate < A | SonarQube |

---

## 7. INTEGRATION & MESSAGING SKILLS

### 7.1 M-Pesa Daraja API Integration

| Skill | Required Level | Application |
|---|---|---|
| Daraja API authentication (OAuth2) | Expert | Token refresh before expiry; store token in Redis with TTL |
| STK Push (Lipa na M-Pesa Online) | Expert | Initiate payment request; handle `ResultCode 0` (success) vs other codes |
| C2B callback processing | Expert | Receive `TransID`, `TransAmount`, `MSISDN`, `BillRefNumber`; acknowledge within 5s; queue for async processing |
| Reversal callback | Expert | Receive reversal notification; trigger reversing journal entry; void associated receipt |
| Idempotency on `TransID` | Expert | `TransID` is unique constraint in DB; duplicate webhook with same `TransID` returns cached success |
| Transaction status query | Advanced | Query Daraja for payment status when callback not received within SLA |

### 7.2 Async Messaging

| Skill | Required Level | Application |
|---|---|---|
| Spring `@Async` + thread pool | Advanced | Minimum viable async for single-instance deployments |
| Redis pub/sub or list queues | Advanced | Webhook payload queuing for lightweight deployments |
| RabbitMQ / Kafka | Advanced | Production-grade message queuing; dead-letter queues for failed webhook processing |
| Idempotent consumers | Expert | All message consumers must be idempotent; duplicate message = no duplicate posting |
| Dead-letter queue handling | Advanced | Failed messages after 3 retries → DLQ; alert raised; manual reprocessing endpoint provided |

### 7.3 Exchange Rate Providers

| Skill | Required Level | Application |
|---|---|---|
| REST API integration (ECB, Open Exchange Rates) | Intermediate | Daily rate sync via `POST /currencies/exchange-rates/sync` |
| Rate fallback logic | Advanced | If provider unavailable: use last known rate; log warning; do not block operations |
| Rate type management | Advanced | Store `SPOT`, `AVERAGE`, and `CLOSING` rates per currency pair per date |

---

## 8. AGENT SELF-ASSESSMENT PROTOCOL

Before accepting any task assignment, the agent must complete this self-assessment:

### 8.1 Pre-Task Checklist

```
□ I have read and understood System Design Prompt v3.0 in full.
□ I have read and understood instructions.md v2.0 in full.
□ I have read and understood skills.md v2.0 (this document) in full.
□ The module(s) I am assigned to are listed in §3 of this document.
□ I can demonstrate expert-level proficiency in all skills listed for my assigned module(s).
□ I understand all error codes in instructions.md §6 and can implement their corresponding exception handlers.
□ I understand the zero-deviation rules in instructions.md §9.
□ I understand that no architectural, business, or IFRS compliance deviations are permitted.
```

### 8.2 Escalation Triggers

The agent must **halt and escalate** (do not proceed) if any of the following are true:

- A required skill in §1–§7 for the assigned module is not at the required proficiency level.
- The task specification conflicts with any rule in `instructions.md §9` (Zero-Deviation Rules).
- The task requires posting to a module not listed in the assigned module set.
- Ambiguity exists in the design specification that could result in more than one valid implementation — do not assume; request clarification.
- An external API contract (M-Pesa, exchange rate provider) has changed from the documented specification.
- A proposed implementation would require a hard delete of any financial record.

### 8.3 Output Traceability Requirement

Every class, method, DB constraint, and configuration value produced by the agent must include a code comment citing its origin in the design document:

```kotlin
// §4.2 — Double-Entry Validator: SUM(debits) == SUM(credits), zero tolerance
// §9.1 — Period State Machine: OPEN → ADJUSTING transition
// §15.3 — Receipt: generated only after journal_entry_id.status == POSTED
```

### 8.4 Competency Verification Statement

> *By accepting a task assignment, the agent attests that it meets all competency requirements in this document for the module(s) assigned. Partial proficiency is not acceptable. If domain gaps emerge during implementation, the agent must immediately halt, document the gap, and request human review. Proceeding with known gaps is a compliance failure.*

---

*Document: skills.md | Version: 2.0 | System: IFRS Financial Accounting | Stack: Kotlin + Spring Boot 3.x + PostgreSQL 15+ | Aligned to: System Design Prompt v3.0 + instructions.md v2.0*

# `instructions.md`
# AI AGENT IMPLEMENTATION INSTRUCTIONS
## IFRS Financial Accounting System — Kotlin + Spring Boot + PostgreSQL
## Version: 2.0 | Reference: System Design Prompt v3.0 | Skills Reference: skills.md v2.0

> **CORE MANDATE:** You are an autonomous implementation agent. Your output must strictly conform to System Design Prompt v3.0. No architectural, business logic, or IFRS compliance deviations are permitted without documented justification and human approval. Every class, method, DB constraint, and config value must be traceable to a specific section of the design document. Non-compliance = build failure. Pre-flight check is mandatory before generating any artifact.

> **ZERO-TOLERANCE POLICY:** Never use hardcoded fallbacks, "dummy" data, or "fixed UUIDs" in core business or security logic (e.g., `AuditorAware`, `UserContext`). All logic must be fully implemented and integrated with the system's real data structures from the start. 
> 
> **SHORTCUT BAN:** "TODO" shortcuts, "For now" comments, or "Simplified" implementations are strictly forbidden in core accounting logic (e.g., depreciation calculation, FX revaluation, double-entry validation). If a module depends on another, implement the real dependency or the necessary master data first.
> 
> **LOGIC-DATA SYMMETRY:** You are strictly forbidden from writing logic that "assumes" data will be available in the future. If your logic requires a specific data point, you MUST update the underlying data model (Database Schema + JPA Entities) and the capture mechanism (Posting Service) to provide that data before implementing the logic. No "logic explanations" or placeholders allowed.

> **ZERO-HEURISTIC POLICY:** Never use "demo", "simplified", or "heuristic-based" logic in service or infrastructure layers. All implementations must be production-grade (e.g., using explicit parameter annotations for AOP instead of positional arguments).

> **IMPORT HYGIENE:** Clean Kotlin imports are mandatory. Fully qualified names (e.g., `@com.pkg.Annotation`) in business logic are strictly forbidden. Consolidate common exceptions in the `GlobalExceptionHandler` to prevent redeclaration conflicts.

> **PHASE COMPLETION GATE:** You are strictly forbidden from transitioning to a new implementation phase (e.g., Phase 4) until the full API surface of all preceding modules (M1–M3) is 100% implemented, functional, and documented as per the endpoint tables in §17 of the Design Doc.

---

## TABLE OF CONTENTS

1. [Pre-Flight Check](#1-pre-flight-check)
2. [Architecture & Stack Constraints](#2-architecture--stack-constraints)
3. [Database & Data Modelling Rules](#3-database--data-modelling-rules)
4. [Module Implementation Sequence](#4-module-implementation-sequence)
5. [Business Logic & State Enforcement](#5-business-logic--state-enforcement)
6. [Complete Error Code Registry](#6-complete-error-code-registry)
7. [API & Integration Contracts](#7-api--integration-contracts)
8. [Cloud-Native & Performance Mandates](#8-cloud-native--performance-mandates)
9. [Security, RBAC & Separation of Duties](#9-security-rbac--separation-of-duties)
10. [Testing & Validation Gates](#10-testing--validation-gates)
11. [Zero-Deviation Rules](#11-zero-deviation-rules)
12. [Agent Execution Protocol](#12-agent-execution-protocol)

---

## 1. PRE-FLIGHT CHECK

**Mandatory before generating any artifact. Agent must verify all items. Halt if any item cannot be confirmed.**

```
□ DESIGN DOC  — System Design Prompt v3.0 has been read in full.
□ SKILLS DOC  — skills.md v2.0 has been read in full. Required proficiency confirmed for assigned module(s).
□ INSTRUCTIONS — This document (instructions.md v2.0) has been read in full.
□ MODULE SCOPE — Assigned module(s) are clearly identified. No out-of-scope modules will be touched.
□ TRACEABILITY — Every output will be annotated with design doc section references (e.g., // §4.2).
□ ZERO DEVIATIONS — All items in §11 of this document are understood and will be enforced.
□ ERROR CODES — The complete error code registry (§6) is understood. All exception handlers will be implemented.
□ IDEMPOTENCY — All POST endpoints for payments, receipts, and journal entries will enforce Idempotency-Key.
□ IMMUTABILITY — No hard deletes. No updates to POSTED records. Audit log is INSERT-only.
□ PRECISION — BigDecimal + DECIMAL(20,6) everywhere. No Float. No Double. No exceptions.
```

**If any item above cannot be confirmed: HALT. Do not generate code. Request clarification.**

---

## 2. ARCHITECTURE & STACK CONSTRAINTS

### 2.1 Framework & Dependencies

```kotlin
// Required Spring Boot starters (build.gradle.kts)
implementation("org.springframework.boot:spring-boot-starter-web")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("org.springframework.boot:spring-boot-starter-batch")
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-cache")
implementation("io.micrometer:micrometer-registry-prometheus")
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.x")
implementation("io.github.resilience4j:resilience4j-spring-boot3")
implementation("org.flywaydb:flyway-core")
implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
```

**ORM choice:** Spring Data JPA with Hibernate as default. Use jOOQ only for complex aggregation queries (trial balance, financial statement generation) where JPQL performance is insufficient.

### 2.2 Package Structure (Mandatory)

```
com.company.accounting
├── module01_coa/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── domain/
│   └── config/
├── module02_transaction/
├── module03_journal/
├── module04_ledger/
├── module05_trialbalance/
├── module06_adjusting/
├── module07_financialstatements/
├── module08_closing/
├── module09_periodmanagement/
├── module10_ifrs/
├── module11_reporting/
├── module12_multicurrency/
├── module13_invoicing/
├── module14_payments/
├── module15_receipting/
├── shared/
│   ├── audit/
│   ├── exceptions/
│   ├── security/
│   └── validation/
└── config/
```

**Rule:** No cross-module direct class dependencies. Inter-module communication via service interfaces defined in `shared/`. Domain events for async cross-module triggers.

### 2.3 Layered Architecture Rules

```
HTTP Request → Controller (validate input, map to command)
            → Service (business logic, @Transactional, state enforcement)
            → Repository (data access only, no business logic)
            → Domain (entities, value objects, state machines)
```

- **Controllers:** Input validation only (`@Valid`). No business logic. No direct repository access.
- **Services:** All business rules here. All `@Transactional` boundaries here. No HTTP concerns.
- **Repositories:** Data access only. No business logic. No service calls.
- **Domain:** Entities, enums, value objects, state machine definitions. No Spring annotations except JPA.

### 2.4 Cloud-Native Configuration

```yaml
# application.yml structure (secrets via env vars — never hardcoded)
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    open-in-view: false  # MANDATORY — prevents lazy loading issues in controllers
    properties:
      hibernate:
        jdbc:
          batch_size: 50
  flyway:
    enabled: true
    locations: classpath:db/migration

server:
  shutdown: graceful
  port: ${PORT:8080}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

## 3. DATABASE & DATA MODELLING RULES

### 3.1 Monetary Precision (Non-Negotiable)

```sql
-- ALL monetary columns MUST use this exact definition
amount DECIMAL(20,6) NOT NULL

-- Kotlin entity mapping
@Column(precision = 20, scale = 6, nullable = false)
val amount: BigDecimal
```

```kotlin
// Rounding: Banker's rounding enforced at ALL calculation points
val rounded = amount.setScale(6, RoundingMode.HALF_EVEN)
```

**Enforcement:** CI/CD Detekt rule fails build if `Float` or `Double` is used in any financial domain class.

### 3.2 Mandatory DB Constraints (DDL — enforce in Flyway migrations)

```sql
-- Journal entry lines: mutual exclusivity of debit and credit
ALTER TABLE journal_entry_lines
  ADD CONSTRAINT chk_debit_credit_exclusive
    CHECK (debit_amount = 0 OR credit_amount = 0),
  ADD CONSTRAINT chk_debit_credit_nonzero
    CHECK (debit_amount + credit_amount > 0),
  ADD CONSTRAINT chk_debit_nonneg CHECK (debit_amount >= 0),
  ADD CONSTRAINT chk_credit_nonneg CHECK (credit_amount >= 0);

-- Unique external references (idempotency)
ALTER TABLE payments ADD CONSTRAINT uq_payments_trans_id UNIQUE (trans_id);
ALTER TABLE receipts ADD CONSTRAINT uq_receipts_number UNIQUE (receipt_number);
ALTER TABLE invoices ADD CONSTRAINT uq_invoices_number UNIQUE (invoice_number);
ALTER TABLE idempotency_keys ADD CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key, entity_id);

-- All FK relationships
ALTER TABLE journal_entry_lines
  ADD CONSTRAINT fk_jel_journal_entry
    FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE RESTRICT;

-- Account balance direction enforcement
ALTER TABLE accounts
  ADD CONSTRAINT chk_normal_balance
    CHECK (normal_balance IN ('DEBIT', 'CREDIT'));
```

### 3.3 Mandatory Audit Columns (Every Entity)

```kotlin
// Base entity — all financial entities extend this
@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class BaseFinancialEntity {
    @Column(nullable = false, updatable = false)
    val entityId: UUID = UUID.randomUUID()  // tenant isolation

    @Column(nullable = false, updatable = false)
    val periodId: UUID? = null              // accounting period reference

    @CreatedDate
    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @CreatedBy
    @Column(nullable = false, updatable = false)
    val createdBy: UUID = UUID.randomUUID() // user ID

    @LastModifiedDate
    @Column(nullable = false)
    var modifiedAt: Instant = Instant.now()

    @LastModifiedBy
    @Column(nullable = false)
    var modifiedBy: UUID = UUID.randomUUID() // user ID
}
```

**CI Gate:** Build fails if any `@Entity` class does not extend `BaseFinancialEntity` or does not declare all six audit columns.

### 3.4 Immutability Enforcement

```kotlin
// Posted records: block all field updates via @Column(updatable = false)
@Column(updatable = false)
val postedAt: Instant? = null

@Column(updatable = false)
val journalEntryId: UUID? = null  // receipt's parent — immutable once set

// Repository layer: throw on attempted update of posted record
@Transactional
fun update(entry: JournalEntry): JournalEntry {
    if (entry.status == JournalEntryStatus.POSTED) {
        throw ImmutableRecordException(
            errorCode = "IMMUTABLE_RECORD",
            message = "Posted journal entries cannot be modified. Use a reversing entry.",
            recordId = entry.id
        )
    }
    return repository.save(entry)
}
```

### 3.5 Soft Delete Pattern

```kotlin
// All financial entities: soft delete only
fun deactivate(id: UUID, reason: String, deactivatedBy: UUID) {
    val entity = repository.findById(id)
        .orElseThrow { ResourceNotFoundException("RESOURCE_NOT_FOUND", id) }
    entity.isActive = false
    entity.deactivatedAt = Instant.now()
    entity.deactivatedBy = deactivatedBy
    entity.deactivationReason = reason
    repository.save(entity)
    auditLogService.log(entity, AuditAction.DEACTIVATE)
}
```

---

## 4. MODULE IMPLEMENTATION SEQUENCE

Implement in strict dependency order. Each module must pass its validation gate before the next begins. No skipping.

### Phase 1 — Foundation (No dependencies)
```
Module 1  — Chart of Accounts
Module 9  — Period Management & Cycle Controller
Shared    — RBAC + Security + Audit Trail base
```
**Gate:** COA CRUD functional; period state machine tested; RBAC enforced on all endpoints.

### Phase 2 — Core Accounting Engine
```
Module 3  — Journal Entry Engine      (depends on: M1, M9)
Module 4  — General Ledger & Posting  (depends on: M3)
Module 5  — Trial Balance             (depends on: M4)
```
**Gate:** Full journal → post → trial balance cycle completes; `SUM(debits) == SUM(credits)` invariant holds under concurrent load test.

### Phase 3 — Period-End Cycle
```
Module 2  — Transaction Capture       (depends on: M1, M3)
Module 6  — Adjusting Entries         (depends on: M3, M4, M5)
Module 7  — Financial Statements      (depends on: M5, M6)
Module 8  — Closing Entries           (depends on: M7)
```
**Gate:** Full nine-step accounting cycle completes end-to-end for one period; all four financial statements generated; closing entries zero all temporary accounts; post-closing TB validates.

### Phase 4 — Compliance & Reporting
```
Module 10 — IFRS Compliance Engine    (depends on: M1–M8)
Module 11 — Reporting & Audit Trail   (depends on: M1–M8)
Module 12 — Multi-Currency            (depends on: M3, M4)
```
**Gate:** IFRS compliance checker runs without false positives on standard test data; all 15 reports generate within performance targets; IAS 21 FX revaluation produces correct journal entries.

### Phase 5 — Revenue Cycle
```
Module 13 — Invoicing                 (depends on: M1, M3, M4, M9, M10)
Module 14 — Payments (M-Pesa)         (depends on: M13, M3, M4)
Module 15 — Receipting                (depends on: M14, M3)
```
**Gate:** Full revenue cycle: invoice raised → M-Pesa webhook received → matched → journal posted → receipt issued → customer AR cleared. M-Pesa reversal triggers reversing entry + receipt void. All within performance targets.

---

## 5. BUSINESS LOGIC & STATE ENFORCEMENT

### 5.1 State Machines — Implementation Pattern

```kotlin
// All state machines: sealed class + exhaustive when
enum class JournalEntryStatus {
    DRAFT, PENDING_APPROVAL, POSTED, REVERSED;

    fun canTransitionTo(next: JournalEntryStatus): Boolean = when (this) {
        DRAFT             -> next == PENDING_APPROVAL
        PENDING_APPROVAL  -> next == POSTED || next == DRAFT
        POSTED            -> next == REVERSED
        REVERSED          -> false  // terminal state
    }
}

// Enforce at service layer
fun transition(entry: JournalEntry, next: JournalEntryStatus, actor: User) {
    if (!entry.status.canTransitionTo(next)) {
        throw InvalidStateTransitionException(
            errorCode = "INVALID_STATE_TRANSITION",
            from = entry.status.name,
            to = next.name,
            recordId = entry.id
        )
    }
    // proceed with transition
}
```

**All system state machines:**

| Entity | States | Terminal State(s) |
|---|---|---|
| `JournalEntry` | `DRAFT → PENDING_APPROVAL → POSTED → REVERSED` | `REVERSED` |
| `Period` | `FUTURE → OPEN → ADJUSTING → CLOSING → CLOSED → REOPENED` | `CLOSED` (soft — can reopen) |
| `Invoice` | `DRAFT → APPROVED → SENT → PARTIALLY_PAID → PAID → VOID` | `PAID`, `VOID` |
| `Payment` | `PENDING → MATCHED → APPROVED → POSTED → REVERSED` | `REVERSED` |
| `Receipt` | `PENDING → POSTED → ISSUED → VOID` | `VOID` |
| `SourceDocument` | `DRAFT → SUBMITTED → REVIEWED → APPROVED → POSTED → ARCHIVED` | `ARCHIVED` |
| `AccountingCycleStep` | Steps 1–9 in sequence (§9.2 of Design Doc) | Step 9 complete |

### 5.2 Double-Entry Validator (§4.2 of Design Doc)

```kotlin
// §4.2 — Double-Entry Validator: zero tolerance
@Component
class DoubleEntryValidator {
    fun validate(entry: JournalEntryCommand) {
        val totalDebits  = entry.lines.sumOf { it.debitAmount }
        val totalCredits = entry.lines.sumOf { it.creditAmount }

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw DoubleEntryMismatchException(
                errorCode = "BALANCE_MISMATCH",
                totalDebits = totalDebits,
                totalCredits = totalCredits,
                difference = totalDebits.subtract(totalCredits).abs()
            )
        }
        if (entry.lines.size < 2) {
            throw ValidationException(
                errorCode = "INSUFFICIENT_JOURNAL_LINES",
                message = "A journal entry must have at least 2 lines."
            )
        }
        // Also validate functional currency totals balance
        val fcDebits  = entry.lines.sumOf { it.functionalCurrencyDebit }
        val fcCredits = entry.lines.sumOf { it.functionalCurrencyCredit }
        if (fcDebits.compareTo(fcCredits) != 0) {
            throw DoubleEntryMismatchException(
                errorCode = "FUNCTIONAL_CURRENCY_BALANCE_MISMATCH",
                totalDebits = fcDebits,
                totalCredits = fcCredits,
                difference = fcDebits.subtract(fcCredits).abs()
            )
        }
    }
}
```

### 5.3 Period Lock Enforcement (AOP Interceptor)

```kotlin
// §9.1, §10.2 — Period lock: intercept all posting operations
@Aspect
@Component
class PeriodLockInterceptor(private val periodService: PeriodService) {

    @Before("@annotation(RequireOpenPeriod)")
    fun checkPeriodOpen(joinPoint: JoinPoint) {
        val periodId = extractPeriodId(joinPoint)
        val period = periodService.findById(periodId)
        val allowedStatuses = setOf(PeriodStatus.OPEN, PeriodStatus.ADJUSTING, PeriodStatus.CLOSING)

        if (period.status !in allowedStatuses) {
            throw PeriodLockedException(
                errorCode = "PERIOD_LOCKED",
                periodId = periodId,
                currentStatus = period.status.name,
                message = "Period ${period.name} is ${period.status}. Posting is not permitted."
            )
        }
    }
}
```

### 5.4 M-Pesa Webhook Async Pipeline

```kotlin
// §14 — M-Pesa async pipeline: acknowledge in <5s, process in background
@RestController
@RequestMapping("/payments/mpesa")
class MpesaWebhookController(
    private val webhookQueue: MpesaWebhookQueue,
    private val idempotencyService: IdempotencyService
) {
    @PostMapping("/callback")
    fun handleCallback(
        @RequestBody payload: MpesaCallbackPayload,
        @RequestHeader("Idempotency-Key") idempotencyKey: String
    ): ResponseEntity<ApiResponse<Unit>> {
        // Check idempotency FIRST
        idempotencyService.checkAndStore(idempotencyKey)

        // Acknowledge immediately — do NOT process here
        webhookQueue.enqueue(payload)

        // Must return within 5 seconds
        return ResponseEntity.ok(ApiResponse.success(Unit))
    }
}

// Background processor
@Component
class MpesaWebhookProcessor(
    private val paymentMatchingService: PaymentMatchingService,
    private val journalAutoPostService: JournalAutoPostService,
    private val receiptService: ReceiptService
) {
    @Async("mpesaWebhookExecutor")
    @Transactional
    fun process(payload: MpesaCallbackPayload) {
        // 1. Validate and deduplicate on TransID
        // 2. Match to invoice (exact, partial, or suspense)
        // 3. Auto-generate and post journal entry
        // 4. Trigger receipt generation
        // 5. Deliver receipt to customer
        // Retry on failure (max 3 attempts, exponential backoff)
    }
}
```

### 5.5 Receipt Generation Rule (Non-Negotiable)

```kotlin
// §15 — Receipt: NEVER generate before journal entry is POSTED
fun generateReceipt(paymentId: UUID): Receipt {
    val payment = paymentRepository.findById(paymentId)
        .orElseThrow { ResourceNotFoundException("PAYMENT_NOT_FOUND", paymentId) }

    val journalEntry = payment.journalEntryId
        ?.let { journalEntryRepository.findById(it).orElse(null) }
        ?: throw BusinessRuleViolationException(
            errorCode = "JOURNAL_ENTRY_NOT_POSTED",
            message = "Receipt cannot be generated: no posted journal entry found for payment $paymentId"
        )

    if (journalEntry.status != JournalEntryStatus.POSTED) {
        throw BusinessRuleViolationException(
            errorCode = "JOURNAL_ENTRY_NOT_POSTED",
            message = "Receipt cannot be generated until journal entry ${journalEntry.id} is POSTED. Current status: ${journalEntry.status}"
        )
    }
    // Proceed with receipt creation
}
```

### 5.6 Closing Entry Sequence (Mandatory Order — §8 of Design Doc)

```kotlin
// §8 — Four closing steps in strict order. No shortcuts.
@Transactional
fun runClosingEntries(periodId: UUID, initiatedBy: UUID) {
    val period = periodService.findById(periodId)
    require(period.status == PeriodStatus.CLOSING) {
        throw PeriodStateException("PERIOD_NOT_IN_CLOSING_STATE", periodId)
    }

    // Step 1: Close all Revenue → Income Summary
    val revenueClose = buildRevenueCloseEntry(periodId)
    journalService.postEntry(revenueClose, initiatedBy)  // §8.1 Step 1

    // Step 2: Close all Expenses → Income Summary
    val expenseClose = buildExpenseCloseEntry(periodId)
    journalService.postEntry(expenseClose, initiatedBy)  // §8.1 Step 2

    // Step 3: Close Income Summary → Retained Earnings
    val incomeSummaryClose = buildIncomeSummaryCloseEntry(periodId)
    journalService.postEntry(incomeSummaryClose, initiatedBy)  // §8.1 Step 3

    // Step 4: Close Dividends → Retained Earnings
    val dividendsClose = buildDividendsCloseEntry(periodId)
    journalService.postEntry(dividendsClose, initiatedBy)  // §8.1 Step 4

    // Validate all temporary accounts are now zero
    validateTemporaryAccountsZero(periodId)  // §8.2

    // Lock period and transfer opening balances
    periodService.close(periodId)            // §8.3
    periodService.transferOpeningBalances(periodId)  // §8.3
}
```

---

## 6. COMPLETE ERROR CODE REGISTRY

**All API errors must return a standardized response using these exact error codes. No raw exceptions in HTTP responses. `@ControllerAdvice` must handle every exception type listed below.**

### 6.1 Standard Error Response Envelope

```json
{
  "success": false,
  "data": null,
  "errors": [
    {
      "error_code": "BALANCE_MISMATCH",
      "http_status": 422,
      "message": "Journal entry debits (5000.00) do not equal credits (4500.00). Difference: 500.00",
      "field": null,
      "context": {
        "total_debits": "5000.000000",
        "total_credits": "4500.000000",
        "difference": "500.000000"
      }
    }
  ],
  "warnings": [],
  "metadata": {
    "entity_id": "uuid",
    "period_id": "uuid",
    "timestamp": "2024-11-01T14:34:00Z",
    "trace_id": "req-uuid"
  }
}
```

---

### 6.2 Validation Errors (HTTP 400)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `VALIDATION_FAILED` | 400 | Bean validation failure (`@Valid`); generic input validation | `field`, `rejected_value`, `constraint` |
| `REQUIRED_FIELD_MISSING` | 400 | Required field is null or blank | `field` |
| `INVALID_FIELD_FORMAT` | 400 | Field fails format validation (UUID, date, ISO 4217 currency, email) | `field`, `rejected_value`, `expected_format` |
| `INVALID_FIELD_VALUE` | 400 | Field value out of allowed range or set | `field`, `rejected_value`, `allowed_values` |
| `INVALID_DATE_RANGE` | 400 | Start date is after end date | `start_date`, `end_date` |
| `INVALID_CURRENCY_CODE` | 400 | Currency code not in ISO 4217 | `currency_code` |
| `INVALID_ACCOUNT_TYPE` | 400 | Account type not in allowed enum | `account_type`, `allowed_types` |
| `INVALID_AMOUNT` | 400 | Amount is negative, zero where not permitted, or exceeds `DECIMAL(20,6)` | `field`, `rejected_value` |
| `NEGATIVE_AMOUNT` | 400 | Monetary amount is less than zero | `field`, `rejected_value` |
| `ZERO_AMOUNT` | 400 | Amount is zero where a positive value is required | `field` |
| `AMOUNT_PRECISION_EXCEEDED` | 400 | More than 6 decimal places provided | `field`, `rejected_value`, `max_scale: 6` |
| `INVALID_DEBIT_CREDIT_LINE` | 400 | A journal entry line has both debit and credit amounts > 0 simultaneously | `line_id`, `debit_amount`, `credit_amount` |
| `INSUFFICIENT_JOURNAL_LINES` | 400 | Journal entry has fewer than 2 lines | `line_count` |
| `INVALID_ACCOUNT_CODE_FORMAT` | 400 | Account code does not match entity's defined numbering convention | `account_code`, `expected_pattern` |
| `INVALID_PAGINATION` | 400 | `page` < 0 or `size` < 1 or `size` > 500 | `page`, `size`, `max_size: 500` |
| `INVALID_SORT_FIELD` | 400 | Sort field not in allowed sortable fields for this endpoint | `sort_field`, `allowed_fields` |
| `INVALID_FILTER_COMBINATION` | 400 | Mutually exclusive filters provided together | `filters` |
| `INVOICE_LINE_ITEM_INVALID` | 400 | Invoice line item has invalid quantity, unit price, or tax rate reference | `line_item_id`, `field` |
| `INVALID_DEPRECIATION_METHOD` | 400 | Depreciation method not in `STRAIGHT_LINE`, `DECLINING_BALANCE`, `UNITS_OF_PRODUCTION` | `method`, `allowed_methods` |
| `INVALID_PAYMENT_CHANNEL` | 400 | Payment channel not in allowed set | `channel`, `allowed_channels` |
| `INVALID_EXCHANGE_RATE` | 400 | Exchange rate is zero or negative | `rate`, `currency_pair` |
| `INVALID_LEASE_TERM` | 400 | Lease term is zero, negative, or start date is after end date | `start_date`, `end_date` |
| `INVALID_FISCAL_YEAR` | 400 | Fiscal year definition is invalid (e.g., not 12 months, overlaps existing year) | `fiscal_year_start`, `fiscal_year_end` |
| `INVALID_COA_HIERARCHY` | 400 | Account hierarchy depth exceeds 5 levels or circular reference detected | `account_id`, `parent_account_id` |
| `CIRCULAR_ACCOUNT_REFERENCE` | 400 | Account's parent chain leads back to itself | `account_id`, `cycle_path` |
| `CONTRA_ACCOUNT_MISCONFIGURED` | 400 | Contra account normal balance not opposite of its parent account type | `account_id`, `expected_normal_balance` |
| `INVALID_IDEMPOTENCY_KEY_FORMAT` | 400 | `Idempotency-Key` header present but not a valid UUID | `idempotency_key` |
| `MISSING_IDEMPOTENCY_KEY` | 400 | `Idempotency-Key` header absent on a POST endpoint that requires it | `endpoint` |
| `INVALID_RATE_TYPE` | 400 | Rate type not in `SPOT`, `AVERAGE`, `CLOSING` | `rate_type` |
| `INVALID_OCI_CLASSIFICATION` | 400 | OCI item not in permitted IAS 1 OCI categories | `oci_category` |

---

### 6.3 Authentication & Authorization Errors (HTTP 401 / 403)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `UNAUTHENTICATED` | 401 | No valid JWT token provided | — |
| `TOKEN_EXPIRED` | 401 | JWT token has expired | `expired_at` |
| `TOKEN_INVALID` | 401 | JWT token signature invalid or malformed | — |
| `TOKEN_REVOKED` | 401 | JWT token has been explicitly revoked | — |
| `ACCESS_DENIED` | 403 | User authenticated but lacks required role/permission | `required_role`, `user_role` |
| `ENTITY_ACCESS_DENIED` | 403 | User's `entity_id` does not match the resource's `entity_id` | `requested_entity_id`, `user_entity_id` |
| `INSUFFICIENT_APPROVAL_AUTHORITY` | 403 | User's role cannot approve entries above their threshold | `entry_amount`, `user_threshold`, `required_role` |
| `ADMIN_CANNOT_POST` | 403 | SYSTEM_ADMIN role attempted to post a journal entry (separation of duties) | `user_role` |
| `AUDITOR_READ_ONLY` | 403 | AUDITOR role attempted a write operation | `user_role`, `attempted_action` |
| `DATA_ENTRY_CANNOT_APPROVE` | 403 | DATA_ENTRY role attempted to approve or post | `user_role` |
| `DUAL_APPROVAL_REQUIRED` | 403 | Action requires dual approval; second approver is the same as first | `first_approver_id` |
| `SELF_APPROVAL_NOT_PERMITTED` | 403 | User attempted to approve their own entry | `entry_created_by`, `approver_id` |
| `CFO_OVERRIDE_REQUIRES_DUAL_APPROVAL` | 403 | CFO period override attempted without second approver | `override_type` |

---

### 6.4 Resource Not Found Errors (HTTP 404)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `RESOURCE_NOT_FOUND` | 404 | Generic resource not found | `resource_type`, `resource_id` |
| `ACCOUNT_NOT_FOUND` | 404 | COA account not found by ID or code | `account_id` or `account_code` |
| `JOURNAL_ENTRY_NOT_FOUND` | 404 | Journal entry not found | `journal_entry_id` |
| `LEDGER_ENTRY_NOT_FOUND` | 404 | Ledger entry not found | `ledger_entry_id` |
| `PERIOD_NOT_FOUND` | 404 | Accounting period not found | `period_id` |
| `INVOICE_NOT_FOUND` | 404 | Invoice not found | `invoice_id` |
| `CUSTOMER_NOT_FOUND` | 404 | Customer not found | `customer_id` |
| `SUPPLIER_NOT_FOUND` | 404 | Supplier not found | `supplier_id` |
| `PAYMENT_NOT_FOUND` | 404 | Payment not found | `payment_id` |
| `RECEIPT_NOT_FOUND` | 404 | Receipt not found | `receipt_id` |
| `ASSET_NOT_FOUND` | 404 | Fixed asset not found in asset register | `asset_id` |
| `LEASE_NOT_FOUND` | 404 | Lease contract not found | `lease_id` |
| `EXCHANGE_RATE_NOT_FOUND` | 404 | No exchange rate found for currency pair and date | `currency_pair`, `rate_date`, `rate_type` |
| `ENTITY_NOT_FOUND` | 404 | Legal entity not found | `entity_id` |
| `COA_TEMPLATE_NOT_FOUND` | 404 | COA template not found | `template_id` |
| `TAX_RATE_NOT_FOUND` | 404 | Tax rate not found | `tax_rate_id` |
| `RECURRING_TEMPLATE_NOT_FOUND` | 404 | Recurring invoice template not found | `template_id` |
| `SOURCE_DOCUMENT_NOT_FOUND` | 404 | Source document not found | `document_id` |
| `AUDIT_LOG_ENTRY_NOT_FOUND` | 404 | Audit log entry not found | `log_id` |
| `PROVISION_NOT_FOUND` | 404 | Provision record not found | `provision_id` |
| `SEGMENT_NOT_FOUND` | 404 | Reporting segment not found | `segment_id` |

---

### 6.5 Conflict Errors (HTTP 409)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `DUPLICATE_REQUEST` | 409 | Duplicate `Idempotency-Key` received; cached response returned | `idempotency_key`, `original_request_at` |
| `DUPLICATE_TRANS_ID` | 409 | M-Pesa `TransID` already processed | `trans_id`, `original_payment_id` |
| `DUPLICATE_INVOICE_NUMBER` | 409 | Invoice number already exists for entity | `invoice_number`, `existing_invoice_id` |
| `DUPLICATE_ACCOUNT_CODE` | 409 | Account code already exists in entity's COA | `account_code` |
| `PERIOD_ALREADY_CLOSED` | 409 | Attempting to close an already-closed period | `period_id`, `period_status` |
| `PERIOD_ALREADY_OPEN` | 409 | Attempting to open an already-open period | `period_id` |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic locking failure (`@Version` conflict) | `resource_type`, `resource_id`, `current_version` |
| `CUSTOMER_ALREADY_DEACTIVATED` | 409 | Attempting to deactivate an already-inactive customer | `customer_id` |
| `ACCOUNT_ALREADY_DEACTIVATED` | 409 | Attempting to deactivate an already-inactive account | `account_id` |
| `RECEIPT_ALREADY_ISSUED` | 409 | Attempting to issue a receipt that is already in ISSUED status | `receipt_id` |
| `INVOICE_ALREADY_PAID` | 409 | Payment applied to a fully-paid invoice | `invoice_id`, `invoice_status` |
| `PERIOD_OVERLAP` | 409 | New period definition overlaps an existing period | `new_period_start`, `new_period_end`, `conflicting_period_id` |
| `FISCAL_YEAR_OVERLAP` | 409 | New fiscal year overlaps an existing fiscal year | `new_year_start`, `new_year_end` |
| `DUPLICATE_RECEIPT_NUMBER` | 409 | Generated receipt number collides (race condition) | `receipt_number` |
| `CONSOLIDATION_ALREADY_RUNNING` | 409 | Consolidation run already in progress for this group period | `group_period_id` |

---

### 6.6 Business Rule Violation Errors (HTTP 422)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `BALANCE_MISMATCH` | 422 | `SUM(debits) ≠ SUM(credits)` in journal entry | `total_debits`, `total_credits`, `difference` |
| `FUNCTIONAL_CURRENCY_BALANCE_MISMATCH` | 422 | Functional currency totals do not balance in multi-currency entry | `fc_debits`, `fc_credits`, `difference` |
| `PERIOD_LOCKED` | 422 | Posting attempted to a CLOSED or locked period | `period_id`, `period_status`, `period_name` |
| `PERIOD_NOT_OPEN` | 422 | Operation requires period in OPEN status; period is in a different state | `period_id`, `current_status`, `required_status` |
| `PERIOD_NOT_IN_ADJUSTING_STATE` | 422 | Adjusting entries require period in ADJUSTING status | `period_id`, `current_status` |
| `PERIOD_NOT_IN_CLOSING_STATE` | 422 | Closing entries require period in CLOSING status | `period_id`, `current_status` |
| `ACCOUNT_INACTIVE` | 422 | Journal entry line references an inactive account | `account_id`, `account_code` |
| `ACCOUNT_TYPE_MISMATCH` | 422 | Transaction attempts to post to wrong account type (e.g., posting revenue to an asset account without justification) | `account_id`, `account_type`, `expected_type` |
| `JOURNAL_ENTRY_NOT_POSTED` | 422 | Receipt or downstream action requires journal entry to be POSTED | `journal_entry_id`, `current_status` |
| `IMMUTABLE_RECORD` | 422 | Attempted edit/delete of a POSTED or ISSUED record | `resource_type`, `resource_id`, `current_status` |
| `INVALID_STATE_TRANSITION` | 422 | State machine transition not allowed | `resource_type`, `resource_id`, `from_status`, `to_status` |
| `CREDIT_LIMIT_EXCEEDED` | 422 | New invoice would breach customer's credit limit | `customer_id`, `credit_limit`, `current_exposure`, `invoice_amount` |
| `INVOICE_NOT_APPROVABLE` | 422 | Invoice not in DRAFT status; cannot be approved | `invoice_id`, `current_status` |
| `INVOICE_VOID_AFTER_POSTING` | 422 | Void attempted on a posted invoice; must use credit note | `invoice_id`, `current_status` |
| `PAYMENT_ALREADY_MATCHED` | 422 | Manual match attempted on a payment already in MATCHED or POSTED status | `payment_id`, `current_status` |
| `SUSPENSE_ACCOUNT_BALANCE_NONZERO` | 422 | Period close attempted with outstanding suspense account balance | `suspense_balance`, `period_id` |
| `TEMPORARY_ACCOUNTS_NOT_ZERO` | 422 | Post-closing trial balance shows non-zero temporary accounts | `non_zero_accounts` |
| `BALANCE_SHEET_DOES_NOT_BALANCE` | 422 | `Assets ≠ Liabilities + Equity` in generated balance sheet | `total_assets`, `total_liabilities_equity`, `difference` |
| `CASH_FLOW_RECONCILIATION_FAILED` | 422 | Closing cash balance on cash flow statement ≠ cash account on balance sheet | `cf_closing_balance`, `bs_cash_balance`, `difference` |
| `RETAINED_EARNINGS_MISMATCH` | 422 | Retained earnings on balance sheet ≠ closing balance from SOCE | `bs_retained_earnings`, `soce_closing_balance` |
| `DEPRECIATION_EXCEEDS_COST` | 422 | Accumulated depreciation would exceed asset acquisition cost | `asset_id`, `acquisition_cost`, `accumulated_depreciation`, `new_charge` |
| `ASSET_FULLY_DEPRECIATED` | 422 | Depreciation charge attempted on a fully-depreciated asset | `asset_id`, `carrying_amount` |
| `IMPAIRMENT_EXCEEDS_CARRYING_AMOUNT` | 422 | Impairment loss exceeds carrying amount | `asset_id`, `carrying_amount`, `impairment_amount` |
| `PROVISION_BELOW_ZERO` | 422 | Provision reversal would take balance negative | `provision_id`, `current_balance`, `reversal_amount` |
| `LEASE_LIABILITY_BELOW_ZERO` | 422 | Lease principal payment would make liability negative | `lease_id`, `current_liability`, `payment_amount` |
| `REVENUE_NOT_EARNED` | 422 | Revenue recognition attempted before performance obligation satisfied (IFRS 15) | `contract_id`, `performance_obligation_id`, `completion_percentage` |
| `DEFERRED_REVENUE_BALANCE_INSUFFICIENT` | 422 | Revenue recognition exceeds deferred revenue balance | `deferred_balance`, `recognition_amount` |
| `INVOICE_PERIOD_MISMATCH` | 422 | Invoice date falls in a locked or closed period | `invoice_date`, `period_status` |
| `PAYMENT_EXCEEDS_INVOICE_BALANCE` | 422 | Payment amount exceeds invoice outstanding balance (overpayment not configured) | `invoice_id`, `outstanding_balance`, `payment_amount` |
| `FX_RATE_MISSING_FOR_DATE` | 422 | Foreign currency transaction has no exchange rate for the transaction date | `currency_pair`, `transaction_date` |
| `FUNCTIONAL_CURRENCY_CHANGE_NOT_PERMITTED` | 422 | Attempting to change entity's functional currency (not permitted without full restatement) | `entity_id`, `current_currency`, `requested_currency` |
| `INTERCOMPANY_ENTITY_NOT_IN_GROUP` | 422 | Intercompany transaction references an entity not in the consolidation group | `entity_id`, `group_id` |
| `NCI_PERCENTAGE_INVALID` | 422 | Non-controlling interest percentage out of range (0–100%) | `nci_percentage` |
| `COA_TEMPLATE_ALREADY_APPLIED` | 422 | Attempting to apply a COA template to an entity that already has accounts | `entity_id` |
| `ADJUSTING_ENTRY_ON_NON_ADJUSTING_TYPE` | 422 | Entry typed as ADJUSTING but does not affect one balance sheet + one income statement account | `entry_id` |
| `CLOSING_ENTRY_IN_WRONG_PERIOD_STATE` | 422 | Closing entries submitted but period is not in CLOSING state | `period_id`, `current_status` |
| `RECURRING_TEMPLATE_PAUSED` | 422 | Manual generation attempted on a paused recurring template | `template_id` |
| `CREDIT_NOTE_EXCEEDS_ORIGINAL_INVOICE` | 422 | Credit note amount exceeds the original invoice amount | `invoice_id`, `invoice_amount`, `credit_note_amount` |
| `MPESA_REVERSAL_WINDOW_EXPIRED` | 422 | M-Pesa reversal attempted outside the allowed reversal window | `payment_id`, `trans_id`, `reversal_deadline` |
| `SUBSIDIARY_LEDGER_OUT_OF_BALANCE` | 422 | Subsidiary ledger totals do not reconcile to GL control account | `control_account_id`, `gl_balance`, `subsidiary_total`, `difference` |
| `TRIAL_BALANCE_NOT_BALANCED` | 422 | Financial statement generation blocked: trial balance does not balance | `total_debits`, `total_credits`, `difference` |
| `INCOME_STATEMENT_NOT_COMPLETE` | 422 | Retained earnings statement generation blocked: income statement not finalised | `period_id` |
| `SOCE_NOT_COMPLETE` | 422 | Balance sheet generation blocked: SOCE not finalised | `period_id` |
| `FINANCIAL_STATEMENTS_NOT_SIGNED_OFF` | 422 | Closing entries blocked: financial statements not signed off | `period_id`, `unsigned_statements` |

---

### 6.7 External Integration Errors (HTTP 502 / 503 / 504)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `MPESA_API_UNAVAILABLE` | 503 | M-Pesa Daraja API unreachable or circuit breaker open | `provider`, `last_successful_call` |
| `MPESA_STK_PUSH_FAILED` | 502 | STK Push request returned a non-zero `ResultCode` | `result_code`, `result_description`, `trans_id` |
| `MPESA_AUTHENTICATION_FAILED` | 502 | M-Pesa OAuth2 token request failed | `provider` |
| `EXCHANGE_RATE_PROVIDER_UNAVAILABLE` | 503 | Exchange rate API unreachable; using last known rate | `provider`, `fallback_rate_date`, `currency_pair` |
| `EXCHANGE_RATE_SYNC_FAILED` | 502 | Exchange rate sync returned unexpected response | `provider`, `http_status` |
| `BANK_FEED_UNAVAILABLE` | 503 | Bank feed provider unavailable | `provider` |
| `WEBHOOK_DELIVERY_FAILED` | 502 | Outbound webhook delivery to customer system failed | `target_url`, `http_status`, `attempt_count` |
| `RECEIPT_DELIVERY_FAILED` | 502 | SMS or email delivery of receipt failed | `delivery_channel`, `recipient`, `failure_reason` |
| `PDF_GENERATION_FAILED` | 502 | Receipt or statement PDF generation failed | `document_type`, `document_id` |
| `EXTERNAL_TIMEOUT` | 504 | External API call timed out | `provider`, `timeout_ms` |

---

### 6.8 System & Infrastructure Errors (HTTP 500)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `INTERNAL_ERROR` | 500 | Unhandled exception; catch-all | `trace_id` (do not expose stack trace) |
| `DATABASE_ERROR` | 500 | Unexpected database failure | `trace_id` |
| `LEDGER_CORRUPTION_DETECTED` | 500 | Running balance recalculation does not match stored balance | `account_id`, `expected_balance`, `stored_balance` |
| `AUDIT_LOG_WRITE_FAILED` | 500 | Audit log INSERT failed (critical — triggers alert) | `trace_id`, `entity_type`, `entity_id` |
| `TRANSACTION_ROLLBACK` | 500 | DB transaction rolled back unexpectedly | `trace_id`, `operation` |
| `DEADLOCK_DETECTED` | 500 | DB deadlock on concurrent posting | `trace_id` |
| `SEQUENCE_GENERATION_FAILED` | 500 | Sequential number generation for receipt/invoice/journal reference failed | `sequence_type`, `entity_id` |
| `QUEUE_UNAVAILABLE` | 500 | Message queue (Redis/RabbitMQ/Kafka) unavailable | `queue_name`, `trace_id` |
| `CACHE_WRITE_FAILED` | 500 | Idempotency key cache write failed | `idempotency_key`, `trace_id` |

---

### 6.9 Rate Limiting & Throttling Errors (HTTP 429)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `RATE_LIMIT_EXCEEDED` | 429 | API rate limit exceeded for user or entity | `limit`, `window`, `retry_after` |
| `WEBHOOK_FLOOD_DETECTED` | 429 | More than N webhook calls received from same source within time window | `source_ip`, `count`, `window`, `retry_after` |
| `BULK_IMPORT_SIZE_EXCEEDED` | 429 | Bulk import file exceeds maximum allowed rows | `row_count`, `max_rows` |

---

### 6.10 Not Implemented / Deprecated (HTTP 501)

| Error Code | HTTP | Trigger Condition | Context Fields |
|---|---|---|---|
| `FEATURE_NOT_IMPLEMENTED` | 501 | Endpoint exists in spec but is not yet implemented in this version | `feature`, `available_in_version` |
| `DEPRECATED_ENDPOINT` | 410 | Endpoint has been deprecated and removed | `replacement_endpoint` |

---

### 6.11 ControllerAdvice Implementation

```kotlin
@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.badRequest().body(ApiResponse.error(ex.errorCode, ex.message, ex.context))

    @ExceptionHandler(DoubleEntryMismatchException::class)
    fun handleBalanceMismatch(ex: DoubleEntryMismatchException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.unprocessableEntity().body(ApiResponse.error("BALANCE_MISMATCH", ex.message, ex.context))

    @ExceptionHandler(PeriodLockedException::class)
    fun handlePeriodLocked(ex: PeriodLockedException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(422).body(ApiResponse.error("PERIOD_LOCKED", ex.message, ex.context))

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.notFound().build()  // with body

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotency(ex: IdempotencyConflictException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(409).body(ApiResponse.error("DUPLICATE_REQUEST", ex.message, ex.context))

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(403).body(ApiResponse.error("ACCESS_DENIED", ex.message))

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing>> {
        log.error("Unhandled exception on ${request.requestURI}", ex) // log full trace server-side
        return ResponseEntity.internalServerError()
            .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred.", mapOf("trace_id" to MDC.get("trace_id"))))
        // DO NOT expose stack trace in response
    }
}
```

---

## 7. API & INTEGRATION CONTRACTS

### 7.1 Standardized Response Envelope

```kotlin
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val errors: List<ApiError> = emptyList(),
    val warnings: List<String> = emptyList(),
    val metadata: ApiMetadata
) {
    companion object {
        fun <T> success(data: T, metadata: ApiMetadata): ApiResponse<T> =
            ApiResponse(true, data, emptyList(), emptyList(), metadata)

        fun error(code: String, message: String, context: Map<String, Any?> = emptyMap()): ApiResponse<Nothing> =
            ApiResponse(false, null, listOf(ApiError(code, message, context)), emptyList(),
                ApiMetadata(traceId = MDC.get("trace_id"), timestamp = Instant.now()))
    }
}

data class ApiError(
    val errorCode: String,
    val message: String,
    val context: Map<String, Any?> = emptyMap()
)

data class ApiMetadata(
    val entityId: UUID? = null,
    val periodId: UUID? = null,
    val timestamp: Instant = Instant.now(),
    val traceId: String? = null
)
```

### 7.2 Idempotency Implementation

```kotlin
@Service
class IdempotencyService(
    private val redis: RedisTemplate<String, String>,
    private val idempotencyRepo: IdempotencyKeyRepository
) {
    private val TTL_HOURS = 24L

    fun checkAndStore(key: String, entityId: UUID): IdempotencyResult {
        val cacheKey = "idempotency:$entityId:$key"

        // Check Redis first (fast path)
        val cached = redis.opsForValue().get(cacheKey)
        if (cached != null) {
            return IdempotencyResult.DUPLICATE(cached)
        }

        // Check DB (source of truth)
        val stored = idempotencyRepo.findByKeyAndEntityId(key, entityId)
        if (stored != null) {
            redis.opsForValue().set(cacheKey, stored.responseBody, TTL_HOURS, TimeUnit.HOURS)
            return IdempotencyResult.DUPLICATE(stored.responseBody)
        }

        // Store new key
        idempotencyRepo.save(IdempotencyKey(key, entityId, Instant.now()))
        redis.opsForValue().set(cacheKey, "PROCESSING", TTL_HOURS, TimeUnit.HOURS)
        return IdempotencyResult.NEW
    }
}
```

### 7.3 Pagination Standard

```kotlin
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

// All list endpoints: ?page=0&size=50&sort=createdAt,desc
// Default: page=0, size=50. Max size=500.
```

---

## 8. CLOUD-NATIVE & PERFORMANCE MANDATES

### 8.1 Dockerfile (Multi-Stage — Required Pattern)

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN ./gradlew dependencies --no-daemon
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Stage 2: Runtime (non-root, slim)
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

### 8.2 Database Indexing (Required — Flyway Migration)

```sql
-- Required indexes for performance targets
CREATE INDEX CONCURRENTLY idx_journal_entries_period_id     ON journal_entries(period_id);
CREATE INDEX CONCURRENTLY idx_journal_entries_status        ON journal_entries(status);
CREATE INDEX CONCURRENTLY idx_journal_entries_entry_date    ON journal_entries(entry_date);
CREATE INDEX CONCURRENTLY idx_journal_entries_entity_id     ON journal_entries(entity_id);
CREATE INDEX CONCURRENTLY idx_ledger_entries_account_id     ON ledger_entries(account_id);
CREATE INDEX CONCURRENTLY idx_ledger_entries_period_id      ON ledger_entries(period_id);
CREATE INDEX CONCURRENTLY idx_payments_trans_id             ON payments(trans_id);
CREATE INDEX CONCURRENTLY idx_payments_status               ON payments(status);
CREATE INDEX CONCURRENTLY idx_payments_customer_id          ON payments(customer_id);
CREATE INDEX CONCURRENTLY idx_invoices_customer_id          ON invoices(customer_id);
CREATE INDEX CONCURRENTLY idx_invoices_status               ON invoices(status);
CREATE INDEX CONCURRENTLY idx_invoices_due_date             ON invoices(due_date);
CREATE INDEX CONCURRENTLY idx_receipts_payment_id           ON receipts(payment_id);
CREATE INDEX CONCURRENTLY idx_audit_log_entity_id           ON audit_log(entity_id, entity_type);
CREATE INDEX CONCURRENTLY idx_audit_log_timestamp           ON audit_log(timestamp DESC);
CREATE INDEX CONCURRENTLY idx_idempotency_key               ON idempotency_keys(idempotency_key, entity_id);
```

### 8.3 Custom Metrics (Required — Micrometer)

```kotlin
@Component
class AccountingMetrics(private val meterRegistry: MeterRegistry) {
    val journalPostDuration: Timer = Timer.builder("je_post_duration")
        .description("Journal entry posting duration").register(meterRegistry)
    val paymentMatchFailures: Counter = Counter.builder("payment_match_failures")
        .description("M-Pesa payments that failed auto-matching").register(meterRegistry)
    val periodLockViolations: Counter = Counter.builder("period_lock_violations")
        .description("Attempts to post to locked periods").register(meterRegistry)
    val ifrsComplianceChecks: Counter = Counter.builder("ifrs_compliance_checks")
        .description("IFRS compliance checks run").register(meterRegistry)
    val receiptDeliveryFailures: Counter = Counter.builder("receipt_delivery_failures")
        .description("Receipt SMS/email delivery failures").register(meterRegistry)
    val suspenseUnmatchedCount: Gauge = Gauge.builder("suspense_unmatched_count") { suspenseCount() }
        .description("Payments currently in suspense account").register(meterRegistry)
    val fxRevaluationRuns: Counter = Counter.builder("fx_revaluation_runs")
        .description("IAS 21 FX revaluation runs completed").register(meterRegistry)
}
```

---

## 9. SECURITY, RBAC & SEPARATION OF DUTIES

### 9.1 JWT Configuration

```kotlin
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(private val jwtFilter: JwtAuthFilter) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests {
            it.requestMatchers("/actuator/health", "/actuator/info").permitAll()
            it.requestMatchers("/payments/mpesa/callback", "/payments/mpesa/reversal-callback").permitAll()
            it.anyRequest().authenticated()
        }
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        .build()
}
```

### 9.2 Approval Threshold Enforcement

```kotlin
// Configurable per entity — loaded from config.approval_thresholds table
data class ApprovalThreshold(
    val entityId: UUID,
    val role: UserRole,
    val maxApprovalAmount: BigDecimal,   // max single entry amount this role can approve
    val requireDualApprovalAbove: BigDecimal  // amount above which 2 approvers required
)

@Service
class ApprovalService(private val thresholdRepository: ApprovalThresholdRepository) {
    fun canApprove(user: UserContext, amount: BigDecimal): Boolean {
        val threshold = thresholdRepository.findByEntityAndRole(user.entityId, user.role)
        return amount <= threshold.maxApprovalAmount
    }

    fun requiresDualApproval(user: UserContext, amount: BigDecimal): Boolean {
        val threshold = thresholdRepository.findByEntityAndRole(user.entityId, user.role)
        return amount > threshold.requireDualApprovalAbove
    }
}
```

### 9.3 Data Retention Implementation

```kotlin
// Soft delete — no hard deletes ever
@Entity
abstract class BaseFinancialEntity {
    var isActive: Boolean = true
    var deactivatedAt: Instant? = null
    var deactivatedBy: UUID? = null
    var deactivationReason: String? = null
}

// Repository: filter inactive by default
interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByEntityIdAndIsActiveTrue(entityId: UUID): List<Account>  // default query
    fun findByEntityId(entityId: UUID): List<Account>                  // admin/audit only
}
```

---

## 10. TESTING & VALIDATION GATES

### 10.1 Integration Test — Full Revenue Cycle

```kotlin
@SpringBootTest
@Testcontainers
class FullRevenueCycleIntegrationTest {

    @Test
    fun `full cycle - invoice to receipt via mpesa`() {
        // 1. Create customer
        val customer = customerService.create(CustomerCommand(...))

        // 2. Create and approve invoice
        val invoice = invoiceService.create(InvoiceCommand(customer.id, ...))
        invoiceService.approve(invoice.id, accountantUser)
        assert(invoice.status == InvoiceStatus.SENT)

        // 3. Simulate M-Pesa webhook
        val payload = MpesaCallbackPayload(TransID = "MP001", TransAmount = "5000.00", BillRefNumber = invoice.invoiceNumber)
        mpesaWebhookProcessor.process(payload)

        // 4. Assert journal entry posted
        val payment = paymentRepository.findByTransId("MP001")
        assertNotNull(payment)
        assertEquals(PaymentStatus.POSTED, payment.status)

        val je = journalEntryRepository.findById(payment.journalEntryId!!)
        assertEquals(JournalEntryStatus.POSTED, je.status)

        // 5. Assert receipt issued
        val receipt = receiptRepository.findByPaymentId(payment.id)
        assertNotNull(receipt)
        assertEquals(ReceiptStatus.ISSUED, receipt.status)
        assertNotNull(receipt.receiptNumber)

        // 6. Assert AR cleared
        val arBalance = ledgerService.getAccountBalance(arAccount.id, period.id)
        assertEquals(BigDecimal.ZERO, arBalance)
    }
}
```

### 10.2 Property-Based Test — Balance Invariance

```kotlin
class DoubleEntryPropertyTest : FreeSpec({
    "balance invariance holds under 10,000 random journal entries" {
        checkAll(10_000, Arb.journalEntryCommand()) { command ->
            val result = journalService.post(command)
            val postedEntry = journalEntryRepository.findById(result.id)
            val totalDebits  = postedEntry.lines.sumOf { it.debitAmount }
            val totalCredits = postedEntry.lines.sumOf { it.creditAmount }
            totalDebits.compareTo(totalCredits) shouldBe 0
        }
    }
})
```

### 10.3 CI/CD Quality Gates (All Must Pass — Build Fails Otherwise)

| Gate | Enforcement |
|---|---|
| No Float/Double on monetary fields | Detekt custom rule: `NoFloatMoneyRule` |
| @Transactional on all posting methods | Detekt custom rule: `TransactionalPostingRule` |
| Audit columns on all @Entity classes | Custom annotation processor |
| Balance invariance | Integration test suite |
| Idempotency on duplicate key | Integration test |
| Receipt not before POSTED JE | Integration test |
| Period lock enforcement | Integration test |
| SonarQube quality gate | A rating minimum |
| Test coverage | ≥ 90% on domain + service layers |
| Performance targets | k6 load test in CI (not just local) |

---

## 11. ZERO-DEVIATION RULES

These rules are **absolute**. No exceptions without written human approval and a documented design document amendment.

```
01. NEVER use float/double for money. Always BigDecimal + DECIMAL(20,6). No exceptions.
02. NEVER bypass @Transactional on posting, approval, or state-transition service methods.
03. NEVER hard-delete any financial record. Soft-deactivate only (is_active = false + audit log).
04. NEVER post to a CLOSED period without elevated approval + reason logged to period_override_log.
05. NEVER generate a receipt before journal_entry_id.status == POSTED.
06. NEVER skip Idempotency-Key validation on payment, receipt, or journal POST endpoints.
07. NEVER skip audit logging on any state transition, approval, reversal, or deactivation.
08. NEVER expose raw stack traces in HTTP responses. Use standardized error envelopes (§6).
09. NEVER allow SUM(debits) ≠ SUM(credits) to pass the double-entry validator.
10. NEVER finalise a Balance Sheet where Assets ≠ Liabilities + Equity.
11. NEVER allow a module to directly instantiate or call classes from another module package.
12. NEVER process M-Pesa webhooks synchronously. Queue immediately; acknowledge in <5s.
13. NEVER store secrets (passwords, API keys, tokens) in code, application.yml, or source control.
14. NEVER log PII (phone number, email, bank account, full TransID) in plaintext.
15. NEVER allow SYSTEM_ADMIN to post or approve journal entries (separation of duties).
16. NEVER allow a user to approve their own journal entry.
17. ALWAYS enforce Assets == Liabilities + Equity before finalising Balance Sheet.
18. ALWAYS run the full double-entry validator on both save and approve operations.
19. ALWAYS include before_state and after_state JSONB snapshots in audit log entries.
20. ALWAYS route /reports/*, /trial-balance, /financial-statements reads to the read replica.
21. ALWAYS return standardized API envelopes (§7.1). No raw Spring exception responses.
22. ALWAYS generate and include trace_id in every API response metadata.
23. ALWAYS acknowledge M-Pesa callbacks within 5 seconds. Process asynchronously.
24. ALWAYS annotate code with design document section references (e.g., // §4.2).
25. ALWAYS validate functional currency balance equality in multi-currency journal entries.
```

---

## 12. AGENT EXECUTION PROTOCOL

### 12.1 Task Acceptance Criteria

An agent may accept and begin a task ONLY if:
- Pre-flight check (§1) is fully completed with all items confirmed.
- The task is within the agent's assigned module scope.
- No zero-deviation rule (§11) conflicts with the task specification.
- All required skill proficiencies (skills.md §3) for the assigned module are met.

### 12.2 Output Requirements

Every artifact produced must include:
- **Code comments** citing design doc sections: `// §4.2 — Double-Entry Validator`
- **Module reference** in the class-level KDoc: `@Module Module 3 — Journal Entry Engine`
- **TRACEABILITY.md** entry: maps the class/method to its design spec section

### 12.3 Traceability Matrix (Required Artifact)

The agent must maintain and update `TRACEABILITY.md` alongside code:

```markdown
| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| DoubleEntryValidator.validate() | M3 | §4.2 | SUM(debits)==SUM(credits) |
| PeriodLockInterceptor | M9 | §9.1, §10.2 | Period state enforcement |
| MpesaWebhookController | M14 | §14 | Async, <5s ack |
| ReceiptService.generate() | M15 | §15.3 | No receipt before POSTED JE |
```

### 12.4 Escalation Protocol

**Halt immediately and escalate (do not proceed) if:**
- A design doc ambiguity has more than one valid implementation interpretation.
- A zero-deviation rule (§11) would need to be broken to implement the task as specified.
- A required external API contract (M-Pesa, exchange rate) differs from the documented spec.
- A performance target (§4.5 of skills.md) cannot be met with the available data model.
- A discovered data integrity issue (e.g., ledger out of balance) is found during implementation.

### 12.5 Communication Format

When an agent completes a task or escalates, it must report in this format:

```
AGENT REPORT
Task:         [Module X — SubmoduleY — TaskZ]
Status:       COMPLETE | ESCALATED | BLOCKED
Artifacts:    [list of files produced]
Traceability: [list of design doc sections covered]
Tests:        [list of tests written; coverage %]
Deviations:   NONE | [list with justification if any]
Escalation:   N/A | [reason for escalation]
```

---

*Document: instructions.md | Version: 2.0 | System: IFRS Financial Accounting | Stack: Kotlin + Spring Boot 3.x + PostgreSQL 15+ | Aligned to: System Design Prompt v3.0 | Skills Reference: skills.md v2.0*

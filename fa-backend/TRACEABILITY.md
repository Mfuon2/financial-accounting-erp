# Traceability Matrix — IFRS Financial Accounting System

## Shared Infrastructure

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `BaseFinancialEntity` | Shared | §3.3, §9.3 | Mandatory audit columns and soft-delete fields |
| `ApiResponse` | Shared | §6.1, §7.1 | Standardized response envelope |
| `ApiError`, `ResponseMetadata` | Shared | §6.1 | Error response structure with trace_id |
| `GlobalExceptionHandler` | Shared | §6.11 | Standardized error mapping to HTTP status codes |
| `BaseAccountingException` | Shared | §6.11 | Base exception with error code, message, context |
| `ValidationException` | Shared | §6.2 | Validation errors (HTTP 400) |
| `ResourceNotFoundException` | Shared | §6.4 | Resource not found (HTTP 404) |
| `ImmutableRecordException` | Shared | §6.6 | Immutable record violation (HTTP 422) |
| `PeriodLockedException` | Shared | §6.6 | Period lock violation (HTTP 422) |
| `AuditLog` | Shared | §11 | Forensic-grade INSERT-only audit trail |
| `AuditAction` enum | Shared | §11 | Actions: CREATE, UPDATE, DELETE, POST, REVERSE, APPROVE, REJECT, CLOSE, REOPEN, EXPORT, TAX_ADJUSTMENT |
| `AuditService` | Shared | §11 | Audit log persistence with REQUIRES_NEW propagation |
| `AuditAspect` | Shared | §11, §12.2 | AOP interception of @Auditable methods with parameter extraction |
| `Auditable` annotation | Shared | §12.2 | Method-level audit marker with action and resource type |
| `AuditEntityId`, `AuditResourceId` | Shared | §11 | Parameter-level annotations for AOP context extraction |
| `UserRole` enum | Shared | §5.2 | RBAC: DATA_ENTRY, ACCOUNTANT, SENIOR_ACCOUNTANT, CONTROLLER_CFO, AUDITOR, SYSTEM_ADMIN |
| `UserContext` | Shared | §18 | Security principal: userId, entityId, role, email |
| `JwtService` | Shared | §18 | JWT token generation and extraction |
| `JwtAuthenticationFilter` | Shared | §18 | Stateless JWT authentication filter |
| `SecurityAuditorAware` | Shared | §18 | Spring Data JPA auditing aware component |
| `SecurityConfig` | Shared | §18 | Stateless session, CSRF disabled, JWT filter chain |
| `SecurityUtils` | Shared | §18 | Extract current authenticated user from security context |
| `JpaConfig` | Shared | §3.3 | Enable JPA auditing with securityAuditorAware |

## Module 1: Chart of Accounts

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `Account` | M1 | §2.1 | Account master data with all audit columns |
| `AccountType` enum | M1 | §2.1 | ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE with normal balance derivation |
| `AccountSubtype` enum | M1 | §2.1 | 22 IFRS-aligned subtypes per IAS 1 |
| `NormalBalance` enum | M1 | §2.1 | DEBIT, CREDIT (immutable, derived from type) |
| `IfrsCategory` enum | M1 | §10.1 | 11 categories for IAS 1 presentation (CURRENT_ASSETS, NON_CURRENT_ASSETS, etc.) |
| `CoaTemplate` enum | M1 | §2.2 | SERVICE, MERCHANDISING, MANUFACTURING, FINANCIAL_SERVICES, NON_PROFIT |
| `AccountRepository` | M1 | §2.1 | Find by entity/code, check uniqueness, find all by entity |
| `AccountService.createAccount` | M1 | §2.1, §2.3, §2.4 | Validate currency, check code/name uniqueness, enforce hierarchy (max 5 levels, circular reference detection) |
| `AccountService.updateAccount` | M1 | §2.1 | Immutable account code after postings, validate new code uniqueness |
| `AccountService.deactivateAccount` | M1 | §3.5 | Soft delete only (is_active=false), block if ledger entries exist |
| `AccountService.getHierarchy` | M1 | §2.3 | Walk parent chain to root, return hierarchy in order |
| `AccountService.getBalance` | M1 | §5.2 | Calculate net balance (debit-normal accounts: debits-credits, credit-normal: credits-debits) |
| `AccountService.applyTemplate` | M1 | §2.2 | Bootstrap accounts from pre-built template |
| `AccountService.validateHierarchy` | M1 | §2.3, §2.4 | Enforce max depth (5), circular reference detection, entity mismatch check |
| `CreateAccountCommand` | M1 | §2.1 | DTO for account creation with subtype derivation of type |
| `UpdateAccountCommand` | M1 | §2.1 | DTO for account updates (code immutable if postings exist) |
| `AccountController` | M1 | §2 | REST endpoints for account CRUD, templates, hierarchy queries |

## Module 2: Transaction Capture & Source Documents

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `SourceDocument` | M2 | §3.2 | Source document master with type, status, date, reference number |
| `SourceDocumentType` enum | M2 | §3.2 | 10 types: SALES_INVOICE, PURCHASE_INVOICE, CASH_RECEIPT, PAYMENT_VOUCHER, BANK_STATEMENT, CREDIT_NOTE, DEBIT_NOTE, PAYROLL_RECORD, TAX_DECLARATION, JOURNAL_VOUCHER |
| `SourceDocumentStatus` enum | M2 | §3.2 | Status lifecycle: DRAFT → SUBMITTED → REVIEWED → APPROVED → POSTED → ARCHIVED (or VOID) |
| `SourceDocumentRepository` | M2 | §3.2 | Find by entity, type, status |
| `SourceDocumentService` | M2 | §3.2 | CRUD operations, status transitions, recognition tests (past event, economic impact, probability, measurability) |
| `SourceDocumentController` | M2 | §3.2 | REST endpoints for source document management |

## Module 3: Journal Entry Engine

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `JournalEntry` | M3 | §4.1 | Journal entry with transaction date, description, status, source type/id, and line collection |
| `JournalEntryLine` | M3 | §4.1 | Journal entry line with account, debit/credit amounts, currency, exchange rate, functional amounts, tax code/amount |
| `JournalEntryStatus` enum | M3 | §4.1, §5.1 | DRAFT → PENDING_APPROVAL → POSTED → REVERSED (state machine with exhaustive transitions) |
| `JournalEntryRepository` | M3 | §4 | Find by entity, period, status |
| `JournalService.createEntry` | M3 | §4.1 | Create DRAFT entry with lines, calculate functional amounts |
| `JournalService.postEntry` | M3 | §4.1, §5.1 | Post entry: validate double-entry, post to ledger, mark POSTED (§2.2 @Transactional) |
| `JournalService.updateEntry` | M3 | §4.1 | Update DRAFT entries only, block if POSTED (§3.4 immutability) |
| `JournalService.deleteEntry` | M3 | §4.1 | Hard delete DRAFT entries only, block if POSTED (§3.4) |
| `JournalService.submitEntry` | M3 | §4.1, §5.1 | Transition DRAFT → PENDING_APPROVAL, validate double-entry on submission |
| `JournalService.rejectEntry` | M3 | §4.1, §5.1 | Transition PENDING_APPROVAL → DRAFT, requires reason logged to audit |
| `JournalService.reverseEntry` | M3 | §4.1, §5.1 | Create reversing entry, mark original as REVERSED (immutable), preserve for audit trail |
| `DoubleEntryValidator.validate` | M3 | §4.2, §5.1 | SUM(debits) == SUM(credits) in both transaction currency and functional currency, min 2 lines, no line with both debit and credit, mutual exclusivity of debit/credit |
| `CreateJournalEntryCommand` | M3 | §4.1 | DTO for journal entry creation |
| `CreateJournalLineCommand` | M3 | §4.1 | DTO for journal entry line (debit/credit amounts, currency, exchange rate) |
| `JournalController` | M3 | §4 | REST endpoints for journal entry CRUD, submission, approval, posting, reversal |

## Module 4: General Ledger & Posting Engine

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `LedgerEntry` | M4 | §5.1 | Ledger entry with account, journal line reference, transaction date, functional debit/credit, running balance (immutable after posting) |
| `LedgerEntryRepository` | M4 | §5.1 | Sum functional debits/credits by account and date range |
| `PostingService.postJournalEntry` | M4 | §5.1, §5.2 | Atomic posting of journal entry to ledger, running balance calculation (§2.2 @Transactional), update account totals and current balance |
| `PostingService.calculateNewBalance` | M4 | §5.2 | Calculate running balance based on account's normal balance (DEBIT: balance+debit-credit, CREDIT: balance+credit-debit) |
| `LedgerService.postDepreciation` | M4 | §6, §11 | Post depreciation journal entry (depreciation expense → accumulated depreciation) |
| `LedgerController` | M4 | §5 | REST endpoints for ledger queries and depreciation posting |

## Module 5: Trial Balance Engine

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `TrialBalanceService.generateTrialBalance` | M5 | §5.3, §5.1 | Generate trial balance report: sum functional debits/credits per account, verify SUM(all debits) == SUM(all credits), present net balance in account's normal balance column (§6) |
| `TrialBalanceReport` | M5 | §5.3 | Report DTO: entityId, asOfDate, rows, totalDebits, totalCredits |
| `TrialBalanceRow` | M5 | §5.3 | Account code, name, debit balance (if DEBIT-normal and positive), credit balance (if CREDIT-normal and positive) |
| `TrialBalanceController` | M5 | §5 | REST endpoint to generate trial balance |

## Module 6: Adjusting Entries Engine

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `AdjustmentService` | M6 | §6 | Create adjusting entries for prepaid, accrued, depreciation, FX revaluation, provision, lease adjustments |
| `AdjustmentController` | M6 | §6 | REST endpoints for adjusting entry generation |

## Module 7: Financial Statement Generator

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `FinancialStatementService.getBalanceSheet` | M7 | §7 | Aggregate accounts by IfrsCategory (CURRENT_ASSETS, NON_CURRENT_ASSETS, LIABILITIES, EQUITY), verify Assets == Liabilities + Equity (§6.6 BALANCE_SHEET_DOES_NOT_BALANCE) |
| `FinancialStatementService.getProfitLoss` | M7 | §7 | Aggregate accounts by IfrsCategory (REVENUE, COST_OF_SALES, OPERATING_EXPENSES, etc.), calculate net income |
| `BalanceSheetReport` | M7 | §7 | Report DTO: totalAssets, totalLiabilities, totalEquity |
| `ProfitLossReport` | M7 | §7 | Report DTO: totalRevenue, totalExpenses, netIncome |
| `FinancialStatementController` | M7 | §7 | REST endpoints for balance sheet, P&L, cash flow statements |

## Module 8: Closing Entries Engine

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `ClosingService` | M8 | §8 | Four-step closing sequence (§8.1): (1) Close Revenue → Income Summary, (2) Close Expenses → Income Summary, (3) Close Income Summary → Retained Earnings, (4) Close Dividends → Retained Earnings |
| `ClosingController` | M8 | §8 | REST endpoint to execute closing entries |

## Module 9: Period Management & Accounting Cycle Controller

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `Period` | M9 | §10.1 | Accounting period with start/end dates, name, status (all audit columns) |
| `PeriodStatus` enum | M9 | §10.1, §5.1 | FUTURE → OPEN → ADJUSTING → CLOSING → CLOSED → REOPENED (state machine with validated transitions) |
| `PeriodRepository` | M9 | §10.1 | Find by entity and date range |
| `PeriodService.generateFiscalYear` | M9 | §10.1 | Create 12 monthly periods, mark first as OPEN, rest as FUTURE |
| `PeriodService.findPeriodForDate` | M9 | §10.1 | Find period containing given date |
| `PeriodService.transitionPeriod` | M9 | §10.1, §5.1 | Enforce state machine transitions, throw on invalid transition (§6.6 INVALID_STATE_TRANSITION) |
| `PeriodService.closePeriod` | M9 | §10.1 | Transition to CLOSED status |
| `RequireOpenPeriod` annotation | M9 | §5.3 | Marker for period lock enforcement via AOP |
| `PeriodLockInterceptor` | M9 | §5.3, §9.1 | AOP aspect to intercept posting operations and verify period is OPEN/ADJUSTING/CLOSING (§6.6 PERIOD_LOCKED) |
| `PeriodController` | M9 | §10 | REST endpoints for period CRUD and transition |
| `AccountingCycleService` | M9 | §9.2 | Orchestrate nine-step accounting cycle (journal → post → adjusting → post → income statement → SOCE → balance sheet → closing → post-closing) |

## Module 10: IFRS Compliance Engine

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `ComplianceService` | M10 | §10 | Check IFRS rules per standard (IAS 1, IAS 2, IAS 7, etc.), generate departure log |

## Module 11: Reporting & Audit Trail

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `AuditLogRepository` | M11 | §11 | Read-only access to audit log (INSERT-only at application level via REQUIRES_NEW) |

## Module 12: Multi-Currency & FX Management

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `Currency` | M12 | §12 | Currency master with code, name, isFunctional flag |
| `CurrencyRepository` | M12 | §12 | Find by entity and currency code |
| `ExchangeRate` | M12 | §12 | Exchange rate with from/to currency, date, rate value, rate type (SPOT, CLOSING, AVERAGE) |
| `ExchangeRateRepository` | M12 | §12 | Find exchange rates by currency pair and date |
| `ExchangeRateService` | M12 | §12 | Fetch and sync exchange rates from external provider, apply rates to multi-currency postings |
| `FXRevaluationService` | M12 | §12 | Generate FX revaluation journal entry per IAS 21 (foreign currency transaction gains/losses) |

## Module 16: Fixed Assets & Depreciation

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `FixedAsset` | M16 | §11 | Asset master with cost, salvage value, useful life, depreciation method, status (ACTIVE, DISPOSED, FULLY_DEPRECIATED) |
| `FixedAssetRepository` | M16 | §11 | Find by entity and asset code |
| `AssetMasterService` | M16 | §11 | Create and manage fixed assets |
| `DepreciationService` | M16 | §11 | Calculate depreciation per method (STRAIGHT_LINE, DOUBLE_DECLINING_BALANCE), post depreciation journal entry |

## Module 13: Invoicing

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `Customer` | M13 (Party) | §14.3 | Customer master with credit limit, AR account, soft-delete |
| `Supplier` | M13 (Party) | §14.3 | Supplier master with AP account, soft-delete |
| `CustomerRepository` | M13 (Party) | §14.3 | Find by entity/code, active filter, existence checks |
| `SupplierRepository` | M13 (Party) | §14.3 | Find by entity/code, active filter, existence checks |
| `CustomerService.create` | M13 (Party) | §14.3, §12.2 | Create customer, enforce code uniqueness, audit on CREATE |
| `CustomerService.deactivate` | M13 (Party) | §3.5, §12.2 | Soft-delete customer, audit on UPDATE |
| `SupplierService.create` | M13 (Party) | §14.3, §12.2 | Create supplier, enforce code uniqueness, audit on CREATE |
| `SupplierService.deactivate` | M13 (Party) | §3.5, §12.2 | Soft-delete supplier, audit on UPDATE |
| `IdempotencyKey` | Shared | §7.2 | Idempotency cache with 24h TTL (DB + Redis) |
| `IdempotencyKeyRepository` | Shared | §7.2 | Find by key and entity (UNIQUE constraint) |
| `IdempotencyService.checkAndStore` | Shared | §7.2 | Write-through Redis/DB caching pattern, returns NEW or DUPLICATE |
| `IdempotencyConflictException` | Shared | §6.5 | Duplicate request exception (HTTP 409) |
| `Invoice` | M13 | §14.1, §14.2 | Invoice master with customer, status machine, line items |
| `InvoiceLine` | M13 | §14.1 | Line item with account, tax, IFRS 15 recognition type |
| `InvoiceStatus` enum | M13 | §14.2, §5.1 | Status machine: DRAFT→APPROVED→SENT→PARTIALLY_PAID→PAID (or VOID/CREDIT_NOTE) |
| `PerformanceObligationType` enum | M13 | §14.1, §11.2 | POINT_IN_TIME (recognize on approval) vs OVER_TIME (deferred) |
| `InvoiceRepository` | M13 | §14.1 | Find by customer/status/date range, credit limit exposure sums |
| `InvoiceLineRepository` | M13 | §14.1 | Find lines by invoice, ordered by line number |
| `InvoiceService.createDraft` | M13 | §14.1, §12.2 | Create DRAFT invoice, compute totals, generate number, audit on CREATE |
| `InvoiceService.approve` | M13 | §14.2, §14.3, §14.4 | Credit limit check, IFRS 15 validation, post journal entry, audit on POST |
| `InvoiceService.applyPayment` | M13 | §14.2 | Update paid/outstanding amounts, transition PARTIALLY_PAID/PAID, audit on UPDATE |
| `InvoiceService.void` | M13 | §14.2 | Void DRAFT invoices only, audit on UPDATE |
| `InvoiceService.createCreditNote` | M13 | §14.2 | Create negative invoice, post reversing journal, validate amount, audit on CREATE |
| `InvoiceService.arAgeing` | M13 | §14 | Generate ageing buckets (0-30, 31-60, 61-90, 90+ days) for AR analysis |
| `Ifrs15RecognitionService.computeRecognition` | M13 | §14.4, §11.2 | Compute revenue recognition per obligation type (POINT_IN_TIME full, OVER_TIME deferred) |
| `Ifrs15RecognitionService.canRecognizeNow` | M13 | §14.4 | Check if all obligations are POINT_IN_TIME (no deferral needed) |
| `Ifrs15RecognitionService.calculateDeferredRevenue` | M13 | §14.4 | Sum OVER_TIME line totals for deferred revenue account credit |

## Shared Tax Module (Referenced by Invoicing)

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `TaxCode`, `TaxRate` | Shared | §13 | Tax code master and rate schedule with effective date |
| `TaxRepositories` | Shared | §13 | Find tax rates by code and effective date |
| `TaxService` | Shared | §13 | Calculate tax amounts per applicable rates |

---

## Database Schema (Flyway Migrations)

| Migration | Changes | Constraints |
|---|---|---|
| V1 | Create accounts table | UNIQUE(entity_id, account_code), UNIQUE(entity_id, account_name), CHECK(normal_balance IN ('DEBIT', 'CREDIT')) |
| V2 | Create accounting_periods table | UNIQUE(entity_id, period_name), UNIQUE(entity_id, start_date, end_date), CHECK(start_date <= end_date) |
| V3 | Add soft-delete columns to accounts, periods | is_active, deactivated_at, deactivated_by, deactivation_reason |
| V4 | Create journal_entries, journal_entry_lines, ledger_entries tables | CHECK(debit_amount XOR credit_amount), CHECK(functional_debit XOR functional_credit), DECIMAL(20,6) for all monetary columns, ON DELETE CASCADE for JEL→JE, ON DELETE RESTRICT for LE→JEL |
| V5 | Add account balance tracking columns | total_debits, total_credits, current_balance, original_currency_balance (DECIMAL(20,6)) |
| V6 | Create currencies, exchange_rates, fixed_assets tables | UNIQUE(entity_id, currency_code), UNIQUE(entity_id, from_currency, to_currency, rate_date, rate_type), UNIQUE(entity_id, asset_code) |
| V7 | Add period_id to multi-currency/asset tables | period_id to currencies, exchange_rates, fixed_assets for period-scoped reporting |
| V8 | Add period_id to ledger_entries | period_id for efficient period-based trial balance queries |
| V9 | Create audit_logs, tax_codes, tax_rates tables | Audit log is INSERT-only with forensic snapshots; tax table indexes on effective date lookups |
| V10 | Create source_documents table | Tracks document lifecycle from DRAFT to ARCHIVED |
| V11 | Add ifrs_category to accounts | IfrsCategory enum mapping for financial statement generation |
| V12 | Rename ledger_entries columns | Rename debit_amount → functional_debit, credit_amount → functional_credit (schema alignment with entity naming) |
| V13 | Create party and idempotency tables | customers table (party module), suppliers table (party module), idempotency_keys table (Redis + DB write-through) with TTL and unique constraints |
| V14 | Create invoicing tables | invoices table with customer FK, status enum, journal entry link; invoice_lines table with cascade delete, account/tax FKs, IFRS 15 recognition tracking |

---

## Module 14: Payments

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `Payment` | M14 | §14 | Payment master with status, method, amount, currency, FX rate, M-Pesa fields, journal entry link |
| `PaymentStatus` enum | M14 | §14 | PENDING → MATCHED → APPROVED → POSTED → REVERSED (state machine) |
| `PaymentMethod` enum | M14 | §14 | BANK_TRANSFER, CHEQUE, CASH, MPESA, CARD, OTHER |
| `PaymentRepository.findByEntityId` | M14 | §14 | Page all payments for an entity |
| `PaymentRepository.findByEntityIdAndStatus` | M14 | §14 | Filter by entity and status |
| `PaymentRepository.findByEntityIdAndCustomerId` | M14 | §14 | Filter by entity and customer |
| `PaymentRepository.findByTransactionReference` | M14 | §14 | Lookup by external transaction reference (M-Pesa, bank) |
| `PaymentRepository.findByMpesaCheckoutRequestId` | M14 | §14 | Idempotent M-Pesa callback lookup |
| `PaymentService.createPayment` | M14 | §14, §7.2 | Create PENDING payment; idempotency via Idempotency-Key header |
| `PaymentService.matchToInvoice` | M14 | §14.2 | Match PENDING payment to invoice; enforce PENDING status guard |
| `PaymentService.approvePayment` | M14 | §14 | Transition MATCHED → APPROVED; enforce MATCHED status guard |
| `PaymentService.postPayment` | M14 | §14, §4.1 | Post APPROVED payment to GL (DR AR / CR Bank); transition → POSTED |
| `PaymentService.reversePayment` | M14 | §14, §4.1 | Reverse POSTED payment; create reversing journal entry; transition → REVERSED |
| `PaymentService.processMpesaCallback` | M14 | §14 | Process Daraja API v2 STK Push callback; update or create payment from M-Pesa result |
| `PaymentController` (all endpoints) | M14 | §14 | POST /payments, GET /payments, GET /payments/{id}, POST /payments/{id}/match, POST /payments/{id}/approve, POST /payments/{id}/post, POST /payments/{id}/reverse |
| `PaymentController.mpesaCallback` | M14 | §14 | POST /payments/mpesa/callback — PUBLIC; responds within 5 s; async processing via @Async |
| V15 migration | M14 | §14 | Create payments table: UNIQUE(entity_id, payment_number), status/method enums, M-Pesa columns, journal_entry_id FK |

## Module 15: Receipts

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `Receipt` | M15 | §15 | Receipt master linked to payment and journal entry; delivery metadata (email, phone) |
| `ReceiptStatus` enum | M15 | §15 | POSTED → ISSUED → VOID (terminal); VOID is soft-delete (isActive=false) |
| `ReceiptRepository.findByEntityId` | M15 | §15 | Page all receipts for an entity |
| `ReceiptRepository.findByPaymentId` | M15 | §15 | Lookup receipt by linked payment (UNIQUE constraint: one receipt per payment) |
| `ReceiptService.generateReceipt` | M15 | §15 | Generate receipt for a POSTED payment; enforce payment POSTED status guard; create POSTED receipt |
| `ReceiptService.issueReceipt` | M15 | §15 | Transition POSTED → ISSUED; record issuedAt timestamp |
| `ReceiptService.voidReceipt` | M15 | §15 | Transition POSTED/ISSUED → VOID; soft-delete (isActive=false); requires non-blank reason |
| `ReceiptController` (all endpoints) | M15 | §15 | POST /receipts/generate, GET /receipts, GET /receipts/{id}, POST /receipts/{id}/issue, POST /receipts/{id}/void, GET /receipts/by-payment/{paymentId} |
| V16 migration | M15 | §15 | Create receipts table: UNIQUE(payment_id), status enum, delivery columns, journal_entry_id FK |

## Module 16 (Fixed Assets — Controller Added)

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `AssetController` | M16 | §11 | POST /assets, GET /assets, GET /assets/{id}, POST /assets/{id}/dispose, POST /assets/depreciation/run |
| `AssetMasterService.createAsset` | M16 | §11 | Create fixed asset with COA account mappings (cost, accum dep, dep expense); enforce unique asset code |
| `AssetMasterService.disposeAsset` | M16 | §11 | Dispose active asset; post disposal journal entry (DR Cash, DR Accum Dep, CR Asset Cost, CR/DR Gain/Loss) |
| `AssetMasterService.findByEntityAndStatus` | M16 | §11 | Filter assets by entity and AssetStatus (ACTIVE, DISPOSED, FULLY_DEPRECIATED) |
| `CreateAssetCommand` | M16 | §11 | DTO for asset creation with all COA account IDs and depreciation parameters |
| `FixedAssetRepository.findByEntityIdAndStatus` | M16 | §11 | Query assets by entity and status for depreciation runs and disposal reports |

## Module 13 (Tax — Controller Added)

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `TaxController` | M13 | §13 | POST /tax/codes, GET /tax/codes, GET /tax/codes/{id}, POST /tax/rates, GET /tax/rates, POST /tax/calculate |
| `TaxService.createTaxCode` | M13 | §13 | Create tax classification code; enforce unique code within entity |
| `TaxService.createTaxRate` | M13 | §13 | Create effective rate for a tax code; support multiple rates with different effective dates |
| `TaxService.listTaxCodes` | M13 | §13 | Return all tax codes for an entity |
| `TaxService.listRates` | M13 | §13 | Return all rates for a tax code ordered by effective date |
| `TaxService.getTaxCodeById` | M13 | §13 | Retrieve a single tax code by primary key |
| `TaxService.calculateTax` | M13 | §13 | Compute tax amount for base amount using effective rate on given date |
| `CreateTaxCodeCommand` | M13 | §13 | DTO for tax code creation (entityId, code, description, isRecoverable) |
| `CreateTaxRateCommand` | M13 | §13 | DTO for tax rate creation (entityId, taxCodeId, rate 0–1, effectiveFrom) |

## Module 9 (Accounting Cycle — Controller and 9-Step Service Added)

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `AccountingCycleController` | M9 | §9.2, §10.2 | POST /accounting-cycle/run, POST /accounting-cycle/transition, GET /accounting-cycle/validate-step |
| `AccountingCycleService.runFullCycle` | M9 | §9.2 | Orchestrate 9-step IFRS cycle: (1) unadjusted TB, (2) adjusting entries + post, (3) adjusted TB, (4) income statement, (5) SOCE, (6) balance sheet, (7) closing entries + post, (8) post-closing TB; drives period OPEN→ADJUSTING→CLOSING→CLOSED |
| `AccountingCycleService.transitionPeriod` | M9 | §10.1 | Manually advance period status after pre-condition validation |
| `AccountingCycleService.validateStep` | M9 | §10.1 | Dry-run validation of a proposed status transition without persisting |
| `ClosingParams` | M9 | §9.2 | Data class with optional cycle parameters (adjusting entry flags, depreciation date, etc.) |
| `AccountingCycleResult` | M9 | §9.2 | Result DTO containing unadjusted TB, adjusted TB, post-closing TB, and income statement snapshots |
| `RunCycleRequest` | M9 | §10.2 | Request body for POST /accounting-cycle/run (entityId, periodId, params) |
| `TransitionPeriodRequest` | M9 | §10.2 | Request body for POST /accounting-cycle/transition (entityId, periodId, newStatus) |

## New Shared Infrastructure (Session)

| Class/Method | Module | Design Doc Section | Rule Enforced |
|---|---|---|---|
| `BusinessRuleViolationException` | Shared | §6.6 | HTTP 422 — domain invariant violations (balance mismatches, period state errors, credit-limit breaches); extends BaseAccountingException; covered by handleAccountingException |
| `ConflictException` | Shared | §6.5 | HTTP 409 — idempotency replays, duplicate keys, optimistic-lock conflicts; extends BaseAccountingException; covered by handleAccountingException |
| `IdempotencyAspect` | Shared | §7.2 | AOP aspect enforcing idempotency key presence on annotated methods |
| `RequireIdempotencyKey` | Shared | §7.2 | Annotation marker for methods that require an Idempotency-Key header |
| `CustomerController` | M13 (Party) | §14.3 | POST /customers, GET /customers, GET /customers/{id}, PUT /customers/{id}, POST /customers/{id}/deactivate |
| `SupplierController` | M13 (Party) | §14.3 | POST /suppliers, GET /suppliers, GET /suppliers/{id}, POST /suppliers/{id}/deactivate |
| `InvoiceController` | M13 | §14.1–§14.4 | POST /invoices, GET /invoices, GET /invoices/{id}, POST /invoices/{id}/approve, POST /invoices/{id}/void, POST /invoices/{id}/credit-note, POST /invoices/{id}/payment, GET /invoices/ar-ageing |
| `FxController` | M12 | §12 | POST /fx/currencies, GET /fx/currencies, POST /fx/exchange-rates, GET /fx/exchange-rates, POST /fx/revaluation |
| `ReceiptController` | M15 | §15 | See Module 15 entry above |

## New Tests Added (Session)

| Test Class | Module | Cases | Coverage |
|---|---|---|---|
| `JournalServiceTest` | M3 | 9 | createEntry, postEntry (double-entry validation), updateEntry (POSTED block), deleteEntry, submitEntry, rejectEntry, reverseEntry, balance check, period lock |
| `ReceiptServiceTest` | M15 | 9 | generateReceipt (happy path, POSTED guard, not-found), issueReceipt (POSTED→ISSUED, already-ISSUED guard), voidReceipt (POSTED→VOID, ISSUED→VOID, missing reason), findByPayment |
| `TrialBalanceServiceTest` | M5 | 5 | generateTrialBalance (balanced, unbalanced detection, empty entity, multi-account, period-scoped) |
| `InvoiceServiceTest` | M13 | Enhancements | createDraft, approve (credit limit breach, IFRS 15 recognition), applyPayment (PARTIALLY_PAID/PAID transitions), void (DRAFT-only guard), createCreditNote, arAgeing buckets |

## Updated Migration History (Session)

| Migration | Changes | Constraints |
|---|---|---|
| V13 | Create party and idempotency tables | customers table (UNIQUE entity_id+customer_code), suppliers table (UNIQUE entity_id+supplier_code), idempotency_keys table (UNIQUE entity_id+idempotency_key, TTL column) |
| V14 | Create invoicing tables | invoices (customer FK, UNIQUE entity_id+invoice_number, status/recognition enums), invoice_lines (cascade delete from invoice, account FK, tax FK, IFRS 15 recognition type) |
| V15 | Create payments table | UNIQUE(entity_id, payment_number), status enum, payment_method enum, M-Pesa columns (checkout_request_id, receipt_number, result_code), journal_entry_id FK |
| V16 | Create receipts table | UNIQUE(payment_id) enforcing one receipt per payment, status enum, delivery columns (email, phone), journal_entry_id FK |

---

## Approved Deviations from Design Prompt

1. **Package Naming**: Approved to keep existing semantic package layout (`com.qesuite.accounting.{coa,journal,ledger,ap,fx,assets,tax,source,shared,...}`) instead of module-numbered packages (`module01_coa`, etc.). This choice prioritizes domain readability and team familiarity with established organizational patterns.

---

*Last Updated: 2026-05-09 by Session Workers A-D — Modules 14/15/16 + Tax + Cycle Controller + Tests*

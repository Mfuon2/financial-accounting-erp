# Financial Accounting System — System Design Prompt

> **Purpose:** This document serves as the complete architectural prompt for engineering a financial accounting software system. It is structured as a set of modules and submodules, each with its own system design instructions, data requirements, business rules, and IFRS compliance notes. It covers every step of the complete accounting cycle as established by standard accounting principles (Stickney et al.; Jonick) and aligned to IFRS (IAS 1, IAS 2, IAS 7, IAS 16, IAS 21, IAS 36, IAS 37, IAS 38, IFRS 9, IFRS 13, IFRS 15, IFRS 16).

---

## Table of Contents

1. [System Overview & Architecture Principles](#1-system-overview--architecture-principles)
2. [Module 1 — Chart of Accounts (COA)](#2-module-1--chart-of-accounts-coa)
3. [Module 2 — Transaction Capture & Source Documents](#3-module-2--transaction-capture--source-documents)
4. [Module 3 — Journal Entry Engine](#4-module-3--journal-entry-engine)
5. [Module 4 — General Ledger & Posting Engine](#5-module-4--general-ledger--posting-engine)
6. [Module 5 — Trial Balance Engine](#6-module-5--trial-balance-engine)
7. [Module 6 — Adjusting Entries Engine](#7-module-6--adjusting-entries-engine)
8. [Module 7 — Financial Statement Generator](#8-module-7--financial-statement-generator)
9. [Module 8 — Closing Entries Engine](#9-module-8--closing-entries-engine)
10. [Module 9 — Period Management & Accounting Cycle Controller](#10-module-9--period-management--accounting-cycle-controller)
11. [Module 10 — IFRS Compliance & Disclosures Engine](#11-module-10--ifrs-compliance--disclosures-engine)
12. [Module 11 — Reporting & Audit Trail](#12-module-11--reporting--audit-trail)
13. [Module 12 — Multi-Entity, Multi-Currency & Consolidation](#13-module-12--multi-entity-multi-currency--consolidation)
14. [Module 13 — Invoicing Module](#14-module-13--invoicing-module) *(NEW)*
15. [Module 14 — Payments Module (M-Pesa & General)](#15-module-14--payments-module-m-pesa--general) *(NEW)*
16. [Module 15 — Receipting Module](#16-module-15--receipting-module) *(NEW)*
17. [Cross-Module Data Contracts](#17-cross-module-data-contracts)
18. [System-Wide Rules & Constraints](#18-system-wide-rules--constraints)

---

## 1. System Overview & Architecture Principles

### 1.1 System Purpose

Design a double-entry financial accounting software system capable of managing the complete accounting cycle for any business type (service, merchandising, manufacturing) of any size (SME to large corporation), under the International Financial Reporting Standards (IFRS) framework.

### 1.2 Core Architectural Principles

- **Double-entry integrity:** Every transaction must produce at least one debit and one credit. The sum of all debits must always equal the sum of all credits at every point in time. This constraint must be enforced at the database level (transaction atomicity), not just at the application level.
- **Accrual basis by default:** The system must recognise revenue when earned and expenses when incurred, regardless of cash movement. Cash-basis mode may be offered as a secondary view but must never be the posting basis for IFRS reporting.
- **Immutable ledger:** Posted journal entries must never be deleted. Corrections are made via reversing entries only. The audit trail is append-only.
- **Period isolation:** Each accounting period (month, quarter, year) must be lockable. Entries cannot be posted to a locked period without elevated authorisation and a documented reason.
- **Account-type awareness:** The system must always know the normal balance (debit or credit) of every account to correctly calculate balances, detect anomalies, and enforce financial statement presentation rules.
- **IFRS-first:** All recognition, measurement, and presentation logic defaults to IFRS. GAAP differences are flagged but do not override IFRS defaults.

### 1.3 High-Level System Modules

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     REVENUE CYCLE (FRONT-OFFICE)                         │
│  ┌───────────────┐    ┌───────────────────┐    ┌────────────────────┐    │
│  │  Module 13    │    │    Module 14       │    │    Module 15       │    │
│  │  INVOICING    │───▶│  PAYMENTS          │───▶│  RECEIPTING        │    │
│  │  (Raise &     │    │  (M-Pesa, Bank,    │    │  (Auto-generate &  │    │
│  │   Manage)     │    │   Cash, Card)      │    │   Deliver Receipt) │    │
│  └───────┬───────┘    └────────┬───────────┘    └─────────┬──────────┘    │
│          │                     │                           │              │
│          └─────────────────────▼───────────────────────────▼              │
│                    AUTO-GENERATES SOURCE DOCUMENTS                        │
└────────────────────────────────┬─────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                  ACCOUNTING CYCLE CONTROLLER                    │
├──────────┬──────────┬──────────┬──────────┬──────────┬──────────┤
│  COA     │ Txn      │ Journal  │  GL &    │  Trial   │ Adjusting│
│ Manager  │ Capture  │ Engine   │ Posting  │ Balance  │ Entries  │
├──────────┴──────────┴──────────┴──────────┴──────────┴──────────┤
│         Financial Statement Generator (4 statements)            │
├─────────────────────────────────────────────────────────────────┤
│         Closing Entries Engine                                  │
├──────────┬──────────┬──────────┬──────────────────────────────  │
│  IFRS    │  Multi-  │ Reporting│  Audit Trail                   │
│Compliance│ Currency │  Engine  │                                │
└──────────┴──────────┴──────────┴────────────────────────────────┘
```

### 1.4 Technology Stack Recommendations

- **Database:** Relational (PostgreSQL preferred) — double-entry integrity requires ACID-compliant transactions and foreign key enforcement.
- **API layer:** RESTful or GraphQL — all modules communicate through well-defined contracts.
- **Concurrency:** Optimistic locking on journal entries to prevent duplicate posting.
- **Decimal precision:** Use `DECIMAL(20,6)` or equivalent — never floating point for monetary values.
- **Timezone:** All dates stored as UTC; period boundaries apply the entity's local accounting timezone.

---

## 2. Module 1 — Chart of Accounts (COA)

### Purpose
The Chart of Accounts is the master reference for every account in the system. Every journal entry, ledger posting, and financial statement line item traces back to a COA record. It must be flexible enough to support any business type while enforcing IFRS account categorisation.

### 2.1 Submodule: Account Master

**Data fields per account:**
| Field | Type | Rules |
|---|---|---|
| `account_id` | UUID | System-generated, immutable |
| `account_code` | String (max 20) | Unique per entity; alphanumeric; user-defined numbering scheme |
| `account_name` | String (max 100) | Human-readable; must be unique per entity |
| `account_type` | Enum | `ASSET`, `LIABILITY`, `EQUITY`, `REVENUE`, `EXPENSE` |
| `account_subtype` | Enum | See subtype table below |
| `normal_balance` | Enum | `DEBIT` or `CREDIT` — derived from `account_type` but stored explicitly |
| `is_temporary` | Boolean | `true` for REVENUE, EXPENSE, DIVIDENDS; `false` for ASSET, LIABILITY, EQUITY |
| `parent_account_id` | UUID (nullable) | Supports hierarchical/sub-account structure |
| `is_active` | Boolean | Inactive accounts cannot receive new postings |
| `ifrs_classification` | String | Maps to IFRS line item (e.g., "IAS 1 — Current Assets") |
| `currency_code` | String (ISO 4217) | Default currency for this account |
| `created_at` | Timestamp | Immutable |

**Account subtype table (IFRS-aligned):**

| Account Type | Subtypes |
|---|---|
| ASSET | Current Cash, Current Receivable, Current Inventory, Current Prepaid, Non-current PPE, Non-current Intangible, Non-current Investment, Non-current Other |
| LIABILITY | Current Payable, Current Accrued, Current Deferred Revenue, Current Tax, Non-current Long-term Debt, Non-current Lease, Non-current Provision, Non-current Deferred Tax |
| EQUITY | Share Capital, Retained Earnings, Other Comprehensive Income, Dividends/Drawings |
| REVENUE | Operating Revenue, Other Income, Finance Income |
| EXPENSE | Cost of Goods Sold, Operating Expense, Depreciation, Amortisation, Finance Cost, Tax Expense |

**Normal balance rules (enforce as DB constraint):**
- ASSET → DEBIT
- EXPENSE → DEBIT
- LIABILITY → CREDIT
- EQUITY → CREDIT
- REVENUE → CREDIT
- Contra accounts (e.g., Accumulated Depreciation) → opposite of parent type (CREDIT for a contra-asset)

### 2.2 Submodule: COA Templates

**System must ship with pre-built COA templates:**
- Service business (small/medium)
- Merchandising business (retail)
- Manufacturing business
- Financial services
- Non-profit / not-for-profit

Each template pre-populates account codes, names, types, and IFRS classifications. Users can modify, add, or deactivate accounts after setup.

**Business rule:** A COA template must be selected or a custom COA imported before any journal entry can be created.

### 2.3 Submodule: Account Hierarchy Manager

- Supports parent-child account relationships (e.g., "Cash and Cash Equivalents" → "Petty Cash", "Bank Account A", "Bank Account B").
- Financial statements roll up child balances to parent totals.
- Maximum hierarchy depth: 5 levels.
- Circular references must be rejected.

### 2.4 Submodule: Account Validation Rules

- Accounts marked `is_active = false` must be blocked from receiving new journal entry lines.
- Account codes must follow the entity's defined numbering convention (validated via regex pattern stored at entity level).
- Renaming an account does not change its `account_id` — all historical entries remain linked.
- Deleting an account is forbidden if it has any ledger entries. Deactivation is the only permissible action.

---

## 3. Module 2 — Transaction Capture & Source Documents

### Purpose
Before a journal entry can exist, the underlying business event must be identified, classified, and supported by a source document. This module manages the intake of financial transactions from all originating sources.

### 3.1 Submodule: Transaction Identification Engine

**Recognition criteria check (IFRS Conceptual Framework):**
Every submitted transaction must pass the following gate before entering the journal:

1. **Past event test:** Has a financial event already occurred (not merely anticipated)?
2. **Economic impact test:** Does the event create, change, or extinguish an asset, liability, equity, income, or expense?
3. **Probability test:** Is it probable that future economic benefits will flow to or from the entity?
4. **Measurability test:** Can the amount be reliably measured in monetary terms?

**Implementation:** Build a transaction classification wizard that guides users through these four tests. For automated ingestion (bank feeds, API), apply rule-based screening with a manual review queue for unclassified transactions.

**Excluded from recognition:**
- Executory contracts (signed but unperformed — e.g., purchase orders)
- Internally generated brands, customer lists (IAS 38)
- Merely possible contingent liabilities (only probable ones recognised — IAS 37)
- General economic events without a specific, measurable financial impact on the entity

### 3.2 Submodule: Source Document Manager

**Supported source document types:**
| Document Type | Originating Event | Key Fields to Capture |
|---|---|---|
| Sales Invoice | Revenue transaction | Invoice number, date, customer, line items, amounts, tax, payment terms |
| Purchase Invoice | Expense / asset acquisition | Supplier, invoice number, date, line items, amounts, due date |
| Receipt | Cash received | Payer, amount, date, purpose, linked invoice (if any) |
| Payment Voucher | Cash paid out | Payee, amount, date, purpose, authorisation |
| Bank Statement | Cash movements | Bank reference, date, amount, description |
| Payroll Summary | Employee expense | Pay period, gross pay, deductions, net pay per employee |
| Credit Note | Revenue reversal | Original invoice reference, reason, amount |
| Asset Purchase Order | Capital expenditure | Asset description, cost, useful life, depreciation method |
| Loan Agreement | Financial liability | Principal, interest rate, repayment schedule |
| Lease Contract | IFRS 16 lease | Commencement date, lease term, payment schedule, incremental borrowing rate |

**File attachment:** Each source document record must support attachment of PDF, image, or CSV files. Store file references (not blobs) in the database; files go to object storage (e.g., S3).

**Document status lifecycle:**
```
DRAFT → SUBMITTED → REVIEWED → APPROVED → POSTED → ARCHIVED
```
Only APPROVED documents may trigger journal entries.

### 3.3 Submodule: Automated Transaction Ingestion

- **Bank feed integration:** Accept OFX/CSV/API feeds from banking providers. Auto-match bank transactions to existing receivables/payables using reference numbers, amounts, and dates.
- **Recurring transactions:** Support templates for transactions that repeat on a schedule (e.g., monthly rent, quarterly loan interest). Auto-generate journal entries on schedule with confirmation step.
- **Duplicate detection:** Flag transactions where the same amount + payee/payer + date combination appears within a configurable time window.

---

## 4. Module 3 — Journal Entry Engine

### Purpose
The journal is the chronological book of original entry. Every financial transaction is first recorded here as a journal entry before being posted to the ledger. This module is the heart of the accounting system — all financial data originates here.

### 4.1 Submodule: Journal Entry Creator

**Journal entry data model:**

**Header (one per entry):**
| Field | Type | Rules |
|---|---|---|
| `journal_entry_id` | UUID | System-generated |
| `entry_date` | Date | Must fall within an open accounting period |
| `period_id` | UUID | FK to accounting period — resolved from `entry_date` |
| `entry_type` | Enum | `TRANSACTION`, `ADJUSTING`, `CLOSING`, `REVERSING`, `OPENING_BALANCE` |
| `reference_number` | String | Auto-generated sequential number per entity (e.g., JE-2024-0001) |
| `description` | String (max 500) | Required; human-readable summary of the entry |
| `source_document_id` | UUID (nullable) | FK to source document if applicable |
| `status` | Enum | `DRAFT`, `PENDING_APPROVAL`, `POSTED`, `REVERSED` |
| `created_by` | UUID | FK to user |
| `posted_by` | UUID (nullable) | FK to user who approved and posted |
| `posted_at` | Timestamp (nullable) | Immutable once set |
| `reversal_of` | UUID (nullable) | FK to original entry if this is a reversing entry |

**Lines (two or more per entry):**
| Field | Type | Rules |
|---|---|---|
| `line_id` | UUID | System-generated |
| `journal_entry_id` | UUID | FK to header |
| `account_id` | UUID | FK to COA — must be an active account |
| `debit_amount` | DECIMAL(20,6) | Mutually exclusive with `credit_amount`; non-negative |
| `credit_amount` | DECIMAL(20,6) | Mutually exclusive with `debit_amount`; non-negative |
| `currency_code` | String | ISO 4217 |
| `functional_currency_amount` | DECIMAL(20,6) | Amount converted to entity's functional currency |
| `exchange_rate` | DECIMAL(20,6) | Rate used for conversion |
| `line_description` | String (max 200) | Optional line-level description |
| `cost_centre_id` | UUID (nullable) | For management accounting dimension |

### 4.2 Submodule: Double-Entry Validator

**Enforce at every save and post operation:**

1. **Balance check:** `SUM(debit_amount) = SUM(credit_amount)` across all lines of the entry. Reject if not equal. Tolerance: zero — no rounding exceptions permitted.
2. **Minimum lines check:** Every entry must have at least 2 lines (can have more for compound entries).
3. **Account activity check:** All accounts referenced must be `is_active = true`.
4. **Period open check:** `entry_date` must fall in an open (unlocked) period.
5. **Amount sign check:** `debit_amount` and `credit_amount` must each be ≥ 0; a line cannot have both a debit and credit amount simultaneously.
6. **Currency check:** All lines in a single entry may carry their own currency, but `functional_currency_amount` must be calculated for each and the functional-currency totals must also balance.

### 4.3 Submodule: Special Journals

For high-volume transaction types, the system should support specialised entry forms that auto-generate the underlying double-entry journal:

| Special Journal | Auto-debit | Auto-credit |
|---|---|---|
| Sales Journal | Accounts Receivable | Revenue account + Sales Tax Payable |
| Cash Receipts Journal | Cash | Accounts Receivable (or Revenue for cash sales) |
| Purchases Journal | Expense or Asset account | Accounts Payable |
| Cash Disbursements Journal | Accounts Payable (or Expense) | Cash |
| Payroll Journal | Wages Expense | Cash / Accrued Payroll / Tax Payable |

Each special journal screen collects the minimum required inputs and programmatically constructs the complete double-entry journal entry. The user never manually assigns debits and credits in these flows — the system handles it.

### 4.4 Submodule: Journal Entry Approval Workflow

- **Draft:** Entry created but not validated or posted.
- **Pending Approval:** Entry passes validation; routed to approver based on configurable rules (e.g., entries above $X require secondary approval).
- **Posted:** Approved entries are locked and posted to the ledger. Cannot be edited.
- **Reversed:** A reversing entry has been created against this entry.

**Approval rules engine:** Configure thresholds by amount, account type, entry type, and user role. Example: all adjusting entries above $10,000 require CFO approval.

### 4.5 Submodule: Reversing Entries

- Any posted journal entry may be reversed.
- A reversing entry is a new journal entry with all debits and credits of the original swapped, dated either on the reversal date or the first day of the next period.
- The original entry's status changes to `REVERSED`.
- The reversing entry stores `reversal_of = original_entry_id`.
- Reversing entries appear in the audit trail and cannot themselves be deleted.

---

## 5. Module 4 — General Ledger & Posting Engine

### Purpose
The general ledger organises every posted journal entry by account, maintaining running balances. It is the master record of all financial activity, categorised and ready for reporting.

### 5.1 Submodule: Ledger Posting Engine

**Posting process (triggered when journal entry status → POSTED):**

1. For each line in the journal entry:
   a. Retrieve the account record from COA.
   b. Determine effect on account balance:
      - If `normal_balance = DEBIT`: debit increases balance, credit decreases it.
      - If `normal_balance = CREDIT`: credit increases balance, debit decreases it.
   c. Write a ledger entry record (see data model below).
   d. Update the account's running balance.
2. The posting must be atomic — all ledger entries for a journal entry succeed together or fail together (database transaction).

**Ledger entry record:**
| Field | Type |
|---|---|
| `ledger_entry_id` | UUID |
| `account_id` | UUID (FK to COA) |
| `journal_entry_id` | UUID (FK to journal entry header) |
| `journal_line_id` | UUID (FK to journal line) |
| `entry_date` | Date |
| `period_id` | UUID |
| `debit_amount` | DECIMAL(20,6) |
| `credit_amount` | DECIMAL(20,6) |
| `running_balance` | DECIMAL(20,6) |
| `functional_currency_amount` | DECIMAL(20,6) |

**Running balance calculation:**
- Maintained per account per period.
- Opening balance for a period = closing balance of the immediately preceding period.
- Balance direction: for DEBIT-normal accounts, balance = opening_balance + total_debits − total_credits. For CREDIT-normal accounts, balance = opening_balance + total_credits − total_debits.

### 5.2 Submodule: T-Account View

- Provide a UI and API endpoint to display any account as a T-account.
- Show all debit entries on the left, all credit entries on the right, for a given date range.
- Show running balance at any selected date.
- Filterable by period, entry type, and date range.

### 5.3 Submodule: Subsidiary Ledgers

For high-volume accounts, maintain subsidiary ledgers that reconcile to the control account in the general ledger:

| Control Account | Subsidiary Ledger |
|---|---|
| Accounts Receivable | Customer ledger (one record per customer) |
| Accounts Payable | Supplier ledger (one record per supplier) |
| Inventory | Inventory ledger (one record per SKU / item) |
| Fixed Assets | Asset register (one record per asset) |
| Loans Payable | Loan schedule (one record per loan) |

**Reconciliation rule:** At all times, the sum of all subsidiary ledger balances for a control account must equal the control account's general ledger balance. The system should run this check automatically on every posting and flag discrepancies.

### 5.4 Submodule: Fixed Asset Register (Sub-ledger)

Each capitalised asset must have a register record:
| Field | Description |
|---|---|
| `asset_id` | Unique identifier |
| `description` | Asset name and description |
| `acquisition_date` | Date placed in service |
| `acquisition_cost` | Original cost (IAS 16) |
| `residual_value` | Estimated salvage value |
| `useful_life_months` | Estimated useful life |
| `depreciation_method` | Straight-line, Declining Balance, Units of Production |
| `accumulated_depreciation` | Running total of depreciation posted |
| `carrying_amount` | Acquisition cost minus accumulated depreciation |
| `impairment_loss` | Cumulative impairment (IAS 36) |
| `disposal_date` | Date of disposal (if applicable) |
| `disposal_proceeds` | Cash received on disposal |

**Depreciation engine:** Automatically calculates the period depreciation charge and creates the adjusting journal entry (Debit: Depreciation Expense / Credit: Accumulated Depreciation) for each period, per asset.

---

## 6. Module 5 — Trial Balance Engine

### Purpose
The trial balance is a point-in-time listing of all general ledger accounts and their balances. It can be generated at any time and serves as the primary verification tool before and after adjustments. It is not a financial statement — it is an internal accuracy report.

### 6.1 Submodule: Trial Balance Generator

**Inputs:**
- Entity ID
- As-of date (any date within or at the end of an open or closed period)
- Type: `UNADJUSTED` (excludes adjusting entries) or `ADJUSTED` (includes all adjusting entries for the period)

**Output format:**
| Account Code | Account Name | Account Type | Debit Balance | Credit Balance |
|---|---|---|---|---|
| 1000 | Cash | Asset | 50,000.00 | — |
| 2000 | Accounts Payable | Liability | — | 12,000.00 |
| … | … | … | … | … |
| **TOTAL** | | | **X** | **X** |

**Validation rule:** `TOTAL DEBIT = TOTAL CREDIT`. If they do not match, the system must:
1. Block financial statement generation.
2. Display an error identifying the magnitude of the imbalance.
3. Provide a drill-down to locate the source entries causing the discrepancy.

### 6.2 Submodule: Trial Balance Discrepancy Detector

When the trial balance does not balance, run automated diagnostics:
- Identify any journal entry where `SUM(debit) ≠ SUM(credit)` (should be impossible if the validator works, but check for data corruption).
- Identify any ledger entry not linked to a valid journal entry.
- Identify any account whose running balance does not match the sum of its ledger entries from opening balance.
- Report all findings to the user with entry IDs and amounts.

### 6.3 Submodule: Working Trial Balance (Worksheet)

A spreadsheet-style view showing:
- Column 1–2: Unadjusted trial balance (debit / credit)
- Column 3–4: Adjustments (debit / credit) — entered inline
- Column 5–6: Adjusted trial balance (debit / credit) — auto-calculated
- Column 7–8: Income statement allocation
- Column 9–10: Balance sheet allocation

This is an optional but powerful tool for period-end close workflows, particularly for accountants reviewing the full cycle at once.

---

## 7. Module 6 — Adjusting Entries Engine

### Purpose
Adjusting entries bring all account balances up to date at the end of an accounting period, ensuring the financial statements reflect the accrual basis of accounting. They are made BEFORE financial statements are prepared and AFTER the unadjusted trial balance is verified. Each adjusting entry must affect at least one income statement account and one balance sheet account.

### 7.1 Submodule: Adjusting Entry Types

The system must support and classify all five categories of adjusting entries:

#### Type 1: Deferred Expenses (Prepaid Expenses)
- **Scenario:** Cash paid in advance; expense not yet incurred.
- **Balance sheet account affected:** Prepaid Asset (decreases)
- **Income statement account affected:** Expense (increases)
- **Journal entry:** Debit Expense / Credit Prepaid Asset
- **Example:** Prepaid insurance paid for 12 months; 1 month has now passed.
- **System automation:** On period close, calculate the portion of any prepaid account that has expired based on the prepayment schedule and auto-propose the adjusting entry.

#### Type 2: Deferred Revenue (Unearned Revenue)
- **Scenario:** Cash received in advance; service/goods not yet delivered.
- **Balance sheet account affected:** Deferred Revenue / Unearned Revenue (decreases)
- **Income statement account affected:** Revenue (increases)
- **Journal entry:** Debit Deferred Revenue / Credit Revenue
- **Example:** Customer pays 6-month subscription upfront; 1 month of service has been provided.
- **System automation:** Maintain a deferred revenue schedule per contract/customer; auto-propose the earned portion each period.

#### Type 3: Accrued Expenses (Accrued Liabilities)
- **Scenario:** Expense incurred but not yet paid or recorded.
- **Balance sheet account affected:** Accrued Liability (increases)
- **Income statement account affected:** Expense (increases)
- **Journal entry:** Debit Expense / Credit Accrued Liability
- **Example:** Wages earned by employees in the last week of the month, not yet paid.
- **Common accruals:** Wages payable, interest payable, income tax payable, utilities accrual.

#### Type 4: Accrued Revenue (Accrued Assets)
- **Scenario:** Revenue earned but not yet invoiced or received.
- **Balance sheet account affected:** Accrued Receivable / Unbilled Revenue (increases)
- **Income statement account affected:** Revenue (increases)
- **Journal entry:** Debit Accrued Receivable / Credit Revenue
- **Example:** Interest earned on a loan to a third party, not yet received in cash.

#### Type 5: Depreciation & Amortisation
- **Scenario:** Allocation of cost of long-lived assets over their useful lives (IAS 16, IAS 38).
- **Balance sheet account affected:** Accumulated Depreciation (increases — contra asset)
- **Income statement account affected:** Depreciation / Amortisation Expense (increases)
- **Journal entry:** Debit Depreciation Expense / Credit Accumulated Depreciation
- **System automation:** Driven by the Fixed Asset Register (Module 4.4). Auto-calculate and propose based on method, cost, residual value, and useful life.

### 7.2 Submodule: IFRS-Specific Adjusting Entries

Beyond the five standard types, the system must support IFRS-required period-end adjustments:

#### Provisions (IAS 37)
- Recognised when: present obligation from a past event + probable outflow + reliable estimate.
- Journal entry: Debit Provision Expense / Credit Provision (Liability)
- System must maintain a provision schedule with: nature, estimated amount, expected timing, changes per period.

#### Impairment Testing (IAS 36)
- Triggered when indicators of impairment exist (annual for goodwill and indefinite-life intangibles).
- Compare carrying amount to recoverable amount (higher of fair value less costs of disposal, and value in use).
- If carrying amount > recoverable amount: Debit Impairment Loss / Credit Accumulated Impairment (contra asset)

#### Foreign Currency Revaluation (IAS 21)
- At each period end, monetary items (cash, receivables, payables) denominated in foreign currencies must be retranslated at the closing rate.
- Differences go to: Profit or Loss (for most items) or Other Comprehensive Income (for qualifying hedges and net investment in foreign operations).
- Journal entry: Debit/Credit Foreign Currency Account / Credit/Debit FX Gain or Loss

#### Fair Value Adjustments (IFRS 9, IFRS 13)
- Financial instruments classified at FVTPL: revalue to fair value at period end; gains/losses to P&L.
- Financial instruments at FVOCI: revalue to fair value; gains/losses to OCI.
- Journal entry: Debit/Credit Investment Account / Credit/Debit Unrealised Gain/Loss

#### Lease Liability & Right-of-Use Asset (IFRS 16)
- Each period: unwind interest on lease liability (Debit Interest Expense / Credit Lease Liability) and record depreciation on ROU asset (Debit Depreciation / Credit Accumulated Depreciation — ROU).
- System must maintain a lease amortisation schedule per lease contract.

### 7.3 Submodule: Adjusting Entry Scheduler & Automation

- Allow accountants to define recurring adjusting entry templates (e.g., monthly depreciation, monthly prepaid amortisation).
- On period-end close trigger, auto-generate proposed adjusting entries from templates.
- Proposed entries appear in a review queue — accountant reviews, modifies if necessary, then approves and posts.
- Track which adjusting entries have been posted for each period to avoid duplicates.

### 7.4 Submodule: Adjusted Trial Balance Validation

After all adjusting entries are posted, re-run the trial balance with `type = ADJUSTED`. The adjusted trial balance must:
- Balance (total debits = total credits).
- Be locked — no further regular journal entries can be posted to the period once the adjusted trial balance is confirmed.
- Serve as the direct source for financial statement generation.

---

## 8. Module 7 — Financial Statement Generator

### Purpose
Generate the four mandatory IFRS financial statements directly from the adjusted trial balance. Each statement has specific IFRS presentation requirements (IAS 1) that the system must enforce. The order of generation matters: Income Statement → Retained Earnings Statement → Balance Sheet → Cash Flow Statement.

### 8.1 Submodule: Statement of Profit or Loss and Other Comprehensive Income

**Source:** Adjusted trial balance — all REVENUE and EXPENSE accounts for the period.

**IFRS presentation requirements (IAS 1):**
- May use either **single-statement** format (P&L and OCI combined) or **two-statement** format (separate P&L and separate OCI statement). Configurable per entity.
- Expenses classified by either **function** (cost of sales, selling, admin, etc.) or **nature** (raw materials, employee costs, depreciation, etc.). Configurable per entity.
- Minimum line items required by IAS 1:
  - Revenue
  - Finance costs
  - Share of profit of associates (if applicable)
  - Tax expense
  - Profit or loss
  - Each component of OCI
  - Total comprehensive income

**Calculation flow:**
```
Revenue
− Cost of Goods Sold (if applicable)
= Gross Profit
− Operating Expenses
= Operating Profit (EBIT)
± Finance Income / Finance Costs
= Profit Before Tax
− Income Tax Expense
= Profit for the Period (Net Income)
± Other Comprehensive Income items
= Total Comprehensive Income
```

**System rules:**
- Net income figure is automatically passed to the Retained Earnings Statement (step 6 of the cycle).
- OCI items are automatically routed to the OCI section of equity on the Balance Sheet.
- Comparative period columns: system must show current period and prior period figures side by side (IAS 1 requirement).

### 8.2 Submodule: Statement of Changes in Equity

**Source:** Opening equity balances + net income from P&L + OCI items + capital transactions (share issues, buybacks, dividends).

**Columns (one per equity component):**
- Share Capital
- Share Premium
- Retained Earnings
- Other Comprehensive Income (OCI) Reserve
- Any other reserves

**Rows:**
- Opening balance (start of period)
- Profit for the period (from P&L)
- Other comprehensive income
- Dividends declared
- Share capital issued / repurchased
- Closing balance (end of period)

**IFRS rule:** Dividends declared must be shown in the Statement of Changes in Equity, not deducted on the face of P&L (IAS 1).

**Retained Earnings calculation (Step 6 of the 9-step cycle):**
```
Opening Retained Earnings
+ Net Income (from P&L)
− Dividends Declared
= Closing Retained Earnings
```
This closing retained earnings figure feeds directly into the Balance Sheet.

### 8.3 Submodule: Statement of Financial Position (Balance Sheet)

**Source:** Adjusted trial balance — all ASSET, LIABILITY, and EQUITY accounts as of period-end date.

**IFRS presentation (IAS 1):**
- **Classified format** (current/non-current distinction) — required unless liquidity-order presentation is more relevant (e.g., banks).
- **Current/Non-current split rule:**
  - Current Asset: expected to be realised or consumed within 12 months of the reporting date or the operating cycle.
  - Current Liability: expected to be settled within 12 months or the operating cycle.

**Minimum line items (IAS 1, paragraph 54):**
Assets: Cash and equivalents, Trade receivables, Inventories, Biological assets, Financial assets, Investments in associates, PPE, Investment property, Intangible assets, Deferred tax assets.

Liabilities: Trade payables, Provisions, Financial liabilities, Current tax liabilities, Deferred tax liabilities.

Equity: Issued capital, Retained earnings, NCI (if consolidated).

**Accounting equation validation:**
```
Total Assets = Total Liabilities + Total Equity
```
The system must enforce this as a hard check. If it does not balance, the Balance Sheet cannot be finalised.

**Closing Retained Earnings link:** The Balance Sheet's Retained Earnings line must pull directly from the Statement of Changes in Equity closing balance — not recalculated independently.

### 8.4 Submodule: Statement of Cash Flows

**Source:** Cash and cash equivalent account movements during the period, re-classified by activity type (IAS 7).

**Three sections:**

**1. Operating Activities**
Cash flows from the entity's primary revenue-generating activities.
- **Direct method** (preferred by IAS 7 and IASB): List actual cash receipts from customers and cash payments to suppliers, employees, etc.
- **Indirect method** (more common in practice): Start from net profit; adjust for non-cash items (depreciation, amortisation, provisions); adjust for changes in working capital (receivables, payables, inventory).
- System should support both methods. If indirect method is used, all reconciling adjustments must be traceable to specific ledger accounts.

**Indirect method template:**
```
Net Profit for the Period
+ Depreciation & Amortisation
+ Impairment Losses
± Changes in Provisions
± Changes in Trade Receivables
± Changes in Inventories
± Changes in Trade Payables
± Changes in Prepayments / Accruals
= Net Cash from Operating Activities
```

**2. Investing Activities**
Cash flows from acquisition/disposal of long-term assets and investments.
- Purchase of PPE
- Proceeds from disposal of PPE
- Purchase of intangible assets
- Purchase/sale of investments
- Loans made to third parties / repayments received

**3. Financing Activities**
Cash flows from transactions that change the capital structure.
- Proceeds from issuing shares
- Dividends paid
- Proceeds from borrowings
- Repayment of borrowings
- Repayment of lease liabilities (principal portion — IAS 7 / IFRS 16)

**Cash flow validation:**
```
Net Cash from Operating Activities
+ Net Cash from Investing Activities
+ Net Cash from Financing Activities
= Net Change in Cash for the Period
Opening Cash Balance + Net Change = Closing Cash Balance
```
The closing cash balance must equal the Cash account balance on the Balance Sheet.

### 8.5 Submodule: Notes & Disclosures Generator

**IFRS requires extensive notes that accompany the four statements. The system must support:**

- **Accounting policies note:** Describe basis of preparation (IFRS), functional currency, revenue recognition policy, depreciation methods, inventory valuation method, etc.
- **Significant estimates and judgements note:** Areas where management judgment materially affects amounts (e.g., useful lives, impairment assessments, provision estimates).
- **Disaggregation of revenue (IFRS 15):** Revenue by category, geography, or contract type.
- **PPE reconciliation (IAS 16):** Opening balance, additions, disposals, depreciation charge, closing carrying amount — per asset class.
- **Lease note (IFRS 16):** ROU assets by class, maturity analysis of lease liabilities.
- **Financial instruments (IFRS 7):** Fair value disclosures, credit risk, liquidity risk, market risk.
- **Related party transactions (IAS 24).**
- **Events after the reporting period (IAS 10).**
- **Segment reporting (IFRS 8):** If the entity is publicly listed or voluntarily reports segments.

**Note template engine:** Pre-build standard note templates for each IAS/IFRS standard. Allow entities to populate the variable fields. Generate the notes as formatted text for inclusion in the financial report.

---

## 9. Module 8 — Closing Entries Engine

### Purpose
Closing entries zero out all temporary accounts (revenues, expenses, and dividends) at the end of the accounting period and transfer their net balances to Retained Earnings. This resets the income statement accounts to zero so the next period begins fresh, while the balance sheet accounts carry forward their closing balances as opening balances for the next period.

**Critical rule from Jonick (Principles of Financial Accounting):** Closing entries are recorded AFTER financial statements are prepared — never before.

### 9.1 Submodule: Closing Entry Generator

**Step-by-step closing process (automate all four steps):**

**Step 1 — Close all Revenue accounts to Income Summary**
```
Debit: Each Revenue Account (individual balances)
Credit: Income Summary
```
This transfers the total revenue for the period to the Income Summary account.

**Step 2 — Close all Expense accounts to Income Summary**
```
Debit: Income Summary
Credit: Each Expense Account (individual balances)
```
This transfers the total expenses for the period to the Income Summary account.

After Steps 1 and 2, the Income Summary account balance equals Net Income (if credit balance) or Net Loss (if debit balance).

**Step 3 — Close Income Summary to Retained Earnings**
```
If Net Income:   Debit Income Summary / Credit Retained Earnings
If Net Loss:     Debit Retained Earnings / Credit Income Summary
```

**Step 4 — Close Dividends/Drawings to Retained Earnings**
```
Debit: Retained Earnings
Credit: Dividends Declared / Drawings Account
```

**After closing, verify:**
- All temporary accounts (Revenue, Expense, Dividends) have zero balances.
- Retained Earnings balance = Opening balance + Net Income − Dividends.
- This matches the Retained Earnings figure on the Balance Sheet.

### 9.2 Submodule: Post-Closing Trial Balance

After closing entries are posted, generate a **post-closing trial balance** that:
- Contains ONLY permanent accounts (Assets, Liabilities, Equity).
- Confirms all temporary accounts have zero balances.
- Confirms Retained Earnings reflects the correct closing figure.
- Serves as the opening balance verification for the next period.

### 9.3 Submodule: Period Lock

Once closing entries are posted and the post-closing trial balance is verified:
- Lock the accounting period. No further entries can be posted without elevated authorisation.
- Transfer closing balances as opening balances for the next period.
- Create an `OPENING_BALANCE` journal entry at the start of the next period for audit trail completeness.

---

## 10. Module 9 — Period Management & Accounting Cycle Controller

### Purpose
Orchestrate and enforce the correct sequence of the nine accounting cycle steps. Prevent steps from being performed out of order. Manage the fiscal calendar and ensure period integrity.

### 10.1 Submodule: Fiscal Calendar Manager

- Define the entity's fiscal year (calendar year or custom year-end).
- Auto-generate accounting periods (monthly, quarterly, annual) for the defined fiscal year.
- Period states:
  - `FUTURE` — not yet opened
  - `OPEN` — current active period; transactions can be posted
  - `ADJUSTING` — regular transactions locked; only adjusting entries allowed
  - `CLOSING` — adjusting entries locked; only closing entries allowed
  - `CLOSED` — fully locked; no entries without elevated permission
  - `REOPENED` — closed period temporarily reopened; all entries require dual approval and explanation

### 10.2 Submodule: Accounting Cycle State Machine

Enforce the nine-step cycle as a state machine per period:

| Step | Action | State Transition | Pre-condition |
|---|---|---|---|
| 1 | Journalize transactions | OPEN | Period is OPEN |
| 2 | Post to ledgers | OPEN | Journal entries exist in PENDING status |
| 3 | Journalize adjusting entries | ADJUSTING | Unadjusted trial balance generated and balanced |
| 4 | Post adjusting entries | ADJUSTING | Adjusting entries exist in PENDING status |
| 5 | Prepare income statement | CLOSING | Adjusted trial balance generated and balanced |
| 6 | Prepare retained earnings statement | CLOSING | Income statement completed |
| 7 | Prepare balance sheet | CLOSING | Retained earnings statement completed; Assets = Liabilities + Equity |
| 8 | Journalize closing entries | CLOSING | All four financial statements finalised and signed off |
| 9 | Post closing entries | CLOSED | Closing entries journalized and approved |

**System enforcement:** The UI and API must block each step until its pre-conditions are met. For example, the Balance Sheet generation endpoint returns an error if the Retained Earnings Statement has not been confirmed.

### 10.3 Submodule: Period-End Checklist

Provide a configurable checklist visible to the accounting team during period close, tracking:
- [ ] All bank accounts reconciled
- [ ] All intercompany transactions matched
- [ ] Depreciation run completed
- [ ] All prepaid schedules reviewed
- [ ] Accruals approved
- [ ] Foreign currency revaluation run
- [ ] Unadjusted trial balance reviewed
- [ ] All adjusting entries posted
- [ ] Adjusted trial balance balanced
- [ ] Financial statements reviewed and signed off
- [ ] Closing entries posted
- [ ] Post-closing trial balance verified
- [ ] Period locked

Each checklist item is assigned to a user, has a status (pending/complete), and is time-stamped.

---

## 11. Module 10 — IFRS Compliance & Disclosures Engine

### Purpose
Ensure all recognition, measurement, presentation, and disclosure requirements of applicable IFRS standards are met. Flag departures and provide guided compliance workflows.

### 11.1 Submodule: IFRS Standard Rules Engine

Maintain a database of IFRS rules mapped to account types, transaction types, and presentation requirements. For each applicable standard, the rules engine must:

| Standard | Key Rules Enforced |
|---|---|
| **IAS 1** | Presentation of financial statements — format, minimum line items, comparative periods, going concern disclosure |
| **IAS 2** | Inventories — cost formulas (FIFO or weighted average; LIFO prohibited under IFRS), NRV write-down |
| **IAS 7** | Cash flow statements — classification of interest and dividends (policy choice), direct vs indirect method |
| **IAS 10** | Events after reporting period — adjusting vs non-adjusting events |
| **IAS 16** | PPE — cost model vs revaluation model, componentisation, depreciation, derecognition |
| **IAS 21** | Foreign exchange — functional currency determination, transaction date rates, closing rates for monetary items |
| **IAS 36** | Impairment — annual testing triggers, CGU identification, recoverable amount calculation |
| **IAS 37** | Provisions — recognition criteria (probable + reliable estimate), contingent liabilities (disclose only), contingent assets (do not recognise) |
| **IAS 38** | Intangible assets — identifiability, research vs development cost split, useful life assessment |
| **IFRS 9** | Financial instruments — classification (amortised cost, FVOCI, FVTPL), ECL impairment model |
| **IFRS 13** | Fair value measurement — hierarchy levels (Level 1/2/3), required disclosures |
| **IFRS 15** | Revenue — 5-step model (identify contract, performance obligations, transaction price, allocate, recognise) |
| **IFRS 16** | Leases — lessee accounting (ROU asset + lease liability), short-term and low-value exemptions |

### 11.2 Submodule: IFRS 15 Revenue Recognition Engine (5-Step Model)

For each revenue contract, track:
1. **Identify the contract** — customer, approval, rights established, payment terms.
2. **Identify performance obligations** — distinct goods or services promised.
3. **Determine the transaction price** — fixed consideration, variable consideration (estimate and constrain), significant financing component.
4. **Allocate the transaction price** — to each performance obligation based on standalone selling prices.
5. **Recognise revenue** — when (or as) each performance obligation is satisfied (point in time vs over time).

System must support:
- Multi-element arrangements (bundle contracts with multiple deliverables).
- Variable consideration with constraint estimates.
- Over-time recognition with progress measurement (input method: costs incurred; or output method: milestones, surveys of completion).
- Contract asset (earned but not yet billed) and contract liability (billed but not yet earned) accounts.

### 11.3 Submodule: IFRS 16 Lease Management

For each lease contract:
- Capture: commencement date, lease term, payment amounts and schedule, incremental borrowing rate.
- On commencement: calculate present value of lease payments = initial lease liability = initial ROU asset (plus initial direct costs, prepayments).
- Generate lease amortisation schedule: interest portion (unwinds at effective interest rate), principal portion, and ROU depreciation.
- At each period end: auto-post interest accrual and depreciation adjusting entries.
- Reassessment triggers: modification of lease terms, change in index/rate, exercise of extension/termination options.

### 11.4 Submodule: Compliance Checker & Departure Log

- Run automated checks against IFRS rules at each period close.
- Flag any departures (e.g., inventory valued above NRV, receivable not impaired under ECL model).
- Require preparer to either remedy the departure or document an approved IFRS departure with justification (rare — departures from IFRS are only permitted in extremely rare circumstances per IAS 1 paragraph 19).
- Maintain an immutable log of all compliance checks, results, and responses.

---

## 12. Module 11 — Reporting & Audit Trail

### Purpose
Provide all reporting outputs required by internal management, external stakeholders, and auditors. Maintain a complete, tamper-proof audit trail of every action in the system.

### 12.1 Submodule: Report Library

**Standard reports available:**

| Report | Description |
|---|---|
| Trial Balance (Unadjusted) | All accounts before period-end adjustments |
| Trial Balance (Adjusted) | All accounts after period-end adjustments |
| Post-Closing Trial Balance | Permanent accounts only, after closing |
| General Ledger Detail | All entries per account for a date range |
| T-Account View | Individual account — debits left, credits right |
| Journal Entry Listing | Chronological list of all journal entries |
| Statement of Profit or Loss | Income statement — current and comparative period |
| Statement of Financial Position | Balance sheet — current and comparative period |
| Statement of Changes in Equity | Equity movements |
| Statement of Cash Flows | IAS 7 — direct or indirect method |
| Accounts Receivable Ageing | Outstanding customer balances by age bracket |
| Accounts Payable Ageing | Outstanding supplier balances by age bracket |
| Fixed Asset Schedule | Asset register with depreciation |
| Depreciation Schedule | Depreciation per asset per period |
| Bank Reconciliation | Cash per books vs bank statement |
| Budget vs Actual | Variance report (requires budget module) |

All reports exportable as: PDF, Excel (XLSX), CSV, and machine-readable JSON.

### 12.2 Submodule: Audit Trail Engine

**Every action in the system generates an immutable audit log entry:**

| Field | Description |
|---|---|
| `log_id` | UUID |
| `timestamp` | UTC timestamp — immutable |
| `user_id` | Who performed the action |
| `action_type` | `CREATE`, `UPDATE`, `POST`, `REVERSE`, `APPROVE`, `LOCK`, `REOPEN`, `DELETE_ATTEMPT` |
| `entity_type` | The object type affected (JournalEntry, LedgerEntry, Account, Period, etc.) |
| `entity_id` | The specific record affected |
| `before_state` | JSON snapshot of record before change |
| `after_state` | JSON snapshot of record after change |
| `ip_address` | Client IP for security tracing |
| `session_id` | User session reference |

**Rules:**
- Audit log records are NEVER deleted or updated.
- Access to audit log is read-only for all users including system administrators.
- Audit log must be exportable for external auditor review.
- Attempted deletions of posted entries are logged as `DELETE_ATTEMPT` (blocked action) rather than silently rejected.

---

## 13. Module 12 — Multi-Entity, Multi-Currency & Consolidation

### Purpose
Support enterprise use cases where multiple legal entities operate under a group structure and where transactions occur in currencies other than the functional currency.

### 13.1 Submodule: Multi-Entity Management

- Each legal entity has its own COA, general ledger, accounting periods, and financial statements.
- Users may be granted access to one or more entities.
- Intercompany transactions: when Entity A sells to Entity B, the system must record the sale in A's books and the purchase in B's books, linked by an intercompany reference.
- Intercompany elimination: on consolidation, intercompany revenues/expenses, receivables/payables, and investments are eliminated.

### 13.2 Submodule: Multi-Currency Engine

**Three currency concepts (IAS 21):**
- **Functional currency:** The currency of the primary economic environment in which the entity operates. Set per entity. Cannot be changed without significant disclosure.
- **Presentation currency:** The currency in which financial statements are presented. May differ from functional currency.
- **Foreign currency:** Any currency other than the functional currency.

**Transaction date processing:**
- Foreign currency transactions are initially recorded at the spot exchange rate on the transaction date.
- Store both: original currency amount + exchange rate + functional currency equivalent.

**Period-end revaluation:**
- Monetary items (cash, receivables, payables in foreign currencies): retranslate at closing rate.
- Non-monetary items measured at historical cost: no retranslation — keep original rate.
- Non-monetary items measured at fair value: retranslate at the rate at the date of fair value measurement.
- Exchange differences on monetary items: to P&L (IAS 21.28).
- Exchange differences on net investment in foreign operations: to OCI (IAS 21.32).

**Exchange rate table:**
- Maintain a daily exchange rate table for all currency pairs used.
- Support: spot rate, average rate (for income statement translation in consolidation), closing rate (for balance sheet translation).
- Source rates via API integration (e.g., ECB, Open Exchange Rates) or manual entry.

### 13.3 Submodule: Consolidation Engine

For group reporting:

1. **Aggregate** all subsidiary financial statements (translated to presentation currency using IAS 21 rules).
2. **Eliminate** intercompany transactions (sales, purchases, loans, dividends).
3. **Eliminate** investment in subsidiaries against subsidiary equity (goodwill or gain on bargain purchase).
4. **Calculate** non-controlling interests (NCI) if not wholly owned.
5. **Produce** consolidated Statement of Financial Position, consolidated P&L, consolidated Statement of Changes in Equity, consolidated Cash Flow Statement.

---

## 14. Module 13 — Invoicing Module

### Purpose
The Invoicing Module is the entry point of the revenue cycle. It enables the business to raise, manage, and track sales invoices, linking each invoice to a customer, the relevant revenue accounts in the COA, and the Accounts Receivable ledger. It is the trigger that initiates the downstream payment and receipting workflow. Revenue recognition follows IFRS 15.

### 14.1 Submodule: Invoice Master & Data Model

**Invoice Header:**
| Field | Type | Rules |
|---|---|---|
| `invoice_id` | UUID | System-generated, immutable |
| `invoice_number` | String | Auto-sequential per entity (e.g., `INV-2024-00001`); configurable prefix |
| `invoice_type` | Enum | `STANDARD`, `PROFORMA`, `RECURRING`, `CREDIT_NOTE` |
| `customer_id` | UUID | FK to Customer master — must exist before invoice can be created |
| `invoice_date` | Date | Date invoice is raised; determines accounting period |
| `due_date` | Date | Calculated from payment terms (e.g., Net 30 = invoice_date + 30 days) |
| `payment_terms` | Enum | `IMMEDIATE`, `NET_7`, `NET_14`, `NET_30`, `NET_60`, `CUSTOM` |
| `currency_code` | String | ISO 4217; defaults to entity functional currency |
| `exchange_rate` | DECIMAL(20,6) | Rate at invoice date if foreign currency |
| `status` | Enum | `DRAFT`, `APPROVED`, `SENT`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `VOID`, `CANCELLED` |
| `subtotal_amount` | DECIMAL(20,6) | Sum of line item net amounts |
| `tax_amount` | DECIMAL(20,6) | Total VAT/tax calculated across all lines |
| `total_amount` | DECIMAL(20,6) | `subtotal_amount + tax_amount` |
| `amount_paid` | DECIMAL(20,6) | Running total of payments received against this invoice |
| `amount_outstanding` | DECIMAL(20,6) | `total_amount - amount_paid`; auto-updated on each payment |
| `journal_entry_id` | UUID (nullable) | FK to the journal entry created when invoice is posted |
| `notes` | String (max 1000) | Optional notes visible on the invoice document |
| `created_by` | UUID | FK to user |
| `approved_by` | UUID (nullable) | FK to approver |
| `sent_at` | Timestamp (nullable) | When the invoice was delivered to the customer |

**Invoice Line Items:**
| Field | Type | Rules |
|---|---|---|
| `line_id` | UUID | System-generated |
| `invoice_id` | UUID | FK to invoice header |
| `line_number` | Integer | Sequence number for display ordering |
| `description` | String (max 500) | Description of the good or service |
| `quantity` | DECIMAL(20,6) | Must be > 0 |
| `unit_price` | DECIMAL(20,6) | Price per unit; must be ≥ 0 |
| `discount_percent` | DECIMAL(5,2) | Optional line-level discount (0–100) |
| `net_amount` | DECIMAL(20,6) | `quantity × unit_price × (1 - discount_percent/100)` |
| `tax_rate_id` | UUID (nullable) | FK to Tax Rate master (e.g., VAT 16% for Kenya) |
| `tax_amount` | DECIMAL(20,6) | `net_amount × tax_rate`; auto-calculated |
| `line_total` | DECIMAL(20,6) | `net_amount + tax_amount` |
| `revenue_account_id` | UUID | FK to COA revenue account — required for journal entry posting |
| `cost_centre_id` | UUID (nullable) | Management accounting dimension |
| `ifrs15_obligation_id` | UUID (nullable) | FK to performance obligation if IFRS 15 tracking is active |

### 14.2 Submodule: Invoice Lifecycle State Machine

```
DRAFT ──▶ APPROVED ──▶ SENT ──▶ PARTIALLY_PAID ──▶ PAID
  │          │                         ▲               
  │          └─────────────────────────┘ (direct payment on approval)
  │
  └──▶ VOID / CANCELLED (before posting only)
```

**State transition rules:**
- `DRAFT → APPROVED`: requires at least one line item; total > 0; all mandatory fields populated; approved by authorised user.
- `APPROVED → POSTED (to ledger)`: triggers automatic journal entry creation (see §14.4). Status moves to `SENT` once the invoice is dispatched to the customer.
- `SENT → PARTIALLY_PAID`: triggered when a payment is received that is less than `total_amount`.
- `PARTIALLY_PAID → PAID` or `SENT → PAID`: triggered when `amount_paid = total_amount`.
- `DRAFT / APPROVED → VOID`: permitted before posting only. After posting, a **Credit Note** must be raised instead (see §14.5).
- `SENT / PARTIALLY_PAID → OVERDUE`: system auto-flags invoices where `due_date < today` and `amount_outstanding > 0`.

**Business rule:** Once an invoice is `APPROVED` and posted to the ledger, it is immutable. No fields can be edited. Corrections require a Credit Note.

### 14.3 Submodule: Customer Master

Every invoice must be linked to a customer record. The customer master holds:

| Field | Type | Rules |
|---|---|---|
| `customer_id` | UUID | System-generated |
| `customer_name` | String | Required |
| `customer_code` | String | Auto-generated reference (e.g., `CUST-00042`) |
| `contact_phone` | String | Used for M-Pesa matching and receipt SMS delivery |
| `contact_email` | String | Used for invoice and receipt email delivery |
| `billing_address` | String | Printed on invoices |
| `tax_pin` | String (nullable) | KRA PIN or equivalent tax ID for B2B customers |
| `credit_limit` | DECIMAL(20,6) | Maximum outstanding balance allowed; system warns if exceeded |
| `payment_terms` | Enum | Default payment terms for this customer (overridable per invoice) |
| `ar_account_id` | UUID | FK to the specific AR sub-account in COA for this customer |
| `currency_code` | String | Customer's preferred invoicing currency |
| `is_active` | Boolean | Inactive customers cannot receive new invoices |

### 14.4 Submodule: Invoice Posting — Automatic Journal Entry

When an invoice transitions from `APPROVED` to `SENT` (or on approval if configured), the system automatically posts a journal entry to the general ledger. **The accountant does not manually enter this — the system generates it from the invoice data.**

**Standard journal entry for a posted invoice:**

```
DATE:    [Invoice Date]
REF:     JE-[auto] linked to INV-2024-00001
DESC:    Sales Invoice — [Customer Name] — INV-2024-00001

DEBIT    Accounts Receivable (Customer sub-ledger)    KES X,XXX.XX
CREDIT   Sales Revenue (per line item account)        KES X,XXX.XX
CREDIT   VAT Payable / Output Tax                     KES   XXX.XX
```

**Rules:**
- One journal entry per invoice.
- If an invoice has multiple line items pointing to different revenue accounts (e.g., services and goods sold), the journal entry has multiple credit lines — one per revenue account.
- The Accounts Receivable debit is always a single consolidated amount (`total_amount`) posted to the customer's AR sub-account.
- VAT/Output Tax is credited to the tax liability account from the COA.
- The journal entry `source_document_id` links back to the invoice ID.
- The journal entry cannot be reversed without first voiding the invoice via a Credit Note.

**IFRS 15 compliance check:** Before posting, the system verifies that the revenue recognition criteria are met — specifically, that the related performance obligation(s) have been satisfied. If the invoice is for services not yet rendered (e.g., advance billing), the system will credit `Deferred Revenue` instead of `Sales Revenue`, and a separate revenue recognition entry will be triggered when the service is delivered.

### 14.5 Submodule: Credit Notes

A Credit Note is the formal mechanism for reversing or partially reducing a posted invoice. It is **the only permissible correction method** after an invoice has been posted.

**Credit Note data model:** mirrors the Invoice data model with an additional field:
- `original_invoice_id` — mandatory FK to the invoice being credited.
- `credit_reason` — Enum: `CANCELLED_ORDER`, `GOODS_RETURNED`, `BILLING_ERROR`, `DISCOUNT_APPLIED`, `OTHER`.

**Journal entry generated by a Credit Note:**
```
DEBIT    Sales Revenue (per original line items)      KES X,XXX.XX
DEBIT    VAT Payable / Output Tax                     KES   XXX.XX
CREDIT   Accounts Receivable (Customer sub-ledger)    KES X,XXX.XX
```
This is the exact mirror-image of the original invoice's journal entry.

**Rules:**
- A Credit Note cannot exceed the original invoice amount.
- Partial credit notes are allowed.
- A Credit Note automatically reduces `amount_outstanding` on the original invoice.
- Credit Notes are numbered separately (e.g., `CN-2024-00001`).

### 14.6 Submodule: Tax Management (VAT/GST)

- Maintain a **Tax Rate master**: rate name, percentage, applicable account (Output Tax for sales; Input Tax for purchases), effective date range.
- Kenya default: VAT at 16% (standard rate) and 0% (zero-rated) per KRA rules.
- Tax is calculated line-by-line; the system sums line-level tax amounts for the invoice total.
- **Tax inclusive vs exclusive:** configurable per invoice; system handles back-calculation for inclusive pricing.
- **Tax report:** Monthly VAT return report — Output Tax vs Input Tax → VAT payable to KRA.

### 14.7 Submodule: Recurring Invoices

- Define a recurring invoice template: customer, line items, frequency (weekly, monthly, quarterly), start date, end date or number of occurrences.
- On the scheduled date, the system auto-generates a draft invoice from the template.
- A notification is sent to the accountant to review and approve before posting (or configure auto-approval for low-risk recurring invoices).
- Each generated invoice is a fully independent invoice record with its own number.

### 14.8 Submodule: Invoice Delivery

- **Email:** Generate a PDF invoice and email it to the customer's email address. PDF includes: invoice number, date, due date, line items, tax breakdown, total, payment instructions (including M-Pesa Paybill/Till number and reference).
- **SMS:** Send a short-form payment request via SMS to the customer's phone number: `"Invoice INV-2024-00001 for KES 5,000 due 30-Nov-2024. Pay via M-Pesa Paybill XXXXXX, Ref: INV-2024-00001."`
- **Customer portal (optional):** A web link where the customer can view and download their invoice.
- **Delivery status:** Track email open/bounce and SMS delivery confirmation.

### 14.9 Submodule: Accounts Receivable Ageing

The AR ageing report is generated directly from open invoice data:

| Customer | Current (0–30 days) | 31–60 days | 61–90 days | 91–120 days | 120+ days | Total |
|---|---|---|---|---|---|---|
| Customer A | KES 50,000 | — | KES 10,000 | — | KES 5,000 | KES 65,000 |

- Auto-flag invoices that are `OVERDUE` (past due date with outstanding balance).
- Configurable automated payment reminder emails/SMS at: 7 days before due, on due date, 7 days overdue, 30 days overdue.
- Ageing buckets are configurable per entity.

---

## 15. Module 14 — Payments Module (M-Pesa & General)

### Purpose
The Payments Module manages the intake, validation, matching, and accounting of all incoming payments from any channel — M-Pesa, bank transfer, cash, and card. For each payment received, it generates the appropriate journal entry and triggers the Receipting Module. This module is the bridge between the customer-facing revenue cycle and the financial accounting backend.

### 15.1 Submodule: Payment Channel Registry

The system supports multiple payment channels. Each channel has a corresponding Cash/Bank account in the COA:

| Channel | COA Account | Notes |
|---|---|---|
| M-Pesa (Paybill) | M-Pesa Paybill Float Account | Settled to bank periodically |
| M-Pesa (Till Number) | M-Pesa Till Float Account | Settled to bank periodically |
| Bank Transfer (EFT/RTGS) | Bank Account (per bank) | One account per bank account |
| Cash | Petty Cash / Cash at Hand | Physical cash received |
| Credit/Debit Card | Merchant Account | POS or online card payments |
| Cheque | Cheques in Transit | Cleared to bank on receipt |

Each channel is mapped to an account in the COA during system setup. This mapping determines the **Debit side** of every payment journal entry.

### 15.2 Submodule: M-Pesa Integration & Webhook Handler

**This is the real-time payment intake pipeline for M-Pesa.**

#### Step 1 — Receive the Webhook

When a customer pays via M-Pesa (STK Push initiated by your system, or direct Paybill/Till payment), Safaricom sends a webhook to your system's callback URL with:

```json
{
  "TransactionType": "Pay Bill",
  "TransID": "MP24110001",
  "TransTime": "20241101143400",
  "TransAmount": "5000.00",
  "BusinessShortCode": "123456",
  "BillRefNumber": "INV-2024-00001",
  "InvoiceNumber": "",
  "OrgAccountBalance": "50000.00",
  "ThirdPartyTransID": "",
  "MSISDN": "2547XXXXXXXX",
  "FirstName": "John",
  "LastName": "Kamau"
}
```

**Critical rules for webhook handling:**
1. **Acknowledge immediately.** Respond to Safaricom with HTTP 200 within 5 seconds. Do NOT process synchronously — queue first, respond, then process.
2. **Idempotency.** Store `TransID` in a received-transactions table. If the same `TransID` arrives again (Safaricom retries), acknowledge it and discard — do not process twice. This is a hard integrity rule.
3. **Queue for processing.** Place the raw payload on an async job queue (e.g., Redis/RabbitMQ). A background worker picks it up and runs the matching and posting pipeline.

#### Step 2 — Parse & Validate the Payload

The background worker validates:
- `TransAmount` is a positive, parseable decimal number.
- `TransTime` is a valid timestamp.
- `MSISDN` is a valid phone number.
- `BusinessShortCode` matches one of the entity's registered M-Pesa channels.
- `TransID` is not already in the `processed_payments` table (duplicate guard).

If validation fails: log the failure, send an alert to the finance team, do NOT post any entry.

#### Step 3 — Payment Matching Engine

**Priority order for matching an incoming payment:**

| Priority | Match Condition | Action |
|---|---|---|
| 1 | `BillRefNumber` exactly matches an open invoice number, AND `TransAmount = invoice.amount_outstanding` | Full match → auto-approve → post |
| 2 | `BillRefNumber` matches an open invoice number, AND `TransAmount < invoice.amount_outstanding` | Partial match → auto-approve → post partial; leave remainder outstanding |
| 3 | `BillRefNumber` matches an open invoice number, AND `TransAmount > invoice.amount_outstanding` | Overpayment → post to invoice amount, post excess to Customer Overpayment liability account, alert accountant |
| 4 | `MSISDN` matches a customer phone number with open invoices | Probable match → flag for manual confirmation before posting |
| 5 | No match found | Post to Suspense Account; alert accountant to manually match |

**Business rule:** The system must never lose a payment. Any payment that cannot be matched automatically is parked in the Suspense Account immediately. Unmatched payments in Suspense generate a daily alert to the accounting team.

### 15.3 Submodule: Payment Data Model

| Field | Type | Rules |
|---|---|---|
| `payment_id` | UUID | System-generated, immutable |
| `payment_number` | String | Auto-sequential (e.g., `PMT-2024-00001`) |
| `payment_channel` | Enum | `MPESA_PAYBILL`, `MPESA_TILL`, `BANK_TRANSFER`, `CASH`, `CARD`, `CHEQUE` |
| `external_reference` | String | Channel transaction ID (e.g., M-Pesa `TransID`); unique per channel; idempotency key |
| `payer_name` | String | Name from the payment notification |
| `payer_phone` | String | Phone number (MSISDN) |
| `payer_bank_ref` | String (nullable) | Bank reference for bank transfers |
| `payment_date` | Date | Date of the transaction (from channel notification) |
| `payment_time` | Timestamp | Full UTC timestamp of the transaction |
| `currency_code` | String | ISO 4217 |
| `amount_received` | DECIMAL(20,6) | Amount as received from the channel |
| `channel_account_id` | UUID | FK to the COA account for this payment channel |
| `status` | Enum | `QUEUED`, `MATCHING`, `MATCHED`, `UNMATCHED`, `POSTED`, `REVERSED`, `FAILED` |
| `invoice_id` | UUID (nullable) | FK to matched invoice (null if unmatched/suspense) |
| `amount_applied_to_invoice` | DECIMAL(20,6) | Amount applied to the matched invoice |
| `suspense_amount` | DECIMAL(20,6) | Amount posted to suspense (for unmatched payments) |
| `journal_entry_id` | UUID (nullable) | FK to the posted journal entry |
| `receipt_id` | UUID (nullable) | FK to the generated receipt |
| `matched_by` | UUID (nullable) | User who manually matched (null if auto-matched) |
| `matched_at` | Timestamp (nullable) | When matching was confirmed |
| `reversal_reference` | String (nullable) | M-Pesa reversal transaction ID if payment was reversed |
| `notes` | String (max 500) | Optional notes |

### 15.4 Submodule: Automatic Journal Entry Generation

Once a payment is matched and approved, the system **automatically generates and posts a journal entry**. No manual data entry by the accountant.

#### Scenario A — Full or Partial Match to Invoice

```
DATE:    [Payment Date]
REF:     JE-[auto] linked to PMT-2024-00001
DESC:    M-Pesa payment received — John Kamau — Trans: MP24110001 — INV-2024-00001

DEBIT    M-Pesa Paybill Float Account              KES 5,000.00
CREDIT   Accounts Receivable (Customer sub-ledger) KES 5,000.00
```

Invoice status is updated: `amount_paid += 5,000.00`; `amount_outstanding -= 5,000.00`.  
If `amount_outstanding = 0`, invoice status moves to `PAID`.  
If `amount_outstanding > 0`, invoice status moves to `PARTIALLY_PAID`.

#### Scenario B — Cash Sale (No Prior Invoice)

```
DEBIT    M-Pesa Paybill Float Account    KES 5,000.00
CREDIT   Sales Revenue                   KES 4,464.29  (ex-VAT)
CREDIT   VAT Payable / Output Tax        KES   535.71  (16% VAT)
```

#### Scenario C — Unmatched Payment (Suspense)

```
DEBIT    M-Pesa Paybill Float Account    KES 5,000.00
CREDIT   Suspense Account                KES 5,000.00
```

When the accountant later matches the suspense payment to an invoice:
```
DEBIT    Suspense Account                KES 5,000.00
CREDIT   Accounts Receivable            KES 5,000.00
```

#### Scenario D — Overpayment

```
DEBIT    M-Pesa Paybill Float Account              KES 5,500.00
CREDIT   Accounts Receivable (Invoice amount)      KES 5,000.00
CREDIT   Customer Overpayment Payable              KES   500.00
```

The overpayment liability is either refunded to the customer or applied to the next invoice.

**Journal entry integrity rules:**
- The journal entry is only posted **after** matching is confirmed (auto or manual).
- If journal entry posting fails for any technical reason, the payment status rolls back to `MATCHING` and an alert is triggered. The Suspense Account is never bypassed.
- All journal entries generated by this module carry `entry_type = TRANSACTION` and `source_document_id` = the payment record ID.

### 15.5 Submodule: Bank Transfer Payment Intake

For non-M-Pesa channels (bank transfers, cash, cheques):

- **Manual entry:** Accountant enters payment details into the system directly (payer, amount, date, bank reference).
- **Bank feed reconciliation:** If bank feed integration is active (Module 3.3), incoming credits on the bank statement are automatically surfaced as unmatched payments for the accountant to match to invoices.
- **Batch import:** Support CSV import of bank statement entries for bulk processing.

The matching and posting logic is identical to M-Pesa (see §15.3).

### 15.6 Submodule: Suspense Account Manager

The Suspense Account is a critical control account. It must:

- Have a dedicated COA account: `Suspense — Unmatched Payments` (Current Liability or contra-Asset subtype).
- Always have a **zero balance at period end** — every item must be matched or investigated before closing.
- Generate a **Suspense Ageing Report**: all unmatched items, amount, days in suspense, payer name, M-Pesa TransID.
- The period-end checklist (Module 9) includes: *"Suspense account cleared to zero"* as a mandatory step before closing.

### 15.7 Submodule: M-Pesa Payment Reversal Handling

Safaricom can initiate a reversal for an M-Pesa transaction (e.g., if the customer disputes or if a transaction error occurs). The system must handle this gracefully:

1. Receive the reversal webhook from Safaricom (similar structure to the original payment webhook, with a `ReversalCode`).
2. Locate the original payment by `TransID`.
3. **Reverse the original journal entry** (create a reversing entry — see Module 3, §4.5):
   ```
   DEBIT    Accounts Receivable (Customer sub-ledger)  KES 5,000.00
   CREDIT   M-Pesa Paybill Float Account               KES 5,000.00
   ```
4. Update the original payment status to `REVERSED`.
5. Update the invoice: `amount_paid -= 5,000.00`; `amount_outstanding += 5,000.00`; status reverts to `SENT` or `PARTIALLY_PAID`.
6. **Void the original receipt** (see Module 15 §16.4 — void and reissue).
7. Notify the customer that their payment was reversed and the invoice is outstanding again.

### 15.8 Submodule: Payment Approval Workflow

- **Auto-approved:** Payments below a configurable threshold (e.g., KES 50,000) with a clean auto-match are automatically approved and posted without human intervention.
- **Manual approval required:** Payments above the threshold, payments with probable (not exact) matches, and overpayments require an accountant to review and confirm before posting.
- **Approval audit:** All manual approvals are time-stamped and user-stamped in the audit trail.

---

## 16. Module 15 — Receipting Module

### Purpose
The Receipting Module generates an official, customer-facing payment receipt immediately after a payment journal entry is posted to the general ledger. The receipt is the confirmation to the customer that their payment has been received **and recorded in the books**. It is always a consequence of a posted journal entry — never a precursor to it.

**Core principle (non-negotiable):** Post first → then receipt. The journal entry is the proof of record. The receipt is the customer-facing confirmation of that proof. These two must always be in sync.

### 16.1 Submodule: Receipt Data Model

| Field | Type | Rules |
|---|---|---|
| `receipt_id` | UUID | System-generated, immutable |
| `receipt_number` | String | Auto-sequential per entity (e.g., `RCP-2024-00441`); configurable prefix |
| `payment_id` | UUID | FK to the payment record — mandatory; receipt cannot exist without a payment |
| `journal_entry_id` | UUID | FK to the posted journal entry — mandatory; receipt cannot be issued before journal entry is `POSTED` |
| `invoice_id` | UUID (nullable) | FK to the invoice settled (null for cash sales or suspense payments) |
| `customer_id` | UUID | FK to customer |
| `receipt_date` | Date | Date the journal entry was posted (= date of recognition) |
| `currency_code` | String | ISO 4217 |
| `amount_received` | DECIMAL(20,6) | Amount shown on receipt = payment amount |
| `invoice_total` | DECIMAL(20,6) | Original invoice total (for reference) |
| `amount_outstanding` | DECIMAL(20,6) | Balance remaining on invoice after this payment |
| `payment_channel` | Enum | Payment method shown on receipt |
| `external_reference` | String | M-Pesa TransID or bank reference — shown on receipt for customer traceability |
| `status` | Enum | `PENDING`, `POSTED`, `ISSUED`, `VOID` |
| `issued_at` | Timestamp (nullable) | When the receipt was delivered to the customer |
| `void_reason` | String (nullable) | Mandatory if status = `VOID` |
| `voided_by` | UUID (nullable) | User who voided |
| `voided_at` | Timestamp (nullable) | When voided |
| `replacement_receipt_id` | UUID (nullable) | FK to the replacement receipt if this one was voided and reissued |

### 16.2 Submodule: Receipt Lifecycle State Machine

```
PENDING ──▶ POSTED ──▶ ISSUED ──▶ VOID
```

| State | Meaning | Trigger |
|---|---|---|
| `PENDING` | Payment received; matching in progress; journal entry not yet posted | Payment queued or being matched |
| `POSTED` | Journal entry has been posted to the ledger; receipt is generated and ready to deliver | Payment journal entry posts successfully |
| `ISSUED` | Receipt has been delivered to the customer via SMS and/or email | Delivery confirmed |
| `VOID` | Receipt has been voided (due to payment reversal or billing error) | Payment reversal received, or accountant voids with documented reason |

**Rules:**
- A receipt transitions from `PENDING` to `POSTED` **only when** the linked `journal_entry_id` has status = `POSTED`. This is enforced at the database level.
- A receipt in `ISSUED` state cannot be edited. Corrections require a void and reissue.
- Voiding a receipt **must** simultaneously trigger (or confirm) the reversing journal entry in the Journal Entry Engine.
- A `VOID` receipt must point to a `replacement_receipt_id` if the payment was re-processed or corrected.

### 16.3 Submodule: Receipt Document Generator

The system generates a formatted PDF receipt from the posted data. The PDF receipt must include:

**Receipt Document Structure:**
```
┌────────────────────────────────────────────────────┐
│  [Business Logo]          OFFICIAL RECEIPT         │
│  [Business Name]          Receipt No: RCP-2024-00441│
│  [Business Address]       Date: 01 Nov 2024         │
│  [Business PIN/Tax No.]                             │
├────────────────────────────────────────────────────┤
│  RECEIVED FROM: John Kamau                         │
│  Phone: 2547XXXXXXXX                               │
├────────────────────────────────────────────────────┤
│  PAYMENT DETAILS                                   │
│  Payment Method: M-Pesa Paybill                    │
│  M-Pesa Reference: MP24110001                      │
│  Payment Date & Time: 01 Nov 2024, 14:34:00        │
├────────────────────────────────────────────────────┤
│  IN RESPECT OF:                                    │
│  Invoice No: INV-2024-00001                        │
│  Description: [Invoice description]                │
├────────────────────────────────────────────────────┤
│  AMOUNT RECEIVED:              KES    5,000.00     │
│  Invoice Total:                KES    5,000.00     │
│  Previous Payments:            KES        0.00     │
│  Balance Outstanding:          KES        0.00     │
│                                                    │
│  INVOICE STATUS: FULLY PAID ✓                      │
├────────────────────────────────────────────────────┤
│  Journal Entry Reference: JE-2024-00552            │
│  [System-generated QR code for verification]       │
└────────────────────────────────────────────────────┘
```

**Required fields on receipt:**
- Receipt number (auto-sequential, unique, immutable)
- Business details (name, address, tax PIN)
- Customer name and phone number
- Payment method and external reference (M-Pesa TransID / bank reference)
- Date and time of payment
- Invoice reference
- Amount received
- Outstanding balance (if partial payment)
- Invoice status after this payment
- Journal entry reference number (for audit traceability)
- QR code linking to a verifiable digital copy (optional but recommended)

### 16.4 Submodule: Void and Reissue

When a receipt must be voided:

1. **Document the reason** — system requires selection from: `PAYMENT_REVERSED`, `INCORRECT_AMOUNT`, `WRONG_CUSTOMER`, `DUPLICATE_RECEIPT`, `OTHER` (with mandatory text explanation).
2. **Confirm the reversing journal entry** — voiding a receipt without a corresponding reversing journal entry is blocked. The two must happen atomically (in the same database transaction).
3. **Mark original receipt `VOID`** — the original is never deleted. It remains in the system with status `VOID`, visible in the audit trail.
4. **Generate replacement receipt** (if applicable) — if the payment was corrected rather than fully reversed, a new receipt is generated from the corrected journal entry. The `replacement_receipt_id` field on the voided receipt points to the new one.
5. **Notify customer** — system sends a notification to the customer: *"Receipt RCP-2024-00441 has been cancelled. [Reason]. Please see replacement receipt RCP-2024-00442."*

**Rule:** Receipts are immutable once issued. Void + reissue is the only permissible correction path.

### 16.5 Submodule: Receipt Delivery Engine

Once a receipt reaches `POSTED` status, delivery is triggered automatically:

**Channel 1 — SMS:**
```
[BusinessName]: Payment received. KES 5,000 via M-Pesa (MP24110001) for INV-2024-00001. 
Receipt: RCP-2024-00441. Balance: KES 0. Invoice PAID. [link to PDF]
```
- Uses the `payer_phone` from the payment record (the MSISDN from the M-Pesa webhook).
- Delivery attempted once; failures are logged and queued for retry.

**Channel 2 — Email:**
- Sends a formatted HTML email with the PDF receipt attached.
- Uses `customer.contact_email` from the Customer Master.
- If no email on file, skip email delivery; SMS is mandatory.

**Channel 3 — In-system notification:**
- The receipt is visible in the customer portal (if applicable) and in the accounting team's dashboard.

**Delivery tracking:**
- `issued_at` timestamp is set when at least one delivery channel succeeds.
- Delivery status (sent, delivered, failed) is logged per channel.
- Failed deliveries trigger an alert to the finance team for manual follow-up.

### 16.6 Submodule: Receipt Reports

| Report | Description |
|---|---|
| Daily Receipt Summary | All receipts issued today — total collections by channel |
| Receipt Register | Full list of all receipts for a period, filterable by customer, channel, status |
| Unissued Receipts Report | Payments posted but receipt delivery failed — for follow-up |
| Voided Receipts Report | All voided receipts with reasons and replacement references |
| Customer Payment History | All receipts for a specific customer — useful for dispute resolution |
| Collections by Channel | Breakdown of payments received by M-Pesa, bank, cash, card |

---

## 17. Cross-Module Data Contracts

### Core Entity Identifiers

Every record in the system carries:
- `entity_id` — the legal entity it belongs to
- `period_id` — the accounting period it relates to
- `created_at` — UTC timestamp of creation
- `created_by` — user ID
- `modified_at` — UTC timestamp of last modification
- `modified_by` — user ID

### Complete Revenue Cycle Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                    MODULE 13 — INVOICING                            │
│  Customer places order / service delivered                          │
│  Invoice raised (DRAFT → APPROVED)                                  │
│  ↓ On approval:                                                     │
│  Auto-journal entry posted:                                         │
│    DEBIT  Accounts Receivable     KES X,XXX                        │
│    CREDIT Sales Revenue           KES X,XXX (ex-VAT)              │
│    CREDIT VAT Payable             KES   XXX                        │
│  Invoice status → SENT                                              │
│  Invoice delivered to customer (email + SMS)                        │
└─────────────────────────────┬───────────────────────────────────────┘
                              │  Customer pays
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    MODULE 14 — PAYMENTS                             │
│  M-Pesa webhook received → Acknowledge (HTTP 200) → Queue          │
│  Background worker: Validate → Match to Invoice                     │
│  Auto or manual approval                                            │
│  ↓ On approval:                                                     │
│  Auto-journal entry posted:                                         │
│    DEBIT  M-Pesa Float Account    KES X,XXX                        │
│    CREDIT Accounts Receivable     KES X,XXX                        │
│  Invoice amount_paid updated; status → PAID or PARTIALLY_PAID      │
│  Payment status → POSTED                                            │
└─────────────────────────────┬───────────────────────────────────────┘
                              │  Journal entry confirmed as POSTED
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    MODULE 15 — RECEIPTING                           │
│  Receipt auto-generated from journal entry + payment + invoice data │
│  Receipt status: PENDING → POSTED → ISSUED                          │
│  PDF generated                                                      │
│  Delivered via SMS (M-Pesa phone) + Email                           │
│  Receipt status → ISSUED                                            │
└─────────────────────────────┬───────────────────────────────────────┘
                              │  Data flows to accounting backend
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ACCOUNTING BACKEND (Modules 1–12)                │
│  General Ledger updated (AR account, M-Pesa account)               │
│  Customer subsidiary ledger updated                                 │
│  Trial Balance reflects new balances                                │
│  Financial Statements include revenue and cash position             │
│  Audit Trail logs: invoice created, payment received, receipt issued│
└─────────────────────────────────────────────────────────────────────┘
```

### Key Data Flows Between All Modules

```
[Module 13 — Invoice] ──posts──▶ [Module 3 — Journal Entry]
                                          │
                                          ▼
[Module 14 — Payment] ──posts──▶ [Module 3 — Journal Entry]
                                          │
                                          ├──▶ [Module 4 — General Ledger]
                                          │
                                          └──▶ [Module 15 — Receipt]
                                                        │
                                                        ▼
                                               Customer Delivery
                                               (SMS + Email + PDF)

[Module 4 — GL] ──▶ [Module 5 — Trial Balance] ──▶ [Module 6 — Adjusting]
                                                              │
                                                              ▼
                                             [Module 7 — Financial Statements]
                                               ├── Income Statement
                                               ├── Changes in Equity
                                               ├── Balance Sheet
                                               └── Cash Flow Statement
                                                              │
                                                              ▼
                                             [Module 8 — Closing Entries]
                                                              │
                                                              ▼
                                      [Module 9 — Period Management] → next period
```

### API Contract Requirements

Each module exposes an internal API with:
- `GET /[module]/[resource]` — retrieve records
- `POST /[module]/[resource]` — create new records
- `PUT /[module]/[resource]/{id}` — update (only where permitted)
- `DELETE /[module]/[resource]/{id}` — soft-delete / deactivate only (hard deletes are never permitted on financial records)
- `POST /[module]/[resource]/{id}/[action]` — trigger state transitions (post, approve, lock, reverse, etc.)

All APIs return standardised response envelopes:
```json
{
  "success": true,
  "data": { ... },
  "errors": [],
  "warnings": [],
  "metadata": {
    "entity_id": "...",
    "period_id": "...",
    "timestamp": "..."
  }
}
```

---

#### Module 1 — Chart of Accounts (COA)

| Endpoint | Method | Description |
|---|---|---|
| `/coa/accounts` | GET | List all accounts; filterable by `type`, `subtype`, `is_active`, `parent_account_id` |
| `/coa/accounts` | POST | Create a new account |
| `/coa/accounts/{id}` | GET | Retrieve a single account by ID |
| `/coa/accounts/{id}` | PUT | Update account name, subtype, IFRS classification, currency (code is immutable once posted against) |
| `/coa/accounts/{id}/deactivate` | POST | Deactivate an account (blocked if it has ledger history; soft-deactivate only) |
| `/coa/accounts/{id}/hierarchy` | GET | Retrieve the full parent-child hierarchy path for an account |
| `/coa/accounts/{id}/balance` | GET | Get the current running balance for an account; accepts `?as_of_date=` and `?period_id=` parameters |
| `/coa/templates` | GET | List all available COA templates (service, merchandising, manufacturing, financial services, non-profit) |
| `/coa/templates/{template_id}/apply` | POST | Apply a COA template to an entity (only permitted before any journal entry exists) |
| `/coa/import` | POST | Import a custom COA from CSV or JSON |
| `/coa/accounts/validate-code` | POST | Validate that a proposed account code conforms to the entity's numbering convention |

---

#### Module 2 — Transaction Capture & Source Documents

| Endpoint | Method | Description |
|---|---|---|
| `/source-documents` | GET | List all source documents; filterable by `type`, `status`, `date_range`, `created_by` |
| `/source-documents` | POST | Submit a new source document record (with metadata and file reference) |
| `/source-documents/{id}` | GET | Retrieve a source document and its current status |
| `/source-documents/{id}` | PUT | Update a source document in `DRAFT` or `SUBMITTED` status only |
| `/source-documents/{id}/submit` | POST | Transition document from `DRAFT` → `SUBMITTED` |
| `/source-documents/{id}/review` | POST | Mark document as `REVIEWED` (reviewer user stamped) |
| `/source-documents/{id}/approve` | POST | Approve document (`REVIEWED` → `APPROVED`); makes it eligible for journal entry creation |
| `/source-documents/{id}/archive` | POST | Archive a posted document (`POSTED` → `ARCHIVED`) |
| `/source-documents/{id}/attachments` | GET | List all file attachments for a source document |
| `/source-documents/{id}/attachments` | POST | Upload a file attachment (PDF, image, CSV); stores file reference to object storage |
| `/source-documents/{id}/attachments/{file_id}` | GET | Download a specific attachment |
| `/source-documents/{id}/attachments/{file_id}` | DELETE | Remove an attachment (only on `DRAFT` documents) |
| `/source-documents/classify` | POST | Run the four-test recognition criteria check on a proposed transaction; returns pass/fail with reasons |
| `/source-documents/recurring` | GET | List all recurring transaction templates |
| `/source-documents/recurring` | POST | Create a recurring transaction template |
| `/source-documents/recurring/{id}` | PUT | Update a recurring template |
| `/source-documents/recurring/{id}/pause` | POST | Pause a recurring template |
| `/source-documents/recurring/{id}/resume` | POST | Resume a paused recurring template |
| `/source-documents/recurring/{id}/generate` | POST | Manually trigger generation of the next instance from a recurring template |
| `/source-documents/bank-feed/import` | POST | Import bank feed entries (OFX, CSV, or API payload) for matching |
| `/source-documents/bank-feed/unmatched` | GET | List all unmatched bank feed entries pending review |
| `/source-documents/duplicates` | GET | List all flagged potential duplicate transactions for review |

---

#### Module 3 — Journal Entry Engine

| Endpoint | Method | Description |
|---|---|---|
| `/journal-entries` | GET | List journal entries; filterable by `status`, `entry_type`, `period_id`, `date_range`, `created_by`, `account_id` |
| `/journal-entries` | POST | Create a new journal entry in `DRAFT` status |
| `/journal-entries/{id}` | GET | Retrieve a journal entry with all its lines |
| `/journal-entries/{id}` | PUT | Update a journal entry in `DRAFT` status only |
| `/journal-entries/{id}/lines` | GET | List all lines for a journal entry |
| `/journal-entries/{id}/lines` | POST | Add a line to a draft journal entry |
| `/journal-entries/{id}/lines/{line_id}` | PUT | Update a specific line on a draft journal entry |
| `/journal-entries/{id}/lines/{line_id}` | DELETE | Remove a line from a draft journal entry |
| `/journal-entries/{id}/validate` | POST | Run the double-entry validator (balance check, account activity, period open, sign check, currency check) without posting; returns validation results |
| `/journal-entries/{id}/submit` | POST | Transition `DRAFT` → `PENDING_APPROVAL`; runs full validation |
| `/journal-entries/{id}/approve` | POST | Approve and post the entry (`PENDING_APPROVAL` → `POSTED`); locks all lines immutably |
| `/journal-entries/{id}/reject` | POST | Reject a pending entry back to `DRAFT` with mandatory rejection reason |
| `/journal-entries/{id}/reverse` | POST | Create a reversing entry against a `POSTED` entry; original status moves to `REVERSED` |
| `/journal-entries/{id}/audit-trail` | GET | Retrieve the full audit history for a journal entry |
| `/journal-entries/special/sales` | POST | Create a Sales Journal entry via simplified form (system constructs the double-entry automatically) |
| `/journal-entries/special/cash-receipts` | POST | Create a Cash Receipts Journal entry |
| `/journal-entries/special/purchases` | POST | Create a Purchases Journal entry |
| `/journal-entries/special/cash-disbursements` | POST | Create a Cash Disbursements Journal entry |
| `/journal-entries/special/payroll` | POST | Create a Payroll Journal entry |
| `/journal-entries/bulk-approve` | POST | Bulk approve multiple entries (within the caller's approval authority threshold) |

---

#### Module 4 — General Ledger & Posting Engine

| Endpoint | Method | Description |
|---|---|---|
| `/ledger/accounts/{account_id}/entries` | GET | Retrieve all ledger entries for an account; filterable by `period_id`, `date_range`, `entry_type` |
| `/ledger/accounts/{account_id}/balance` | GET | Get the closing balance for an account at a given `?as_of_date=` or `?period_id=` |
| `/ledger/accounts/{account_id}/t-account` | GET | Retrieve T-account view (debits / credits / running balance) for a date range |
| `/ledger/entries/{ledger_entry_id}` | GET | Retrieve a single ledger entry with its linked journal entry |
| `/ledger/post/{journal_entry_id}` | POST | Trigger posting of an approved journal entry to the ledger (called internally on approval; also available for system replay) |
| `/ledger/reconcile/control-accounts` | POST | Run subsidiary-ledger-to-control-account reconciliation check across all control accounts; returns any discrepancies |
| `/ledger/reconcile/control-accounts/{account_id}` | GET | Get reconciliation status for a specific control account (AR, AP, Inventory, Fixed Assets, Loans) |
| `/ledger/subsidiary/customers` | GET | List all customer subsidiary ledger balances (AR sub-ledger) |
| `/ledger/subsidiary/customers/{customer_id}` | GET | Get the full subsidiary ledger history for a specific customer |
| `/ledger/subsidiary/suppliers` | GET | List all supplier subsidiary ledger balances (AP sub-ledger) |
| `/ledger/subsidiary/suppliers/{supplier_id}` | GET | Get the full subsidiary ledger history for a specific supplier |
| `/ledger/subsidiary/inventory` | GET | List inventory subsidiary ledger by SKU |
| `/ledger/subsidiary/inventory/{sku_id}` | GET | Get ledger history for a specific inventory item |
| `/ledger/assets` | GET | List all fixed asset register records |
| `/ledger/assets` | POST | Create a new fixed asset record |
| `/ledger/assets/{asset_id}` | GET | Retrieve a fixed asset record with depreciation schedule |
| `/ledger/assets/{asset_id}` | PUT | Update asset details (residual value, useful life) — only before first depreciation is posted |
| `/ledger/assets/{asset_id}/depreciation-schedule` | GET | Retrieve the full depreciation schedule for an asset |
| `/ledger/assets/{asset_id}/depreciate` | POST | Post the depreciation adjusting entry for a specific asset for the current period |
| `/ledger/assets/{asset_id}/impair` | POST | Record an impairment loss on an asset (IAS 36); posts the impairment journal entry |
| `/ledger/assets/{asset_id}/dispose` | POST | Record disposal of an asset; calculates and posts gain/loss on disposal |
| `/ledger/bank-reconciliation` | GET | Retrieve the current bank reconciliation for a specified bank account and period |
| `/ledger/bank-reconciliation/{account_id}` | POST | Submit a completed bank reconciliation for a period |

---

#### Module 5 — Trial Balance Engine

| Endpoint | Method | Description |
|---|---|---|
| `/trial-balance` | GET | Generate a trial balance; requires `?entity_id=`, `?period_id=` (or `?as_of_date=`), and `?type=UNADJUSTED\|ADJUSTED\|POST_CLOSING` |
| `/trial-balance/validate` | POST | Run the trial balance balance check and return pass/fail with discrepancy details |
| `/trial-balance/discrepancy-report` | GET | Run automated diagnostics to identify the source of any trial balance imbalance for a given period |
| `/trial-balance/working-worksheet` | GET | Retrieve the working trial balance worksheet (unadjusted columns + adjustments columns + adjusted columns + IS/BS allocation columns) |
| `/trial-balance/working-worksheet` | PUT | Update the adjustments columns on the working worksheet (proposed adjusting entries entered inline) |
| `/trial-balance/export` | POST | Export the trial balance as PDF, XLSX, or CSV; requires `?type=` and `?period_id=` |
| `/trial-balance/confirm-adjusted` | POST | Lock the adjusted trial balance for a period, preventing further regular journal entries and enabling financial statement generation |

---

#### Module 6 — Adjusting Entries Engine

| Endpoint | Method | Description |
|---|---|---|
| `/adjusting-entries` | GET | List all adjusting entries for a period; filterable by `type` (`DEFERRED_EXPENSE`, `DEFERRED_REVENUE`, `ACCRUED_EXPENSE`, `ACCRUED_REVENUE`, `DEPRECIATION`, `PROVISION`, `IMPAIRMENT`, `FX_REVALUATION`, `FAIR_VALUE`, `LEASE`) |
| `/adjusting-entries` | POST | Manually create an adjusting entry |
| `/adjusting-entries/{id}` | GET | Retrieve a specific adjusting entry |
| `/adjusting-entries/{id}/approve` | POST | Approve and post an adjusting entry |
| `/adjusting-entries/templates` | GET | List all recurring adjusting entry templates |
| `/adjusting-entries/templates` | POST | Create a recurring adjusting entry template (e.g., monthly depreciation, prepaid amortisation) |
| `/adjusting-entries/templates/{id}` | PUT | Update a recurring template |
| `/adjusting-entries/templates/{id}/delete` | POST | Soft-delete a recurring template |
| `/adjusting-entries/propose` | POST | Auto-generate all proposed adjusting entries for a period from active templates; places them in a review queue |
| `/adjusting-entries/propose/prepaid/{schedule_id}` | POST | Auto-calculate and propose the expiry adjusting entry for a specific prepaid schedule |
| `/adjusting-entries/propose/deferred-revenue/{schedule_id}` | POST | Auto-propose the earned-portion entry for a specific deferred revenue schedule |
| `/adjusting-entries/propose/depreciation` | POST | Auto-propose depreciation entries for all active fixed assets for the period |
| `/adjusting-entries/propose/fx-revaluation` | POST | Auto-propose foreign currency revaluation entries (IAS 21) using the closing exchange rate for the period |
| `/adjusting-entries/propose/lease/{lease_id}` | POST | Auto-propose IFRS 16 interest accrual and ROU depreciation entries for a specific lease |
| `/adjusting-entries/propose/fair-value` | POST | Auto-propose fair value adjustments (IFRS 9 / IFRS 13) for financial instruments marked at FVTPL or FVOCI |
| `/adjusting-entries/review-queue` | GET | List all proposed adjusting entries awaiting accountant review before posting |
| `/adjusting-entries/review-queue/{id}/approve` | POST | Approve a proposed adjusting entry from the review queue; posts it to the ledger |
| `/adjusting-entries/review-queue/{id}/reject` | POST | Reject a proposed entry; requires a reason; entry is removed from the queue |
| `/adjusting-entries/review-queue/approve-all` | POST | Bulk-approve all entries in the review queue (requires Accountant role minimum) |
| `/adjusting-entries/duplicate-check/{period_id}` | GET | Check whether any adjusting entry type has already been posted for the period (prevents duplicates) |

---

#### Module 7 — Financial Statement Generator

| Endpoint | Method | Description |
|---|---|---|
| `/financial-statements/income-statement` | GET | Generate the Statement of Profit or Loss and OCI for a period; requires `?period_id=`; accepts `?format=SINGLE_STATEMENT\|TWO_STATEMENT` and `?expense_classification=FUNCTION\|NATURE` |
| `/financial-statements/income-statement/confirm` | POST | Sign off the income statement for the period; required before retained earnings statement can be generated |
| `/financial-statements/changes-in-equity` | GET | Generate the Statement of Changes in Equity for a period |
| `/financial-statements/changes-in-equity/confirm` | POST | Sign off the statement of changes in equity |
| `/financial-statements/balance-sheet` | GET | Generate the Statement of Financial Position as of period-end; validates `Assets = Liabilities + Equity` |
| `/financial-statements/balance-sheet/confirm` | POST | Sign off the balance sheet; required before closing entries can be generated |
| `/financial-statements/cash-flow` | GET | Generate the Statement of Cash Flows; accepts `?method=DIRECT\|INDIRECT` |
| `/financial-statements/cash-flow/confirm` | POST | Sign off the cash flow statement |
| `/financial-statements/package` | GET | Retrieve all four statements as a single consolidated package for a period |
| `/financial-statements/notes` | GET | Retrieve all generated IFRS disclosure notes for a period |
| `/financial-statements/notes/{note_id}` | GET | Retrieve a specific disclosure note (e.g., PPE reconciliation, lease note, revenue disaggregation) |
| `/financial-statements/notes/{note_id}` | PUT | Populate variable fields in a note template |
| `/financial-statements/export` | POST | Export the complete financial statements package (PDF, XLSX, or JSON); requires all four statements to be confirmed |
| `/financial-statements/comparative` | GET | Generate a comparative financial statements view showing current period and prior period side by side |

---

#### Module 8 — Closing Entries Engine

| Endpoint | Method | Description |
|---|---|---|
| `/closing-entries/generate` | POST | Auto-generate all four closing entry steps for a period (revenue → Income Summary; expenses → Income Summary; Income Summary → Retained Earnings; dividends → Retained Earnings); returns the proposed entries for review |
| `/closing-entries` | GET | List all generated closing entries for a period |
| `/closing-entries/{id}` | GET | Retrieve a specific closing entry |
| `/closing-entries/approve-all` | POST | Approve and post all closing entries for the period atomically; blocked if any financial statement is not yet confirmed |
| `/closing-entries/verify` | POST | Run post-close verification: confirm all temporary accounts have zero balances and Retained Earnings matches the Statement of Changes in Equity |
| `/closing-entries/post-closing-trial-balance` | GET | Generate the post-closing trial balance (permanent accounts only) |

---

#### Module 9 — Period Management & Accounting Cycle Controller

| Endpoint | Method | Description |
|---|---|---|
| `/periods` | GET | List all accounting periods for an entity; filterable by `fiscal_year`, `status` |
| `/periods` | POST | Create a new accounting period (typically done during fiscal year setup) |
| `/periods/{id}` | GET | Retrieve a period and its current cycle state |
| `/periods/{id}/open` | POST | Open a `FUTURE` period for transaction entry |
| `/periods/{id}/transition-to-adjusting` | POST | Lock regular transactions; allow adjusting entries only (requires unadjusted trial balance to be balanced) |
| `/periods/{id}/transition-to-closing` | POST | Lock adjusting entries; allow closing entries only (requires adjusted trial balance confirmed) |
| `/periods/{id}/close` | POST | Fully close the period after closing entries are posted and verified; transfers closing balances as opening balances for the next period |
| `/periods/{id}/reopen` | POST | Reopen a closed period with dual-approval and mandatory documented reason |
| `/periods/{id}/checklist` | GET | Retrieve the period-end checklist with item statuses and assigned users |
| `/periods/{id}/checklist/{item_id}` | PUT | Update a checklist item status (pending → complete) with user stamp and timestamp |
| `/periods/{id}/cycle-state` | GET | Get the current step in the nine-step accounting cycle for a period, including which steps are complete and which are blocked |
| `/periods/{id}/opening-balances` | GET | Retrieve the opening balance journal entry for a period (generated from prior period closing balances) |
| `/periods/{id}/opening-balances` | POST | Manually post opening balances (for initial system setup or first period) |
| `/fiscal-years` | GET | List all fiscal years defined for an entity |
| `/fiscal-years` | POST | Define a new fiscal year and auto-generate monthly periods |

---

#### Module 10 — IFRS Compliance & Disclosures Engine

| Endpoint | Method | Description |
|---|---|---|
| `/ifrs/compliance-check` | POST | Run the full IFRS compliance check for a period; returns a list of passes, warnings, and failures per standard |
| `/ifrs/compliance-check/{period_id}` | GET | Retrieve the stored compliance check results for a period |
| `/ifrs/departure-log` | GET | List all documented IFRS departures |
| `/ifrs/departure-log` | POST | Document an approved IFRS departure with standard reference, justification, and approver |
| `/ifrs/departure-log/{id}` | GET | Retrieve a specific departure record |
| `/ifrs/revenue-contracts` | GET | List all revenue contracts being tracked under IFRS 15 |
| `/ifrs/revenue-contracts` | POST | Create a new revenue contract for IFRS 15 tracking (5-step model) |
| `/ifrs/revenue-contracts/{id}` | GET | Retrieve a contract with its performance obligations and recognition schedule |
| `/ifrs/revenue-contracts/{id}/obligations` | GET | List all performance obligations for a contract |
| `/ifrs/revenue-contracts/{id}/obligations` | POST | Add a performance obligation to a contract |
| `/ifrs/revenue-contracts/{id}/obligations/{ob_id}/satisfy` | POST | Mark a performance obligation as satisfied; triggers revenue recognition journal entry |
| `/ifrs/revenue-contracts/{id}/obligations/{ob_id}/progress` | PUT | Update over-time recognition progress (input or output method) and post the proportional revenue entry |
| `/ifrs/leases` | GET | List all lease contracts being tracked under IFRS 16 |
| `/ifrs/leases` | POST | Create a new lease contract; system calculates PV of lease payments and generates initial ROU asset and lease liability entries |
| `/ifrs/leases/{id}` | GET | Retrieve lease details with amortisation schedule |
| `/ifrs/leases/{id}/amortisation-schedule` | GET | Full period-by-period lease amortisation schedule (interest, principal, ROU depreciation) |
| `/ifrs/leases/{id}/modify` | POST | Record a lease modification; system recalculates the liability and ROU asset |
| `/ifrs/leases/{id}/terminate` | POST | Record early termination of a lease |
| `/ifrs/provisions` | GET | List all provisions (IAS 37) |
| `/ifrs/provisions` | POST | Create a new provision record |
| `/ifrs/provisions/{id}` | PUT | Update a provision estimate |
| `/ifrs/provisions/{id}/reverse` | POST | Reverse or release a provision |
| `/ifrs/impairment` | GET | List all impairment assessments |
| `/ifrs/impairment` | POST | Record an impairment assessment result (IAS 36); posts impairment entry if carrying amount exceeds recoverable amount |
| `/ifrs/exchange-rates` | GET | List exchange rates for a date range and currency pair |
| `/ifrs/exchange-rates` | POST | Manually enter exchange rates |
| `/ifrs/exchange-rates/sync` | POST | Trigger synchronisation of exchange rates from the configured external rate source (e.g., ECB API) |

---

#### Module 11 — Reporting & Audit Trail

| Endpoint | Method | Description |
|---|---|---|
| `/reports/trial-balance` | GET | Trial balance report (unadjusted, adjusted, or post-closing); accepts `?period_id=`, `?type=` |
| `/reports/general-ledger` | GET | General ledger detail report; filterable by `account_id`, `date_range`, `entry_type` |
| `/reports/journal-listing` | GET | Chronological list of all journal entries for a period; filterable by `status`, `entry_type` |
| `/reports/income-statement` | GET | Formatted income statement report with comparative period |
| `/reports/balance-sheet` | GET | Formatted balance sheet report with comparative period |
| `/reports/changes-in-equity` | GET | Formatted statement of changes in equity |
| `/reports/cash-flow` | GET | Cash flow statement; accepts `?method=DIRECT\|INDIRECT` |
| `/reports/ar-ageing` | GET | Accounts receivable ageing report; accepts `?as_of_date=`, `?customer_id=` |
| `/reports/ap-ageing` | GET | Accounts payable ageing report |
| `/reports/fixed-asset-schedule` | GET | Fixed asset register with accumulated depreciation and carrying amounts |
| `/reports/depreciation-schedule` | GET | Period depreciation report by asset |
| `/reports/bank-reconciliation` | GET | Bank reconciliation report for a specified account and period |
| `/reports/vat-return` | GET | Monthly VAT return report (Output Tax vs Input Tax → VAT payable) |
| `/reports/budget-vs-actual` | GET | Budget versus actual variance report (requires budget module integration) |
| `/reports/suspense-ageing` | GET | Ageing of all items in the Suspense — Unmatched Payments account |
| `/reports/export` | POST | Export any named report as PDF, XLSX, CSV, or JSON; body specifies `report_name` and parameters |
| `/audit-trail` | GET | Query the audit trail; filterable by `user_id`, `entity_type`, `entity_id`, `action_type`, `date_range` |
| `/audit-trail/{log_id}` | GET | Retrieve a specific audit log entry with before/after state snapshots |
| `/audit-trail/export` | POST | Export audit trail records as PDF or CSV for external auditor review |

---

#### Module 12 — Multi-Entity, Multi-Currency & Consolidation

| Endpoint | Method | Description |
|---|---|---|
| `/entities` | GET | List all legal entities the authenticated user has access to |
| `/entities` | POST | Create a new legal entity |
| `/entities/{id}` | GET | Retrieve entity settings (functional currency, fiscal year, COA, etc.) |
| `/entities/{id}` | PUT | Update entity settings |
| `/entities/{id}/functional-currency` | GET | Retrieve the functional currency setting for an entity |
| `/entities/{id}/functional-currency` | PUT | Update the functional currency (restricted; triggers disclosure requirement) |
| `/entities/intercompany/transactions` | GET | List all intercompany transactions across entities |
| `/entities/intercompany/transactions` | POST | Record an intercompany transaction; system posts mirrored entries in both entity ledgers |
| `/entities/intercompany/match` | POST | Match intercompany receivables against payables across entity pairs |
| `/entities/intercompany/unmatched` | GET | List unmatched intercompany balances flagged for resolution |
| `/consolidation/run` | POST | Trigger consolidation for a group and period; aggregates subsidiary statements and runs elimination entries |
| `/consolidation/{run_id}` | GET | Retrieve consolidation results including elimination journal entries |
| `/consolidation/eliminations` | GET | List all intercompany elimination entries for a consolidation run |
| `/consolidation/eliminations` | POST | Manually add or adjust an elimination entry |
| `/consolidation/nci` | GET | Get the non-controlling interest calculation for the consolidation |
| `/consolidation/statements` | GET | Retrieve the consolidated financial statements package for a group period |
| `/consolidation/export` | POST | Export the consolidated financial statements |
| `/currencies` | GET | List all currencies configured in the system |
| `/currencies/exchange-rates` | GET | Retrieve exchange rates; filterable by `currency_pair`, `date_range`, `rate_type` (`SPOT`, `AVERAGE`, `CLOSING`) |
| `/currencies/exchange-rates` | POST | Manually post exchange rates |
| `/currencies/exchange-rates/sync` | POST | Pull latest rates from the configured external rate provider |
| `/currencies/revalue` | POST | Run period-end monetary item revaluation (IAS 21) across all foreign currency balances; proposes the FX gain/loss journal entry |

---

#### Modules 13–15 — Invoicing, Payments & Receipting

| Endpoint | Method | Description |
|---|---|---|
| `/invoicing/invoices` | GET | List invoices; filterable by `status`, `customer_id`, `date_range`, `overdue=true` |
| `/invoicing/invoices` | POST | Create a new invoice in `DRAFT` status |
| `/invoicing/invoices/{id}` | GET | Retrieve an invoice with all line items |
| `/invoicing/invoices/{id}` | PUT | Update a `DRAFT` invoice |
| `/invoicing/invoices/{id}/approve` | POST | Approve and post the invoice to the ledger; triggers automatic journal entry |
| `/invoicing/invoices/{id}/send` | POST | Deliver the invoice to the customer (email + SMS) |
| `/invoicing/invoices/{id}/void` | POST | Void an invoice (only before posting; after posting a Credit Note is required) |
| `/invoicing/invoices/{id}/credit-note` | POST | Raise a Credit Note against a posted invoice |
| `/invoicing/invoices/{id}/payment-history` | GET | List all payments applied to an invoice |
| `/invoicing/invoices/{id}/reminders` | POST | Manually trigger a payment reminder to the customer |
| `/invoicing/invoices/overdue` | GET | List all invoices past their due date with outstanding balances |
| `/invoicing/customers` | GET | List all customers |
| `/invoicing/customers` | POST | Create a new customer |
| `/invoicing/customers/{id}` | GET | Retrieve a customer record |
| `/invoicing/customers/{id}` | PUT | Update customer details |
| `/invoicing/customers/{id}/deactivate` | POST | Deactivate a customer (blocked from new invoices) |
| `/invoicing/customers/{id}/statement` | GET | Generate a customer account statement (all invoices, payments, and outstanding balance) |
| `/invoicing/customers/{id}/credit-limit/check` | GET | Check whether a new invoice would breach the customer's credit limit |
| `/invoicing/tax-rates` | GET | List all tax rate master records |
| `/invoicing/tax-rates` | POST | Create a new tax rate |
| `/invoicing/tax-rates/{id}` | PUT | Update a tax rate (effective date range controls applicability) |
| `/invoicing/recurring-templates` | GET | List all recurring invoice templates |
| `/invoicing/recurring-templates` | POST | Create a recurring invoice template |
| `/invoicing/recurring-templates/{id}` | PUT | Update a recurring template |
| `/invoicing/recurring-templates/{id}/pause` | POST | Pause a recurring template |
| `/invoicing/recurring-templates/{id}/generate` | POST | Manually trigger generation of the next draft invoice from a template |
| `/invoicing/ar-ageing` | GET | Accounts receivable ageing report with configurable buckets |
| `/payments/receive` | POST | Manual payment entry for non-M-Pesa channels (bank transfer, cash, cheque, card) |
| `/payments/mpesa/callback` | POST | M-Pesa webhook endpoint (Safaricom-facing); acknowledges immediately and queues for async processing |
| `/payments/mpesa/reversal-callback` | POST | M-Pesa reversal webhook endpoint; triggers reversing journal entry and receipt void |
| `/payments/mpesa/stk-push` | POST | Initiate an M-Pesa STK Push payment request to a customer |
| `/payments` | GET | List all payments; filterable by `status`, `channel`, `date_range`, `invoice_id`, `customer_id` |
| `/payments/{id}` | GET | Retrieve a payment record |
| `/payments/{id}/match` | POST | Manually match an unmatched payment to an invoice |
| `/payments/{id}/approve` | POST | Approve a matched payment for journal entry posting |
| `/payments/{id}/reverse` | POST | Reverse a posted payment (creates reversing journal entry; voids receipt; restores invoice balance) |
| `/payments/suspense` | GET | List all payments currently in the Suspense account awaiting matching |
| `/payments/suspense/ageing` | GET | Ageing report of suspense items |
| `/payments/bulk-import` | POST | Batch import bank statement entries (CSV) for matching |
| `/receipts` | GET | List all receipts; filterable by `status`, `customer_id`, `date_range`, `payment_channel` |
| `/receipts/{id}` | GET | Retrieve a receipt |
| `/receipts/{id}/pdf` | GET | Download the receipt as a PDF |
| `/receipts/{id}/resend` | POST | Resend a receipt to the customer (SMS and/or email) |
| `/receipts/{id}/void` | POST | Void a receipt with documented reason; atomically confirms the reversing journal entry |
| `/receipts/reports/daily-summary` | GET | Daily receipt summary — total collections by channel for a date |
| `/receipts/reports/register` | GET | Full receipt register for a period; filterable by customer, channel, status |
| `/receipts/reports/unissued` | GET | Payments posted but receipt delivery failed — for follow-up |
| `/receipts/reports/voided` | GET | All voided receipts with reasons and replacement references |
| `/receipts/reports/customer-history/{customer_id}` | GET | Full payment receipt history for a specific customer |
| `/receipts/reports/collections-by-channel` | GET | Collections breakdown by payment channel for a period |

---

## 18. System-Wide Rules & Constraints

### Immutability Rules
1. Posted journal entries cannot be edited or deleted. Correction = reversing entry.
2. Closed periods cannot receive new entries without elevated approval.
3. Audit log entries are never modified or deleted.
4. Account IDs are permanent — accounts cannot be deleted if they have ledger history.

### Mathematical Precision Rules
1. All monetary amounts stored as `DECIMAL(20,6)` — never `FLOAT` or `DOUBLE`.
2. Rounding: use banker's rounding (round half to even) for consistency.
3. Currency conversion: apply exchange rate at transaction date; store both original and converted amounts.
4. Trial balance tolerance: zero — `SUM(debits) - SUM(credits)` must equal exactly 0.00.

### Security & Access Control
1. Role-based access control (RBAC) — minimum roles: Data Entry, Accountant, Senior Accountant, Controller/CFO, Auditor (read-only), System Admin.
2. Accountants can create and approve their own entries up to a configurable threshold; above threshold requires second approval.
3. Auditors have read-only access to all records including audit trail.
4. System admins cannot post journal entries — separation of duties.

### Data Retention
1. All financial records retained for minimum 7 years (common statutory requirement; configurable by jurisdiction).
2. Audit log retained indefinitely or for the statutory period, whichever is longer.
3. Soft-delete only — no hard deletes on any financial record.

### Performance Requirements
1. Trial balance generation: < 5 seconds for entities with up to 10 years of history.
2. Journal entry posting: < 500ms per entry.
3. Financial statement generation: < 30 seconds including all four statements.
4. Concurrent users: system must support at least 50 concurrent users per entity without degradation.

---

*End of Financial Accounting System Design Prompt*

*Document version: 3.0 | Aligned to: IFRS as issued by the IASB | Based on: Stickney et al. — Financial Accounting (13th ed.); Jonick — Principles of Financial Accounting | Updated: Invoicing, Payments (M-Pesa), and Receipting modules added (v2.0); Complete FA API endpoints added for all Modules 1–12 (v3.0)*

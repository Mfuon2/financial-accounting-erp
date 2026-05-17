# Contributing to QeSuite FA

Thank you for your interest in contributing to QeSuite FA — the open-source IFRS financial accounting system. Whether you are fixing a bug, proposing a new feature, improving documentation, or adding tests, every contribution matters.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Before You Start](#before-you-start)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [How to Contribute](#how-to-contribute)
- [Coding Standards](#coding-standards)
- [Accounting Standards](#accounting-standards)
- [Commit Message Format](#commit-message-format)
- [Pull Request Process](#pull-request-process)
- [Issue Labels](#issue-labels)
- [Recognition](#recognition)

---

## Code of Conduct

All contributors must follow our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold a respectful and inclusive environment.

---

## Before You Start

1. **Search existing issues and PRs first** — your idea may already be in progress.
2. **Open an issue before large changes** — discuss the approach before investing significant time. This prevents wasted effort on PRs we cannot merge.
3. **Small, focused PRs are preferred** — one concern per pull request. A PR that fixes a bug and refactors unrelated code will be asked to split.
4. **Accounting correctness is non-negotiable** — any change that touches the general ledger, journal entry logic, AP/AR lifecycle, or financial statements must be accompanied by an explanation of the accounting treatment and a reference to the relevant IAS/IFRS standard.

---

## Development Setup

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Java (JDK) | 17 |
| Kotlin | 1.9 |
| Maven | 3.9 |
| Node.js | 20 |
| npm | 10 |
| PostgreSQL | 15 |
| Docker | 24 |

### 1. Fork and Clone

```bash
git clone https://github.com/YOUR_USERNAME/qesuite-fa.git
cd qesuite-fa
git remote add upstream https://github.com/your-org/qesuite-fa.git
```

### 2. Database

```bash
docker-compose up -d postgres
```

Or manually create a PostgreSQL database named `qesuite` with a user `qesuite`.

### 3. Backend

```bash
cd fa-backend
cp src/main/resources/application.example.properties \
   src/main/resources/application.properties
# Edit application.properties with your DB credentials and JWT secret
./mvnw spring-boot:run
```

Flyway will run all migrations automatically on startup.

API available at: `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 4. Frontend

```bash
cd fa-frontend
cp .env.example .env
# VITE_API_BASE_URL=http://localhost:8080
npm install
npm run dev
```

UI available at: `http://localhost:5173`

---

## Project Structure

```
qesuite-fa/
├── fa-backend/              Spring Boot API (Kotlin)
│   └── src/main/kotlin/
│       └── com/qesuite/accounting/
│           ├── assets/      Fixed assets, depreciation (IAS 16)
│           ├── ap/          Accounting periods
│           ├── coa/         Chart of accounts
│           ├── fx/          Multi-currency (IAS 21)
│           ├── journal/     General ledger, journal entries
│           ├── party/       Customers, suppliers
│           ├── payables/    Accounts payable, bills
│           ├── revenue/     Invoices, AR
│           └── shared/      Audit, security, exceptions, code gen
│
├── fa-frontend/             Vue 3 SPA (Vite)
│   └── src/
│       ├── api/             API client modules (one file per domain)
│       ├── components/      Reusable UI primitives and layouts
│       ├── composables/     Vue composables (useAuth, useAppMode, etc.)
│       ├── data/            Demo/static data for development mode
│       ├── utils/           Formatting helpers
│       └── views/           Route-level page components
│
├── README.md
├── LICENSE
├── CODE_OF_CONDUCT.md
├── SECURITY.md
└── CONTRIBUTING.md          (you are here)
```

---

## How to Contribute

### Reporting Bugs

1. Check if the bug is already reported in [Issues](https://github.com/your-org/qesuite-fa/issues)
2. Open a new issue using the **Bug Report** template
3. Include: expected behavior, actual behavior, steps to reproduce, environment details
4. For accounting bugs: include the journal entries or account balances that are incorrect and what the correct treatment should be according to IAS/IFRS

### Suggesting Features

1. Open a new issue using the **Feature Request** template
2. Describe the use case, not just the solution
3. For accounting features: reference the applicable standard (e.g., "IAS 37 — Provisions, Contingent Liabilities")
4. Discuss before opening a PR — design alignment is important for financial software

### Good First Issues

Look for issues tagged [`good first issue`](https://github.com/your-org/qesuite-fa/issues?q=label%3A%22good+first+issue%22) — these are well-scoped, self-contained tasks suitable for first-time contributors.

---

## Coding Standards

### Backend (Kotlin / Spring Boot)

- Follow the [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `@Transactional` at the service layer, never at the controller layer
- All public service methods must have a single, clearly named `Command` or `Request` parameter DTO
- Exceptions: use the typed exceptions in `shared/exceptions/` — never throw raw `RuntimeException`
- Audit logging: use `@Auditable` on service methods that mutate financial data
- No raw SQL in service code — use Spring Data JPA repositories
- Every new DB table must have a corresponding Flyway migration in `V{N}__description.sql`
- Field-level validation belongs on the request DTO via Jakarta Bean Validation, not inside service logic

```kotlin
// Good
@Service
class AssetMasterService(...) {
    @Transactional
    fun createAsset(command: CreateAssetCommand): FixedAsset {
        // validate → resolve → persist
    }
}

// Bad — validation inside service, raw exception
fun createAsset(assetCode: String, cost: BigDecimal): FixedAsset {
    if (assetCode.isBlank()) throw RuntimeException("bad input")
}
```

### Frontend (Vue 3 / Vite)

- Use `<script setup>` Composition API — no Options API
- One component per file; file name matches the component name (PascalCase)
- API calls live in `src/api/*.js` — never `fetch()` directly from a component
- All dropdown/select elements use `<SearchableSelect>` — no native `<select>`
- Use `useAuth()` for `entityId` — never hardcode or pass entity IDs as props from parent routes
- Loading states: use the `loading` prop on `<Button>` for async actions
- Demo mode: every API call must have an `isDemo.value ? demoData : realApiCall` branch
- No inline styles for colors — use CSS custom properties (`var(--accent)`, `var(--danger)`, etc.)
- Format all amounts with `fmt()` and dates with `fmtDate()` from `@/utils/format.js`

```js
// Good
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')
const res = await assetsApi.list(entityId.value)

// Bad — hardcoded entity, direct fetch, no demo branch
const res = await fetch('/api/v1/assets?entityId=abc-123')
```

### General

- No commented-out code in PRs
- No `console.log` left in frontend code
- No `TODO` comments without a linked GitHub issue number
- Tests are required for new service-layer logic in the backend

---

## Accounting Standards

This is financial software. Changes that affect the general ledger must be held to a higher standard:

### Rules

1. **Every journal entry must balance** — total debits must equal total credits. The service layer must enforce this before calling `journalService.postEntryAsSystem()`.
2. **Use the correct normal balance** — assets and expenses are debit-normal; liabilities, equity, and income are credit-normal. Contra-asset accounts (e.g., Accumulated Depreciation) are credit-normal — do not read their `currentBalance` for arithmetic without understanding the sign convention.
3. **Reference the standard** — every accounting behavior must cite an IAS/IFRS clause in the service-layer Javadoc. e.g., `// §16.3 — IAS 16 Asset Disposal`
4. **Immutability of posted entries** — posted journal entries must never be modified. Corrections are made by reversal, not by update.
5. **Period integrity** — transactions must be posted to an OPEN accounting period. Posting to a CLOSED period must be rejected with `PERIOD_CLOSED`.

### Accounting Treatments Reference

| Event | Debit | Credit | Standard |
|---|---|---|---|
| Purchase an asset | Asset at Cost | Cash / AP | IAS 16 |
| Monthly depreciation | Depreciation Expense | Accumulated Depreciation | IAS 16 |
| Asset disposal (gain) | Proceeds + Accum. Dep | Asset at Cost + Gain | IAS 16 |
| Record vendor bill | Expense accounts | Accounts Payable | IAS 37 / general |
| Pay vendor bill | Accounts Payable | Cash / Bank | General |
| Raise sales invoice | Accounts Receivable | Revenue | IFRS 15 |
| Collect AR | Cash / Bank | Accounts Receivable | General |
| FX gain on settlement | Cash | AR / AP + FX Gain | IAS 21 |

---

## Commit Message Format

We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

```
<type>(<scope>): <short description>

[optional body — explain WHY, not what]

[optional footer — closes #issue, breaking changes]
```

### Types

| Type | When to use |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `accounting` | Accounting logic correction (treated as high-priority fix) |
| `docs` | Documentation only |
| `style` | Formatting, no logic change |
| `refactor` | Refactoring, no behavior change |
| `test` | Adding or updating tests |
| `chore` | Build, CI, dependencies |
| `security` | Security-related change |

### Examples

```bash
feat(assets): add double-declining balance depreciation method

Implements IAS 16 §62 DDB method. Rate = 2 / usefulLifeMonths applied
to the opening book value each period. Caps at remaining depreciable
amount to prevent over-depreciation.

Closes #42

---

fix(payables): use per-asset accumulated depreciation for disposal

Previously read accumDepAccount.currentBalance which overstated the
cleared amount when multiple assets share one contra-asset account.
Fixed to use asset.accumulatedDepreciation (per-asset stored field).

Closes #87

---

accounting(ap): cap debit note amount at outstanding balance

IAS 37 prohibits reducing AP below zero. Debit note validation now
enforces amount <= outstandingAmount before posting the reversal entry.
```

---

## Pull Request Process

1. **Branch from `main`** — use a descriptive branch name:
   ```
   feat/ias-36-impairment
   fix/ddb-depreciation-overcalculation
   docs/contributing-guide
   ```

2. **Keep your branch up to date:**
   ```bash
   git fetch upstream
   git rebase upstream/main
   ```

3. **Run checks before opening the PR:**
   ```bash
   # Backend
   cd fa-backend && ./mvnw verify

   # Frontend
   cd fa-frontend && npm run build
   ```

4. **Fill in the PR template completely** — PRs without a description of the change and its accounting rationale (where applicable) will be returned for revision.

5. **One approval required** — at least one maintainer must review and approve.

6. **Squash and merge** — we keep a clean linear history on `main`.

### PR Checklist

- [ ] Branch is up to date with `main`
- [ ] Backend tests pass (`./mvnw verify`)
- [ ] Frontend builds without errors (`npm run build`)
- [ ] New service methods have accounting rationale documented
- [ ] Journal entries in new code are balanced (DR = CR)
- [ ] Demo data updated if a new API shape was introduced
- [ ] No `console.log`, commented-out code, or TODOs without issue references

---

## Issue Labels

| Label | Meaning |
|---|---|
| `good first issue` | Self-contained, suitable for new contributors |
| `accounting` | Requires accounting domain knowledge |
| `bug` | Something is broken |
| `enhancement` | New feature or improvement |
| `documentation` | Docs-only change |
| `security` | Security-related issue |
| `breaking change` | Changes existing API or behavior |
| `needs discussion` | Approach not yet decided |
| `help wanted` | Maintainers welcome community input |

---

## Recognition

All contributors are credited in:

- The **GitHub Contributors** graph
- **Release notes** — meaningful contributions are called out by name
- The project **README** — top contributors listed

We believe in recognizing the humans behind the commits.

---

Thank you for helping build a better open-source financial accounting system for everyone.  
*The QeSuite FA Maintainers*

# QeSuite — Open-Source IFRS Financial Accounting System

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Status: Active](https://img.shields.io/badge/Status-Active-success.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](https://vuejs.org)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF.svg)](https://kotlinlang.org)
[![PRs Welcome](https://img.shields.io/badge/PRs-Welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Contributions Welcome](https://img.shields.io/badge/Contributions-Welcome-brightgreen.svg)](CONTRIBUTING.md)

> **The open-source IFRS-compliant double-entry accounting system built for developers and finance teams alike.**

**QeSuite FA** is a production-ready, self-hosted financial accounting platform engineered to the exacting standards of *International Financial Reporting Standards (IFRS)* and *Generally Accepted Accounting Principles (GAAP)*. It delivers a complete general ledger engine, a rich AP/AR lifecycle, multi-currency FX processing, and a high-density terminal-grade UI — all in a single open-source repository.

---

## Table of Contents

- [Why QeSuite FA?](#-why-qesuite-fa)
- [Feature Set](#-feature-set)
- [Architecture](#-architecture)
- [Tech Stack](#-tech-stack)
- [Quick Start](#-quick-start)
- [Screenshots](#-screenshots)
- [IFRS Compliance](#-ifrs-compliance)
- [API Reference](#-api-reference)
- [Contributing](#-contributing)
- [Security](#-security)
- [License](#-license)
- [Disclaimer](#-disclaimer)

---

## 🚀 Why QeSuite FA?

Traditional ERPs — SAP, Oracle, Sage — are expensive, slow to deploy, and notoriously hard to customize. Cloud-only alternatives like QuickBooks Online or Xero lock your financial data in proprietary silos. **QeSuite FA breaks that mold.**

| Feature | QeSuite FA | QuickBooks | Xero | SAP |
|---|:---:|:---:|:---:|:---:|
| Open Source | ✅ | ❌ | ❌ | ❌ |
| Self-Hosted | ✅ | ❌ | ❌ | ✅ |
| IFRS Compliant | ✅ | Partial | Partial | ✅ |
| Double-Entry Engine | ✅ | ✅ | ✅ | ✅ |
| API-First | ✅ | Limited | Limited | Limited |
| Developer Friendly | ✅ | ❌ | ❌ | ❌ |
| Free | ✅ | ❌ | ❌ | ❌ |

Whether you are a **startup** building a billing engine, a **developer** integrating financials into a SaaS platform, or a **finance team** demanding audit-grade accuracy — QeSuite FA is designed for you.

---

## ✨ Feature Set

### Core Accounting Engine
- **Immutable Double-Entry General Ledger** — mathematically balanced, forensically auditable
- **9-Step Accounting Cycle** — from source documents through post-closing trial balance
- **Chart of Accounts (CoA)** — fully hierarchical, IFRS-categorized with account subtypes
- **Journal Entries** — draft → submit → approve → post workflow with full reversal support
- **Period Management** — OPEN → ADJUSTING → CLOSED lifecycle with period-end controls
- **Trial Balance** — unadjusted and adjusted, always balanced

### Accounts Payable (AP)
- **Vendor Bill Management** — full lifecycle: Draft → Approved → Partially Paid → Paid → Void
- **Debit Notes (Purchase Credit Notes)** — reduces AP balance with proportional expense reversal
- **Single Payment Recording** — with cash account selection and payment method tracking
- **Batch Payment Runs** — consolidated journal entry for multiple vendor payments
- **AP Aging Report** — bucketed by current, 1-30, 31-60, 61-90, and 90+ days overdue
- **Duplicate Bill Detection** — warns on same supplier/date/amount within tolerance

### Accounts Receivable (AR)
- **Customer Invoice Lifecycle** — from draft through payment collection
- **Credit Notes** — full and partial, with GL reversal
- **AR Aging Report** — multi-bucket aging with customer drill-down
- **Automatic Payment Terms** — due date auto-calculated from supplier/customer NET-N terms

### Fixed Assets (IAS 16)
- **Asset Register** — acquisition cost, salvage value, useful life, category, assignment
- **Depreciation Methods** — Straight-Line (SL) and Double-Declining Balance (DDB)
- **Automated Monthly Depreciation Runs** — batch-processes all active assets; caps at depreciable amount
- **Asset Disposal** — posts DR Proceeds / DR Accum Dep / CR Cost / CR/DR Gain-Loss on disposal
- **Depreciation Schedule** — forward-looking projection for up to N months
- **Status Tracking** — ACTIVE → FULLY_DEPRECIATED → DISPOSED lifecycle

### Multi-Currency & FX (IAS 21)
- **Functional Currency** — entity-level base currency for consolidation
- **Transaction Currency** — any currency per transaction with exchange rate capture
- **FX Gain/Loss Realization** — automated journal entries on settlement differences
- **Currency Repository** — manage supported currencies with ISO codes

### Reporting & Compliance
- **Financial Statements** — Income Statement, Balance Sheet, Statement of Cash Flows
- **Forensic Audit Trail** — INSERT-only log of every CREATE/UPDATE/DELETE/POST/APPROVE event
- **Role-Based Access Control** — DATA_ENTRY → ACCOUNTANT → SENIOR_ACCOUNTANT → CONTROLLER_CFO → AUDITOR → SYSTEM_ADMIN
- **Export** — structured data export for external reporting tools
- **Tax Management** — tax code registration, tax-inclusive line items, input VAT tracking

### Developer Experience
- **REST API** — OpenAPI 3.0 / Swagger UI documented
- **JWT Authentication** — access + refresh token rotation, MFA support
- **Code Generation** — configurable entity number formats (prefix, year-scope, padding, custom patterns)
- **Webhook-ready architecture** — extend with your own integrations
- **Docker Compose** — one-command local environment

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        QeSuite FA                               │
│                                                                 │
│  ┌──────────────────────┐    ┌───────────────────────────────┐  │
│  │   fa-frontend        │    │   fa-backend                  │  │
│  │                      │    │                               │  │
│  │  Vue 3 + Vite        │◄──►│  Kotlin + Spring Boot 3       │  │
│  │  Composition API     │    │  Spring Security (JWT)        │  │
│  │  High-density UI     │    │  Spring Data JPA              │  │
│  │  SearchableSelect    │    │  PostgreSQL                   │  │
│  │  Real-time KPIs      │    │  Flyway Migrations            │  │
│  │                      │    │  OpenAPI / Swagger            │  │
│  └──────────────────────┘    └───────────────────────────────┘  │
│                                       │                         │
│                              ┌────────▼────────┐               │
│                              │   PostgreSQL     │               │
│                              │   (Primary DB)   │               │
│                              └─────────────────┘               │
└─────────────────────────────────────────────────────────────────┘
```

### Module Structure (Backend)

```
com.qesuite.accounting
├── assets          → Fixed assets, depreciation (IAS 16)
├── ap              → Accounting periods (period-end cycle)
├── coa             → Chart of accounts, account hierarchy
├── fx              → Currencies, exchange rates (IAS 21)
├── journal         → Journal entries, general ledger
├── party           → Customers, suppliers
├── payables        → Accounts payable, bills, payments
├── payments        → Payment methods, bank accounts
├── revenue         → Invoices, AR lifecycle
└── shared
    ├── audit       → Forensic audit log (§11)
    ├── codegen     → Entity number generation
    ├── exceptions  → Typed business rule exceptions
    └── security    → JWT, RBAC, MFA
```

---

## 🛠 Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Frontend Framework | Vue.js | 3.5 |
| Build Tool | Vite | 6.x |
| Backend Language | Kotlin | 1.9 |
| Backend Framework | Spring Boot | 3.3 |
| Security | Spring Security + JWT | 6.x |
| Persistence | Spring Data JPA + Hibernate | 6.x |
| Database | PostgreSQL | 15+ |
| Migrations | Flyway | — |
| API Docs | OpenAPI 3 / Swagger UI | — |
| Containerization | Docker + Docker Compose | — |

---

## ⚡ Quick Start

### Prerequisites

- Java 17+
- Node.js 20+
- Docker & Docker Compose
- PostgreSQL 15+ (or use the provided Docker Compose)

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/qesuite-fa.git
cd qesuite-fa
```

### 2. Start the Database

```bash
docker-compose up -d postgres
```

### 3. Start the Backend

```bash
cd fa-backend
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.  
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 4. Start the Frontend

```bash
cd fa-frontend
npm install
npm run dev
```

The application will be available at `http://localhost:5173`.

### 5. Environment Configuration

Copy the example environment files:

```bash
cp fa-backend/src/main/resources/application.example.properties \
   fa-backend/src/main/resources/application.properties

cp fa-frontend/.env.example fa-frontend/.env
```

Key environment variables:

| Variable | Description | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/qesuite` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `qesuite` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | — |
| `JWT_SECRET` | HS256 signing secret (32+ chars) | — |
| `JWT_EXPIRY_SECONDS` | Access token TTL | `900` |
| `VITE_API_BASE_URL` | Backend base URL for frontend | `http://localhost:8080` |

### 6. Full Stack with Docker Compose

```bash
docker-compose up --build
```

This starts PostgreSQL, the Spring Boot API, and the Vue frontend behind Nginx.

---

## 📸 Screenshots

> High-density, terminal-grade UI optimized for fast data entry and low eye fatigue.

| Dashboard | Vendor Bills | AP Aging |
|---|---|---|
| KPI cards, sparklines, approvals queue, audit trail | Full AP lifecycle with payment runs | Bucketed aging with supplier drill-down |

| Fixed Assets | Journal Entries | Chart of Accounts |
|---|---|---|
| Asset register, depreciation schedule | Draft → Approve → Post workflow | Hierarchical CoA with IFRS categories |

---

## 📋 IFRS Compliance

QeSuite FA is designed to support compliance with the following international standards:

| Standard | Coverage |
|---|---|
| **IAS 1** | Presentation of Financial Statements — Balance Sheet, P&L, Cash Flow |
| **IAS 7** | Statement of Cash Flows |
| **IAS 16** | Property, Plant & Equipment — acquisition, depreciation (SL/DDB), disposal |
| **IAS 21** | Effects of Changes in Foreign Exchange Rates — multi-currency, FX gain/loss |
| **IAS 36** | Impairment of Assets (framework ready) |
| **IFRS 9** | Financial Instruments (classification framework) |
| **IFRS 15** | Revenue Recognition — invoice lifecycle, deferred revenue |

> **Important:** QeSuite FA provides a technical framework designed to support IFRS compliance. Actual compliance depends on correct configuration, data entry, and professional accounting oversight. See the [Disclaimer](#-disclaimer) section.

---

## 📡 API Reference

The REST API is fully documented via OpenAPI 3.0. Once the backend is running, visit:

```
http://localhost:8080/swagger-ui.html
```

### Key Endpoints

```
POST   /api/v1/auth/login              → Authenticate and receive JWT tokens
GET    /api/v1/coa/accounts            → List chart of accounts
POST   /api/v1/journals                → Create journal entry
POST   /api/v1/journals/{id}/approve   → Approve and post to GL
POST   /api/v1/assets                  → Create fixed asset
POST   /api/v1/assets/batch-depreciate → Run depreciation for a period
POST   /api/v1/bills                   → Create vendor bill
POST   /api/v1/bills/{id}/approve      → Approve bill and post AP journal
POST   /api/v1/bills/payment-run       → Batch vendor payment run
GET    /api/v1/bills/ageing            → AP aging report
POST   /api/v1/invoices                → Create customer invoice
GET    /api/v1/audit-logs              → Forensic audit trail
```

A full Postman collection is included in the repository:
`fa-backend/QeSuite IFRS Financial Accounting API.postman_collection.json`

---

## 🤝 Contributing

We welcome contributions from accountants, developers, and finance technologists alike. See [CONTRIBUTING.md](CONTRIBUTING.md) for:

- Development environment setup
- Code style guidelines
- How to submit pull requests
- How to report bugs and propose features

**First time contributing?** Look for issues tagged [`good first issue`](https://github.com/your-org/qesuite-fa/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).

---

## 🔐 Security

For responsible disclosure of security vulnerabilities, please read our [Security Policy](SECURITY.md).

**Do not** file public GitHub Issues for security vulnerabilities.

---

## 📜 License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

You are free to use, modify, and distribute this software under the terms of the GPL-3.0. Any derivative work must also be distributed under the same license. See [LICENSE](LICENSE) for the full license text.

---

## ⚠️ Disclaimer

QeSuite FA is open-source software provided **"as is"**, without warranty of any kind. See the full [disclaimer and limitation of liability](#limitation-of-liability) below, and the [LICENSE](LICENSE) file.

### Intended Use
This software is a **technical accounting framework** and is not a substitute for professional accounting, legal, tax, or financial advice. Organizations using this software bear sole responsibility for:

- Ensuring that the system is correctly configured for their jurisdiction and applicable accounting standards
- Verifying the accuracy of all financial data entered into the system
- Engaging qualified accounting professionals to review financial statements produced by the system
- Compliance with local tax laws, reporting requirements, and audit obligations

### Limitation of Liability
**The authors, contributors, and maintainers of QeSuite FA shall not be liable for any direct, indirect, incidental, special, exemplary, or consequential damages** (including but not limited to loss of profit, loss of data, business interruption, regulatory fines, or any other financial loss) arising in any way from the use of this software, even if advised of the possibility of such damages.

**Use of this software in production financial systems is entirely at your own risk.**

---

## 🌟 Star History

If QeSuite FA helps your team, **please give us a ⭐ star** — it helps others discover the project.

---

## 📬 Contact & Community

- **Issues & Feature Requests:** [GitHub Issues](https://github.com/your-org/qesuite-fa/issues)
- **Discussions:** [GitHub Discussions](https://github.com/your-org/qesuite-fa/discussions)
- **Security:** See [SECURITY.md](SECURITY.md)

---

*Built with ❤️ for the open-source finance community.*  
*QeSuite FA — IFRS accounting software that respects your data and your developers.*

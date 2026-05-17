# Security Policy

## Supported Versions

We actively maintain security patches for the following versions of QeSuite FA:

| Version | Supported |
|---|---|
| `main` (latest) | ✅ Actively maintained |
| Tagged releases (last 2) | ✅ Security patches backported |
| Older releases | ❌ Not supported |

We strongly recommend always running the latest tagged release in production.

---

## Reporting a Vulnerability

**Please do NOT report security vulnerabilities through public GitHub Issues, Discussions, or Pull Requests.**

Public disclosure before a patch is available puts all users of QeSuite FA at risk. We take security seriously and will act swiftly on verified reports.

### How to Report

Send a detailed report by **encrypted email** to:

```
security@qesuite.io
```

*(Update this address to your actual security contact before publishing.)*

### What to Include

Please provide as much of the following as possible to help us triage quickly:

- **Type of vulnerability** — e.g., SQL injection, authentication bypass, privilege escalation, data exposure, SSRF, broken access control
- **Affected component** — frontend, backend, API endpoint, authentication layer, audit trail
- **Attack vector** — local, network, authenticated/unauthenticated
- **Steps to reproduce** — detailed, reproducible steps
- **Proof of concept** — code snippet, request/response pair, or screenshot (if applicable)
- **Potential impact** — what financial data or operations could be affected
- **Suggested fix** — if you have one (optional but appreciated)

### What to Expect

| Timeline | Action |
|---|---|
| Within **48 hours** | Acknowledgement of your report |
| Within **7 days** | Initial assessment and severity classification |
| Within **30 days** | Patch developed and reviewed (for high/critical issues) |
| Within **90 days** | Full public disclosure via GitHub Security Advisory |

We follow a **coordinated disclosure** model. We will notify you before any public disclosure and credit you (by name or alias, your choice) in the release notes and security advisory unless you request anonymity.

---

## Security Architecture

Understanding QeSuite FA's security design helps identify the right attack surface:

### Authentication
- **JWT-based** — access tokens in memory only (never persisted to localStorage or cookies)
- **Refresh tokens** — stored in `sessionStorage` only (tab-scoped, cleared on browser close)
- **Refresh token rotation** — a new refresh token is issued on every `/refresh` call
- **Silent refresh** — concurrent 401s share a single refresh promise to prevent token races
- **MFA support** — built into the authentication flow

### Authorization
- **Role-Based Access Control (RBAC)** enforced at the Spring Security layer via `@PreAuthorize`
- Roles: `DATA_ENTRY`, `ACCOUNTANT`, `SENIOR_ACCOUNTANT`, `CONTROLLER_CFO`, `AUDITOR`, `SYSTEM_ADMIN`
- Entity-scoped data isolation — all queries are filtered by `entityId`

### Data Integrity
- **Immutable audit log** — INSERT-only `audit_logs` table records every critical financial event with user, action, resource type, and before/after payloads
- **Double-entry constraint** — journal entries are validated for debit/credit balance before posting
- **Optimistic locking** — JPA versioning prevents concurrent modification conflicts

### API Security
- All endpoints require valid JWT except `/api/v1/auth/**`
- Input validation via Jakarta Bean Validation (`@Valid`) on all request bodies
- Structured error responses — no internal stack traces exposed to clients
- CORS configured per deployment environment

### Known Non-Scope Areas
The following are **out of scope** for security reports unless they enable critical financial data access:
- Rate limiting (planned, not yet implemented)
- Brute-force protection on login (planned)
- CSRF (mitigated by JWT-only auth, no cookie-based sessions)

---

## Responsible Disclosure Policy

We are committed to:

1. Responding to all security reports within 48 hours
2. Working with reporters transparently and in good faith
3. Not pursuing legal action against researchers who report vulnerabilities in good faith and do not exploit them
4. Providing credit to reporters (with their consent) in our public advisories
5. Coordinating the timing of any public disclosure with the reporter

We ask reporters to:

1. Give us reasonable time to investigate and patch before any public disclosure
2. Not access, modify, or delete financial data beyond what is necessary to demonstrate the vulnerability
3. Not perform denial-of-service attacks or disrupt service availability
4. Not engage in social engineering of project contributors or users

---

## Security Updates & Advisories

Security advisories are published via:

- **GitHub Security Advisories** — [https://github.com/your-org/qesuite-fa/security/advisories](https://github.com/your-org/qesuite-fa/security/advisories)
- **Release Notes** — tagged releases include a security changelog section

To be notified of security releases, watch this repository and select **"Security alerts"** in your GitHub watch settings.

---

## Dependency Vulnerability Management

We use automated dependency scanning:

- **Dependabot** — enabled for both Maven (backend) and npm (frontend) dependency updates
- **GitHub Actions** — CI pipeline includes dependency vulnerability checks
- **Spring Boot version** — kept on the latest Spring Boot 3.x release train to benefit from security patches

If you discover a vulnerability in one of our dependencies (not in our own code), please still report it to us — we may need to apply a patch before the upstream dependency releases one.

---

*Thank you for helping keep QeSuite FA and its users safe.*

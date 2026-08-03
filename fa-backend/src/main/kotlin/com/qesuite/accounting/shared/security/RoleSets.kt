package com.qesuite.accounting.shared.security

/**
 * §13, §18 — Named `@PreAuthorize` SpEL expressions, derived from (not invented independently of)
 * the role-gating conventions already established across this codebase's correctly-gated
 * controllers. Introduced as part of the segregation-of-duties fast-follow to the IDOR sweep:
 * Financial Systems Architect's full audit found 13 controllers with **zero** role-based access
 * control (any authenticated user of any role, including `DATA_ENTRY`, could close periods,
 * dispose assets, approve invoices, create tax codes, etc.).
 *
 * Using named constants (rather than hand-rolling the SpEL string on every endpoint) is a
 * deliberate choice: [com.qesuite.accounting.payments.controller.PaymentController] and
 * [com.qesuite.accounting.receipts.controller.ReceiptController] both contain a real,
 * pre-existing bug from hand-rolling this string — `hasAnyRole('...','CFO','...')` instead of
 * `'CONTROLLER_CFO'` (the actual [UserRole] enum value), meaning CONTROLLER_CFO users are
 * currently silently denied on every gated endpoint in those two files. Flagged for the backlog,
 * not fixed here (out of scope — those controllers already have a gate, just a broken one).
 *
 * Category → precedent this was matched to (endpoint : file):
 * - [BROAD_READ] — operational read, everyone including DATA_ENTRY needs it to do their job
 *   (pick an account, see which period is open, check a customer balance) and AUDITOR needs it
 *   for oversight. Matches `JournalController.getAll`/`.getById` and `BillController.listBills`/
 *   `.getBill`.
 * - [PREPARER] — draft-level create/update/submit a document. DATA_ENTRY-includable, AUDITOR
 *   excluded (auditors observe, they don't prepare). Matches `JournalController.create`/
 *   `.update`/`.submit` and `BillController.createBill`/`.updateBill`.
 * - [ACCOUNTING_READ] — a formal report or a read of a downstream artifact of a controlled
 *   action (trial balance, AP ageing, payment-run list, fixed-asset register). Excludes
 *   DATA_ENTRY, includes AUDITOR. Matches `TrialBalanceController`, `FinancialStatementController`,
 *   `ComplianceController.checkIas1Compliance`, `BillController.getApAgeing`/`.listPaymentRuns`,
 *   and `LedgerController`'s read endpoints (`getEntriesByAccount`, `getAssetSchedule`, etc.).
 * - [ACCOUNTING_OP] — a financial-consequence action that requires accounting judgment but is
 *   not itself the final sign-off (debit/credit note issuance, recording a payment, a
 *   preview/dry-run of a period-end action). Excludes DATA_ENTRY and AUDITOR. Matches
 *   `BillController.createDebitNote`/`.recordPayment` and `ClosingController.previewClosing`.
 * - [APPROVER] — segregation-of-duties: sign-off on something a preparer already created, or an
 *   irreversible GL-posting/disposal action. Excludes DATA_ENTRY **and** ACCOUNTANT. Matches
 *   `JournalController.approve`/`.reject`/`.reverse`, `BillController.approveBill`/`.voidBill`/
 *   `.processPaymentRun`, and `LedgerController.postDepreciation`.
 * - [ADMIN_CONFIG] — foundational, entity-wide configuration whose misuse corrupts financial
 *   statements or the fiscal calendar itself (COA structure, tax code/rate definitions,
 *   functional currency, period close/reopen). Matches `OrganizationController.updateOrganization`/
 *   `.updateMyOrganization` and, critically, `ClosingController.runClosing`/`.reopenPeriod` — the
 *   exact same period-closing functionality that `PeriodController.transitionPeriod` and
 *   `AccountingCycleController` can also reach, so those must be gated at the SAME bar or they'd
 *   silently reopen the bypass ClosingController already closes.
 * - [ADMIN_CONFIG_READ] — read access to entity/admin-level configuration. Matches
 *   `OrganizationController.findById` (`hasAnyRole('SYSTEM_ADMIN','AUDITOR','CONTROLLER_CFO')`).
 *
 * No `allowAuditor`-style broadening beyond what's listed: every existing `@PreAuthorize`
 * combining AUDITOR with SYSTEM_ADMIN in this codebase also combines it with the standard
 * per-entity operational roles — AUDITOR is an entity-scoped compliance/oversight role here,
 * not a platform-wide or approval-tier one.
 */
object RoleSets {
    const val BROAD_READ =
        "hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')"

    const val PREPARER =
        "hasAnyRole('DATA_ENTRY','ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')"

    const val ACCOUNTING_READ =
        "hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN')"

    const val ACCOUNTING_OP =
        "hasAnyRole('ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')"

    const val APPROVER =
        "hasAnyRole('SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN')"

    const val ADMIN_CONFIG =
        "hasAnyRole('CONTROLLER_CFO','SYSTEM_ADMIN')"

    const val ADMIN_CONFIG_READ =
        "hasAnyRole('SYSTEM_ADMIN','AUDITOR','CONTROLLER_CFO')"
}

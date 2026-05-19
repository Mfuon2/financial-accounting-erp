# QeSuite IFRS — Comprehensive Bug Report V2
**Tester:** Claude (FA Specialist / Bug Hunter) | **Date:** 2026-05-19 | **Mode:** Full Bug Hunter
**Stack:** Kotlin Spring Boot · Vue 3 · PostgreSQL 15 · Nginx  
**Org:** TEsting Org | PVT-20240001 | FY 2026 → FY 2025 | KES  
**Format:** Expected vs Actual · Module · Severity

---

## 🔴 CRITICAL BUGS

### BUG-24 | Periods Module | 🔴 Critical
**Module:** Setup → Periods → Generate Fiscal Year  
**Expected:** Default year should be the next or previous logical fiscal year (e.g. 2025 or 2027 when current is FY2026)  
**Actual:** Default year field shows **2032** — 6 years ahead, completely illogical  
**Impact:** Users likely to create incorrect fiscal years by accident  
**Fix:** Default to `currentFiscalYear - 1` or `currentFiscalYear + 1` based on context

---

### BUG-25 | Periods Module | 🔴 Critical
**Module:** Setup → Periods → Activate  
**Expected:** Only ONE accounting period should be OPEN at any time (fundamental accounting control)  
**Actual:** System allows unlimited concurrent OPEN periods — after generating FY2025 and activating Feb 2025, THREE periods were simultaneously OPEN: Jan 2025, Feb 2025, May 2026  
**Impact:** Users can post transactions to any OPEN period, making period boundaries meaningless and financial reports unreliable  
**Fix:** Enforce single-open-period rule. When activating a new period, auto-close or warn that another period is already OPEN. Or implement a "working period" concept separate from OPEN status.

---

### BUG-27 | Periods Module | 🔴 Critical
**Module:** Setup → Periods → Generate Fiscal Year  
**Expected:** Generating a prior-year (FY2025) should NOT affect the user's current working context (FY2026)  
**Actual:** Generating FY2025 while working in FY2026 automatically switches the entire app context to FY2025. All data views, period indicators, and new transactions now default to FY2025  
**Impact:** User loses their FY2026 working context without warning. Data for FY2026 becomes harder to access.  
**Fix:** Show a warning before generating. Do not auto-switch fiscal year context. Add explicit "Switch fiscal year" UI control.

---

### BUG-34 | Navigation | 🔴 Critical
**Module:** Global — Fiscal Year Switcher  
**Expected:** Users should be able to manually switch between fiscal years (FY2025 ↔ FY2026) via a clear UI control  
**Actual:** No fiscal year switcher exists in the UI. The active fiscal year is determined by the period with highest status priority. Once FY2025 was generated and became active, there is NO way to switch back to FY2026 through the UI.  
**Impact:** Users who accidentally generate a historical fiscal year become stuck in that context  
**Fix:** Add a fiscal year dropdown in the org selector (sidebar) that lists all generated fiscal years and allows switching

---

### BUG-29 | Journal Entries | 🔴 Critical
**Module:** Ledger → Journal Entries → New Journal  
**Expected:** When user types a custom date (e.g. 01/01/2025) in the DATE field for a historical period, the JE should be saved with that date  
**Actual:** The custom date does not persist. All journal entries are saved with today's date (19 May 2026) regardless of what date was entered. JEs posted to JANUARY 2025 period show DATE = 19 May 2026  
**Impact:** Historical journal entries have wrong dates. Comparative reports, period-specific queries, and audit trails all show incorrect dates. The Comparative TB is empty because JEs dated 19 May 2026 don't appear in historical date ranges.  
**Fix:** Properly bind and persist the DATE field. Validate that the date falls within the selected period's date range. In `JournalEntries.vue`, trace the `date` model binding and ensure it's included in the API request payload.

---

### BUG-33 | Comparative TB | 🔴 Critical  
**Module:** Reports → Comparative TB  
**Expected:** Date input fields accept YYYY-MM-DD format and produce valid API calls  
**Actual:** Typing dates into the Comparative TB date fields produces corrupted year values. E.g., typing "31052025" produces `compareAsOfDate=52025-12-31` (year 52025). The backend returns HTTP 500.  
**Impact:** Comparative TB is completely unusable via keyboard input. Users must use the date picker calendar icon.  
**Fix:** Use `<input type="date">` HTML5 native date picker consistently. Ensure value model is `YYYY-MM-DD`. Add frontend validation before making API calls. Backend should return HTTP 400 with message "Invalid date format" not HTTP 500.

---

## 🟡 MEDIUM BUGS

### BUG-28 | Journal Entries | 🟡 Medium
**Module:** Ledger → Journal Entries  
**Expected:** Journal entry reference numbers should reset per fiscal year (JE-2025-0001 for FY2025 entries)  
**Actual:** Reference numbers are sequential across ALL fiscal years. A journal entry in JANUARY 2025 gets reference `JE-2026-0012`  
**Impact:** Confusing audit trail. Reference numbers don't indicate which year the entry belongs to  
**Fix:** Implement year-scoped JE numbering. Backend should use `entityId + fiscalYear` as the sequence namespace.

---

### BUG-30 | Periods Module | 🟡 Medium
**Module:** Setup → Periods  
**Expected:** When a period is closed, CLOSED BY (user name) and CLOSED AT (timestamp) should be recorded and displayed in the periods table  
**Actual:** Both columns show "—" after period close. The audit data is either not captured or not displayed  
**Impact:** No audit trail for who closed which period and when — critical for SOX/audit compliance  
**Fix:** Backend: capture `closedByUserId` and `closedAt` on period status transitions. Frontend: display in periods table.

---

### BUG-31 | Period-End Tasks | 🟡 Medium
**Module:** Period-End → Period-End Tasks → Validate cycle  
**Expected:** "Validate cycle" button should check prerequisites and show what's blocking completion, or show a helpful "Period not ready to close" message  
**Actual:** Clicking "Validate cycle" on an OPEN period triggers error: "Request failed: Period must be in CLOSING state to move to CLOSED"  
**Impact:** Confusing for users — the error reveals internal state machine logic rather than guiding the user  
**Fix:** "Validate cycle" should inspect the current period state and provide a checklist of what needs to be done first (e.g. "Period must be in ADJUSTING state before running the cycle"). Alternatively, rename the button to "Run close cycle" and only enable it when prerequisites are met.

---

### BUG-32 | Comparative TB | 🟡 Medium
**Module:** Reports → Comparative TB  
**Expected:** Comparative TB should show balances for accounts that have had transactions in the selected date range  
**Actual:** Comparative TB shows all "—" (empty) for FY2025 accounts because BUG-29 caused all FY2025 JEs to be dated as 19 May 2026. The report can't find any transactions within the Jan 2025 date range.  
**Root Cause:** Caused by BUG-29 (date not persisting in JEs). Fix BUG-29 first.  
**Secondary Issue:** Even with correct dates, the Comparative TB date input is broken (BUG-33)

---

### BUG-35 | Source Documents | 🟡 Medium
**Module:** Ledger → Source Documents  
**Expected:** Toast message: "Document submitted."  
**Actual:** Toast message: **"Document submitd."** (missing 'te')  
**Impact:** Unprofessional appearance in a financial system  
**Fix:** In `SourceDocumentService.kt` or the frontend toast message, fix the typo: "submitd" → "submitted"

---

### BUG-36 | Users Module | 🟡 Medium
**Module:** Setup → Users  
**Expected:** "Active members" count should reflect users who are currently active/enabled. The logged-in SYSTEM_ADMIN should show STATUS: ACTIVE  
**Actual:** Header shows "0 active members" yet 2 users are listed. Both users (including the currently-logged-in Leonard Mfuon SYSTEM_ADMIN) show STATUS: **INACTIVE**  
**Impact:** User management is misleading — impossible to distinguish who is actually active  
**Fix:** The "active" status should track whether the user account is ENABLED/DISABLED (not session-based). The logged-in user's account should clearly show ACTIVE status.

---

### BUG-37 | Sub-Ledgers | 🟡 Medium
**Module:** Reports → Sub-Ledgers  
**Expected:** Sub-Ledgers (Customer AR, Supplier AP) should show all outstanding balances for the entity regardless of which fiscal year is currently active  
**Actual:** When app context is FY2025, the Sub-Ledgers show 0 customers and 0 suppliers even though customers, invoices and bills exist (created in FY2026 context)  
**Impact:** Sub-ledgers become completely empty when user accidentally switches to a historical fiscal year  
**Fix:** Sub-Ledgers should query at the entity level, not the fiscal-year level. Outstanding balances are entity-wide, not period-specific.

---

### BUG-38 | Sub-Ledgers | 🟡 Medium
**Module:** Reports → Sub-Ledgers → Fixed Assets tab  
**Expected:** Fixed Assets sub-ledger should show the Dell Latitude 5540 Laptop (FA0001) created earlier  
**Actual:** Shows "0 assets" AND returns HTTP 400 Bad Request error  
**Impact:** Fixed Assets sub-ledger is broken — users cannot see asset register from this report  
**Fix:** Investigate the backend endpoint for Fixed Assets sub-ledger. The HTTP 400 suggests a validation error in the request parameters. Likely the entityId or periodId being sent doesn't match the expected format when in FY2025 context.

---

## 🟢 LOW / DESIGN ISSUES

### BUG-COA-INDENT | Chart of Accounts | 🟢 Design Note (NOT A BUG)
**Module:** Ledger → Chart of Accounts  
**User Question:** "Is the account indenting working as expected — should it be by level?"  
**Analysis:** The indentation IS working correctly by level:
- Code: `paddingLeft: (acctDepth(a) * 18) + 'px'`
- Level 0 (e.g. 1-0000 ASSETS): 0px padding
- Level 1 (e.g. 1-1000 Current Assets): 18px padding  
- Level 2 (e.g. 1-1100 Cash and Bank): 36px padding
- Level 3 (e.g. 1-1101 Cash on Hand): 54px padding
- Both Tree and List views apply this same indentation

**This is NOT a bug.** The design is intentional:
- Tree mode: Collapsible tree + depth-based indentation + expand/collapse arrows
- List mode: All accounts visible + depth-based indentation (no arrows)

**Design improvement suggestions (not bugs):**
1. The 18px increment could be slightly larger (24px) for clearer visual separation at deeper levels
2. In List mode, header accounts could use bold font or background tint to distinguish from posting accounts
3. Tree lines (vertical connecting lines like "│") would make hierarchy more obvious at a glance

---

### BUG-39 | Multiple Modules | 🟢 Low
**Module:** Any modal with dropdown sub-components  
**Expected:** Pressing Escape while a sub-dropdown (e.g. Currency or Period picker inside Invoice modal) is open should close ONLY that dropdown, leaving the parent modal open  
**Actual:** Escape closes the entire parent modal, losing all entered data (BUG-05 — source fix applied but needs rebuild)  
**Status:** Source fix applied in Round 2. Needs Docker rebuild to deploy.

---

## ✅ PERIOD CLOSE WORKFLOW — VERIFIED WORKING

The period-close workflow was fully tested and works correctly:

| Step | Action | Result |
|------|--------|--------|
| 1 | OPEN → Click "Begin Adjusting" | Period transitions to ADJUSTING ✅ |
| 2 | ADJUSTING → Click "Begin Closing" | Period transitions to CLOSING ✅ |
| 3 | CLOSING → Click "Close Period" | Period transitions to CLOSED ✅ |
| 4 | Try to create JE in CLOSED period | CLOSED period NOT in dropdown — posting blocked ✅ |
| 5 | CLOSED → Click "Reopen" | Can reopen (for corrections) ✅ |

**The period lock system works.** CLOSED periods are correctly excluded from the journal entry period picker.

---

## ✅ FEATURES VERIFIED WORKING IN V2 TESTING

| Feature | Result | Notes |
|---------|--------|-------|
| Period lifecycle: OPEN→ADJUSTING→CLOSING→CLOSED | ✅ Works | Complete 3-step workflow |
| Posting locked to CLOSED periods | ✅ Works | JE period dropdown excludes CLOSED |
| Generate fiscal year | ✅ Works | Creates 12 periods with correct ranges |
| Source Document lifecycle (5 stages) | ✅ Works | DRAFT→SUBMITTED→REVIEWED→APPROVED→ARCHIVED |
| FX Revaluation (IAS 21) page renders | ✅ Works | Empty with correct guidance (no foreign currencies) |
| Users & Roles page | ✅ Works (data issue noted) | RBAC enforced, roles visible |
| API Keys management | Not tested | Module present in sidebar |
| Invoice approval post-fix | ✅ Works | BUG-01 fixed and verified |
| Bill approval post-fix | ✅ Works | BUG-01B fixed and verified |
| COA indentation | ✅ By design | Correctly indented by level (18px × depth) |
| Period-end workflow (15 steps) | ✅ Renders | Validates pre-conditions correctly |

---

## REBUILD REQUIRED FOR THESE FIXES TO GO LIVE

Run `docker compose up --build -d` to activate:
- BUG-05: Escape key modal fix (Payments.vue, SearchableSelect.vue)
- BUG-06: Past periods "FUTURE" label fix (Invoices.vue)
- BUG-07: Invoice AGE display fix (Invoices.vue)
- BUG-11: Journal period UUID display fix (JournalEntries.vue)
- BUG-12: Balance Sheet P&L in equity fix (FinancialReportService.kt)
- BUG-14: Invoice revenue header account filter (Invoices.vue)
- BUG-17: Deferred Revenue COA account (V37 migration)
- BUG-18A: PaymentService account resolvers fix
- BUG-18B: Payment match error surfacing fix
- BUG-21: Credit Notes invoice/customer columns fix
- BUG-22: T-Account UUID header fix
- BUG-23: AR Ageing current bucket + message fix

---

## PRIORITY ORDER FOR NEXT SPRINT

### P0 (Release Blocker)
1. BUG-29: JE date field not persisting — affects all historical reporting
2. BUG-25: Multiple concurrent OPEN periods — breaks period accounting control
3. BUG-27: FY generation auto-switches context — dangerous UX trap
4. BUG-34: No fiscal year switcher — users get stuck in wrong FY

### P1 (High Priority)
5. BUG-33: Comparative TB date input broken (HTTP 500 on invalid date)
6. BUG-38: Fixed Assets sub-ledger HTTP 400 error
7. BUG-37: Sub-Ledgers empty when FY context switched
8. BUG-30: Period close audit trail (CLOSED BY/AT not recorded)
9. BUG-28: JE reference numbers not year-scoped

### P2 (Medium Priority)
10. BUG-31: Period validate cycle confusing error message
11. BUG-36: Users showing INACTIVE incorrectly
12. BUG-35: "Document submitd." typo
13. BUG-24: Generate FY defaults to year 2032


---

## 🔧 HARDCODED DATA & DASHBOARD STEPPER BUGS

### BUG-HC-01 | Dashboard Stepper | 🔴 Critical — Misleading Progress Display
**Module:** Dashboard → 9-Step Accounting Cycle widget (bottom of dashboard)  
**File:** `fa-frontend/src/views/overview/Dashboard.vue` lines 194-203, 284-288

**What the stepper shows:**
```
Steps: [Source Docs | Journalize | Post to Ledger | Trial Balance | 
        Adjusting | Adj. Trial Bal. | Statements | Closing | Post-Closing TB]
```

**Hardcoded progress logic (THE BUG):**
```js
const stepsDone = computed(() => {
  if (s === 'CLOSED' || s === 'REOPENED') return 9
  if (s === 'CLOSING')   return 7   // ← jumps from 4 to 7 (3 steps at once!)
  if (s === 'ADJUSTING') return 4
  if (s === 'OPEN')      return 3   // ← assumes steps 1-3 done just because period is OPEN
  return 0
})
```

**Expected:** Progress should reflect ACTUAL task completion — i.e. steps done based on Period-End Tasks module (which has 15 real tasks). The stepper should be connected to the actual task completion state.

**Actual — 3 specific problems:**

1. **Non-progressive jumps:** When period moves from ADJUSTING (4 done) to CLOSING, the stepper jumps to 7 done — marking "Adjusting", "Adj. Trial Bal.", and "Statements" ALL as done simultaneously with one button click. In reality, the user may not have done any of those tasks.

2. **False assumption at OPEN:** When period status is OPEN, shows steps 1–3 (Source Docs, Journalize, Post to Ledger) as already complete. A brand-new OPEN period has done NONE of these.

3. **Disconnect from Period-End Tasks:** The Period-End Tasks module has 15 real tasks (step-by-step). The dashboard stepper completely ignores this data and uses a simple hardcoded period-status mapping instead.

**Fix:** Connect `stepsDone` to the actual Period-End Task completion count from the API endpoint. Query the 15 period-end tasks and count how many are COMPLETED. Map to 0–9 progressively.

---

### BUG-HC-02 | Tax & Currency — Hardcoded Default Tax Codes | 🟡 Medium
**Module:** Setup → Tax & Currency  
**File:** `fa-frontend/src/views/setup/TaxCurrency.vue` line 36, `fa-frontend/src/data/index.js` lines 38-43

**Hardcoded data:**
```js
// data/index.js — hardcoded Apollo Enterprises demo tax codes
export const TAX_CODES = [
  { code: "VAT-16", name: "VAT Standard 16%", type: "OUTPUT", rate: 0.16, account: "2-2100" },
  { code: "VAT-0",  name: "VAT Zero-Rated",   type: "OUTPUT", rate: 0.00, account: "2-2100" },
  { code: "VAT-EX", name: "VAT Exempt",       type: "EXEMPT", rate: 0.00, account: null     },
  { code: "WHT-5",  name: "Withholding 5%",   type: "WHT",    rate: 0.05, account: "2-2110" },
]
```

**TaxCurrency.vue initializes with this hardcoded data:**
```js
const taxList = ref([...TAX_CODES])  // ← hardcoded initial state even in PRODUCTION mode!
```

**Expected:** Tax list should start empty and load from the API. If API returns empty, show empty state (not Apollo's demo taxes).

**Actual:** When Tax & Currency page first loads, it shows VAT-16, VAT-0, VAT-EX, WHT-5 as the initial list. The `onMounted` hook then tries to replace this with API data, but there's a race — if API fails or returns empty, these hardcoded Apollo demo taxes remain visible.

**Additional issue:** The hardcoded codes use `-` separator (VAT-16) while the real backend uses `_` separator (VAT_16). Inconsistent format.

**Fix:** Initialize `taxList = ref([])` and show loading spinner. Only populate from API. Remove dependency on `data/index.js` in production-mode views.

---

### BUG-HC-03 | Source Documents — Hardcoded Data in Component | 🟡 Medium
**Module:** Ledger → Source Documents  
**File:** `fa-frontend/src/views/ledger/SourceDocs.vue`

**Issue:** `SourceDocs.vue` directly imports `SOURCE_DOCS` from `data/index.js`:
```js
import { SOURCE_DOCS } from '@/data/index.js'
```
The source documents demo data includes Apollo-specific fake documents with static references and amounts. In production mode (`isDemo = false`), the API should be called instead, but any initialization using this data could leak fake documents into the UI momentarily.

**Fix:** Ensure `SOURCE_DOCS` is ONLY used when `isDemo.value === true`. Verify the component never pre-populates the list with demo data in production mode.

---

### BUG-HC-04 | Demo Data Scope — `data/index.js` is 513 lines of hardcoded Apollo data | 🟢 Low
**Module:** All modules  
**File:** `fa-frontend/src/data/index.js`

**What's hardcoded (all Apollo Enterprises specific):**
- ORG: Apollo Enterprises Ltd, PVT-20240001, finance@apollo.co.ke, 12 Westlands Road
- CURRENCIES: KES, USD, EUR, GBP with hardcoded rates
- FX_RATES: Specific CBK rates from Feb 2026
- TAX_CODES: Apollo's specific tax setup
- COA: Full chart of accounts with hardcoded balances
- PERIODS: 12 hardcoded periods
- CUSTOMERS, SUPPLIERS: Apollo's specific counterparties
- INVOICES, BILLS, PAYMENTS, RECEIPTS: Fake financial transactions
- USERS: Named Apollo employees (w.njeri, m.karanja, h.bakari, l.wairimu)
- SPARK_CASH/AR/REV/EXP: Hardcoded trend data arrays

**Expected:** Demo mode data should use GENERIC company names ("Demo Company Ltd") not customer-specific names ("Apollo Enterprises Ltd", "Wambui Njeri", etc.)

**Actual:** If a customer accidentally switches to Demo Mode, they'll see Apollo's fake data displayed as their own

**Fix:** Anonymize demo data. Replace "Apollo Enterprises" with "Demo Company Ltd". Replace employee names with generic names ("Jane Doe", "John Smith"). Replace apollo.co.ke emails with example.com emails.

---

### BUG-HC-05 | Dashboard — Hardcoded Sparkline Label Format | 🟢 Low
**Module:** Dashboard → Revenue vs Expenses chart  
**File:** `fa-frontend/src/api/dashboard.js` line 17

**Hardcoded:**
```js
sparkLabels: ['M03','M04','M05','M06','M07','M08','M09','M10','M11','M12','JAN','FEB'],
```

**Expected:** Sparkline labels should be dynamic, showing the actual last 12 months relative to today's date

**Actual:** In Demo mode, sparkline X-axis always shows M03-M12-JAN-FEB regardless of what month it actually is. In production mode, labels come from the API (correct). This only affects demo mode but is still confusing.

**Fix:** Generate labels dynamically in demo mode using `new Date()` to calculate the last 12 month labels.

---

### BUG-HC-06 | Dashboard Stepper — Step Names Hardcoded in English Only | 🟢 Low
**Module:** Dashboard → 9-Step Accounting Cycle  
**File:** `fa-frontend/src/views/overview/Dashboard.vue` lines 284-287

**Hardcoded:**
```js
const steps = [
  'Source Docs', 'Journalize', 'Post to Ledger', 'Trial Balance',
  'Adjusting', 'Adj. Trial Bal.', 'Statements', 'Closing', 'Post-Closing TB',
]
```

**Issue:** Step names are hardcoded English strings with no i18n support. "Adj. Trial Bal." is an abbreviation that accountants in non-English markets may not recognize.

**Fix:** Move step names to a locale file. Use full names: "Adjusted Trial Balance" instead of "Adj. Trial Bal."

---

## 📊 DASHBOARD 9-STEP STEPPER — VISUAL VERIFICATION

The stepper currently shows:
- When period is **OPEN**: Steps 1, 2, 3 show green checkmarks (Source Docs ✓, Journalize ✓, Post to Ledger ✓), Step 4 (Trial Balance) is "active". This is misleading — the period being OPEN does not mean any of those steps were completed.
- When period is **ADJUSTING**: Step 4 shows green (Trial Balance ✓), Step 5 (Adjusting) is active
- When period is **CLOSING**: Steps 5, 6, 7 ALL jump to green simultaneously (Adjusting ✓, Adj. TB ✓, Statements ✓), Step 8 (Closing) is active — a 3-step jump
- When period is **CLOSED**: All 9 steps show green — including "Post-Closing TB" which was never actually run

**The stepper is purely decorative/aspirational — it does not reflect actual accounting work done.**


---

## 🎯 DASHBOARD VISUAL ANALYSIS — CONFIRMED BUGS

### BUG-HC-01 CONFIRMED (Visual Evidence)
**9-Step Stepper for "JANUARY 2025" showing period status = OPEN (stepsDone = 3):**

Visible in screenshot:
- ✅ Source Docs (green filled circle) — FALSELY marked as done
- ✅ Journalize (green filled circle) — FALSELY marked as done
- ✅ Post to Ledger (green filled circle) — FALSELY marked as done
- 🟢 Trial Balance (large active circle "4") — shown as next step
- ○ Adjusting (5), Adj. Trial Bal. (6), Statements (7), Closing (8), Post-Closing TB (9) — all empty

**Reality:** JANUARY 2025 only has one journal entry (opening capital). No source documents were attached. The period is actually CLOSED but the indicator shows OPEN (stale data). Yet the stepper shows 3 steps done regardless.

---

### BUG-40 | Dashboard | 🟡 Medium — Stale Period Status in Dashboard
**Module:** Dashboard → Active period indicator + 9-Step stepper  
**Expected:** After closing JANUARY 2025, the top bar and stepper should reflect the new active period (FEBRUARY 2025, which is OPEN)  
**Actual:** Top bar still shows "2025-01 OPEN" even though January 2025 is CLOSED. The stepper also shows "JANUARY 2025" and progress based on OPEN status. This is stale data from `useActivePeriod` composable which isn't invalidated after period transitions.  
**Fix:** After any period status change, call `activePeriod.load(force: true)` to refresh the cached period data.

---

### BUG-41 | Dashboard | 🟡 Medium — Mixed Fiscal Year Data on Same Dashboard
**Module:** Dashboard  
**Observed:** Dashboard simultaneously shows:
- **KPI cards**: FY2026 data (Cash 15.02M, AR 704K, Revenue 1.16M, Expenses 511K)
- **Trial Balance Health**: "JANUARY 2025" data (Assets 15.69M, Imbalance 652K)
- **Revenue chart**: X-axis starts "Jun 25" — spanning both FY2025 and FY2026
- **Approvals Queue**: Shows INV-2026-0002 (FY2026 invoice)

**Expected:** Dashboard should show data consistently for ONE fiscal year context, or clearly label which year each widget is showing  
**Actual:** Widgets silently pull data from different fiscal years creating a confusing mix. A CFO viewing this dashboard cannot tell which year's data they're looking at without reading widget headers carefully.  
**Fix:** Either: (a) add fiscal year badge to each widget, or (b) add a global FY filter on the dashboard that ensures all widgets show the same year's data.

---

### BUG-42 | Revenue Chart | 🟢 Low — Chart Shows Deceptive Spike
**Module:** Dashboard → Revenue vs Expenses chart  
**Observed:** The "Revenue vs Expenses — Last 12 months" chart shows a FLAT LINE for 11 months (Jun 2025 — Apr 2026) then a MASSIVE spike in May 2026. This is technically correct (all transactions were entered on May 19, 2026) but looks alarming and misleading to any reviewer.  
**Root Cause:** BUG-29 (JE dates not persisting) — all journal entries got dated as today (19 May 2026) even though they belong to different periods. The chart correctly plots by transaction DATE, so all history appears in one day.  
**Fix:** Fix BUG-29 (date persistence) — then historical transactions will spread across their proper dates and the chart will show a realistic trend.

---

## 📋 COMPLETE BUG INVENTORY — FINAL SUMMARY

| BUG | Module | Severity | Status | Expected | Actual |
|-----|--------|----------|--------|----------|--------|
| BUG-24 | Periods — Generate FY | 🔴 | Open | Default to logical year | Defaults to 2032 |
| BUG-25 | Periods — Activate | 🔴 | Open | One OPEN period at a time | 3 periods OPEN simultaneously |
| BUG-27 | Periods — Generate FY | 🔴 | Open | Keep current FY context | Auto-switches to generated FY |
| BUG-28 | Journal Entries | 🟡 | Open | JE refs per fiscal year | Sequential across all years |
| BUG-29 | Journal Entries | 🔴 | Open | Date field persists custom date | Always saves today's date |
| BUG-30 | Periods — Close | 🟡 | Open | Record who/when closed | CLOSED BY/AT = "—" |
| BUG-31 | Period-End Tasks | 🟡 | Open | Validate shows guidance | Shows technical error |
| BUG-32 | Comparative TB | 🟡 | Open | Shows historical balances | All zeros (caused by BUG-29) |
| BUG-33 | Comparative TB | 🔴 | Open | Accept typed dates | Corrupts year to 52025 / HTTP 500 |
| BUG-34 | Navigation | 🔴 | Open | Manual FY switcher exists | No FY switcher in UI |
| BUG-35 | Source Docs | 🟢 | Open | "Document submitted." | "Document submitd." (typo) |
| BUG-36 | Users | 🟡 | Open | Active user shows ACTIVE | Admin shows INACTIVE; count = 0 |
| BUG-37 | Sub-Ledgers | 🟡 | Open | Shows entity-wide data | Empty when FY context changed |
| BUG-38 | Sub-Ledgers | 🟡 | Open | Shows fixed assets | HTTP 400 error |
| BUG-40 | Dashboard | 🟡 | Open | Refreshes after period close | Shows stale OPEN status |
| BUG-41 | Dashboard | 🟡 | Open | Consistent FY context | Mixes FY2025 and FY2026 data |
| BUG-42 | Revenue Chart | 🟢 | Open | Historical trend line | Flat then spike (caused by BUG-29) |
| BUG-HC-01 | Dashboard Stepper | 🔴 | Open | True task completion progress | Hardcoded status-based progress |
| BUG-HC-02 | Tax & Currency | 🟡 | Open | Loads empty, then from API | Pre-populated with Apollo demo data |
| BUG-HC-03 | Source Docs | 🟡 | Open | No demo data in prod mode | Imports hardcoded demo docs |
| BUG-HC-04 | All Modules (Demo) | 🟢 | Open | Generic demo company | Apollo Enterprises-specific demo data |
| BUG-HC-05 | Dashboard Chart | 🟢 | Open | Dynamic month labels | Hardcoded "M03-M12-JAN-FEB" |
| BUG-HC-06 | Dashboard Stepper | 🟢 | Open | i18n + full step names | English-only abbreviations |

**Previously found and fixed (Rounds 1-4):**
BUG-01, 01B, 02, 04, 05, 06, 07, 11, 12, 13, 14, 15, 17, 18A, 18B, 19, 20, 21, 22, 23


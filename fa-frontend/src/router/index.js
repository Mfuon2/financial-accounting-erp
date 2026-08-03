import { watch }                       from 'vue'
import { isAuthenticated, authReady }  from '@/composables/useAuth.js'
import { isDemo }                      from '@/composables/useAppMode.js'

const PUBLIC_PATHS = new Set(['/login', '/signup', '/forgot-password', '/reset-password'])

export const routes = [
  { path: '/login',            component: () => import('@/views/auth/Login.vue'),         meta: { public: true } },
  { path: '/signup',           component: () => import('@/views/auth/Signup.vue'),        meta: { public: true } },
  { path: '/forgot-password',  component: () => import('@/views/auth/ForgotPassword.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '',              redirect: '/dashboard' },
      { path: '/dashboard',   component: () => import('@/views/overview/Dashboard.vue') },
      { path: '/approvals',   component: () => import('@/views/overview/Approvals.vue') },
      { path: '/coa',         component: () => import('@/views/ledger/ChartOfAccounts.vue') },
      { path: '/periods',     component: () => import('@/views/ledger/Periods.vue') },
      { path: '/journals',    component: () => import('@/views/ledger/JournalEntries.vue') },
      { path: '/source-docs', component: () => import('@/views/ledger/SourceDocs.vue') },
      { path: '/customers',   component: () => import('@/views/parties/Customers.vue') },
      { path: '/suppliers',   component: () => import('@/views/parties/Suppliers.vue') },
      { path: '/assets',      component: () => import('@/views/assets/FixedAssets.vue') },
      { path: '/depreciation',component: () => import('@/views/assets/DepreciationRun.vue') },
      { path: '/invoices',    component: () => import('@/views/revenue/Invoices.vue') },
      { path: '/credit-notes',component: () => import('@/views/revenue/CreditNotes.vue') },
      { path: '/payments',    component: () => import('@/views/revenue/Payments.vue') },
      { path: '/receipts',    component: () => import('@/views/revenue/Receipts.vue') },
      { path: '/ar-ageing',   component: () => import('@/views/revenue/ARAgeing.vue') },
      { path: '/trial-balance',component: () => import('@/views/period-end/TrialBalance.vue') },
      { path: '/period-end',  component: () => import('@/views/period-end/PeriodEndTasks.vue') },
      { path: '/fx',          component: () => import('@/views/period-end/FXReval.vue') },
      { path: '/pnl',         component: () => import('@/views/statements/ProfitLoss.vue') },
      { path: '/balance-sheet',component: () => import('@/views/statements/BalanceSheet.vue') },
      { path: '/cash-flow',   component: () => import('@/views/statements/CashFlow.vue') },
      { path: '/close',       component: () => import('@/views/statements/ClosePeriod.vue') },
      { path: '/t-account',   component: () => import('@/views/reports/TAccountView.vue') },
      { path: '/sub-ledger',  component: () => import('@/views/reports/SubLedger.vue') },
      { path: '/audit',       component: () => import('@/views/reports/AuditTrail.vue') },
      { path: '/ias1',        component: () => import('@/views/reports/IAS1Check.vue') },
      { path: '/comparative', component: () => import('@/views/reports/ComparativeTB.vue') },
      { path: '/bills',       component: () => import('@/views/payables/Bills.vue') },
      { path: '/ap-ageing',   component: () => import('@/views/payables/APAgeing.vue') },
      { path: '/profile',      component: () => import('@/views/setup/Profile.vue') },
      { path: '/organization',component: () => import('@/views/setup/Organization.vue') },
      { path: '/users',       component: () => import('@/views/setup/Users.vue') },
      { path: '/api-keys',    component: () => import('@/views/setup/ApiKeys.vue') },
      { path: '/tax',         component: () => import('@/views/setup/TaxCurrency.vue') },
      { path: '/categories',  component: () => import('@/views/setup/Categories.vue') },
      { path: '/security',    component: () => import('@/views/setup/Security.vue') },
      { path: '/setup-health', component: () => import('@/views/setup/SetupHealth.vue') },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue') },
]

export const CRUMBS = {
  '/dashboard':     ['Overview', 'Dashboard'],
  '/approvals':     ['Overview', 'Approvals'],
  '/coa':           ['Ledger', 'Chart of Accounts'],
  '/periods':       ['Ledger', 'Periods'],
  '/journals':      ['Ledger', 'Journal Entries'],
  '/source-docs':   ['Ledger', 'Source Documents'],
  '/customers':     ['Parties', 'Customers'],
  '/suppliers':     ['Parties', 'Suppliers'],
  '/assets':        ['Assets', 'Fixed Asset Register'],
  '/depreciation':  ['Assets', 'Depreciation Run'],
  '/invoices':      ['Revenue', 'Invoices'],
  '/credit-notes':  ['Revenue', 'Credit Notes'],
  '/payments':      ['Revenue', 'Payments'],
  '/receipts':      ['Revenue', 'Receipts'],
  '/ar-ageing':     ['Revenue', 'AR Ageing'],
  '/trial-balance': ['Period-End', 'Trial Balance'],
  '/period-end':    ['Period-End', 'Workflow'],
  '/fx':            ['Period-End', 'FX Revaluation'],
  '/pnl':           ['Statements', 'Profit & Loss'],
  '/balance-sheet': ['Statements', 'Balance Sheet'],
  '/cash-flow':     ['Statements', 'Cash Flow'],
  '/close':         ['Statements', 'Close Period'],
  '/t-account':     ['Reports', 'T-Account'],
  '/sub-ledger':    ['Reports', 'Sub-Ledgers'],
  '/audit':         ['Reports', 'Audit Trail'],
  '/ias1':          ['Reports', 'IAS 1 Compliance'],
  '/comparative':   ['Reports', 'Comparative TB'],
  '/bills':         ['Payables', 'Vendor Bills'],
  '/ap-ageing':     ['Payables', 'AP Ageing'],
  '/profile':       ['Setup', 'My Profile'],
  '/organization':  ['Setup', 'Organization'],
  '/users':         ['Setup', 'Users'],
  '/api-keys':      ['Setup', 'API Keys'],
  '/tax':           ['Setup', 'Tax & Currency'],
  '/categories':    ['Setup', 'Categories'],
  '/security':      ['Setup', 'Security'],
  '/setup-health':  ['Setup', 'System Health'],
}

/**
 * Attach navigation guard to the router instance.
 * Called from main.js after createRouter().
 *
 * Guard logic:
 *  1. Wait for auth init to complete (authReady).
 *  2. Public route (login/signup) + already authenticated → redirect to dashboard.
 *  3. Protected route + not authenticated → redirect to /login, preserve intended path.
 *  4. Otherwise allow navigation.
 */
export function setupGuards(router) {
  router.beforeEach(async (to) => {
    // Demo mode bypasses auth entirely
    if (isDemo.value) {
      if (to.path === '/login') return '/dashboard'
      return true
    }

    // Wait for silent-refresh init before making any decision
    if (!authReady.value) {
      await new Promise(resolve => {
        const stop = watch(authReady, (val) => { if (val) { stop(); resolve() } }, { immediate: true })
      })
    }

    const isPublic = to.meta?.public === true || PUBLIC_PATHS.has(to.path)
    const authed   = isAuthenticated.value

    // Already logged in → don't show login page again
    if (isPublic && authed) return '/dashboard'

    // Protected route → must be logged in
    if (!isPublic && !authed) {
      return { path: '/login', query: to.path !== '/' ? { next: to.fullPath } : {} }
    }

    return true
  })
}

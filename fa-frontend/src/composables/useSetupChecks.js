import { ref, computed } from 'vue'
import { fx, accounts as accountsApi, tax, periods as periodsApi, customers as customersApi, suppliers as suppliersApi } from '@/api/index.js'
import { isDemo } from '@/composables/useAppMode.js'

// ── Singleton state (module-level — shared across all useSetupChecks() calls) ─
const _checks  = ref([])
const _loading = ref(false)
const _lastRun = ref(null)

function toArr(v) {
  if (!v) return []
  if (Array.isArray(v)) return v
  if (v.content) return v.content
  return []
}

// ── Check definitions ─────────────────────────────────────────────────────────
const CHECKS = [
  {
    id: 'open_period',
    label: 'Active accounting period',
    description: 'An OPEN or ADJUSTING period is required before any transaction, journal entry, invoice, or payment can be posted.',
    fix: 'Create or open a period',
    route: '/periods',
    severity: 'critical',
    run: async (entityId) => {
      const res = await periodsApi.list({ entityId, size: 50 })
      return toArr(res).some(p => ['OPEN', 'ADJUSTING', 'CLOSING'].includes(p.status))
    },
  },
  {
    id: 'functional_currency',
    label: 'Functional currency registered',
    description: 'Exactly one currency must be marked as functional (reporting currency). Required for financial statements and IAS 21 FX revaluation.',
    fix: 'Go to Tax & Currency → Currencies tab → mark one as Functional',
    route: '/tax',
    severity: 'critical',
    run: async (entityId) => {
      const res = await fx.currencies(entityId)
      return toArr(res).some(c => c.isFunctional)
    },
  },
  {
    id: 'chart_of_accounts',
    label: 'Chart of accounts populated',
    description: 'At least one posting account must exist before journal entries, invoices, or payments can be recorded.',
    fix: 'Set up chart of accounts',
    route: '/coa',
    severity: 'critical',
    run: async (entityId) => {
      const res = await accountsApi.list({ entityId, size: 5 })
      return toArr(res).length > 0
    },
  },
  {
    id: 'tax_codes',
    label: 'Tax codes configured',
    description: 'Tax codes are required to apply VAT, withholding tax, or other levies on customer invoices and vendor bills.',
    fix: 'Add at least one tax code in Setup → Tax & Currency',
    route: '/tax',
    severity: 'warning',
    run: async (entityId) => {
      const res = await tax.list(entityId)
      return toArr(res).length > 0
    },
  },
  {
    id: 'fx_closing_rates',
    label: 'FX closing rates for current period',
    description: 'If you have foreign currencies registered, CLOSING rates must be entered before running IAS 21 FX revaluation.',
    fix: 'Enter closing rates in Period-End → FX Revaluation',
    route: '/fx',
    severity: 'warning',
    run: async (entityId) => {
      const res = await fx.currencies(entityId)
      const list = toArr(res)
      const foreign = list.filter(c => !c.isFunctional)
      // No foreign currencies = nothing to revalue = passes
      return foreign.length === 0
    },
  },
  {
    id: 'customers',
    label: 'Customers created',
    description: 'No customers have been created. You need customers to raise invoices and track accounts receivable.',
    fix: 'Add customers in Parties → Customers',
    route: '/customers',
    severity: 'info',
    run: async (entityId) => {
      const res = await customersApi.list({ entityId, size: 1 })
      return toArr(res).length > 0
    },
  },
  {
    id: 'suppliers',
    label: 'Suppliers created',
    description: 'No suppliers have been created. You need suppliers to record vendor bills and track accounts payable.',
    fix: 'Add suppliers in Parties → Suppliers',
    route: '/suppliers',
    severity: 'info',
    run: async (entityId) => {
      const res = await suppliersApi.list({ entityId, size: 1 })
      return toArr(res).length > 0
    },
  },
]

export function useSetupChecks() {
  const criticalFails = computed(() => _checks.value.filter(c => c.status === 'FAIL' && c.severity === 'critical').length)
  const warningFails  = computed(() => _checks.value.filter(c => c.status === 'FAIL' && c.severity === 'warning').length)
  const totalIssues   = computed(() => _checks.value.filter(c => c.status === 'FAIL').length)
  const hasIssues     = computed(() => totalIssues.value > 0)

  async function runChecks(entityId, { force = false } = {}) {
    if (!entityId || isDemo.value) { _checks.value = []; return }
    // Skip re-run if checks already completed within the last 5 minutes
    if (!force && _lastRun.value && (Date.now() - _lastRun.value) < 5 * 60 * 1000) return
    _loading.value = true
    try {
      const results = await Promise.allSettled(CHECKS.map(c => c.run(entityId)))
      _checks.value = CHECKS.map((def, i) => ({
        ...def,
        status: results[i].status === 'fulfilled'
          ? (results[i].value ? 'PASS' : 'FAIL')
          : 'ERROR',
      }))
      _lastRun.value = new Date()
    } finally {
      _loading.value = false
    }
  }

  return { checks: _checks, loading: _loading, lastRun: _lastRun, criticalFails, warningFails, totalIssues, hasIssues, runChecks }
}

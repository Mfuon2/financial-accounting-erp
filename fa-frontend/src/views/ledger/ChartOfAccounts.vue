<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { accounts as accountsApi } from '@/api/index.js'
import { useAuth }  from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { useAccountCache } from '@/composables/useAccountCache.js'
import { fmt }      from '@/utils/format.js'
import PageHeader      from '@/components/PageHeader.vue'
import Button          from '@/components/primitives/Button.vue'
import Ico             from '@/components/primitives/Ico.vue'
import Badge           from '@/components/primitives/Badge.vue'
import ChipFilter      from '@/components/primitives/ChipFilter.vue'
import Segmented       from '@/components/primitives/Segmented.vue'
import Modal           from '@/components/overlays/Modal.vue'
import TableToolbar    from '@/components/tables/TableToolbar.vue'
import TableFooter     from '@/components/tables/TableFooter.vue'
import Banner          from '@/components/data-display/Banner.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

// ── Auth ──────────────────────────────────────────────────────────────────────
const { currentUser } = useAuth()
const { toast } = useToast()
const { invalidate: invalidateAccountCache } = useAccountCache()
const entityId     = computed(() => currentUser.value?.entityId ?? 'current')
const isSuperAdmin = computed(() => currentUser.value?.role === 'SYSTEM_ADMIN')

// ── Constants ─────────────────────────────────────────────────────────────────
const ACCOUNT_SUBTYPES = [
  { value: 'CASH_AND_EQUIVALENTS',     label: 'Cash & Equivalents',     type: 'ASSET' },
  { value: 'CURRENT_RECEIVABLE',       label: 'Current Receivable',      type: 'ASSET' },
  { value: 'CURRENT_INVENTORY',        label: 'Current Inventory',       type: 'ASSET' },
  { value: 'CURRENT_PREPAID',          label: 'Current Prepaid',         type: 'ASSET' },
  { value: 'NON_CURRENT_PPE',          label: 'Non-Current PPE',         type: 'ASSET' },
  { value: 'NON_CURRENT_INTANGIBLE',   label: 'Non-Current Intangible',  type: 'ASSET' },
  { value: 'NON_CURRENT_INVESTMENT',   label: 'Non-Current Investment',  type: 'ASSET' },
  { value: 'NON_CURRENT_OTHER',        label: 'Non-Current Other',       type: 'ASSET' },
  { value: 'CURRENT_PAYABLE',          label: 'Current Payable',         type: 'LIABILITY' },
  { value: 'CURRENT_ACCRUED',          label: 'Current Accrued',         type: 'LIABILITY' },
  { value: 'CURRENT_DEFERRED_REVENUE', label: 'Deferred Revenue',        type: 'LIABILITY' },
  { value: 'CURRENT_TAX',             label: 'Current Tax',             type: 'LIABILITY' },
  { value: 'NON_CURRENT_LONG_TERM_DEBT', label: 'Long-Term Debt',       type: 'LIABILITY' },
  { value: 'NON_CURRENT_LEASE',        label: 'Lease Liability',         type: 'LIABILITY' },
  { value: 'NON_CURRENT_PROVISION',    label: 'Provision',               type: 'LIABILITY' },
  { value: 'NON_CURRENT_DEFERRED_TAX', label: 'Deferred Tax Liability',  type: 'LIABILITY' },
  { value: 'SHARE_CAPITAL',            label: 'Share Capital',           type: 'EQUITY' },
  { value: 'RETAINED_EARNINGS',        label: 'Retained Earnings',       type: 'EQUITY' },
  { value: 'OTHER_COMPREHENSIVE_INCOME', label: 'OCI',                   type: 'EQUITY' },
  { value: 'DIVIDENDS_DRAWINGS',       label: 'Dividends / Drawings',    type: 'EQUITY' },
  { value: 'OPERATING_REVENUE',        label: 'Operating Revenue',       type: 'REVENUE' },
  { value: 'OTHER_INCOME',             label: 'Other Income',            type: 'REVENUE' },
  { value: 'FINANCE_INCOME',           label: 'Finance Income',          type: 'REVENUE' },
  { value: 'COGS',                     label: 'Cost of Goods Sold',      type: 'EXPENSE' },
  { value: 'OPERATING_EXPENSES',       label: 'Operating Expenses',      type: 'EXPENSE' },
  { value: 'DEPRECIATION',             label: 'Depreciation',            type: 'EXPENSE' },
  { value: 'AMORTISATION',             label: 'Amortisation',            type: 'EXPENSE' },
  { value: 'FINANCE_COST',             label: 'Finance Cost',            type: 'EXPENSE' },
  { value: 'TAX_EXPENSE',              label: 'Tax Expense',             type: 'EXPENSE' },
]

const IFRS_CATEGORIES = [
  'CURRENT_ASSETS', 'NON_CURRENT_ASSETS',
  'CURRENT_LIABILITIES', 'NON_CURRENT_LIABILITIES',
  'EQUITY', 'REVENUE', 'COST_OF_SALES',
  'OPERATING_EXPENSES', 'OTHER_INCOME_EXPENSE', 'FINANCE_COSTS', 'TAX_EXPENSE',
]

const SUBTYPE_TO_TYPE = Object.fromEntries(ACCOUNT_SUBTYPES.map(s => [s.value, s.type]))

// ── State ─────────────────────────────────────────────────────────────────────
const allAccounts = ref([])
const loading     = ref(true)
const search      = ref('')
const typeFilter  = ref('ALL')
const viewMode    = ref('tree')
const collapsed   = ref(new Set())

// ── Modals ────────────────────────────────────────────────────────────────────
const showNew            = ref(false)
const showEdit           = ref(false)
const showImport         = ref(false)
const showDeactivate     = ref(false)
const showBulkDeactivate = ref(false)
const detailAcct         = ref(null)

// ── Bulk selection (SYSTEM_ADMIN only) ────────────────────────────────────────
const selectedIds   = ref(new Set())
const bulkDeactivating = ref(false)

const selectableVisible = computed(() => visible.value.filter(a => a.isActive))
const allVisibleSelected = computed(
  () => selectableVisible.value.length > 0 && selectableVisible.value.every(a => selectedIds.value.has(a.id))
)

function toggleSelectAll() {
  const next = new Set(selectedIds.value)
  if (allVisibleSelected.value) {
    selectableVisible.value.forEach(a => next.delete(a.id))
  } else {
    selectableVisible.value.forEach(a => next.add(a.id))
  }
  selectedIds.value = next
}

function toggleSelect(id) {
  const next = new Set(selectedIds.value)
  next.has(id) ? next.delete(id) : next.add(id)
  selectedIds.value = next
}

function clearSelection() { selectedIds.value = new Set() }

async function doBulkDeactivate() {
  bulkDeactivating.value = true
  const ids = [...selectedIds.value]
  let success = 0, failed = 0
  for (const id of ids) {
    try { await accountsApi.deactivate(id); success++ }
    catch { failed++ }
  }
  bulkDeactivating.value = false
  showBulkDeactivate.value = false
  clearSelection()
  if (failed === 0) toast.success(`${success} account${success !== 1 ? 's' : ''} deactivated`)
  else toast.error(`${success} deactivated, ${failed} failed (accounts with ledger history cannot be deactivated)`)
  await load()
}

// ── New / Edit form ───────────────────────────────────────────────────────────
const blankForm = () => ({ accountCode: '', accountName: '', accountSubtype: '', ifrsCategory: '', parentAccountId: '', currencyCode: '' })
const form       = ref(blankForm())
const saving     = ref(false)
const codeValid  = ref(null)   // null=unchecked, true=available, false=taken

// ── Import state ──────────────────────────────────────────────────────────────
const importFile    = ref(null)
const importRows    = ref([])
const importErrors  = ref([])
const importing     = ref(false)
const importFileRef = ref(null)

// ── Load ──────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  try {
    const data = await accountsApi.list({ entityId: entityId.value })
    allAccounts.value = Array.isArray(data) ? data : (data?.content ?? [])
    await nextTick()
    // Collapse everything at depth ≥ 1 so only the 8 class roots show initially
    const init = new Set()
    for (const a of allAccounts.value) {
      if ((depthMap.value[a.id] ?? 0) >= 1) init.add(a.id)
    }
    collapsed.value = init
  } catch { /* stays empty */ } finally { loading.value = false }
}
onMounted(load)

// ── Computed tree / list ──────────────────────────────────────────────────────
const idMap   = computed(() => Object.fromEntries(allAccounts.value.map(a => [a.id, a])))
const codeMap = computed(() => Object.fromEntries(allAccounts.value.map(a => [a.accountCode, a])))

function inferParentCode(code, codeSet) {
  const dash = code.indexOf('-')
  if (dash === -1) return null
  const prefix = code.slice(0, dash)
  const suffix = code.slice(dash + 1)
  if (suffix.length !== 4) return null
  if (parseInt(suffix, 10) === 0) return null

  const d = suffix.split('')
  // Zero rightmost non-zero digit group → candidate parent code
  for (let i = d.length - 1; i >= 0; i--) {
    if (d[i] !== '0') {
      const cand = d.map((c, j) => j >= i ? '0' : c)
      const pc = `${prefix}-${cand.join('')}`
      if (pc !== code && codeSet.has(pc)) return pc
      break
    }
  }
  // Fallback: find nearest smaller code with more trailing zeros
  const trailingZeros = [...suffix].reverse().findIndex(c => c !== '0')
  const suffixNum = parseInt(suffix, 10)
  const tz = trailingZeros === -1 ? 4 : trailingZeros
  const best = [...codeSet]
    .filter(c => {
      if (!c.startsWith(prefix + '-')) return false
      if (c === code) return false
      const s = c.split('-')[1]
      if (!s || s.length !== 4) return false
      const sn = parseInt(s, 10)
      if (sn >= suffixNum) return false
      const ctz = [...s].reverse().findIndex(x => x !== '0')
      return (ctz === -1 ? 4 : ctz) > tz
    })
    .sort((a, b) => parseInt(b.split('-')[1], 10) - parseInt(a.split('-')[1], 10))
  return best[0] ?? null
}

const inferredParentMap = computed(() => {
  const codeSet = new Set(allAccounts.value.map(a => a.accountCode))
  const cm = codeMap.value
  const result = {}
  for (const a of allAccounts.value) {
    if (a.parentAccountId) {
      result[a.id] = a.parentAccountId
    } else {
      const pc = inferParentCode(a.accountCode, codeSet)
      result[a.id] = pc ? (cm[pc]?.id ?? null) : null
    }
  }
  return result
})

const depthMap = computed(() => {
  const ipm = inferredParentMap.value
  const cache = {}
  function d(id, seen = new Set()) {
    if (id in cache) return cache[id]
    const pid = ipm[id]
    if (!pid || seen.has(id)) { cache[id] = 0; return 0 }
    seen.add(id)
    cache[id] = 1 + d(pid, seen)
    return cache[id]
  }
  for (const a of allAccounts.value) d(a.id)
  return cache
})

const childrenOfSet = computed(() => new Set(Object.values(inferredParentMap.value).filter(Boolean)))

const visible = computed(() => {
  const ipm = inferredParentMap.value
  const sorted = [...allAccounts.value].sort((a, b) => a.accountCode < b.accountCode ? -1 : 1)
  return sorted.filter(a => {
    if (typeFilter.value !== 'ALL' && a.accountType !== typeFilter.value) return false
    if (search.value) {
      const q = search.value.toLowerCase()
      if (!a.accountCode.toLowerCase().includes(q) && !a.accountName.toLowerCase().includes(q)) return false
    }
    if (viewMode.value === 'list') return true
    // When a search is active, show all matching accounts regardless of collapse state
    if (search.value) return true
    let pid = ipm[a.id]
    while (pid) {
      if (collapsed.value.has(pid)) return false
      pid = ipm[pid]
    }
    return true
  })
})

function acctDepth(a) { return depthMap.value[a.id] ?? 0 }
function hasChildren(id) { return childrenOfSet.value.has(id) }
function toggleCollapse(id) {
  const n = new Set(collapsed.value)
  n.has(id) ? n.delete(id) : n.add(id)
  collapsed.value = n
}

const TYPE_FILTERS = ['ALL', 'ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE']
const VIEW_OPTS    = [{ value: 'tree', label: 'Tree' }, { value: 'list', label: 'List' }]

// ── New account ───────────────────────────────────────────────────────────────
function openNew() { form.value = blankForm(); codeValid.value = null; showNew.value = true }

async function checkCode() {
  if (!form.value.accountCode) return
  try {
    codeValid.value = await accountsApi.validateCode({ entityId: entityId.value, code: form.value.accountCode })
  } catch { codeValid.value = null }
}

async function createAccount() {
  if (!form.value.accountCode || !form.value.accountName || !form.value.accountSubtype) {
    toast.error('Code, name and subtype are required.')
    return
  }
  saving.value = true
  try {
    await accountsApi.create({
      entityId: entityId.value,
      accountCode: form.value.accountCode,
      accountName: form.value.accountName,
      accountSubtype: form.value.accountSubtype,
      ifrsCategory: form.value.ifrsCategory || undefined,
      parentAccountId: form.value.parentAccountId || undefined,
      currencyCode: form.value.currencyCode || undefined,
    })
    toast.success(`Account ${form.value.accountCode} created.`)
    showNew.value = false
    invalidateAccountCache() // ensure JE dropdown re-fetches on next open
    await load()
  } catch {} finally { saving.value = false }
}

// ── Edit account ──────────────────────────────────────────────────────────────
function openEdit(a) {
  form.value = {
    accountCode: a.accountCode,
    accountName: a.accountName,
    accountSubtype: a.accountSubtype,
    ifrsCategory: a.ifrsCategory,
    parentAccountId: a.parentAccountId ?? '',
    currencyCode: a.currencyCode ?? '',
    _id: a.id,
  }
  detailAcct.value = null
  showEdit.value = true
}

async function updateAccount() {
  if (!form.value.accountName || !form.value.accountSubtype) {
    toast.error('Name and subtype are required.')
    return
  }
  saving.value = true
  try {
    await accountsApi.update(form.value._id, {
      accountCode: form.value.accountCode,
      accountName: form.value.accountName,
      accountSubtype: form.value.accountSubtype,
      ifrsCategory: form.value.ifrsCategory || 'OPERATING_EXPENSES',
    })
    toast.success('Account updated.')
    showEdit.value = false
    await load()
  } catch {} finally { saving.value = false }
}

// ── Deactivate ────────────────────────────────────────────────────────────────
let _deactivateTarget = null
function confirmDeactivate(a) { _deactivateTarget = a; detailAcct.value = null; showDeactivate.value = true }

async function doDeactivate() {
  if (!_deactivateTarget) return
  saving.value = true
  try {
    await accountsApi.deactivate(_deactivateTarget.id)
    toast.success(`Account ${_deactivateTarget.accountCode} deactivated.`)
    showDeactivate.value = false
    await load()
  } catch {} finally { saving.value = false; _deactivateTarget = null }
}

// ── Export CSV ────────────────────────────────────────────────────────────────
function exportCsv() {
  const headers = ['accountCode', 'accountName', 'accountType', 'accountSubtype', 'normalBalance', 'ifrsCategory', 'parentAccountId', 'isActive']
  const rows = allAccounts.value.map(a =>
    headers.map(h => JSON.stringify(a[h] ?? '')).join(',')
  )
  const csv = [headers.join(','), ...rows].join('\n')
  downloadBlob(csv, 'text/csv', `chart-of-accounts-${today()}.csv`)
  toast.success('CoA exported.')
}

// ── Sample template download ──────────────────────────────────────────────────
function downloadTemplate() {
  const csv = `accountCode,accountName,accountSubtype,ifrsCategory,parentAccountCode,currencyCode
1-0000,ASSETS,NON_CURRENT_OTHER,CURRENT_ASSETS,,KES
1-1000,Current Assets,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-0000,KES
1-1100,Cash and Bank,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1000,KES
1-1101,Cash on Hand,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1100,KES
1-1102,Petty Cash,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1100,KES
1-1110,Bank Accounts - KES,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1100,KES
1-1111,KCB Current Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1110,KES
1-1112,Equity Bank Current Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1110,KES
1-1113,Stanbic Bank Current Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1110,KES
1-1114,NCBA Current Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1110,KES
1-1115,Co-operative Bank Current Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1110,KES
1-1120,Bank Accounts - Foreign Currency,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1100,KES
1-1121,USD Bank Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1120,KES
1-1122,EUR Bank Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1120,KES
1-1123,GBP Bank Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1120,KES
1-1130,Mobile Money Accounts,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1100,KES
1-1131,M-Pesa Business Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1130,KES
1-1132,Airtel Money Business Account,CASH_AND_EQUIVALENTS,CURRENT_ASSETS,1-1130,KES
1-1200,Accounts Receivable,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1000,KES
1-1201,Trade Debtors - Local,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1200,KES
1-1202,Trade Debtors - Export,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1200,KES
1-1203,Provision for Bad Debts,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1200,KES
1-1210,Other Receivables,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1200,KES
1-1211,Staff Debtors / Employee Loans,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1210,KES
1-1212,Director Loan Accounts,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1210,KES
1-1213,Intercompany Receivables,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1210,KES
1-1214,Insurance Claims Receivable,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1210,KES
1-1215,Deposits Receivable,CURRENT_RECEIVABLE,CURRENT_ASSETS,1-1210,KES
1-1220,Tax Receivables,CURRENT_TAX,CURRENT_ASSETS,1-1200,KES
1-1221,Input VAT (16%),CURRENT_TAX,CURRENT_ASSETS,1-1220,KES
1-1222,Withholding Tax Recoverable,CURRENT_TAX,CURRENT_ASSETS,1-1220,KES
1-1223,Corporate Tax Installments / Advance Tax,CURRENT_TAX,CURRENT_ASSETS,1-1220,KES
1-1300,Prepaid Expenses,CURRENT_PREPAID,CURRENT_ASSETS,1-1000,KES
1-1301,Prepaid Rent,CURRENT_PREPAID,CURRENT_ASSETS,1-1300,KES
1-1302,Prepaid Insurance,CURRENT_PREPAID,CURRENT_ASSETS,1-1300,KES
1-1303,Prepaid Licences and Subscriptions,CURRENT_PREPAID,CURRENT_ASSETS,1-1300,KES
1-1304,Prepaid Advertising,CURRENT_PREPAID,CURRENT_ASSETS,1-1300,KES
1-1305,Advance Payments to Suppliers,CURRENT_PREPAID,CURRENT_ASSETS,1-1300,KES
1-1306,Accrued Income,CURRENT_PREPAID,CURRENT_ASSETS,1-1300,KES
1-1400,Inventory,CURRENT_INVENTORY,CURRENT_ASSETS,1-1000,KES
1-1401,Raw Materials,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-1402,Work-in-Progress,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-1403,Finished Goods,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-1404,Merchandise / Trading Stock,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-1405,Packaging Materials,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-1406,Consumables and Spare Parts,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-1407,Goods in Transit,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-1408,Provision for Obsolete Stock,CURRENT_INVENTORY,CURRENT_ASSETS,1-1400,KES
1-2000,Non-Current Assets,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-0000,KES
1-2100,Property Plant and Equipment,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2000,KES
1-2101,Land,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2102,Buildings,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2103,Motor Vehicles,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2104,Plant and Machinery,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2105,Office Equipment,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2106,Computer Hardware,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2107,Furniture and Fittings,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2108,Leasehold Improvements,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2109,Capital Work-in-Progress,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2150,Accumulated Depreciation - PPE,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2100,KES
1-2151,Accum. Depr. - Buildings,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2150,KES
1-2152,Accum. Depr. - Motor Vehicles,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2150,KES
1-2153,Accum. Depr. - Plant and Machinery,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2150,KES
1-2154,Accum. Depr. - Office Equipment,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2150,KES
1-2155,Accum. Depr. - Computer Hardware,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2150,KES
1-2156,Accum. Depr. - Furniture and Fittings,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2150,KES
1-2157,Accum. Depr. - Leasehold Improvements,NON_CURRENT_PPE,NON_CURRENT_ASSETS,1-2150,KES
1-2200,Right-of-Use Assets (IFRS 16),NON_CURRENT_LEASE,NON_CURRENT_ASSETS,1-2000,KES
1-2201,ROU Asset - Property Leases,NON_CURRENT_LEASE,NON_CURRENT_ASSETS,1-2200,KES
1-2202,ROU Asset - Vehicle Leases,NON_CURRENT_LEASE,NON_CURRENT_ASSETS,1-2200,KES
1-2203,ROU Asset - Equipment Leases,NON_CURRENT_LEASE,NON_CURRENT_ASSETS,1-2200,KES
1-2210,Accum. Depr. - ROU Assets,NON_CURRENT_LEASE,NON_CURRENT_ASSETS,1-2200,KES
1-2300,Intangible Assets,NON_CURRENT_INTANGIBLE,NON_CURRENT_ASSETS,1-2000,KES
1-2301,Goodwill,NON_CURRENT_INTANGIBLE,NON_CURRENT_ASSETS,1-2300,KES
1-2302,Computer Software,NON_CURRENT_INTANGIBLE,NON_CURRENT_ASSETS,1-2300,KES
1-2303,Trademarks and Patents,NON_CURRENT_INTANGIBLE,NON_CURRENT_ASSETS,1-2300,KES
1-2304,Licences,NON_CURRENT_INTANGIBLE,NON_CURRENT_ASSETS,1-2300,KES
1-2305,Accum. Amortisation - Intangibles,NON_CURRENT_INTANGIBLE,NON_CURRENT_ASSETS,1-2300,KES
1-2400,Non-Current Investments,NON_CURRENT_INVESTMENT,NON_CURRENT_ASSETS,1-2000,KES
1-2401,Investment in Subsidiaries,NON_CURRENT_INVESTMENT,NON_CURRENT_ASSETS,1-2400,KES
1-2402,Investment in Associates,NON_CURRENT_INVESTMENT,NON_CURRENT_ASSETS,1-2400,KES
1-2403,Investment in Joint Ventures,NON_CURRENT_INVESTMENT,NON_CURRENT_ASSETS,1-2400,KES
1-2404,Government Securities / T-Bills,NON_CURRENT_INVESTMENT,NON_CURRENT_ASSETS,1-2400,KES
1-2405,Investment Property,NON_CURRENT_INVESTMENT,NON_CURRENT_ASSETS,1-2400,KES
1-2500,Other Non-Current Assets,NON_CURRENT_OTHER,NON_CURRENT_ASSETS,1-2000,KES
1-2501,Long-term Prepayments,NON_CURRENT_OTHER,NON_CURRENT_ASSETS,1-2500,KES
1-2502,Long-term Employee Advances,NON_CURRENT_OTHER,NON_CURRENT_ASSETS,1-2500,KES
1-2503,Deferred Tax Asset,NON_CURRENT_DEFERRED_TAX,NON_CURRENT_ASSETS,1-2500,KES
2-0000,LIABILITIES,CURRENT_PAYABLE,CURRENT_LIABILITIES,,KES
2-1000,Current Liabilities,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-0000,KES
2-1100,Accounts Payable,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1000,KES
2-1101,Trade Creditors - Local Suppliers,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1100,KES
2-1102,Trade Creditors - Import Suppliers,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1100,KES
2-1103,Intercompany Payables,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1100,KES
2-1110,Accrued Expenses,CURRENT_ACCRUED,CURRENT_LIABILITIES,2-1100,KES
2-1111,Accrued Salaries and Wages,CURRENT_ACCRUED,CURRENT_LIABILITIES,2-1110,KES
2-1112,Accrued Leave Pay,CURRENT_ACCRUED,CURRENT_LIABILITIES,2-1110,KES
2-1113,Accrued Utilities,CURRENT_ACCRUED,CURRENT_LIABILITIES,2-1110,KES
2-1114,Accrued Professional Fees,CURRENT_ACCRUED,CURRENT_LIABILITIES,2-1110,KES
2-1115,Accrued Rent,CURRENT_ACCRUED,CURRENT_LIABILITIES,2-1110,KES
2-1120,Other Current Payables,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1100,KES
2-1121,Customer Deposits / Advance Receipts,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1120,KES
2-1122,Dividends Payable,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1120,KES
2-1123,Bank Overdraft,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1120,KES
2-1124,Current Portion of Long-term Loans,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1120,KES
2-1125,Current Portion of Lease Liability,CURRENT_PAYABLE,CURRENT_LIABILITIES,2-1120,KES
2-1200,Deferred Revenue (IFRS 15),CURRENT_DEFERRED_REVENUE,CURRENT_LIABILITIES,2-1000,KES
2-1201,Contract Liabilities - Short-term,CURRENT_DEFERRED_REVENUE,CURRENT_LIABILITIES,2-1200,KES
2-1202,Subscription Revenue Received in Advance,CURRENT_DEFERRED_REVENUE,CURRENT_LIABILITIES,2-1200,KES
2-1203,Retentions Payable,CURRENT_DEFERRED_REVENUE,CURRENT_LIABILITIES,2-1200,KES
2-2100,Tax Payables,CURRENT_TAX,CURRENT_LIABILITIES,2-1000,KES
2-2101,VAT Payable (Output VAT 16%),CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2102,PAYE Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2103,NSSF Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2104,NHIF / SHIF Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2105,Housing Levy Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2106,NITA Levy Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2107,Withholding Tax Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2108,Excise Duty Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2109,Corporate Tax Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-2110,Withholding Tax Payable,CURRENT_TAX,CURRENT_LIABILITIES,2-2100,KES
2-3000,Non-Current Liabilities,NON_CURRENT_LONG_TERM_DEBT,NON_CURRENT_LIABILITIES,2-0000,KES
2-3100,Long-term Borrowings,NON_CURRENT_LONG_TERM_DEBT,NON_CURRENT_LIABILITIES,2-3000,KES
2-3101,Long-term Bank Loans,NON_CURRENT_LONG_TERM_DEBT,NON_CURRENT_LIABILITIES,2-3100,KES
2-3102,Bonds and Debentures Payable,NON_CURRENT_LONG_TERM_DEBT,NON_CURRENT_LIABILITIES,2-3100,KES
2-3103,Shareholder Loans,NON_CURRENT_LONG_TERM_DEBT,NON_CURRENT_LIABILITIES,2-3100,KES
2-3200,Lease Liabilities (IFRS 16),NON_CURRENT_LEASE,NON_CURRENT_LIABILITIES,2-3000,KES
2-3201,Long-term Lease Liability - Property,NON_CURRENT_LEASE,NON_CURRENT_LIABILITIES,2-3200,KES
2-3202,Long-term Lease Liability - Vehicles,NON_CURRENT_LEASE,NON_CURRENT_LIABILITIES,2-3200,KES
2-3203,Finance Lease Liability,NON_CURRENT_LEASE,NON_CURRENT_LIABILITIES,2-3200,KES
2-3300,Non-Current Provisions,NON_CURRENT_PROVISION,NON_CURRENT_LIABILITIES,2-3000,KES
2-3301,Provision for Employee Gratuity,NON_CURRENT_PROVISION,NON_CURRENT_LIABILITIES,2-3300,KES
2-3302,Provision for Legal Claims,NON_CURRENT_PROVISION,NON_CURRENT_LIABILITIES,2-3300,KES
2-3303,Provision for Restructuring,NON_CURRENT_PROVISION,NON_CURRENT_LIABILITIES,2-3300,KES
2-3304,Asset Retirement Obligation,NON_CURRENT_PROVISION,NON_CURRENT_LIABILITIES,2-3300,KES
2-3400,Deferred Tax Liability,NON_CURRENT_DEFERRED_TAX,NON_CURRENT_LIABILITIES,2-3000,KES
3-0000,EQUITY,SHARE_CAPITAL,EQUITY,,KES
3-1000,Share Capital,SHARE_CAPITAL,EQUITY,3-0000,KES
3-1001,Ordinary Share Capital,SHARE_CAPITAL,EQUITY,3-1000,KES
3-1002,Preference Share Capital,SHARE_CAPITAL,EQUITY,3-1000,KES
3-1003,Share Premium,SHARE_CAPITAL,EQUITY,3-1000,KES
3-2000,Retained Earnings,RETAINED_EARNINGS,EQUITY,3-0000,KES
3-2001,Retained Earnings - Prior Years,RETAINED_EARNINGS,EQUITY,3-2000,KES
3-2002,Profit / Loss for the Year,RETAINED_EARNINGS,EQUITY,3-2000,KES
3-3000,Other Reserves,OTHER_COMPREHENSIVE_INCOME,EQUITY,3-0000,KES
3-3001,Revaluation Reserve,OTHER_COMPREHENSIVE_INCOME,EQUITY,3-3000,KES
3-3002,Foreign Currency Translation Reserve,OTHER_COMPREHENSIVE_INCOME,EQUITY,3-3000,KES
3-3003,Fair Value Reserve (FVOCI),OTHER_COMPREHENSIVE_INCOME,EQUITY,3-3000,KES
3-3004,General Reserve,OTHER_COMPREHENSIVE_INCOME,EQUITY,3-3000,KES
3-4000,Dividends and Drawings,DIVIDENDS_DRAWINGS,EQUITY,3-0000,KES
3-4001,Dividends Declared - Ordinary,DIVIDENDS_DRAWINGS,EQUITY,3-4000,KES
3-4002,Dividends Declared - Preference,DIVIDENDS_DRAWINGS,EQUITY,3-4000,KES
4-0000,REVENUE,OPERATING_REVENUE,REVENUE,,KES
4-1000,Operating Revenue,OPERATING_REVENUE,REVENUE,4-0000,KES
4-1100,Sales of Goods,OPERATING_REVENUE,REVENUE,4-1000,KES
4-1101,Local Sales,OPERATING_REVENUE,REVENUE,4-1100,KES
4-1102,Export Sales,OPERATING_REVENUE,REVENUE,4-1100,KES
4-1103,Sales Returns and Allowances,OPERATING_REVENUE,REVENUE,4-1100,KES
4-1104,Trade Discounts Allowed,OPERATING_REVENUE,REVENUE,4-1100,KES
4-1200,Service Revenue,OPERATING_REVENUE,REVENUE,4-1000,KES
4-1201,Professional / Consulting Services,OPERATING_REVENUE,REVENUE,4-1200,KES
4-1202,Managed Services Revenue,OPERATING_REVENUE,REVENUE,4-1200,KES
4-1203,Contract Revenue (IFRS 15),OPERATING_REVENUE,REVENUE,4-1200,KES
4-1204,Subscription Revenue,OPERATING_REVENUE,REVENUE,4-1200,KES
4-1300,Rental and Lease Income,OPERATING_REVENUE,REVENUE,4-1000,KES
4-1301,Rental Income - Property,OPERATING_REVENUE,REVENUE,4-1300,KES
4-1302,Rental Income - Equipment,OPERATING_REVENUE,REVENUE,4-1300,KES
4-1400,Commission and Agency Income,OPERATING_REVENUE,REVENUE,4-1000,KES
4-1401,Commission Income,OPERATING_REVENUE,REVENUE,4-1400,KES
4-1402,Agency / Distribution Income,OPERATING_REVENUE,REVENUE,4-1400,KES
4-1403,Franchise Income,OPERATING_REVENUE,REVENUE,4-1400,KES
4-2000,Other Income,OTHER_INCOME,REVENUE,4-0000,KES
4-2001,Gain on Disposal of Assets,OTHER_INCOME,REVENUE,4-2000,KES
4-2002,Insurance Claim Income,OTHER_INCOME,REVENUE,4-2000,KES
4-2003,Government Grants Income,OTHER_INCOME,REVENUE,4-2000,KES
4-2004,Foreign Exchange Gain,OTHER_INCOME,REVENUE,4-2000,KES
4-2005,Bad Debt Recovery,OTHER_INCOME,REVENUE,4-2000,KES
4-2006,Scrap and By-product Sales,OTHER_INCOME,REVENUE,4-2000,KES
4-2007,Sundry / Miscellaneous Income,OTHER_INCOME,REVENUE,4-2000,KES
4-3000,Finance Income,FINANCE_INCOME,REVENUE,4-0000,KES
4-3001,Interest Income - Bank Deposits,FINANCE_INCOME,REVENUE,4-3000,KES
4-3002,Interest Income - Staff Loans,FINANCE_INCOME,REVENUE,4-3000,KES
4-3003,Dividend Income,FINANCE_INCOME,REVENUE,4-3000,KES
4-3004,Income from Government Securities,FINANCE_INCOME,REVENUE,4-3000,KES
5-0000,COST OF SALES,COGS,COST_OF_SALES,,KES
5-1000,Direct Costs,COGS,COST_OF_SALES,5-0000,KES
5-1001,Opening Stock,COGS,COST_OF_SALES,5-1000,KES
5-1002,Purchases - Goods / Raw Materials,COGS,COST_OF_SALES,5-1000,KES
5-1003,Import Duties and Clearing Costs,COGS,COST_OF_SALES,5-1000,KES
5-1004,Freight and Carriage Inwards,COGS,COST_OF_SALES,5-1000,KES
5-1005,Direct Labour / Manufacturing Wages,COGS,COST_OF_SALES,5-1000,KES
5-1006,Sub-Contractor Costs,COGS,COST_OF_SALES,5-1000,KES
5-1007,Manufacturing Overheads,COGS,COST_OF_SALES,5-1000,KES
5-1008,Closing Stock,COGS,COST_OF_SALES,5-1000,KES
5-1009,Provision for Inventory Write-down,COGS,COST_OF_SALES,5-1000,KES
6-0000,OPERATING EXPENSES,OPERATING_EXPENSES,OPERATING_EXPENSES,,KES
6-1000,Personnel Costs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-1001,Salaries and Wages,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1002,Directors Remuneration,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1003,Overtime Pay,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1004,NSSF Employer Contribution,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1005,NHIF / SHIF Employer Contribution,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1006,Housing Levy Employer Contribution,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1007,NITA Levy,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1008,Leave Pay Expense,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1009,Gratuity / End of Service Benefits,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1010,Staff Medical and Insurance,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1011,Staff Training and Development,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1012,Staff Transport Allowance,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1013,Staff Uniforms and PPE,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1014,Staff Welfare and Team Building,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-1015,Recruitment Costs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-1000,KES
6-2000,Occupancy and Premises Costs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-2001,Rent Expense,OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-2002,Rates and Land Rent,OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-2003,Electricity,OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-2004,Water,OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-2005,Building Maintenance and Repairs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-2006,Security Services,OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-2007,Cleaning and Sanitation,OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-2008,Short-term Lease Expense (IFRS 16),OPERATING_EXPENSES,OPERATING_EXPENSES,6-2000,KES
6-3000,Administrative Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-3001,Office Supplies and Stationery,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3002,Telephone and Internet,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3003,Postage and Courier,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3004,Printing and Photocopying,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3005,Audit and Accounting Fees,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3006,Legal and Professional Fees,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3007,Consulting and Advisory Fees,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3008,Board and AGM Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3009,Licences and Government Fees,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3010,Subscriptions and Memberships,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3011,Bank Charges and Transaction Fees,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3012,Insurance - General,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3013,Insurance - Motor Vehicle,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3014,Directors and Officers Insurance,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-3015,Courier and Delivery Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-3000,KES
6-4000,Selling and Distribution Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-4001,Advertising and Promotion,OPERATING_EXPENSES,OPERATING_EXPENSES,6-4000,KES
6-4002,Digital Marketing and Social Media,OPERATING_EXPENSES,OPERATING_EXPENSES,6-4000,KES
6-4003,Marketing Events and Activations,OPERATING_EXPENSES,OPERATING_EXPENSES,6-4000,KES
6-4004,Sales Commissions,OPERATING_EXPENSES,OPERATING_EXPENSES,6-4000,KES
6-4005,Carriage Outwards / Delivery Costs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-4000,KES
6-4006,Customer Entertainment,OPERATING_EXPENSES,OPERATING_EXPENSES,6-4000,KES
6-4007,Market Research,OPERATING_EXPENSES,OPERATING_EXPENSES,6-4000,KES
6-5000,Motor Vehicle and Travel Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-5001,Fuel and Lubricants,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-5002,Vehicle Maintenance and Repairs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-5003,Vehicle Insurance,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-5004,Vehicle Licensing and NTSA Inspection,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-5005,Hired Transport and Taxis,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-5006,Staff Travel - Local,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-5007,Staff Travel - International,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-5008,Accommodation and Per Diem,OPERATING_EXPENSES,OPERATING_EXPENSES,6-5000,KES
6-6000,Information Technology Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-6001,Software Subscriptions and Licences,OPERATING_EXPENSES,OPERATING_EXPENSES,6-6000,KES
6-6002,IT Support and Maintenance,OPERATING_EXPENSES,OPERATING_EXPENSES,6-6000,KES
6-6003,Cloud Hosting and Server Costs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-6000,KES
6-6004,Cybersecurity and Data Protection,OPERATING_EXPENSES,OPERATING_EXPENSES,6-6000,KES
6-6005,Hardware Replacement and Consumables,OPERATING_EXPENSES,OPERATING_EXPENSES,6-6000,KES
6-7000,Depreciation and Amortisation,DEPRECIATION,OPERATING_EXPENSES,6-0000,KES
6-7001,Depreciation - Buildings,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7002,Depreciation - Motor Vehicles,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7003,Depreciation - Plant and Machinery,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7004,Depreciation - Office Equipment,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7005,Depreciation - Computer Hardware,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7006,Depreciation - Furniture and Fittings,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7007,Depreciation - Leasehold Improvements,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7008,Depreciation - ROU Assets (IFRS 16),DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7009,Amortisation - Computer Software,AMORTISATION,OPERATING_EXPENSES,6-7000,KES
6-7010,Amortisation - Licences and Patents,AMORTISATION,OPERATING_EXPENSES,6-7000,KES
6-7011,Impairment Loss - PPE,DEPRECIATION,OPERATING_EXPENSES,6-7000,KES
6-7012,Impairment Loss - Goodwill,AMORTISATION,OPERATING_EXPENSES,6-7000,KES
6-8000,Provisions and Write-offs,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-8001,Bad Debt Written Off,OPERATING_EXPENSES,OPERATING_EXPENSES,6-8000,KES
6-8002,Movement in Provision for Bad Debts,OPERATING_EXPENSES,OPERATING_EXPENSES,6-8000,KES
6-8003,Inventory Write-off,OPERATING_EXPENSES,OPERATING_EXPENSES,6-8000,KES
6-8004,Provision for Employee Benefits Charge,OPERATING_EXPENSES,OPERATING_EXPENSES,6-8000,KES
6-9000,Other Operating Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-0000,KES
6-9001,Donations and CSR Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-9000,KES
6-9002,Penalties and Fines,OPERATING_EXPENSES,OPERATING_EXPENSES,6-9000,KES
6-9003,Foreign Exchange Losses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-9000,KES
6-9004,Loss on Disposal of Assets,OPERATING_EXPENSES,OPERATING_EXPENSES,6-9000,KES
6-9005,Miscellaneous Expenses,OPERATING_EXPENSES,OPERATING_EXPENSES,6-9000,KES
7-0000,FINANCE COSTS,FINANCE_COST,FINANCE_COSTS,,KES
7-1000,Interest and Finance Charges,FINANCE_COST,FINANCE_COSTS,7-0000,KES
7-1001,Interest on Bank Loans,FINANCE_COST,FINANCE_COSTS,7-1000,KES
7-1002,Interest on Bank Overdraft,FINANCE_COST,FINANCE_COSTS,7-1000,KES
7-1003,Interest on Lease Liabilities (IFRS 16),FINANCE_COST,FINANCE_COSTS,7-1000,KES
7-1004,Loan Arrangement Fees,FINANCE_COST,FINANCE_COSTS,7-1000,KES
7-1005,Interest on Shareholder Loans,FINANCE_COST,FINANCE_COSTS,7-1000,KES
7-1006,Finance Charges - Hire Purchase,FINANCE_COST,FINANCE_COSTS,7-1000,KES
7-1007,Bond / Debenture Interest,FINANCE_COST,FINANCE_COSTS,7-1000,KES
8-0000,TAX EXPENSE,TAX_EXPENSE,TAX_EXPENSE,,KES
8-1000,Income Tax Expense,TAX_EXPENSE,TAX_EXPENSE,8-0000,KES
8-1001,Current Year Corporation Tax,TAX_EXPENSE,TAX_EXPENSE,8-1000,KES
8-1002,Prior Year Tax Adjustment,TAX_EXPENSE,TAX_EXPENSE,8-1000,KES
8-1003,Deferred Tax Charge / Credit,TAX_EXPENSE,TAX_EXPENSE,8-1000,KES`
  downloadBlob(csv, 'text/csv', 'coa-import-template.csv')
  toast.success('Template downloaded.')
}

// ── Import ────────────────────────────────────────────────────────────────────
function onFileChange(e) {
  importFile.value = e.target.files[0] ?? null
  importRows.value = []
  importErrors.value = []
  if (importFile.value) parseCsv(importFile.value)
}

function parseCsv(file) {
  const reader = new FileReader()
  reader.onload = (e) => {
    const text = e.target.result
    const lines = text.split('\n').filter(l => l.trim() && !l.startsWith('#'))
    if (lines.length < 2) { importErrors.value = [{ row: 0, msg: 'File is empty or has no data rows.' }]; return }

    const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, ''))
    const required = ['accountCode', 'accountName', 'accountSubtype']
    const missing  = required.filter(r => !headers.includes(r))
    if (missing.length) { importErrors.value = [{ row: 0, msg: `Missing required columns: ${missing.join(', ')}` }]; return }

    const existingCodes = new Set(allAccounts.value.map(a => a.accountCode))
    const validSubtypes = new Set(ACCOUNT_SUBTYPES.map(s => s.value))
    const validIfrs     = new Set(IFRS_CATEGORIES)

    const rows = []
    const errors = []
    const seenCodes = new Set()

    lines.slice(1).forEach((line, idx) => {
      const rowNum = idx + 2
      const vals   = splitCsvLine(line)
      const row    = Object.fromEntries(headers.map((h, i) => [h, (vals[i] ?? '').trim()]))

      const errs = []
      if (!row.accountCode)  errs.push('accountCode is required')
      if (!row.accountName)  errs.push('accountName is required')
      if (!row.accountSubtype) errs.push('accountSubtype is required')
      else if (!validSubtypes.has(row.accountSubtype)) errs.push(`Unknown accountSubtype: ${row.accountSubtype}`)
      if (row.ifrsCategory && !validIfrs.has(row.ifrsCategory)) errs.push(`Unknown ifrsCategory: ${row.ifrsCategory}`)
      if (row.accountCode && existingCodes.has(row.accountCode)) errs.push(`Account code ${row.accountCode} already exists`)
      if (row.accountCode && seenCodes.has(row.accountCode))     errs.push(`Duplicate code ${row.accountCode} in file`)
      if (row.accountCode) seenCodes.add(row.accountCode)

      if (errs.length) errors.push(...errs.map(msg => ({ row: rowNum, msg })))
      else rows.push(row)
    })

    importRows.value  = rows
    importErrors.value = errors
  }
  reader.readAsText(file)
}

// Simple CSV line splitter handling quoted fields
function splitCsvLine(line) {
  const result = []; let cur = ''; let inQ = false
  for (const ch of line) {
    if (ch === '"') { inQ = !inQ }
    else if (ch === ',' && !inQ) { result.push(cur); cur = '' }
    else cur += ch
  }
  result.push(cur)
  return result
}

async function doImport() {
  if (importErrors.value.length) { toast.error('Fix validation errors before importing.'); return }
  if (!importRows.value.length)  { toast.error('No valid rows to import.'); return }
  importing.value = true
  try {
    // Resolve parentAccountId from parentAccountCode
    const codeToId = Object.fromEntries(allAccounts.value.map(a => [a.accountCode, a.id]))
    const commands = importRows.value.map(r => ({
      entityId: entityId.value,
      accountCode: r.accountCode,
      accountName: r.accountName,
      accountSubtype: r.accountSubtype,
      ifrsCategory: r.ifrsCategory || 'OPERATING_EXPENSES',
      parentAccountId: r.parentAccountCode ? (codeToId[r.parentAccountCode] ?? undefined) : undefined,
      currencyCode: r.currencyCode || undefined,
    }))
    await accountsApi.importJson(entityId.value, commands)
    toast.success(`${commands.length} account(s) imported successfully.`)
    showImport.value = false
    importFile.value = null; importRows.value = []; importErrors.value = []
    if (importFileRef.value) importFileRef.value.value = ''
    invalidateAccountCache()
    await load()
  } catch {} finally { importing.value = false }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function downloadBlob(content, type, filename) {
  const blob = new Blob([content], { type })
  const url  = URL.createObjectURL(blob)
  const a    = Object.assign(document.createElement('a'), { href: url, download: filename })
  document.body.appendChild(a); a.click(); document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function today() { return new Date().toISOString().slice(0, 10) }

function typeColor(t) {
  return { ASSET: 'info', LIABILITY: 'warn', EQUITY: 'draft', REVENUE: 'approved', EXPENSE: 'error' }[t] ?? 'draft'
}

// Auto-set IFRS category from subtype
watch(() => form.value.accountSubtype, (st) => {
  if (!st) return
  const type = SUBTYPE_TO_TYPE[st]
  if (!form.value.ifrsCategory) {
    const defaults = {
      ASSET: 'CURRENT_ASSETS', LIABILITY: 'CURRENT_LIABILITIES',
      EQUITY: 'EQUITY', REVENUE: 'REVENUE', EXPENSE: 'OPERATING_EXPENSES',
    }
    form.value.ifrsCategory = defaults[type] ?? ''
  }
})
</script>

<template>
  <div class="page">
    <PageHeader title="Chart of Accounts" meta="IFRS-compliant account structure">
      <Button variant="ghost" icon="download" @click="downloadTemplate">Template</Button>
      <Button variant="ghost" icon="upload" @click="showImport = true">Import</Button>
      <Button variant="ghost" icon="download" @click="exportCsv">Export</Button>
      <Button variant="primary" icon="plus" @click="openNew">New account</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search">
        <ChipFilter v-for="f in TYPE_FILTERS" :key="f" :active="typeFilter === f" @click="typeFilter = f">{{ f }}</ChipFilter>
        <div style="margin-left:auto">
          <Segmented v-model="viewMode" :options="VIEW_OPTS" />
        </div>
      </TableToolbar>

      <!-- Bulk action bar (SYSTEM_ADMIN only, visible when rows are selected) -->
      <Transition name="bulk-bar">
        <div v-if="isSuperAdmin && selectedIds.size > 0" class="bulk-bar">
          <span class="bulk-count">{{ selectedIds.size }} account{{ selectedIds.size !== 1 ? 's' : '' }} selected</span>
          <Button variant="ghost" size="sm" @click="clearSelection">Clear</Button>
          <Button
            size="sm"
            style="background:oklch(0.55 0.22 25);color:#fff;border:none"
            @click="showBulkDeactivate = true"
          >
            Deactivate selected
          </Button>
        </div>
      </Transition>

      <div class="card">
        <div v-if="loading" style="padding:32px;text-align:center;color:var(--muted);font-size:13px">Loading…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th v-if="isSuperAdmin" class="cb-col">
                <input
                  type="checkbox"
                  class="cb"
                  :checked="allVisibleSelected"
                  :indeterminate="selectedIds.size > 0 && !allVisibleSelected"
                  @change="toggleSelectAll"
                />
              </th>
              <th style="width:100px">Code</th>
              <th>Account name</th>
              <th style="width:100px">Type</th>
              <th style="width:130px">Subtype</th>
              <th style="width:70px">Normal</th>
              <th style="width:150px">IFRS category</th>
              <th style="width:80px">Status</th>
              <th style="width:90px"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="a in visible"
              :key="a.id"
              :class="[!a.isActive ? 'row-inactive' : '', isSuperAdmin && selectedIds.has(a.id) ? 'row-selected' : '']"
            >
              <td v-if="isSuperAdmin" class="cb-col">
                <input
                  v-if="a.isActive"
                  type="checkbox"
                  class="cb"
                  :checked="selectedIds.has(a.id)"
                  @change="toggleSelect(a.id)"
                />
              </td>
              <td><code style="font-size:12px">{{ a.accountCode }}</code></td>
              <td>
                <div class="acct-name-cell" :style="{ paddingLeft: (acctDepth(a) * 18) + 'px' }">
                  <button
                    v-if="hasChildren(a.id) && viewMode === 'tree'"
                    class="collapse-btn"
                    @click.stop="toggleCollapse(a.id)"
                  >
                    <Ico name="chev-right" :size="10" :style="{ transform: collapsed.has(a.id) ? '' : 'rotate(90deg)', transition: 'transform .15s' }" />
                  </button>
                  <span>{{ a.accountName }}</span>
                  <span v-if="a.isHeader" class="header-badge" title="Header/summary account — posting blocked (IAS 1 §29)">Header</span>
                </div>
              </td>
              <td><Badge :status="typeColor(a.accountType)" :dot="false">{{ a.accountType }}</Badge></td>
              <td style="font-size:11.5px;color:var(--muted)">{{ a.accountSubtype?.replace(/_/g,' ') }}</td>
              <td><Badge :status="a.normalBalance === 'DEBIT' ? 'info' : 'posted'" :dot="false">{{ a.normalBalance === 'DEBIT' ? 'DR' : 'CR' }}</Badge></td>
              <td style="font-size:11.5px;color:var(--muted)">{{ a.ifrsCategory?.replace(/_/g,' ') }}</td>
              <td>
                <Badge v-if="a.isActive" status="active" :dot="false">Active</Badge>
                <Badge v-else status="void" :dot="false">Inactive</Badge>
              </td>
              <td>
                <div style="display:flex;gap:4px">
                  <Button variant="ghost" size="sm" @click="detailAcct = a">View</Button>
                  <Button variant="ghost" size="sm" @click="openEdit(a)">Edit</Button>
                </div>
              </td>
            </tr>
            <tr v-if="!loading && !visible.length">
              <td :colspan="isSuperAdmin ? 9 : 8" style="text-align:center;padding:32px;color:var(--muted);font-size:13px">
                No accounts found. <a style="color:var(--accent);cursor:pointer" @click="openNew">Create one</a> or <a style="color:var(--accent);cursor:pointer" @click="showImport=true">import a CoA</a>.
              </td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="visible.length" :label="`of ${allAccounts.length} accounts`" />
      </div>
    </div>

    <!-- ── Detail drawer ──────────────────────────────────────────────────── -->
    <Modal :open="!!detailAcct" :title="detailAcct?.accountCode" :subtitle="detailAcct?.accountName" :width="560" @close="detailAcct = null">
      <div v-if="detailAcct" class="detail-grid">
        <div class="drow"><span class="dlabel">Type</span><span>{{ detailAcct.accountType }}</span></div>
        <div class="drow"><span class="dlabel">Subtype</span><span>{{ detailAcct.accountSubtype?.replace(/_/g,' ') }}</span></div>
        <div class="drow"><span class="dlabel">Normal balance</span><span>{{ detailAcct.normalBalance }}</span></div>
        <div class="drow"><span class="dlabel">IFRS category</span><span>{{ detailAcct.ifrsCategory?.replace(/_/g,' ') }}</span></div>
        <div class="drow"><span class="dlabel">IFRS ref</span><span>{{ detailAcct.ifrsClassification || '—' }}</span></div>
        <div class="drow"><span class="dlabel">Currency</span><span>{{ detailAcct.currencyCode || 'Functional' }}</span></div>
        <div class="drow"><span class="dlabel">Temporary</span><span>{{ detailAcct.isTemporary ? 'Yes (closed at year-end)' : 'No (permanent)' }}</span></div>
        <div class="drow"><span class="dlabel">Status</span><span>{{ detailAcct.isActive ? 'Active' : 'Inactive' }}</span></div>
        <div class="drow"><span class="dlabel">Account ID</span><code style="font-size:11px">{{ detailAcct.id }}</code></div>
      </div>
      <template #footer>
        <Button variant="primary" icon="edit" @click="openEdit(detailAcct)">Edit</Button>
        <Button v-if="detailAcct?.isActive" variant="ghost" @click="confirmDeactivate(detailAcct)">Deactivate</Button>
        <Button variant="ghost" @click="detailAcct = null">Close</Button>
      </template>
    </Modal>

    <!-- ── New account ────────────────────────────────────────────────────── -->
    <Modal :open="showNew" title="New account" :width="560" @close="showNew = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Account code <span class="req">*</span></label>
          <div style="position:relative">
            <input v-model="form.accountCode" class="input" style="width:100%;box-sizing:border-box" placeholder="e.g. 1-1111" @blur="checkCode" />
            <span v-if="codeValid === true"  style="position:absolute;right:10px;top:50%;transform:translateY(-50%);color:oklch(0.52 0.18 145);font-size:12px">✓ Available</span>
            <span v-if="codeValid === false" style="position:absolute;right:10px;top:50%;transform:translateY(-50%);color:oklch(0.55 0.18 15);font-size:12px">✕ Taken</span>
          </div>
        </div>
        <div class="field">
          <label>Account name <span class="req">*</span></label>
          <input v-model="form.accountName" class="input" style="width:100%;box-sizing:border-box" placeholder="e.g. Petty Cash" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Account subtype <span class="req">*</span></label>
          <SearchableSelect
            v-model="form.accountSubtype"
            :options="['ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE'].flatMap(type => ACCOUNT_SUBTYPES.filter(x => x.type === type).map(s => ({ value: s.value, label: s.label, group: type })))"
            placeholder="— Select subtype —"
            style="width:100%;box-sizing:border-box"
          />
        </div>
        <div class="field">
          <label>IFRS category</label>
          <SearchableSelect
            v-model="form.ifrsCategory"
            :options="[{ value: '', label: '— Auto —' }, ...IFRS_CATEGORIES.map(c => ({ value: c, label: c.replace(/_/g, ' ') }))]"
            placeholder="— Auto —"
            style="width:100%;box-sizing:border-box"
          />
        </div>
        <div class="field">
          <label>Currency code</label>
          <input v-model="form.currencyCode" class="input" style="width:100%;box-sizing:border-box" placeholder="KES (default)" maxlength="3" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Parent account</label>
          <SearchableSelect
            v-model="form.parentAccountId"
            :options="allAccounts.map(a => ({ value: a.id, label: `${a.accountCode} · ${a.accountName}` }))"
            placeholder="— None (root) —"
            style="width:100%;box-sizing:border-box"
          />
        </div>
      </div>
      <template #footer>
        <Button variant="primary" :loading="saving" @click="createAccount">Create account</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Edit account ───────────────────────────────────────────────────── -->
    <Modal :open="showEdit" title="Edit account" :width="520" @close="showEdit = false">
      <div class="form-grid" style="grid-template-columns:1fr 1fr">
        <div class="field">
          <label>Account code</label>
          <input v-model="form.accountCode" class="input" />
        </div>
        <div class="field">
          <label>Account name <span class="req">*</span></label>
          <input v-model="form.accountName" class="input" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Account subtype <span class="req">*</span></label>
          <SearchableSelect
            v-model="form.accountSubtype"
            :options="['ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE'].flatMap(type => ACCOUNT_SUBTYPES.filter(x => x.type === type).map(s => ({ value: s.value, label: s.label, group: type })))"
            placeholder="— Select subtype —"
          />
        </div>
        <div class="field">
          <label>IFRS category</label>
          <SearchableSelect
            v-model="form.ifrsCategory"
            :options="IFRS_CATEGORIES.map(c => ({ value: c, label: c.replace(/_/g, ' ') }))"
            placeholder="— Select category —"
          />
        </div>
      </div>
      <template #footer>
        <Button variant="primary" :loading="saving" @click="updateAccount">Save changes</Button>
        <Button variant="ghost" @click="showEdit = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Import modal ───────────────────────────────────────────────────── -->
    <Modal :open="showImport" title="Import Chart of Accounts" subtitle="Upload a CSV file to bulk-create accounts" :width="640" @close="showImport = false">
      <div class="import-body">
        <Banner kind="info" icon="info" style="margin-bottom:12px">
          CSV must include: <strong>accountCode, accountName, accountSubtype</strong>.
          Optional: ifrsCategory, parentAccountCode, currencyCode.
          <a style="color:var(--accent);cursor:pointer;margin-left:6px" @click="downloadTemplate">Download template →</a>
        </Banner>

        <div class="file-drop" @click="importFileRef?.click()">
          <Ico name="upload" :size="22" style="color:var(--muted)" />
          <div style="font-size:13px;font-weight:600;margin-top:8px">
            {{ importFile ? importFile.name : 'Click to select a CSV file' }}
          </div>
          <div style="font-size:12px;color:var(--muted);margin-top:2px">
            {{ importFile ? `${importRows.length} valid rows` : '.csv files only' }}
          </div>
          <input ref="importFileRef" type="file" accept=".csv,text/csv" style="display:none" @change="onFileChange" />
        </div>

        <!-- Validation errors -->
        <div v-if="importErrors.length" class="import-errors">
          <div class="import-errors-head">
            <Ico name="warn" :size="13" />
            {{ importErrors.length }} validation error{{ importErrors.length > 1 ? 's' : '' }} — fix in your file and re-upload
          </div>
          <div v-for="(e, i) in importErrors.slice(0, 20)" :key="i" class="import-error-row">
            <span class="row-tag">Row {{ e.row }}</span>{{ e.msg }}
          </div>
          <div v-if="importErrors.length > 20" style="font-size:11.5px;color:var(--muted);margin-top:4px">
            … and {{ importErrors.length - 20 }} more errors
          </div>
        </div>

        <!-- Preview -->
        <div v-else-if="importRows.length" class="import-preview">
          <div style="font-size:12px;font-weight:600;color:var(--muted);margin-bottom:6px">Preview — {{ importRows.length }} row(s) to import</div>
          <table class="tbl" style="font-size:12px">
            <thead>
              <tr><th>Code</th><th>Name</th><th>Subtype</th><th>IFRS</th><th>Parent code</th></tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in importRows.slice(0, 10)" :key="i">
                <td><code>{{ r.accountCode }}</code></td>
                <td>{{ r.accountName }}</td>
                <td style="font-size:11px;color:var(--muted)">{{ r.accountSubtype }}</td>
                <td style="font-size:11px;color:var(--muted)">{{ r.ifrsCategory || '—' }}</td>
                <td style="font-size:11px;color:var(--muted)">{{ r.parentAccountCode || '—' }}</td>
              </tr>
              <tr v-if="importRows.length > 10">
                <td colspan="5" style="text-align:center;color:var(--muted);font-size:11.5px">… {{ importRows.length - 10 }} more rows</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <template #footer>
        <Button
          variant="primary"
          icon="upload"
          :disabled="!importRows.length || !!importErrors.length"
          :loading="importing"
          @click="doImport"
        >Import {{ importRows.length || '' }} accounts</Button>
        <Button variant="ghost" @click="showImport = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Bulk deactivate confirm ──────────────────────────────────────── -->
    <Modal :open="showBulkDeactivate" title="Bulk deactivate accounts" :width="460" @close="showBulkDeactivate = false">
      <div style="font-size:13px;line-height:1.6;margin:0 0 12px">
        You are about to deactivate <strong>{{ selectedIds.size }} account{{ selectedIds.size !== 1 ? 's' : '' }}</strong>.
        Accounts with existing ledger history will be skipped. This action cannot be undone — all historical data is preserved.
      </div>
      <Banner kind="warn" icon="warn">
        This operation is restricted to <strong>SYSTEM_ADMIN</strong> users only.
        Deactivated accounts cannot be used in new journal entries.
      </Banner>
      <template #footer>
        <Button
          variant="primary"
          style="background:oklch(0.55 0.22 25);color:#fff;border:none"
          :loading="bulkDeactivating"
          @click="doBulkDeactivate"
        >
          Deactivate {{ selectedIds.size }} account{{ selectedIds.size !== 1 ? 's' : '' }}
        </Button>
        <Button variant="ghost" @click="showBulkDeactivate = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Deactivate confirm ─────────────────────────────────────────────── -->
    <Modal :open="showDeactivate" title="Deactivate account" :width="420" @close="showDeactivate = false">
      <p style="font-size:13px;line-height:1.6;margin:0">
        Are you sure you want to deactivate <strong>{{ _deactivateTarget?.accountCode }} · {{ _deactivateTarget?.accountName }}</strong>?
        This will prevent it from being used in new journal entries. All historical data is preserved.
      </p>
      <Banner kind="warn" icon="warn" style="margin-top:12px">
        Deactivation will be blocked if this account has ledger history.
      </Banner>
      <template #footer>
        <Button variant="primary" style="background:var(--neg,#e53e3e)" :loading="saving" @click="doDeactivate">Deactivate</Button>
        <Button variant="ghost" @click="showDeactivate = false">Cancel</Button>
      </template>
    </Modal>

  </div>
</template>

<style scoped>
.row-inactive  { opacity: 0.5; }
.row-selected  { background: color-mix(in oklab, oklch(0.55 0.22 25) 5%, var(--surface)) !important; }

/* ── Checkbox column ───────────────── */
.cb-col { width: 36px; padding: 0 8px; }
.cb {
  width: 14px; height: 14px;
  accent-color: oklch(0.55 0.22 25);
  cursor: pointer;
}

/* ── Bulk action bar ───────────────── */
.bulk-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  background: color-mix(in oklab, oklch(0.55 0.22 25) 8%, var(--surface));
  border: 1px solid color-mix(in oklab, oklch(0.55 0.22 25) 30%, transparent);
  border-radius: 8px;
  font-size: 13px;
}
.bulk-count {
  font-weight: 600;
  color: oklch(0.40 0.18 25);
  flex: 1;
}

.bulk-bar-enter-active, .bulk-bar-leave-active { transition: opacity 0.15s, transform 0.15s; }
.bulk-bar-enter-from, .bulk-bar-leave-to { opacity: 0; transform: translateY(-6px); }

.acct-name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.collapse-btn {
  background: none; border: none; cursor: pointer; padding: 2px;
  color: var(--muted); display: flex; align-items: center; flex-shrink: 0;
}
.collapse-btn:hover { color: var(--text); }
.header-badge {
  font-size: 10px; font-weight: 600; letter-spacing: .4px; padding: 1px 6px;
  border-radius: 4px; background: oklch(0.93 0.05 270); color: oklch(0.42 0.18 270);
  border: 1px solid oklch(0.80 0.10 270); flex-shrink: 0; white-space: nowrap;
  cursor: default;
}

.req { color: oklch(0.55 0.18 15); }

/* ── Detail grid ───────────────── */
.detail-grid { display: flex; flex-direction: column; gap: 0; }
.drow {
  display: flex; align-items: baseline; gap: 12px;
  padding: 8px 0; border-bottom: 1px solid var(--border-2, var(--border));
  font-size: 13px;
}
.drow:last-child { border-bottom: none; }
.dlabel { width: 130px; flex-shrink: 0; font-size: 11.5px; color: var(--muted); font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; }

/* ── Import ────────────────────── */
.import-body { display: flex; flex-direction: column; gap: 12px; }

.file-drop {
  border: 2px dashed var(--border-strong);
  border-radius: 10px;
  padding: 28px;
  text-align: center;
  cursor: pointer;
  transition: border-color .15s, background .15s;
}
.file-drop:hover { border-color: var(--accent); background: color-mix(in oklab, var(--accent) 4%, var(--surface)); }

.import-errors {
  border: 1px solid color-mix(in oklab, oklch(0.55 0.18 15) 30%, transparent);
  background: color-mix(in oklab, oklch(0.55 0.18 15) 6%, var(--surface));
  border-radius: 8px;
  padding: 10px 14px;
}
.import-errors-head {
  display: flex; align-items: center; gap: 6px;
  font-size: 12.5px; font-weight: 600;
  color: oklch(0.40 0.16 15); margin-bottom: 8px;
}
.import-error-row {
  font-size: 12px; color: oklch(0.38 0.14 15);
  padding: 3px 0; display: flex; align-items: baseline; gap: 8px;
}
.row-tag {
  font-size: 10.5px; font-weight: 700; font-family: monospace;
  background: color-mix(in oklab, oklch(0.55 0.18 15) 15%, transparent);
  padding: 1px 5px; border-radius: 3px; flex-shrink: 0;
}

.import-preview { border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.import-preview > div:first-child { padding: 8px 12px; border-bottom: 1px solid var(--border); background: var(--surface-2); }
</style>

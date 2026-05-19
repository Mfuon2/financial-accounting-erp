<script setup>
import { ref, computed, onMounted } from 'vue'
import { journals as journalsApi, periods as periodsApi } from '@/api/index.js'
import { useAuth }         from '@/composables/useAuth.js'
import { useOrganization } from '@/composables/useOrganization.js'
import { useToast } from '@/composables/useToast.js'
import { useAccountCache } from '@/composables/useAccountCache.js'
import { fmt, fmtDate, fmtDateTime } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Ico from '@/components/primitives/Ico.vue'
import Badge from '@/components/primitives/Badge.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import AccountCombobox  from '@/components/primitives/AccountCombobox.vue'
import AmountInput      from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { currentUser } = useAuth()
const { org } = useOrganization()
const { toast } = useToast()

const functionalCcy = computed(() => org.value?.functionalCurrency ?? 'KES')
const acctCache = useAccountCache()

// ── State ─────────────────────────────────────────────────────────────────────
const allJournals  = ref([])
const allPeriods   = ref([])
const loading      = ref(false)
const tab          = ref('ALL')
const search       = ref('')
const drawer       = ref(null)
const showNew      = ref(false)
const showImport   = ref(false)
const actionLoading = ref(false)

// reject modal
const showReject   = ref(false)
const rejectReason = ref('')

// import state
const importFile   = ref(null)
const importRows   = ref([])
const importErrors = ref([])
const importLoading = ref(false)

const entityId = computed(() => currentUser.value?.entityId ?? 'current')

// ── Account lookups (from cache) ──────────────────────────────────────────────
const accountById   = computed(() => Object.fromEntries(acctCache.accounts.value.map(a => [a.id, a])))
const accountByCode = computed(() => Object.fromEntries(acctCache.accounts.value.map(a => [a.accountCode, a])))

// ── Periods for new JE form ───────────────────────────────────────────────────
const openPeriods = computed(() => allPeriods.value.filter(p => p.status === 'OPEN' || p.status === 'ADJUSTING'))
const periodById = computed(() => Object.fromEntries(allPeriods.value.map(p => [p.id, p])))

// ── Data load ─────────────────────────────────────────────────────────────────
function toArray(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.content)) return data.content
  return []
}

async function load() {
  loading.value = true
  try {
    const [je, per] = await Promise.all([
      journalsApi.list({ entityId: entityId.value }),
      periodsApi.list({ entityId: entityId.value }),
    ])
    allJournals.value = toArray(je)
    allPeriods.value  = toArray(per)
    // Accounts use the shared cache — only hits the API when stale
    await acctCache.load(entityId.value)
  } catch {
    toast.error('Failed to load journal entries')
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ── Filtering ─────────────────────────────────────────────────────────────────
const counts = computed(() => ({
  ALL:              allJournals.value.length,
  DRAFT:            allJournals.value.filter(j => j.status === 'DRAFT').length,
  PENDING_APPROVAL: allJournals.value.filter(j => j.status === 'PENDING_APPROVAL').length,
  POSTED:           allJournals.value.filter(j => j.status === 'POSTED').length,
}))

const tabs = computed(() => [
  { id: 'ALL',              label: 'All',              count: counts.value.ALL },
  { id: 'DRAFT',            label: 'Drafts',           count: counts.value.DRAFT },
  { id: 'PENDING_APPROVAL', label: 'Pending approval', count: counts.value.PENDING_APPROVAL },
  { id: 'POSTED',           label: 'Posted',           count: counts.value.POSTED },
])

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return allJournals.value.filter(j => {
    const matchTab    = tab.value === 'ALL' || j.status === tab.value
    const ref         = j._ref || j.reference || j.id
    const matchSearch = !q || ref.toLowerCase().includes(q) || (j.description || '').toLowerCase().includes(q)
    return matchTab && matchSearch
  })
})

// ── Display helpers ───────────────────────────────────────────────────────────
function displayRef(j) {
  if (!j) return ''
  return j._ref || j.reference || (j.id ? j.id.substring(0, 8).toUpperCase() : '—')
}

function lineAccountDisplay(line) {
  if (line._accountCode) return `${line._accountCode}  ${line._accountName || ''}`
  const acct = accountById.value[line.accountId]
  if (acct) return `${acct.accountCode}  ${acct.accountName}`
  return line.accountId
}

function totalDebit(j) {
  return (j.lines || []).reduce((s, l) => s + Number(l.functionalDebit || l.debitAmount || 0), 0)
}
function totalCredit(j) {
  return (j.lines || []).reduce((s, l) => s + Number(l.functionalCredit || l.creditAmount || 0), 0)
}

// ── Export CSV ────────────────────────────────────────────────────────────────
function exportCSV() {
  const header = ['Reference', 'Date', 'Period ID', 'Description', 'Source', 'Status', 'Account', 'Memo', 'Debit', 'Credit', 'Currency']
  const rows = []
  for (const j of allJournals.value) {
    for (const l of (j.lines || [])) {
      rows.push([
        displayRef(j),
        j.transDate || '',
        j.periodId || '',
        (j.description || '').replace(/,/g, ';'),
        j.sourceType || '',
        j.status,
        lineAccountDisplay(l).replace(/,/g, ';'),
        (l.description || '').replace(/,/g, ';'),
        l.debitAmount || l.functionalDebit || 0,
        l.creditAmount || l.functionalCredit || 0,
        l.currencyCode || functionalCcy.value,
      ])
    }
  }
  const csv = [header, ...rows].map(r => r.join(',')).join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url  = URL.createObjectURL(blob)
  const a    = document.createElement('a')
  a.href     = url
  a.download = `journal-entries-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  toast.success('Journal entries exported')
}

// ── Template download ─────────────────────────────────────────────────────────
function downloadTemplate() {
  const header = ['batch_ref', 'date', 'description', 'account_code', 'memo', 'debit', 'credit', 'currency', 'exchange_rate']
  const example = [
    ['IMPORT-001', '2026-03-31', 'March payroll', '5-2000', 'Salaries gross',  '624000', '',       'KES', '1'],
    ['IMPORT-001', '2026-03-31', 'March payroll', '1-1100', 'Net pay',         '',       '553000', 'KES', '1'],
    ['IMPORT-001', '2026-03-31', 'March payroll', '2-2110', 'PAYE accrual',    '',       '71000',  'KES', '1'],
    ['IMPORT-002', '2026-03-31', 'Accrued rent',  '5-3000', 'Office rent Q1',  '120000', '',       'KES', '1'],
    ['IMPORT-002', '2026-03-31', 'Accrued rent',  '2-1100', 'Rent payable',    '',       '120000', 'KES', '1'],
  ]
  const notes = [
    '# Import template for Journal Entries',
    '# batch_ref: group lines into one JE — same ref = same entry',
    '# date: YYYY-MM-DD format',
    '# account_code: must match an existing account code in the system',
    '# debit/credit: leave one blank per line (not both)',
    '# Each batch_ref group must balance (total debits = total credits)',
    '# currency: ISO 4217 code (KES, USD, EUR, GBP)',
    '# exchange_rate: vs functional currency (leave 1 for functional currency entries)',
  ]
  const csv = [...notes, header.join(','), ...example.map(r => r.join(','))].join('\n')
  const blob = new Blob([csv], { type: 'text/csv' })
  const url  = URL.createObjectURL(blob)
  const a    = document.createElement('a')
  a.href     = url
  a.download = 'journal-entries-import-template.csv'
  a.click()
  URL.revokeObjectURL(url)
}

// ── Import ────────────────────────────────────────────────────────────────────
function onImportFile(e) {
  const file = e.target.files[0]
  if (!file) return
  importFile.value = file
  importErrors.value = []
  importRows.value = []
  const reader = new FileReader()
  reader.onload = ev => parseImportCSV(ev.target.result)
  reader.readAsText(file)
}

function parseImportCSV(text) {
  const lines = text.split('\n').filter(l => l.trim() && !l.trim().startsWith('#'))
  if (lines.length < 2) { importErrors.value = ['CSV has no data rows']; return }

  const headers = lines[0].split(',').map(h => h.trim().toLowerCase())
  const required = ['batch_ref', 'date', 'account_code', 'debit', 'credit']
  const missing = required.filter(r => !headers.includes(r))
  if (missing.length) { importErrors.value = [`Missing required columns: ${missing.join(', ')}`]; return }

  const rows = lines.slice(1).map((line, i) => {
    const vals = parseCSVLine(line)
    const row = {}
    headers.forEach((h, j) => { row[h] = vals[j]?.trim() || '' })
    row._lineNum = i + 2
    return row
  })

  const errors = []

  // Group by batch_ref
  const groups = {}
  rows.forEach(row => {
    if (!row.batch_ref) { errors.push(`Row ${row._lineNum}: missing batch_ref`); return }
    if (!groups[row.batch_ref]) groups[row.batch_ref] = []
    groups[row.batch_ref].push(row)
  })

  // Validate each group
  Object.entries(groups).forEach(([ref, grpRows]) => {
    // date required on first row
    if (!grpRows[0].date) errors.push(`${ref}: missing date`)
    // account codes must exist
    grpRows.forEach(r => {
      if (!r.account_code) { errors.push(`${ref} row ${r._lineNum}: missing account_code`); return }
      if (!accountByCode.value[r.account_code]) errors.push(`${ref} row ${r._lineNum}: account "${r.account_code}" not found`)
    })
    // balance check
    const dr = grpRows.reduce((s, r) => s + Number(r.debit || 0), 0)
    const cr = grpRows.reduce((s, r) => s + Number(r.credit || 0), 0)
    if (Math.abs(dr - cr) > 0.01) errors.push(`${ref}: unbalanced — debit ${dr.toFixed(2)} ≠ credit ${cr.toFixed(2)}`)
  })

  importErrors.value = errors
  importRows.value = rows
}

function parseCSVLine(line) {
  const result = []
  let cur = '', inQ = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (c === '"') { inQ = !inQ }
    else if (c === ',' && !inQ) { result.push(cur); cur = '' }
    else { cur += c }
  }
  result.push(cur)
  return result
}

async function submitImport() {
  if (importErrors.value.length) { toast.error('Fix errors before importing'); return }
  importLoading.value = true
  const groups = {}
  importRows.value.forEach(row => {
    if (!groups[row.batch_ref]) groups[row.batch_ref] = []
    groups[row.batch_ref].push(row)
  })

  // Find an open period to assign
  const defaultPeriod = openPeriods.value[0]
  if (!defaultPeriod && !('isDemo' in window)) {
    toast.error('No open/adjusting period found — cannot import')
    importLoading.value = false
    return
  }

  let created = 0, failed = 0
  for (const [, grpRows] of Object.entries(groups)) {
    try {
      const first = grpRows[0]
      const cmd = {
        entityId: entityId.value,
        periodId: defaultPeriod?.id ?? '',
        transDate: first.date,
        description: first.description || first.batch_ref,
        lines: grpRows.map(r => {
          const acct = accountByCode.value[r.account_code]
          return {
            accountId:    acct?.id || r.account_code,
            description:  r.memo || '',
            debitAmount:  Number(r.debit || 0),
            creditAmount: Number(r.credit || 0),
            currencyCode: r.currency || 'KES',
            exchangeRate: Number(r.exchange_rate || 1),
          }
        }),
      }
      await journalsApi.create(cmd)
      created++
    } catch { failed++ }
  }

  toast.success(`Import complete: ${created} entries created${failed ? `, ${failed} failed` : ''}`)
  importLoading.value = false
  showImport.value = false
  importFile.value = null
  importRows.value = []
  importErrors.value = []
  await load()
}

// ── Actions ───────────────────────────────────────────────────────────────────
async function doSubmit(j) {
  actionLoading.value = true
  try {
    await journalsApi.submit(j.id)
    toast.success(`${displayRef(j)} submitted for approval`)
    drawer.value = null
    await load()
  } catch (e) {
    toast.error(e?.message || 'Submit failed')
  } finally { actionLoading.value = false }
}

async function doApprove(j) {
  actionLoading.value = true
  try {
    await journalsApi.approve(j.id)
    toast.success(`${displayRef(j)} approved and posted`)
    drawer.value = null
    await load()
  } catch (e) {
    toast.error(e?.message || 'Approve failed')
  } finally { actionLoading.value = false }
}

async function openReject(j) {
  drawer.value = j
  rejectReason.value = ''
  showReject.value = true
}

async function doReject() {
  if (!rejectReason.value.trim()) { toast.error('Rejection reason is required'); return }
  actionLoading.value = true
  try {
    await journalsApi.reject(drawer.value.id, rejectReason.value.trim())
    toast.success(`${displayRef(drawer.value)} rejected`)
    showReject.value = false
    drawer.value = null
    await load()
  } catch (e) {
    toast.error(e?.message || 'Reject failed')
  } finally { actionLoading.value = false }
}

async function doReverse(j) {
  actionLoading.value = true
  try {
    await journalsApi.reverse(j.id)
    toast.success(`Reversal entry created for ${displayRef(j)}`)
    drawer.value = null
    await load()
  } catch (e) {
    toast.error(e?.message || 'Reversal failed')
  } finally { actionLoading.value = false }
}

// ── New journal entry form ────────────────────────────────────────────────────
const newForm = ref({ periodId: '', transDate: '', description: '' })
const newLines = ref([
  { accountCode: '', memo: '', debit: 0, credit: 0, currency: '', exchangeRate: 1 },
  { accountCode: '', memo: '', debit: 0, credit: 0, currency: '', exchangeRate: 1 },
])
const newSaving = ref(false)
const newFormErrors = ref([])

const newTotalDebit  = computed(() => newLines.value.reduce((s, l) => s + Number(l.debit), 0))
const newTotalCredit = computed(() => newLines.value.reduce((s, l) => s + Number(l.credit), 0))
const newBalanced    = computed(() => Math.abs(newTotalDebit.value - newTotalCredit.value) < 0.01)

function addLine() {
  newLines.value.push({ accountCode: '', memo: '', debit: 0, credit: 0, currency: functionalCcy.value, exchangeRate: 1 })
}
function removeLine(i) {
  if (newLines.value.length > 2) newLines.value.splice(i, 1)
}

function openNewModal() {
  const ccy = functionalCcy.value
  newForm.value = {
    periodId: openPeriods.value[0]?.id || '',
    transDate: new Date().toISOString().slice(0, 10),
    description: '',
  }
  newLines.value = [
    { accountCode: '', memo: '', debit: 0, credit: 0, currency: ccy, exchangeRate: 1 },
    { accountCode: '', memo: '', debit: 0, credit: 0, currency: ccy, exchangeRate: 1 },
  ]
  newFormErrors.value = []
  showNew.value = true
}

async function saveNew(andSubmit = false) {
  newFormErrors.value = []
  const errs = []

  if (!newForm.value.periodId)               errs.push('Period is required')
  if (!newForm.value.transDate)              errs.push('Date is required')
  if (!newForm.value.description?.trim())    errs.push('Description is required')
  if (!newBalanced.value)                    errs.push('Entry must be balanced (total debits = total credits)')

  newLines.value.forEach((l, i) => {
    const acct = accountByCode.value[l.accountCode.trim()]
    if (!l.accountCode.trim()) errs.push(`Line ${i + 1}: account code is required`)
    else if (!acct)            errs.push(`Line ${i + 1}: account "${l.accountCode}" not found`)
    if (!l.debit && !l.credit) errs.push(`Line ${i + 1}: enter a debit or credit amount`)
  })

  if (errs.length) { newFormErrors.value = errs; return }

  newSaving.value = true
  try {
    const cmd = {
      entityId:    entityId.value,
      periodId:    newForm.value.periodId,
      transDate:   newForm.value.transDate,
      description: newForm.value.description || null,
      sourceType:  'MANUAL',
      lines: newLines.value.map(l => {
        const acct = accountByCode.value[l.accountCode.trim()]
        return {
          accountId:    acct.id,
          description:  l.memo || null,
          debitAmount:  Number(l.debit) || 0,
          creditAmount: Number(l.credit) || 0,
          currencyCode: l.currency || 'KES',
          exchangeRate: Number(l.exchangeRate) || 1,
        }
      }),
    }
    const created = await journalsApi.create(cmd)
    if (andSubmit && created?.id) {
      await journalsApi.submit(created.id)
      toast.success('Journal entry created and submitted for approval')
    } else {
      toast.success('Journal entry saved as draft')
    }
    showNew.value = false
    await load()
  } catch (e) {
    toast.error(e?.message || 'Failed to save journal entry')
  } finally {
    newSaving.value = false
  }
}

function periodDisplay(p) {
  if (p.periodName) {
    const parts = p.periodName.split(' ')
    return parts.length === 2 ? `${parts[1]}-${parts[0].slice(0,3)}` : p.periodName
  }
  return p.startDate?.substring(0, 7) || p.id
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Journal Entries"
      :meta="`${allJournals.length} entries`"
      :tabs="tabs"
      :activeTab="tab"
      @tab="tab = $event"
    >
      <Button variant="ghost" icon="download" @click="exportCSV">Export</Button>
      <Button variant="ghost" icon="upload" @click="showImport = true">Import</Button>
      <Button variant="primary" icon="plus" @click="openNewModal">New journal</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search" />

      <div v-if="loading" class="card" style="padding:24px;text-align:center;color:var(--muted)">
        Loading journal entries…
      </div>

      <div v-else class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Reference</th>
              <th>Date</th>
              <th>Description</th>
              <th>Source</th>
              <th class="num">Debit</th>
              <th class="num">Credit</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="j in filtered" :key="j.id" class="row-link" @click="drawer = j">
              <td><code>{{ displayRef(j) }}</code></td>
              <td>{{ fmtDate(j.transDate) }}</td>
              <td>
                <div>{{ j.description || '(no description)' }}</div>
                <div class="muted" style="font-size:11px">{{ (j.lines || []).length }} lines</div>
              </td>
              <td>
                <Badge :status="j.sourceType === 'MANUAL' ? 'draft' : 'info'" :dot="false">{{ j.sourceType || 'MANUAL' }}</Badge>
              </td>
              <td class="num mono">{{ fmt(totalDebit(j)) }}</td>
              <td class="num mono">{{ fmt(totalCredit(j)) }}</td>
              <td><Badge :status="j.status" :dot="false" /></td>
              <td @click.stop>
                <IconBtn icon="dots" @click="drawer = j" />
              </td>
            </tr>
            <tr v-if="!filtered.length">
              <td colspan="8" style="text-align:center;padding:24px;color:var(--muted)">No journal entries found</td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="entries" />
    </div>

    <!-- ── Detail drawer modal ─────────────────────────────────────────────── -->
    <Modal
      :open="drawer !== null && !showReject"
      :title="displayRef(drawer)"
      :subtitle="`${drawer?.reference || displayRef(drawer)} · ${fmtDate(drawer?.transDate)}`"
      :width="900"
      @close="drawer = null"
    >
      <template v-if="drawer">
        <div class="kpi-grid" style="grid-template-columns:repeat(6,1fr)">
          <div class="je-stat">
            <div class="je-stat-label">Reference</div>
            <div class="je-stat-value mono">{{ drawer.reference || displayRef(drawer) }}</div>
          </div>
          <div class="je-stat">
            <div class="je-stat-label">Date</div>
            <div class="je-stat-value">{{ fmtDate(drawer.transDate) || '—' }}</div>
          </div>
          <div class="je-stat">
            <div class="je-stat-label">Period</div>
            <div class="je-stat-value">{{ drawer.periodId ? (periodById[drawer.periodId]?.periodName ?? drawer.periodId.toString().slice(0, 8)) : '—' }}</div>
          </div>
          <div class="je-stat">
            <div class="je-stat-label">Source</div>
            <div class="je-stat-value">{{ drawer.sourceType || 'MANUAL' }}</div>
          </div>
          <div class="je-stat">
            <div class="je-stat-label">Status</div>
            <div class="je-stat-value"><Badge :status="drawer.status" :dot="false" /></div>
          </div>
          <div class="je-stat">
            <div class="je-stat-label">Lines</div>
            <div class="je-stat-value mono">{{ (drawer.lines || []).length }}</div>
          </div>
        </div>

        <div class="card">
          <table class="tbl je-editor">
            <thead>
              <tr>
                <th>Account</th>
                <th>Memo</th>
                <th class="num">Debit</th>
                <th class="num">Credit</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(line, i) in (drawer.lines || [])" :key="i">
                <td><code>{{ lineAccountDisplay(line) }}</code></td>
                <td class="muted">{{ line.description || '—' }}</td>
                <td class="num mono">{{ line.debitAmount || line.functionalDebit ? fmt(Number(line.debitAmount || line.functionalDebit)) : '' }}</td>
                <td class="num mono">{{ line.creditAmount || line.functionalCredit ? fmt(Number(line.creditAmount || line.functionalCredit)) : '' }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="je-totals">
                <td colspan="2">
                  <div :class="['je-balanced', Math.abs(totalDebit(drawer) - totalCredit(drawer)) < 0.01 ? 'pos' : 'neg']">
                    {{ Math.abs(totalDebit(drawer) - totalCredit(drawer)) < 0.01 ? 'Balanced' : 'Unbalanced' }}
                  </div>
                </td>
                <td class="num mono"><strong>{{ fmt(totalDebit(drawer)) }}</strong></td>
                <td class="num mono"><strong>{{ fmt(totalCredit(drawer)) }}</strong></td>
              </tr>
            </tfoot>
          </table>
        </div>

        <div v-if="drawer._ref" class="card">
          <div class="card-head"><Ico name="clock" :size="13" /> Timeline</div>
          <div class="card-body no-pad">
            <div class="timeline">
              <template v-if="drawer._submittedBy">
                <div class="tl-row"><span class="tl-label">Submitted</span><span class="tl-body">Submitted for approval</span><span class="tl-actor">{{ drawer._submittedBy }}</span></div>
              </template>
              <template v-if="drawer._postedBy && drawer.status === 'POSTED'">
                <div class="tl-row"><span class="tl-label">{{ fmtDateTime(drawer._postedAt) }}</span><span class="tl-body">Posted to ledger</span><span class="tl-actor">{{ drawer._postedBy }}</span></div>
              </template>
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <template v-if="drawer?.status === 'POSTED'">
          <Button variant="ghost" icon="rotate" :loading="actionLoading" @click="doReverse(drawer)">Reverse</Button>
        </template>
        <template v-else-if="drawer?.status === 'PENDING_APPROVAL'">
          <Button variant="primary" icon="approve" :loading="actionLoading" @click="doApprove(drawer)">Approve &amp; post</Button>
          <Button variant="ghost" icon="reject" @click="openReject(drawer)">Reject</Button>
        </template>
        <template v-else-if="drawer?.status === 'DRAFT'">
          <Button variant="primary" icon="approve" :loading="actionLoading" @click="doSubmit(drawer)">Submit for approval</Button>
        </template>
      </template>
    </Modal>

    <!-- ── Reject reason modal ────────────────────────────────────────────── -->
    <Modal :open="showReject" title="Reject Journal Entry" subtitle="Provide a reason for rejection" :width="480" @close="showReject = false">
      <div class="field">
        <label>Rejection reason</label>
        <textarea v-model="rejectReason" class="input" rows="3" placeholder="e.g. Incorrect account allocation — use 6100 not 6000" style="height:80px;resize:vertical" />
      </div>
      <template #footer>
        <Button variant="primary" icon="reject" :loading="actionLoading" @click="doReject">Confirm rejection</Button>
        <Button variant="ghost" @click="showReject = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── New journal entry modal ────────────────────────────────────────── -->
    <Modal :open="showNew" title="New Journal Entry" subtitle="Creates a new entry in DRAFT status" :width="900" @close="showNew = false">
      <div class="form-grid cols-3">
        <div class="field">
          <label>Period</label>
          <SearchableSelect
            v-model="newForm.periodId"
            :options="openPeriods.map(p => ({ value: p.id, label: p.periodName ?? `${periodDisplay(p)} (${p.status})` }))"
            placeholder="Select period…"
          />
        </div>
        <div class="field">
          <label>Date</label>
          <input v-model="newForm.transDate" class="input" type="date" />
        </div>
        <div class="field">
          <label>Currency</label>
          <input class="input mono" type="text" :value="functionalCcy" disabled />
        </div>
        <div class="field" style="grid-column:span 3">
          <label>Description <span class="req">*</span></label>
          <input v-model="newForm.description" class="input" type="text" placeholder="Entry description… (required)" />
        </div>
      </div>

      <div v-if="newFormErrors.length" class="error-list">
        <div v-for="e in newFormErrors" :key="e" class="error-item">{{ e }}</div>
      </div>

      <div class="card">
        <table class="tbl je-editor">
          <thead>
            <tr>
              <th style="width:260px">Account</th>
              <th>Memo</th>
              <th class="num" style="width:120px">Debit</th>
              <th class="num" style="width:120px">Credit</th>
              <th style="width:32px"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(line, i) in newLines" :key="i">
              <td>
                <AccountCombobox
                  v-model="line.accountCode"
                  :accounts="acctCache.postableAccounts.value"
                  :allAccounts="acctCache.accounts.value"
                  :error="!!(line.accountCode && !accountByCode[line.accountCode.trim()])"
                  placeholder="Search by code or name…"
                />
              </td>
              <td><input v-model="line.memo" class="input" placeholder="Memo" style="width:100%" /></td>
              <td class="num"><AmountInput v-model="line.debit"  class="input mono" style="text-align:right;width:100%" /></td>
              <td class="num"><AmountInput v-model="line.credit" class="input mono" style="text-align:right;width:100%" /></td>
              <td><IconBtn icon="x" @click="removeLine(i)" /></td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="je-totals">
              <td colspan="2">
                <div :class="['je-balanced', newBalanced ? 'pos' : 'neg']">
                  {{ newBalanced ? 'Balanced' : 'Unbalanced' }}
                </div>
              </td>
              <td class="num mono"><strong>{{ fmt(newTotalDebit) }}</strong></td>
              <td class="num mono"><strong>{{ fmt(newTotalCredit) }}</strong></td>
              <td></td>
            </tr>
          </tfoot>
        </table>
        <Button variant="ghost" icon="plus" style="margin-top:10px;padding-left:0" @click="addLine">Add line</Button>
      </div>

      <template #footer>
        <Button variant="primary" icon="approve" :loading="newSaving" @click="saveNew(true)">Submit for approval</Button>
        <Button variant="ghost" icon="doc" :loading="newSaving" @click="saveNew(false)">Save as draft</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Import modal ────────────────────────────────────────────────────── -->
    <Modal :open="showImport" title="Import Journal Entries" subtitle="Bulk create drafts from a CSV file" :width="700" @close="showImport = false">
      <div class="import-section">
        <div class="import-tip">
          Download the <button class="link-btn" @click="downloadTemplate">import template</button> to see the required format.
          Each <code>batch_ref</code> group becomes one journal entry. Account codes must match existing accounts.
        </div>

        <label class="file-drop">
          <input type="file" accept=".csv" style="display:none" @change="onImportFile" />
          <div class="file-drop-inner">
            <Ico name="upload" :size="20" />
            <span v-if="importFile">{{ importFile.name }}</span>
            <span v-else>Click to select CSV file</span>
          </div>
        </label>

        <div v-if="importErrors.length" class="error-list">
          <div v-for="e in importErrors" :key="e" class="error-item">{{ e }}</div>
        </div>

        <div v-if="importRows.length && !importErrors.length" class="import-preview">
          <div class="import-preview-label">Preview — {{ importRows.length }} rows, ready to import</div>
          <table class="tbl" style="font-size:12px">
            <thead>
              <tr><th>Batch ref</th><th>Date</th><th>Account</th><th>Memo</th><th class="num">Debit</th><th class="num">Credit</th></tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in importRows.slice(0, 10)" :key="i">
                <td><code>{{ r.batch_ref }}</code></td>
                <td>{{ r.date }}</td>
                <td><code>{{ r.account_code }}</code></td>
                <td class="muted">{{ r.memo || '—' }}</td>
                <td class="num mono">{{ r.debit || '' }}</td>
                <td class="num mono">{{ r.credit || '' }}</td>
              </tr>
              <tr v-if="importRows.length > 10">
                <td colspan="6" class="muted" style="text-align:center">… and {{ importRows.length - 10 }} more rows</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <template #footer>
        <Button variant="primary" icon="upload" :disabled="!importRows.length || !!importErrors.length" :loading="importLoading" @click="submitImport">Import as drafts</Button>
        <Button variant="ghost" icon="download" @click="downloadTemplate">Download template</Button>
        <Button variant="ghost" @click="showImport = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.req { color: oklch(0.55 0.18 15); }

.je-stat {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--r-lg, 10px);
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.je-stat-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--muted);
}
.je-stat-value {
  font-size: 15px;
  font-weight: 700;
  color: var(--fg);
  letter-spacing: -0.01em;
}
.je-stat-value.mono { font-family: var(--font-mono); }

.je-editor td { padding: 4px 6px; }
.je-editor input { height: 28px; font-size: 12px; }

.je-totals td { padding: 8px 6px; border-top: 1px solid var(--border); }
.je-balanced { font-size: 11px; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; }
.je-balanced.pos { color: var(--accent); }
.je-balanced.neg { color: oklch(0.55 0.22 25); }

.error-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.error-item {
  font-size: 12px;
  color: oklch(0.55 0.22 25);
  background: color-mix(in oklab, oklch(0.55 0.22 25) 8%, var(--surface));
  padding: 4px 10px;
  border-radius: 4px;
}

.input-error { border-color: oklch(0.55 0.22 25) !important; }

.tl-row {
  display: grid;
  grid-template-columns: 120px 1fr auto;
  gap: 12px;
  align-items: start;
  padding: 8px 16px;
  border-bottom: 1px solid var(--border);
  font-size: 12px;
}
.tl-label { color: var(--muted); font-family: monospace; }
.tl-actor { color: var(--muted); }

/* ── Import modal ── */
.import-section { display: flex; flex-direction: column; gap: 12px; }
.import-tip {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
}
.link-btn {
  background: none; border: none; padding: 0;
  color: var(--accent); cursor: pointer; font-size: inherit;
  text-decoration: underline;
}
.file-drop {
  display: block;
  border: 2px dashed var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s;
}
.file-drop:hover { border-color: var(--accent); }
.file-drop-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 20px;
  font-size: 13px;
  color: var(--muted);
}
.import-preview-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  margin-bottom: 6px;
}
</style>

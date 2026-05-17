<script setup>
import { ref, onMounted } from 'vue'
import { TAX_CODES, CURRENCIES, FX_RATES } from '@/data/index.js'
import { tax, fx } from '@/api/index.js'
import { fmtDate } from '@/utils/format.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import Modal        from '@/components/overlays/Modal.vue'
import AmountInput  from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const TAX_TYPE_OPTIONS = [
  { value: 'OUTPUT', label: 'OUTPUT' },
  { value: 'INPUT',  label: 'INPUT' },
  { value: 'EXEMPT', label: 'EXEMPT' },
  { value: 'WHT',    label: 'WHT' },
]

const { toast } = useToast()

const tab = ref('tax')
const tabs = [
  { id: 'tax', label: 'Tax codes',  count: TAX_CODES.length },
  { id: 'ccy', label: 'Currencies', count: CURRENCIES.length },
  { id: 'fx',  label: 'FX rates',   count: FX_RATES.length },
]
const newLabel = { tax: 'New tax code', ccy: 'New currency', fx: 'New FX rate' }

const taxList = ref([...TAX_CODES])
const ccyList = ref([...CURRENCIES])
const fxList  = ref([...FX_RATES])

// Edit state
const editItem = ref(null)
const editForm = ref(null)
const saving   = ref(false)

// New state
const showNew   = ref(false)
const newSaving = ref(false)
const newTax = ref({ code: '', name: '', type: 'OUTPUT', ratePct: 16, accountCode: '', recoverable: true })
const newCcy = ref({ code: '', name: '', symbol: '', decimals: 2, functional: false })
const newFx  = ref({ fromCurrency: '', toCurrency: '', rateValue: '', asOf: '', source: 'MANUAL' })

// Import state
const showImport   = ref(false)
const importRows   = ref([])
const importErrors = ref([])
const importing    = ref(false)
const importFileEl = ref(null)

function toArray(v) {
  if (!v) return []
  if (Array.isArray(v)) return v
  if (v.content) return v.content
  return []
}

onMounted(async () => {
  try {
    const [t, c, r] = await Promise.all([tax.list(), fx.currencies(), fx.rates()])
    if (t) taxList.value = toArray(t).length ? toArray(t) : taxList.value
    if (c) ccyList.value = toArray(c).length ? toArray(c) : ccyList.value
    if (r) fxList.value  = toArray(r).length ? toArray(r) : fxList.value
  } catch { /* stays on static data */ }
})

// ── Edit ────────────────────────────────────────────────────────────────────

function openEdit(item, type) {
  editItem.value = { ...item, _type: type }
  if (type === 'tax') editForm.value = { description: item.description ?? '', isRecoverable: item.isRecoverable ?? true }
  if (type === 'ccy') editForm.value = { currencyName: item.name ?? item.currencyName }
  if (type === 'fx')  editForm.value = { rateValue: item.rate ?? item.rateValue }
}

function closeEdit() { editItem.value = null; editForm.value = null }

async function saveEdit() {
  if (!editItem.value || !editForm.value) return
  saving.value = true
  try {
    const type = editItem.value._type
    if (type === 'tax') {
      await tax.update(editItem.value.id ?? editItem.value.code, editForm.value)
      const idx = taxList.value.findIndex(t => (t.id ?? t.code) === (editItem.value.id ?? editItem.value.code))
      if (idx !== -1) taxList.value[idx] = { ...taxList.value[idx], ...editForm.value }
      toast.success('Tax code updated.')
    } else if (type === 'ccy') {
      await fx.updateCurrency(editItem.value.id ?? editItem.value.code, editForm.value)
      const idx = ccyList.value.findIndex(c => (c.id ?? c.code) === (editItem.value.id ?? editItem.value.code))
      if (idx !== -1) ccyList.value[idx] = { ...ccyList.value[idx], name: editForm.value.currencyName, currencyName: editForm.value.currencyName }
      toast.success('Currency updated.')
    } else if (type === 'fx') {
      await fx.updateRate(editItem.value.id, editForm.value)
      const idx = fxList.value.findIndex(r => r.id === editItem.value.id)
      if (idx !== -1) fxList.value[idx] = { ...fxList.value[idx], rate: Number(editForm.value.rateValue), rateValue: Number(editForm.value.rateValue) }
      toast.success('FX rate updated.')
    }
    closeEdit()
  } catch { /* handled by apiFetch */ } finally {
    saving.value = false
  }
}

async function toggleTax(item) {
  try {
    await tax.toggle(item.id ?? item.code)
    const idx = taxList.value.findIndex(t => (t.id ?? t.code) === (item.id ?? item.code))
    if (idx !== -1) taxList.value[idx] = { ...taxList.value[idx], active: !taxList.value[idx].active }
    toast.success(`${item.code ?? item.name} ${taxList.value[idx]?.active ? 'activated' : 'deactivated'}.`)
  } catch { /* handled */ }
}

// ── New ─────────────────────────────────────────────────────────────────────

function openNew() {
  newTax.value = { code: '', name: '', type: 'OUTPUT', ratePct: 16, accountCode: '', recoverable: true }
  newCcy.value = { code: '', name: '', symbol: '', decimals: 2, functional: false }
  newFx.value  = { fromCurrency: '', toCurrency: '', rateValue: '', asOf: new Date().toISOString().slice(0, 10), source: 'MANUAL' }
  showNew.value = true
}

async function createNew() {
  newSaving.value = true
  try {
    if (tab.value === 'tax') {
      if (!newTax.value.code || !newTax.value.name || newTax.value.ratePct === '') {
        toast.warn('Code, name and rate are required.'); return
      }
      const body = {
        code: newTax.value.code.toUpperCase().trim(),
        name: newTax.value.name.trim(),
        type: newTax.value.type,
        rate: Number(newTax.value.ratePct) / 100,
        accountCode: newTax.value.accountCode || undefined,
        recoverable: newTax.value.recoverable,
        active: true,
      }
      const created = await tax.create(body)
      taxList.value.unshift(created ?? body)
      toast.success(`Tax code ${body.code} created.`)
      showNew.value = false

    } else if (tab.value === 'ccy') {
      if (!newCcy.value.code || !newCcy.value.name || !newCcy.value.symbol) {
        toast.warn('Code, name and symbol are required.'); return
      }
      const body = {
        code: newCcy.value.code.toUpperCase().trim(),
        name: newCcy.value.name.trim(),
        symbol: newCcy.value.symbol.trim(),
        decimals: Number(newCcy.value.decimals),
        functional: newCcy.value.functional,
      }
      const created = await fx.createCurrency(body)
      ccyList.value.unshift(created ?? body)
      toast.success(`Currency ${body.code} added.`)
      showNew.value = false

    } else {
      if (!newFx.value.fromCurrency || !newFx.value.toCurrency || !newFx.value.rateValue || !newFx.value.asOf) {
        toast.warn('All fields are required for an FX rate.'); return
      }
      const body = {
        fromCurrency: newFx.value.fromCurrency.toUpperCase(),
        toCurrency:   newFx.value.toCurrency.toUpperCase(),
        rateValue:    Number(newFx.value.rateValue),
        rateDate:     newFx.value.asOf,
        rateType:     newFx.value.source || 'MANUAL',
      }
      const created = await fx.createRate(body)
      fxList.value.unshift(created ?? { ...body, id: Date.now(), from: body.fromCurrency, to: body.toCurrency, rate: body.rateValue, asOf: body.rateDate, source: body.rateType })
      toast.success(`FX rate ${body.fromCurrency}/${body.toCurrency} added.`)
      showNew.value = false
    }
  } catch { /* handled */ } finally {
    newSaving.value = false
  }
}

// ── Export CSV ───────────────────────────────────────────────────────────────

function exportCsv() {
  let headers, rows, filename
  if (tab.value === 'tax') {
    headers  = ['code','name','type','rate_pct','account_code','recoverable','active']
    rows     = taxList.value.map(t => [t.code, t.name, t.type, ((t.rate ?? 0)*100).toFixed(2), t.account ?? t.accountCode ?? '', t.isRecoverable ?? true, t.active])
    filename = 'tax-codes.csv'
  } else if (tab.value === 'ccy') {
    headers  = ['code','name','symbol','decimals','functional']
    rows     = ccyList.value.map(c => [c.code, c.name ?? c.currencyName, c.symbol, c.decimals ?? 2, c.functional ?? c.isFunctional ?? false])
    filename = 'currencies.csv'
  } else {
    headers  = ['from_currency','to_currency','rate','as_of_date','source']
    rows     = fxList.value.map(r => [r.from ?? r.fromCurrency, r.to ?? r.toCurrency, (r.rate ?? r.rateValue ?? 0).toFixed(6), r.asOf ?? r.rateDate ?? '', r.source ?? r.rateType ?? ''])
    filename = 'fx-rates.csv'
  }
  const csv = [headers, ...rows].map(r => r.map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')
  triggerDownload(csv, filename, 'text/csv')
}

// ── Template download ────────────────────────────────────────────────────────

function downloadTemplate() {
  let csv, filename
  if (tab.value === 'tax') {
    filename = 'tax-codes-template.csv'
    csv = `# Tax Codes Import Template
# type: OUTPUT | INPUT | EXEMPT | WHT
# rate_pct: percentage value (e.g. 16 for 16%)
# recoverable: true | false
code,name,type,rate_pct,account_code,recoverable
VAT16,Standard VAT,OUTPUT,16,2310,true
VAT8,Reduced VAT,OUTPUT,8,2310,true
VAT0,Zero-rated VAT,OUTPUT,0,2310,true
WHT5,Withholding Tax 5%,WHT,5,2320,false
EXEMPT,Tax Exempt,EXEMPT,0,,false`
  } else if (tab.value === 'ccy') {
    filename = 'currencies-template.csv'
    csv = `# Currencies Import Template
# code: ISO 4217 currency code (3 letters)
# decimals: decimal places (usually 2; UGX = 0)
# functional: true = reporting/functional currency (only one allowed)
code,name,symbol,decimals,functional
KES,Kenyan Shilling,KSh,2,true
USD,US Dollar,$,2,false
EUR,Euro,€,2,false
GBP,British Pound,£,2,false
UGX,Ugandan Shilling,USh,0,false`
  } else {
    filename = 'fx-rates-template.csv'
    csv = `# FX Rates Import Template
# rate: units of to_currency per 1 unit of from_currency
# as_of_date: YYYY-MM-DD
# source: CBK | ECB | BOU | MANUAL (or any string)
from_currency,to_currency,rate,as_of_date,source
USD,KES,129.45,2026-05-01,CBK
EUR,KES,140.20,2026-05-01,CBK
GBP,KES,163.80,2026-05-01,CBK
USD,UGX,3720.00,2026-05-01,BOU
EUR,USD,1.08,2026-05-01,ECB`
  }
  triggerDownload(csv, filename, 'text/csv')
}

function triggerDownload(content, filename, mime) {
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([content], { type: mime }))
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}

// ── Import CSV ───────────────────────────────────────────────────────────────

function openImport() {
  importRows.value   = []
  importErrors.value = []
  showImport.value   = true
}

function handleImportFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (e) => {
    const lines = e.target.result.split('\n').filter(l => l.trim() && !l.trim().startsWith('#'))
    if (lines.length < 2) { toast.warn('File appears empty.'); return }
    const headers = lines[0].split(',').map(h => h.trim().replace(/^"|"$/g, '').toLowerCase())
    const errors = []
    const rows   = []

    for (let i = 1; i < lines.length; i++) {
      const vals = lines[i].split(',').map(v => v.trim().replace(/^"|"$/g, ''))
      if (vals.every(v => !v)) continue
      const row = Object.fromEntries(headers.map((h, j) => [h, vals[j] ?? '']))

      if (tab.value === 'tax') {
        if (!row.code)         { errors.push(`Row ${i}: code required`); continue }
        if (!row.name)         { errors.push(`Row ${i}: name required`); continue }
        if (!['OUTPUT','INPUT','EXEMPT','WHT'].includes(row.type?.toUpperCase())) { errors.push(`Row ${i}: invalid type "${row.type}"`); continue }
        if (isNaN(Number(row.rate_pct))) { errors.push(`Row ${i}: rate_pct must be a number`); continue }
        rows.push({ code: row.code.toUpperCase(), name: row.name, type: row.type.toUpperCase(), rate: Number(row.rate_pct)/100, accountCode: row.account_code || undefined, recoverable: row.recoverable !== 'false', active: true })

      } else if (tab.value === 'ccy') {
        if (!row.code || row.code.length !== 3) { errors.push(`Row ${i}: code must be 3 letters`); continue }
        if (!row.name)   { errors.push(`Row ${i}: name required`); continue }
        if (!row.symbol) { errors.push(`Row ${i}: symbol required`); continue }
        rows.push({ code: row.code.toUpperCase(), name: row.name, symbol: row.symbol, decimals: Number(row.decimals)||2, functional: row.functional === 'true' })

      } else {
        if (!row.from_currency) { errors.push(`Row ${i}: from_currency required`); continue }
        if (!row.to_currency)   { errors.push(`Row ${i}: to_currency required`); continue }
        if (isNaN(Number(row.rate)) || Number(row.rate) <= 0) { errors.push(`Row ${i}: rate must be a positive number`); continue }
        if (!row.as_of_date)    { errors.push(`Row ${i}: as_of_date required`); continue }
        rows.push({ fromCurrency: row.from_currency.toUpperCase(), toCurrency: row.to_currency.toUpperCase(), rateValue: Number(row.rate), rateDate: row.as_of_date, rateType: row.source || 'MANUAL' })
      }
    }
    importRows.value   = rows
    importErrors.value = errors
    if (importFileEl.value) importFileEl.value.value = ''
  }
  reader.readAsText(file)
}

async function runImport() {
  if (!importRows.value.length) return
  importing.value = true
  let ok = 0, fail = 0
  for (const row of importRows.value) {
    try {
      if (tab.value === 'tax') {
        const res = await tax.create(row)
        taxList.value.push(res ?? row)
      } else if (tab.value === 'ccy') {
        const res = await fx.createCurrency(row)
        ccyList.value.push(res ?? row)
      } else {
        const res = await fx.createRate(row)
        fxList.value.push(res ?? { ...row, id: Date.now()+ok, from: row.fromCurrency, to: row.toCurrency, rate: row.rateValue, asOf: row.rateDate, source: row.rateType })
      }
      ok++
    } catch { fail++ }
  }
  importing.value = false
  toast[fail && ok === 0 ? 'error' : fail ? 'warn' : 'success'](`Imported ${ok} record${ok !== 1 ? 's' : ''}${fail ? `, ${fail} failed` : ''}.`)
  if (ok > 0) { importRows.value = []; importErrors.value = []; showImport.value = false }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Tax & Currency"
      meta="Tax codes · currencies · exchange rates"
      :tabs="tabs"
      :activeTab="tab"
      @tab="tab = $event"
    >
      <Button variant="ghost" icon="download" @click="downloadTemplate">Template</Button>
      <Button variant="ghost" icon="upload" @click="openImport">Import</Button>
      <Button variant="ghost" icon="download" @click="exportCsv">Export</Button>
      <Button variant="primary" icon="plus" @click="openNew">{{ newLabel[tab] }}</Button>
    </PageHeader>

    <div class="page-section stack">

      <!-- Tax codes -->
      <div v-if="tab === 'tax'" class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>Type</th>
              <th class="num">Rate (%)</th>
              <th>Linked account</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in taxList" :key="t.code">
              <td><span class="code-cell">{{ t.code }}</span></td>
              <td>{{ t.name }}</td>
              <td><Badge status="info" :dot="false">{{ t.type }}</Badge></td>
              <td class="num">{{ ((t.rate ?? 0) * 100).toFixed(0) }}%</td>
              <td>{{ t.account ?? t.accountCode ?? '—' }}</td>
              <td><Badge :status="t.active ? 'active' : 'inactive'" :dot="false" /></td>
              <td style="display:flex;gap:6px;align-items:center">
                <Button variant="ghost" size="sm" icon="edit" @click="openEdit(t, 'tax')">Edit</Button>
                <Button variant="ghost" size="sm" @click="toggleTax(t)">{{ t.active ? 'Disable' : 'Enable' }}</Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Currencies -->
      <div v-else-if="tab === 'ccy'" class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Name</th>
              <th>Symbol</th>
              <th class="num">Decimals</th>
              <th>Role</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in ccyList" :key="c.code">
              <td><span class="code-cell">{{ c.code }}</span></td>
              <td>{{ c.name ?? c.currencyName }}</td>
              <td>{{ c.symbol }}</td>
              <td class="num">{{ c.decimals }}</td>
              <td>
                <Badge v-if="c.functional || c.isFunctional" status="active" :dot="false">Functional</Badge>
                <Badge v-else status="info" :dot="false">Foreign</Badge>
              </td>
              <td>
                <Button variant="ghost" size="sm" icon="edit" @click="openEdit(c, 'ccy')">Edit</Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- FX rates -->
      <div v-else-if="tab === 'fx'" class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>From</th>
              <th>To</th>
              <th class="num">Rate</th>
              <th>As of</th>
              <th>Source</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in fxList" :key="r.id">
              <td><span class="code-cell">{{ r.from ?? r.fromCurrency }}</span></td>
              <td><span class="code-cell">{{ r.to ?? r.toCurrency }}</span></td>
              <td class="num fw-500">{{ (r.rate ?? r.rateValue ?? 0).toFixed(4) }}</td>
              <td>{{ fmtDate(r.asOf ?? r.rateDate) }}</td>
              <td>{{ r.source ?? r.rateType ?? '—' }}</td>
              <td>
                <Button variant="ghost" size="sm" icon="edit" @click="openEdit(r, 'fx')">Edit</Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Edit modal -->
    <Modal
      :open="!!editItem"
      :title="editItem?._type === 'fx' ? `Edit FX Rate — ${editItem?.from ?? editItem?.fromCurrency}/${editItem?.to ?? editItem?.toCurrency}` : `Edit — ${editItem?.code}`"
      :width="420"
      @close="closeEdit"
    >
      <template v-if="editForm">
        <template v-if="editItem._type === 'tax'">
          <div class="field">
            <label>Description</label>
            <input class="input" v-model="editForm.description" placeholder="Optional description" />
          </div>
          <div class="field" style="margin-top:12px">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
              <input type="checkbox" v-model="editForm.isRecoverable" />
              <span>Input tax is recoverable (e.g. claimable VAT input credit)</span>
            </label>
          </div>
          <div class="info-box">
            <strong>Note:</strong> Tax code, name, and rate are immutable once the code is in use. These changes are audit-logged.
          </div>
        </template>

        <template v-else-if="editItem._type === 'ccy'">
          <div class="field">
            <label>Currency name</label>
            <input class="input" v-model="editForm.currencyName" placeholder="e.g. Kenyan Shilling" />
          </div>
          <div class="info-box">
            <strong>Note:</strong> Currency code and functional status cannot be changed after registration.
          </div>
        </template>

        <template v-else-if="editItem._type === 'fx'">
          <div class="field">
            <label>Exchange rate ({{ editItem.from ?? editItem.fromCurrency }} → {{ editItem.to ?? editItem.toCurrency }})</label>
            <AmountInput class="input" v-model="editForm.rateValue" :decimals="4" placeholder="0.0000" />
          </div>
          <div class="info-box">
            <strong>Note:</strong> Updating this rate does not restate already-posted journal entries. Run FX revaluation after updating closing rates.
          </div>
        </template>
      </template>

      <template #footer>
        <Button variant="primary" :loading="saving" @click="saveEdit">Save</Button>
        <Button variant="ghost" @click="closeEdit">Cancel</Button>
      </template>
    </Modal>

    <!-- New item modal -->
    <Modal
      :open="showNew"
      :title="newLabel[tab]"
      :width="500"
      @close="showNew = false"
    >
      <!-- New tax code -->
      <template v-if="tab === 'tax'">
        <div class="form-grid cols-2">
          <div class="field">
            <label>Code <span class="req">*</span></label>
            <input class="input mono" v-model="newTax.code" placeholder="VAT16" style="text-transform:uppercase" />
          </div>
          <div class="field">
            <label>Name <span class="req">*</span></label>
            <input class="input" v-model="newTax.name" placeholder="Standard VAT" />
          </div>
          <div class="field">
            <label>Type <span class="req">*</span></label>
            <SearchableSelect
              v-model="newTax.type"
              :options="TAX_TYPE_OPTIONS"
              placeholder="Select type"
            />
          </div>
          <div class="field">
            <label>Rate (%) <span class="req">*</span></label>
            <input class="input" type="number" step="0.01" min="0" max="100" v-model="newTax.ratePct" placeholder="16" />
          </div>
          <div class="field" style="grid-column:span 2">
            <label>Linked account code</label>
            <input class="input mono" v-model="newTax.accountCode" placeholder="2310" />
          </div>
          <div class="field" style="grid-column:span 2">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
              <input type="checkbox" v-model="newTax.recoverable" />
              <span>Input tax is recoverable (claimable VAT credit)</span>
            </label>
          </div>
        </div>
      </template>

      <!-- New currency -->
      <template v-else-if="tab === 'ccy'">
        <div class="form-grid cols-2">
          <div class="field">
            <label>ISO Code <span class="req">*</span></label>
            <input class="input mono" v-model="newCcy.code" placeholder="KES" maxlength="3" style="text-transform:uppercase" />
          </div>
          <div class="field">
            <label>Symbol <span class="req">*</span></label>
            <input class="input" v-model="newCcy.symbol" placeholder="KSh" />
          </div>
          <div class="field" style="grid-column:span 2">
            <label>Currency name <span class="req">*</span></label>
            <input class="input" v-model="newCcy.name" placeholder="Kenyan Shilling" />
          </div>
          <div class="field">
            <label>Decimal places</label>
            <input class="input" type="number" min="0" max="6" v-model="newCcy.decimals" />
          </div>
          <div class="field" style="display:flex;align-items:flex-end">
            <label style="display:flex;align-items:center;gap:8px;cursor:pointer">
              <input type="checkbox" v-model="newCcy.functional" />
              <span>Functional / reporting currency</span>
            </label>
          </div>
        </div>
      </template>

      <!-- New FX rate -->
      <template v-else-if="tab === 'fx'">
        <div class="form-grid cols-2">
          <div class="field">
            <label>From currency <span class="req">*</span></label>
            <input class="input mono" v-model="newFx.fromCurrency" placeholder="USD" maxlength="3" style="text-transform:uppercase" />
          </div>
          <div class="field">
            <label>To currency <span class="req">*</span></label>
            <input class="input mono" v-model="newFx.toCurrency" placeholder="KES" maxlength="3" style="text-transform:uppercase" />
          </div>
          <div class="field">
            <label>Rate <span class="req">*</span></label>
            <AmountInput class="input" v-model="newFx.rateValue" :decimals="4" placeholder="129.4500" />
          </div>
          <div class="field">
            <label>As of date <span class="req">*</span></label>
            <input class="input" type="date" v-model="newFx.asOf" />
          </div>
          <div class="field" style="grid-column:span 2">
            <label>Source</label>
            <input class="input mono" v-model="newFx.source" placeholder="CBK" />
          </div>
        </div>
      </template>

      <template #footer>
        <Button variant="primary" :loading="newSaving" @click="createNew">Create</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- Import modal -->
    <Modal
      :open="showImport"
      :title="`Import ${tab === 'tax' ? 'tax codes' : tab === 'ccy' ? 'currencies' : 'FX rates'}`"
      subtitle="Upload a CSV file to bulk-create records"
      :width="640"
      @close="showImport = false"
    >
      <div class="import-zone">
        <input ref="importFileEl" type="file" accept=".csv,text/csv" style="display:none" @change="handleImportFile" />
        <Button variant="ghost" icon="upload" @click="importFileEl?.click()">Choose CSV file</Button>
        <span class="muted" style="font-size:12px">or drag and drop a .csv file here</span>
      </div>

      <div v-if="importErrors.length" class="error-list">
        <div class="error-head">{{ importErrors.length }} validation error{{ importErrors.length > 1 ? 's' : '' }}</div>
        <div v-for="e in importErrors" :key="e" class="error-row">{{ e }}</div>
      </div>

      <div v-if="importRows.length" class="import-preview">
        <div class="preview-head">{{ importRows.length }} record{{ importRows.length > 1 ? 's' : '' }} ready to import</div>
        <div class="preview-scroll">
          <!-- Tax preview -->
          <table v-if="tab === 'tax'" class="tbl">
            <thead><tr><th>Code</th><th>Name</th><th>Type</th><th class="num">Rate</th><th>Account</th></tr></thead>
            <tbody>
              <tr v-for="(r, i) in importRows" :key="i">
                <td><span class="code-cell">{{ r.code }}</span></td>
                <td>{{ r.name }}</td>
                <td><Badge status="info" :dot="false">{{ r.type }}</Badge></td>
                <td class="num">{{ (r.rate * 100).toFixed(2) }}%</td>
                <td>{{ r.accountCode ?? '—' }}</td>
              </tr>
            </tbody>
          </table>
          <!-- Currency preview -->
          <table v-else-if="tab === 'ccy'" class="tbl">
            <thead><tr><th>Code</th><th>Name</th><th>Symbol</th><th class="num">Dec.</th><th>Role</th></tr></thead>
            <tbody>
              <tr v-for="(r, i) in importRows" :key="i">
                <td><span class="code-cell">{{ r.code }}</span></td>
                <td>{{ r.name }}</td>
                <td>{{ r.symbol }}</td>
                <td class="num">{{ r.decimals }}</td>
                <td><Badge :status="r.functional ? 'active' : 'info'" :dot="false">{{ r.functional ? 'Functional' : 'Foreign' }}</Badge></td>
              </tr>
            </tbody>
          </table>
          <!-- FX preview -->
          <table v-else class="tbl">
            <thead><tr><th>From</th><th>To</th><th class="num">Rate</th><th>As of</th><th>Source</th></tr></thead>
            <tbody>
              <tr v-for="(r, i) in importRows" :key="i">
                <td><span class="code-cell">{{ r.fromCurrency }}</span></td>
                <td><span class="code-cell">{{ r.toCurrency }}</span></td>
                <td class="num fw-500">{{ r.rateValue.toFixed(4) }}</td>
                <td>{{ r.rateDate }}</td>
                <td>{{ r.rateType }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <template #footer>
        <Button variant="primary" :loading="importing" :disabled="!importRows.length" @click="runImport">
          Import {{ importRows.length || '' }} record{{ importRows.length !== 1 ? 's' : '' }}
        </Button>
        <Button variant="ghost" icon="download" @click="downloadTemplate">Download template</Button>
        <Button variant="ghost" @click="showImport = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.info-box {
  margin-top: 14px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
  background: color-mix(in oklab, var(--accent) 6%, var(--surface));
  border: 1px solid color-mix(in oklab, var(--accent) 20%, transparent);
  border-radius: 6px;
  color: var(--fg-2);
}
.req { color: oklch(0.5 0.22 25); }

.import-zone {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border: 1px dashed var(--border);
  border-radius: 8px;
  margin-bottom: 16px;
}

.error-list {
  margin-bottom: 14px;
  padding: 10px 12px;
  background: color-mix(in oklab, oklch(0.5 0.22 25) 8%, var(--surface));
  border: 1px solid color-mix(in oklab, oklch(0.5 0.22 25) 30%, transparent);
  border-radius: 6px;
}
.error-head { font-size: 12px; font-weight: 600; margin-bottom: 6px; color: oklch(0.5 0.22 25); }
.error-row  { font-size: 11px; color: var(--fg-2); line-height: 1.6; }

.import-preview { margin-top: 4px; }
.preview-head   { font-size: 12px; font-weight: 600; margin-bottom: 8px; color: var(--fg-2); }
.preview-scroll { max-height: 280px; overflow-y: auto; border: 1px solid var(--border); border-radius: 6px; }
</style>

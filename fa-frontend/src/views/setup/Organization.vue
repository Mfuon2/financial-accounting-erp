<script setup>
import { ref, computed, onMounted } from 'vue'
import { organization, numberConfig as numberConfigApi } from '@/api/index.js'
import { useAppMode, isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader from '@/components/PageHeader.vue'
import Button     from '@/components/primitives/Button.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { mode, setMode } = useAppMode()
const { currentUser }   = useAuth()
const { toast }         = useToast()
const entityId = computed(() => currentUser.value?.entityId)

// ── Tabs ───────────────────────────────────────────────────────────────────────
const tab = ref('profile')

// ── Company profile ────────────────────────────────────────────────────────────
const form     = ref({})
const original = ref({})
const saving   = ref(false)

onMounted(async () => {
  try {
    const data = await organization.get()
    if (data) { form.value = { ...data }; original.value = { ...data } }
  } catch {}
})

async function save() {
  saving.value = true
  try {
    const updated = await organization.update({
      name: form.value.name, legalName: form.value.legalName,
      taxIdentificationNumber: form.value.taxIdentificationNumber,
      reportingCurrency: form.value.reportingCurrency,
      countryCode: form.value.countryCode, timezone: form.value.timezone,
      fiscalYearStartMonth: form.value.fiscalYearStartMonth,
      addressLine1: form.value.addressLine1, addressLine2: form.value.addressLine2,
      city: form.value.city, postalCode: form.value.postalCode,
      phone: form.value.phone, email: form.value.email, website: form.value.website,
    })
    if (updated) { form.value = { ...updated }; original.value = { ...updated } }
    toast.success('Organization profile saved.')
  } catch {} finally { saving.value = false }
}

function discard() { form.value = { ...original.value } }

// ── Environment ────────────────────────────────────────────────────────────────
const switching = ref(false)

function pickMode(m) {
  if (m === mode.value) return
  switching.value = true
  setMode(m)
  toast.success(`Switched to ${m === 'demo' ? 'Demo' : 'Production'} mode — reloading…`, 2000)
  setTimeout(() => window.location.reload(), 700)
}

// ── Numbering ──────────────────────────────────────────────────────────────────
const numRows      = ref([])
const numLoading   = ref(false)
const savingModule = ref(null)   // moduleKey currently being saved

async function loadNumbering() {
  if (numRows.value.length || numLoading.value) return
  numLoading.value = true
  try {
    const rows = await numberConfigApi.getAll(entityId.value)
    numRows.value = rows.map(r => ({ ...r, _selected: r.prefix, _format: r.customFormat ?? r.format }))
  } catch {
    toast.error('Failed to load numbering config.')
  } finally { numLoading.value = false }
}

function onTabChange(t) {
  tab.value = t
  if (t === 'numbering') loadNumbering()
}

function isDirty(row) {
  return row._selected !== row.prefix || row._format !== (row.customFormat ?? row.format)
}

async function savePrefix(row) {
  if (!isDirty(row)) return
  // Format must contain a sequence token
  if (!row._format.match(/\{0+\}/)) {
    toast.warn('Format must contain a sequence token like {0000}.')
    return
  }
  savingModule.value = row.moduleKey
  try {
    const isDefaultFmt = row._format === defaultFormat(row._selected, row.yearScoped)
    const updated = await numberConfigApi.update(entityId.value, row.moduleKey, row._selected, isDefaultFmt ? null : row._format)
    const idx = numRows.value.findIndex(r => r.moduleKey === row.moduleKey)
    if (idx !== -1) {
      numRows.value[idx] = {
        ...updated,
        _selected: updated.prefix,
        _format:   updated.customFormat ?? updated.format,
      }
    }
    toast.success(`${row.label} numbering saved.`)
  } catch {
    row._selected = row.prefix
    row._format   = row.customFormat ?? row.format
  } finally { savingModule.value = null }
}

function defaultFormat(prefix, yearScoped) {
  return yearScoped ? `{PREFIX}-{YYYY}-{0000}` : `{PREFIX}{0000}`
}

function fmtExample(row) {
  const year = new Date().getFullYear()
  const fmt  = row._format || row.format
  const padMatch = fmt.match(/\{(0+)\}/)
  const pad  = padMatch ? padMatch[1].length : 4
  const seq  = '1'.padStart(pad, '0')
  return fmt
    .replace('{PREFIX}', row._selected)
    .replace('{YYYY}',   row.yearScoped ? String(year) : '')
    .replace('{YY}',     row.yearScoped ? String(year % 100).padStart(2, '0') : '')
    .replace(/\{0+\}/,   seq)
}
</script>

<template>
  <div class="page">
    <PageHeader :title="`Organization`" :meta="`${form.name} · ${form.registrationNumber}`">
      <Button variant="ghost" icon="eye">Audit log</Button>
    </PageHeader>

    <!-- Tab bar -->
    <div class="tab-bar">
      <button :class="['tab-btn', tab === 'profile'   && 'tab-btn--active']" @click="onTabChange('profile')">Company profile</button>
      <button :class="['tab-btn', tab === 'env'       && 'tab-btn--active']" @click="onTabChange('env')">Environment</button>
      <button :class="['tab-btn', tab === 'numbering' && 'tab-btn--active']" @click="onTabChange('numbering')">Numbering</button>
    </div>

    <div class="tab-body">

      <!-- ── Company profile ─────────────────────────────────────────────────── -->
      <div v-if="tab === 'profile'" class="org-section">
        <div class="org-section-head">
          <span class="org-section-title">Company profile</span>
          <span class="org-section-meta">Only CONTROLLER_CFO and SYSTEM_ADMIN may save changes</span>
        </div>
        <div class="org-form">
          <div class="row-2">
            <div class="field"><label>Trading name</label><input v-model="form.name" class="input" placeholder="Trading name" /></div>
            <div class="field"><label>Legal name</label><input v-model="form.legalName" class="input" placeholder="Full legal name" /></div>
          </div>
          <div class="row-2">
            <div class="field"><label>Registration no. <span class="locked-badge">locked</span></label><input :value="form.registrationNumber" class="input" disabled /></div>
            <div class="field"><label>Tax ID (PIN)</label><input v-model="form.taxIdentificationNumber" class="input" placeholder="e.g. A001234567A" /></div>
          </div>
          <div class="row-3">
            <div class="field"><label>Functional currency <span class="locked-badge">locked</span></label><input :value="form.functionalCurrency" class="input" disabled /></div>
            <div class="field"><label>Reporting currency</label><input v-model="form.reportingCurrency" class="input" placeholder="e.g. KES" /></div>
            <div class="field"><label>Fiscal year start <span class="locked-badge">locked</span></label><input :value="`Month ${form.fiscalYearStartMonth}`" class="input" disabled /></div>
          </div>
          <div class="row-2">
            <div class="field"><label>Country</label><input v-model="form.countryCode" class="input" placeholder="e.g. KE" /></div>
            <div class="field"><label>Timezone</label><input v-model="form.timezone" class="input" placeholder="e.g. Africa/Nairobi" /></div>
          </div>
          <div class="row-2">
            <div class="field"><label>Email</label><input v-model="form.email" class="input" type="email" placeholder="contact@company.com" /></div>
            <div class="field"><label>Phone</label><input v-model="form.phone" class="input" placeholder="+254..." /></div>
          </div>
          <div class="field"><label>Address</label><input v-model="form.addressLine1" class="input" placeholder="Street address" /></div>
        </div>
        <div class="org-actions">
          <Button variant="primary" icon="save" :loading="saving" @click="save">Save changes</Button>
          <Button variant="ghost" @click="discard">Discard</Button>
        </div>
      </div>

      <!-- ── Environment ─────────────────────────────────────────────────────── -->
      <div v-else-if="tab === 'env'" class="org-section">
        <div class="org-section-head">
          <span class="org-section-title">Environment</span>
          <span class="org-section-meta">Switching reloads the app with the selected data source</span>
        </div>
        <div class="env-grid">
          <button :class="['env-tile', mode === 'demo' && 'env-tile--active']" :disabled="switching" @click="pickMode('demo')">
            <div class="env-row">
              <div :class="['env-radio', mode === 'demo' && 'env-radio--on']"><div v-if="mode === 'demo'" class="env-dot" /></div>
              <div class="env-info">
                <div class="env-name">Demo mode <span v-if="mode === 'demo'" class="env-badge env-badge--demo">ACTIVE</span></div>
                <div class="env-desc">Built-in sample data. No backend required — safe for demos and UI review.</div>
              </div>
            </div>
          </button>
          <button :class="['env-tile', mode === 'production' && 'env-tile--active env-tile--prod']" :disabled="switching" @click="pickMode('production')">
            <div class="env-row">
              <div :class="['env-radio', mode === 'production' && 'env-radio--on env-radio--prod']"><div v-if="mode === 'production'" class="env-dot env-dot--prod" /></div>
              <div class="env-info">
                <div class="env-name">Production mode <span v-if="mode === 'production'" class="env-badge env-badge--prod">ACTIVE</span></div>
                <div class="env-desc">Live backend API. Requires a running server and valid session token.</div>
              </div>
            </div>
          </button>
        </div>
      </div>

      <!-- ── Numbering ───────────────────────────────────────────────────────── -->
      <div v-else-if="tab === 'numbering'" class="org-section">
        <div class="org-section-head">
          <span class="org-section-title">Document numbering</span>
          <span class="org-section-meta">Changes apply to new records only — existing codes are unaffected</span>
        </div>

        <div v-if="numLoading" class="num-empty">Loading…</div>

        <table v-else class="num-table">
          <thead>
            <tr>
              <th>Module</th>
              <th>Prefix</th>
              <th>Format</th>
              <th>Example</th>
              <th>Resets</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in numRows" :key="row.moduleKey">
              <td class="num-label">{{ row.label }}</td>
              <td>
                <SearchableSelect
                  v-model="row._selected"
                  :options="row.allowedPrefixes.map(p => ({ value: p, label: p }))"
                  :mono="true"
                  :compact="true"
                />
              </td>
              <td>
                <input
                  v-model="row._format"
                  class="input mono num-fmt-input"
                  spellcheck="false"
                  :title="'Tokens: {PREFIX} {YYYY} {YY} {0000}'"
                />
              </td>
              <td class="mono num-example">{{ fmtExample(row) }}</td>
              <td>
                <span :class="['reset-badge', row.yearScoped ? 'reset-badge--year' : 'reset-badge--never']">
                  {{ row.resets }}
                </span>
              </td>
              <td class="num-action">
                <Button
                  v-if="isDirty(row)"
                  variant="primary"
                  size="sm"
                  :loading="savingModule === row.moduleKey"
                  @click="savePrefix(row)"
                >
                  Save
                </Button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

    </div>
  </div>
</template>

<style scoped>
/* ── Tab bar ───────────────────────────────────────────────────────────────── */
.tab-bar {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--border);
  padding: 0 24px;
  background: var(--surface);
}
.tab-btn {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  background: none;
  border: none;
  border-bottom: 2px solid transparent;
  cursor: pointer;
  transition: color 0.12s, border-color 0.12s;
  margin-bottom: -1px;
}
.tab-btn:hover { color: var(--text); }
.tab-btn--active { color: var(--accent); border-bottom-color: var(--accent); font-weight: 600; }

/* ── Tab body ──────────────────────────────────────────────────────────────── */
.tab-body {
  padding: 16px 24px 24px;
  max-width: 860px;
}

/* ── Section card ──────────────────────────────────────────────────────────── */
.org-section {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 10px;
  overflow: hidden;
}
.org-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 16px;
  border-bottom: 1px solid var(--border);
  background: var(--surface-2);
}
.org-section-title { font-size: 13px; font-weight: 600; }
.org-section-meta  { font-size: 11px; color: var(--muted); font-family: monospace; }

/* ── Form ──────────────────────────────────────────────────────────────────── */
.org-form {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.row-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 10px; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field label {
  font-size: 11px; font-weight: 600; letter-spacing: 0.03em;
  text-transform: uppercase; color: var(--muted);
  display: flex; align-items: center; gap: 5px;
}
.field .input { font-size: 13px; height: 32px; padding: 0 10px; }

.locked-badge {
  font-size: 9px; font-weight: 700; letter-spacing: 0.05em;
  text-transform: uppercase; padding: 1px 5px; border-radius: 3px;
  background: var(--surface-3); color: var(--muted-2, var(--muted)); font-family: monospace;
}
.org-actions {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
  background: var(--surface-2);
}

/* ── Environment ───────────────────────────────────────────────────────────── */
.env-grid { display: grid; grid-template-columns: 1fr 1fr; }
.env-tile {
  text-align: left; background: transparent; border: none;
  padding: 14px 16px; cursor: pointer; transition: background 0.12s;
  border-right: 1px solid var(--border);
}
.env-tile:last-child { border-right: none; }
.env-tile:hover:not(:disabled) { background: var(--surface-2); }
.env-tile:disabled { opacity: 0.6; cursor: not-allowed; }
.env-tile--active { background: color-mix(in oklab, var(--accent) 5%, var(--surface)) !important; }
.env-tile--prod.env-tile--active { background: color-mix(in oklab, oklch(0.52 0.18 145) 5%, var(--surface)) !important; }
.env-row { display: flex; align-items: flex-start; gap: 10px; }
.env-info { flex: 1; }
.env-name { font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 7px; margin-bottom: 3px; }
.env-desc { font-size: 12px; color: var(--muted); line-height: 1.45; }
.env-radio { width: 15px; height: 15px; border-radius: 50%; border: 2px solid var(--border-strong); display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 1px; transition: border-color 0.12s; }
.env-radio--on   { border-color: var(--accent); }
.env-radio--prod { border-color: oklch(0.52 0.18 145); }
.env-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); }
.env-dot--prod { background: oklch(0.52 0.18 145); }
.env-badge { font-size: 9.5px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; padding: 2px 6px; border-radius: 3px; }
.env-badge--demo { background: color-mix(in oklab, var(--accent) 14%, transparent); color: var(--accent); }
.env-badge--prod { background: color-mix(in oklab, oklch(0.52 0.18 145) 14%, transparent); color: oklch(0.38 0.14 145); }

/* ── Numbering table ───────────────────────────────────────────────────────── */
.num-empty { padding: 40px; text-align: center; color: var(--muted); font-size: 13px; }

.num-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.num-table th {
  padding: 8px 14px;
  text-align: left;
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--muted);
  border-bottom: 1px solid var(--border);
  background: var(--surface-2);
}
.num-table td {
  padding: 9px 14px;
  border-bottom: 1px solid var(--border);
  vertical-align: middle;
}
.num-table tr:last-child td { border-bottom: none; }
.num-table tr:hover td { background: var(--surface-2); }

.num-label { font-weight: 500; }

.num-select {
  height: 30px;
  padding: 0 8px;
  font-size: 12px;
  width: 100px;
}

.num-fmt-input {
  height: 30px;
  font-size: 12px;
  width: 200px;
  padding: 0 8px;
}

.num-example {
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  white-space: nowrap;
}

.num-action { width: 72px; text-align: right; }

.reset-badge {
  display: inline-block;
  padding: 2px 7px;
  border-radius: 99px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
}
.reset-badge--year  { background: color-mix(in oklab, var(--accent) 12%, transparent); color: var(--accent); }
.reset-badge--never { background: var(--surface-3); color: var(--muted); }
</style>

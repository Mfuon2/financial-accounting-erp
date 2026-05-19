<script setup>
import { ref, computed, onMounted } from 'vue'
import { reports } from '@/api/index.js'
import { useAuth } from '@/composables/useAuth.js'
import { useActivePeriod } from '@/composables/useActivePeriod.js'
import { useToast } from '@/composables/useToast.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import Banner from '@/components/data-display/Banner.vue'

const { currentUser } = useAuth()
const { activePeriod } = useActivePeriod()
const { toast } = useToast()

const DATE_RE = /^\d{4}-\d{2}-\d{2}$/

const entityId = computed(() => currentUser.value?.entityId ?? null)

function defaultCurrentDate() {
  const p = activePeriod.value
  if (p?.endDate) return p.endDate
  const today = new Date()
  return new Date(today.getFullYear(), today.getMonth() + 1, 0).toISOString().slice(0, 10)
}

function defaultPriorDate() {
  const p = activePeriod.value
  if (p?.startDate) {
    const d = new Date(p.startDate)
    d.setDate(d.getDate() - 1)
    return d.toISOString().slice(0, 10)
  }
  const today = new Date()
  return new Date(today.getFullYear(), today.getMonth(), 0).toISOString().slice(0, 10)
}

const currentDate = ref(defaultCurrentDate())
const priorDate   = ref(defaultPriorDate())
const report      = ref(null)
const loading     = ref(false)
const error       = ref(null)

async function load() {
  if (!entityId.value) return
  if (!DATE_RE.test(currentDate.value) || !DATE_RE.test(priorDate.value)) {
    toast.error('Dates must be in YYYY-MM-DD format (use the date picker)')
    return
  }
  loading.value = true
  error.value = null
  try {
    const res = await reports.comparative({ entityId: entityId.value, asOfDate: currentDate.value, compareAsOfDate: priorDate.value })
    if (res) report.value = res
  } catch (e) {
    error.value = e?.message ?? 'Failed to load comparative trial balance.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const rows = computed(() => report.value?.rows ?? [])

function rowStyle(r) {
  const indent = r.depth * 16
  if (r.isHeader && r.depth === 0) return { fontWeight: '700', textTransform: 'uppercase', fontSize: '11px', letterSpacing: '0.04em', color: 'var(--text-muted)' }
  if (r.isHeader) return { paddingLeft: indent + 'px', fontWeight: '600' }
  return { paddingLeft: indent + 'px' }
}

function trStyle(r) {
  if (r.isHeader && r.depth === 0) return { background: 'var(--surface-alt, var(--surface))', borderTop: '2px solid var(--border)' }
  if (r.isHeader) return { background: 'color-mix(in oklab, var(--border) 30%, transparent)' }
  return {}
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Comparative Trial Balance"
      :meta="report ? `${report.compareAsOfDate} vs ${report.asOfDate}` : 'Select dates and run'"
    >
      <div style="display:flex;align-items:center;gap:8px;font-size:13px;color:var(--muted)">
        Prior
        <input type="date" v-model="priorDate"
          style="font-size:13px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;background:var(--surface);color:var(--text)" />
        Current
        <input type="date" v-model="currentDate"
          style="font-size:13px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;background:var(--surface);color:var(--text)" />
        <button
          @click="load"
          style="padding:4px 12px;border:1px solid var(--border);border-radius:6px;background:var(--surface);cursor:pointer;font-size:13px"
        >Run</button>
      </div>
    </PageHeader>

    <div class="page-section stack">
      <Banner v-if="error" kind="error" icon="warn">{{ error }}</Banner>

      <div v-if="loading" class="card" style="padding:48px;text-align:center;color:var(--text-muted)">Loading…</div>
      <div v-else-if="!report && !error" class="card" style="padding:48px;text-align:center;color:var(--text-muted)">
        Select date range and click <strong>Run</strong> to generate the report.
      </div>
      <div v-else class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th rowspan="2">Code</th>
              <th rowspan="2">Account</th>
              <th colspan="2" style="text-align:center;border-bottom:1px solid var(--border);border-left:1px solid var(--border)">{{ report?.compareAsOfDate ?? priorDate }} (Prior)</th>
              <th colspan="2" style="text-align:center;border-bottom:1px solid var(--border);border-left:1px solid var(--border)">{{ report?.asOfDate ?? currentDate }} (Current)</th>
              <th colspan="2" style="text-align:center;border-left:1px solid var(--border)">Movement</th>
            </tr>
            <tr>
              <th class="num" style="border-left:1px solid var(--border)">DR</th>
              <th class="num">CR</th>
              <th class="num" style="border-left:1px solid var(--border)">DR</th>
              <th class="num">CR</th>
              <th class="num" style="border-left:1px solid var(--border)">DR</th>
              <th class="num">CR</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.accountCode" :style="trStyle(r)">
              <td style="white-space:nowrap"><code style="font-size:11px">{{ r.accountCode }}</code></td>
              <td :style="rowStyle(r)">{{ r.accountName }}</td>
              <td class="num mono" style="border-left:1px solid var(--border)">{{ r.priorDebit   ? fmt(r.priorDebit,   { decimals: 0 }) : '—' }}</td>
              <td class="num mono">{{ r.priorCredit  ? fmt(r.priorCredit,  { decimals: 0 }) : '—' }}</td>
              <td class="num mono" style="border-left:1px solid var(--border)">{{ r.currentDebit  ? fmt(r.currentDebit,  { decimals: 0 }) : '—' }}</td>
              <td class="num mono">{{ r.currentCredit ? fmt(r.currentCredit, { decimals: 0 }) : '—' }}</td>
              <td class="num mono" :style="{ borderLeft: '1px solid var(--border)', color: r.movementDebit > 0 ? 'var(--pos)' : '' }">
                {{ r.movementDebit  ? fmt(r.movementDebit,  { signed: true, decimals: 0 }) : '—' }}
              </td>
              <td class="num mono" :style="{ color: r.movementCredit > 0 ? 'var(--pos)' : '' }">
                {{ r.movementCredit ? fmt(r.movementCredit, { signed: true, decimals: 0 }) : '—' }}
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td colspan="2" class="fw-600">Totals</td>
              <td class="num mono fw-600" style="border-left:1px solid var(--border)">{{ fmt(report?.priorTotalDebits   || 0, { decimals: 0 }) }}</td>
              <td class="num mono fw-600">{{ fmt(report?.priorTotalCredits  || 0, { decimals: 0 }) }}</td>
              <td class="num mono fw-600" style="border-left:1px solid var(--border)">{{ fmt(report?.currentTotalDebits  || 0, { decimals: 0 }) }}</td>
              <td class="num mono fw-600">{{ fmt(report?.currentTotalCredits || 0, { decimals: 0 }) }}</td>
              <td colspan="2" style="border-left:1px solid var(--border)"></td>
            </tr>
          </tfoot>
        </table>
      </div>

      <TableFooter :total="rows.length" label="accounts" />
    </div>
  </div>
</template>

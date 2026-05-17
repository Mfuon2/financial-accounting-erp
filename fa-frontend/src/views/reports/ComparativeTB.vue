<script setup>
import { ref, computed, onMounted } from 'vue'
import { TRIAL_BALANCE } from '@/data/index.js'
import { reports } from '@/api/index.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import Banner from '@/components/data-display/Banner.vue'

const DEMO_REPORT = {
  entityId: 'demo',
  asOfDate: '2026-02-28',
  compareAsOfDate: '2026-01-31',
  rows: TRIAL_BALANCE.map(r => ({
    accountCode:    r.code, accountName: r.name,
    currentDebit:   r.dr || 0, currentCredit:  r.cr || 0,
    priorDebit:     (r.dr || 0) * 0.92, priorCredit: (r.cr || 0) * 0.92,
    movementDebit:  (r.dr || 0) * 0.08, movementCredit: (r.cr || 0) * 0.08,
  })),
  currentTotalDebits:  TRIAL_BALANCE.reduce((s, r) => s + (r.dr || 0), 0),
  currentTotalCredits: TRIAL_BALANCE.reduce((s, r) => s + (r.cr || 0), 0),
  priorTotalDebits:    TRIAL_BALANCE.reduce((s, r) => s + ((r.dr || 0) * 0.92), 0),
  priorTotalCredits:   TRIAL_BALANCE.reduce((s, r) => s + ((r.cr || 0) * 0.92), 0),
}

const currentDate = ref('2026-02-28')
const priorDate   = ref('2026-01-31')
const report      = ref(DEMO_REPORT)
const loading     = ref(false)
const error       = ref(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const res = await reports.comparative({ entityId: 'current', asOfDate: currentDate.value, compareAsOfDate: priorDate.value })
    if (res) report.value = res
  } catch (e) {
    error.value = e?.message ?? 'Failed to load comparative trial balance.'
  } finally {
    loading.value = false
  }
}

onMounted(load)

const rows = computed(() => report.value?.rows ?? [])
</script>

<template>
  <div class="page">
    <PageHeader
      title="Comparative Trial Balance"
      :meta="`${report.compareAsOfDate} vs ${report.asOfDate}`"
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

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th rowspan="2">Code</th>
              <th rowspan="2">Account</th>
              <th colspan="2" style="text-align:center;border-bottom:1px solid var(--border)">{{ report.compareAsOfDate }} (Prior)</th>
              <th colspan="2" style="text-align:center;border-bottom:1px solid var(--border)">{{ report.asOfDate }} (Current)</th>
              <th colspan="2" rowspan="1" style="text-align:center">Movement</th>
            </tr>
            <tr>
              <th class="num">DR</th>
              <th class="num">CR</th>
              <th class="num">DR</th>
              <th class="num">CR</th>
              <th class="num">DR</th>
              <th class="num">CR</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.accountCode">
              <td><code>{{ r.accountCode }}</code></td>
              <td>{{ r.accountName }}</td>
              <td class="num mono">{{ r.priorDebit   ? fmt(r.priorDebit,   { decimals: 0 }) : '—' }}</td>
              <td class="num mono">{{ r.priorCredit  ? fmt(r.priorCredit,  { decimals: 0 }) : '—' }}</td>
              <td class="num mono">{{ r.currentDebit  ? fmt(r.currentDebit,  { decimals: 0 }) : '—' }}</td>
              <td class="num mono">{{ r.currentCredit ? fmt(r.currentCredit, { decimals: 0 }) : '—' }}</td>
              <td class="num mono" :style="{ color: r.movementDebit  > 0 ? 'var(--pos)' : '' }">
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
              <td class="num mono fw-600">{{ fmt(report.priorTotalDebits   || 0, { decimals: 0 }) }}</td>
              <td class="num mono fw-600">{{ fmt(report.priorTotalCredits  || 0, { decimals: 0 }) }}</td>
              <td class="num mono fw-600">{{ fmt(report.currentTotalDebits  || 0, { decimals: 0 }) }}</td>
              <td class="num mono fw-600">{{ fmt(report.currentTotalCredits || 0, { decimals: 0 }) }}</td>
              <td colspan="2"></td>
            </tr>
          </tfoot>
        </table>
      </div>

      <TableFooter :total="rows.length" label="accounts" />
    </div>
  </div>
</template>

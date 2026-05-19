<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { reports, periods as periodsApi } from '@/api/index.js'
import { useAuth }         from '@/composables/useAuth.js'
import { useOrganization } from '@/composables/useOrganization.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast }   from '@/composables/useToast.js'
import { useRouter }  from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import StRow from '@/components/data-display/StRow.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const router = useRouter()
const { toast }       = useToast()
const { currentUser } = useAuth()
const { isDemo }      = useAppMode()
const { org, load: loadOrg } = useOrganization()

const entityId = computed(() => currentUser.value?.entityId ?? null)

const periodOptions    = ref([])
const selectedPeriodId = ref(null)
const selectedPeriod   = computed(() => periodOptions.value.find(p => p.value === selectedPeriodId.value) ?? null)

const report  = ref(null)
const loading = ref(false)

async function loadPeriods() {
  if (isDemo.value || !entityId.value) return
  try {
    const data = await periodsApi.list({ entityId: entityId.value, size: 50 })
    const items = data?.content ?? (Array.isArray(data) ? data : [])
    periodOptions.value = items
      .filter(p => ['OPEN','ADJUSTING','CLOSING','CLOSED','REOPENED'].includes(p.status))
      .sort((a, b) => (b.startDate ?? '').localeCompare(a.startDate ?? ''))
      .map(p => ({ value: p.id, label: `${p.periodName} (${p.status})`, startDate: p.startDate, endDate: p.endDate }))
    if (periodOptions.value.length && !selectedPeriodId.value) {
      selectedPeriodId.value = periodOptions.value[0].value
    }
  } catch { }
}

async function load() {
  loading.value = true
  try {
    const params = isDemo.value
      ? {}
      : { entityId: entityId.value, startDate: selectedPeriod.value?.startDate, endDate: selectedPeriod.value?.endDate }
    if (!isDemo.value && (!params.entityId || !params.startDate)) { loading.value = false; return }
    const res = await reports.pnl(params)
    if (res) report.value = res
  } catch { } finally { loading.value = false }
}

onMounted(async () => { loadOrg(); await loadPeriods(); await load() })
watch(selectedPeriodId, load)
watch(entityId, async (val) => { if (val) { await loadPeriods(); await load() } })

const sections = computed(() => {
  const d = report.value
  if (!d) return []
  if (d.sections) return d.sections
  return [
    { type: 'section',  label: 'Revenue' },
    { type: 'subtotal', label: 'Total Revenue',       current: Number(d.totalRevenue) },
    { type: 'section',  label: 'Expenses' },
    { type: 'subtotal', label: 'Total Expenses',      current: -Number(d.totalExpenses) },
    { type: 'total',    label: 'Net Income / (Loss)', current: Number(d.netIncome) },
  ]
})

const periodLabel = computed(() => {
  const d = report.value
  if (!d) return selectedPeriod.value?.label ?? '—'
  if (d.period) return d.period
  if (d.startDate && d.endDate) return `${d.startDate} – ${d.endDate}`
  return selectedPeriod.value?.label ?? '—'
})

const entityName = computed(() =>
  org.value?.name ?? currentUser.value?.organizationName ?? currentUser.value?.entityName ?? ''
)

async function exportPdf() {
  if (!isDemo.value && (!entityId.value || !selectedPeriod.value)) {
    toast.warn('Select a period first')
    return
  }
  try {
    const params = isDemo.value ? {} : { entityId: entityId.value, startDate: selectedPeriod.value.startDate, endDate: selectedPeriod.value.endDate }
    await reports.pdfPnl(params, 'profit-loss.pdf')
    toast.success('PDF downloaded.')
  } catch { }
}
</script>

<template>
  <div class="page">
    <PageHeader title="Statement of Profit & Loss" :meta="periodLabel">
      <template #default>
        <SearchableSelect
          v-if="!isDemo && periodOptions.length"
          v-model="selectedPeriodId"
          :options="periodOptions"
          placeholder="Select period…"
          style="width:220px"
        />
        <Button size="sm" icon="download" variant="ghost" @click="exportPdf">PDF</Button>
        <Button size="sm" icon="download" variant="ghost">Excel</Button>
        <Button size="sm" icon="external" variant="ghost" @click="router.push('/ias1')">IAS 1 check</Button>
      </template>
    </PageHeader>
    <div class="page-section">
      <div v-if="loading && !report" style="padding:48px;text-align:center;color:var(--text-muted)">Loading…</div>
      <div v-else class="statement">
        <div class="statement-head">
          <h2>Statement of Profit &amp; Loss</h2>
          <div class="e-name">{{ entityName }}</div>
          <div class="e-period">{{ periodLabel }}</div>
        </div>
        <div class="st-row st-header" style="margin-top:16px;margin-bottom:6px;font-size:10.5px;color:var(--muted);font-weight:700;letter-spacing:0.05em;text-transform:uppercase">
          <div></div>
          <div class="st-num">Current period</div>
          <div class="st-num dim">Prior period</div>
        </div>
        <StRow v-for="(r, i) in sections" :key="i" v-bind="r" />
        <div style="margin-top:24px;font-size:10.5px;color:var(--muted);border-top:1px solid var(--border);padding-top:12px;display:flex;justify-content:space-between">
          <span>All figures in functional currency · IFRS compliant</span>
          <span class="mono">Generated {{ new Date().toLocaleDateString() }} · QeSuite IFRS</span>
        </div>
      </div>
    </div>
  </div>
</template>

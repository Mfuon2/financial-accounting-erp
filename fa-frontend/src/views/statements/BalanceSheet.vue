<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { reports } from '@/api/index.js'
import { useAuth }         from '@/composables/useAuth.js'
import { useOrganization } from '@/composables/useOrganization.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast }   from '@/composables/useToast.js'
import { useRouter }  from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import StRow from '@/components/data-display/StRow.vue'

const router = useRouter()
const { toast }       = useToast()
const { currentUser } = useAuth()
const { isDemo }      = useAppMode()
const { org, load: loadOrg } = useOrganization()

const entityId  = computed(() => currentUser.value?.entityId ?? null)
const asOfDate  = ref(new Date().toISOString().slice(0, 10))

const report  = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = isDemo.value
      ? {}
      : { entityId: entityId.value, asOfDate: asOfDate.value }
    if (!isDemo.value && (!params.entityId || !params.asOfDate)) { loading.value = false; return }
    const res = await reports.balanceSheet(params)
    if (res) report.value = res
  } catch { } finally { loading.value = false }
}

onMounted(() => { loadOrg(); load() })
watch(asOfDate, load)
watch(entityId, (val) => { if (val) load() })

const sections = computed(() => {
  const d = report.value
  if (!d) return []
  if (d.sections) return d.sections
  return [
    { type: 'section',  label: 'Assets' },
    { type: 'subtotal', label: 'Total Assets',               current: Number(d.totalAssets) },
    { type: 'section',  label: 'Liabilities' },
    { type: 'subtotal', label: 'Total Liabilities',          current: Number(d.totalLiabilities) },
    { type: 'section',  label: 'Equity' },
    { type: 'subtotal', label: 'Total Equity',               current: Number(d.totalEquity) },
    { type: 'total',    label: 'Total Liabilities & Equity', current: Number(d.totalLiabilities) + Number(d.totalEquity) },
  ]
})

const dateMeta = computed(() => `As at ${report.value?.asOfDate ?? asOfDate.value}`)

const entityName = computed(() =>
  org.value?.name ?? currentUser.value?.organizationName ?? currentUser.value?.entityName ?? ''
)

async function exportPdf() {
  if (!isDemo.value && !entityId.value) {
    toast.warn('No entity loaded')
    return
  }
  try {
    const params = isDemo.value ? {} : { entityId: entityId.value, asOfDate: asOfDate.value }
    await reports.pdfBalanceSheet(params, 'balance-sheet.pdf')
    toast.success('PDF downloaded.')
  } catch { }
}
</script>

<template>
  <div class="page">
    <PageHeader title="Statement of Financial Position" :meta="dateMeta">
      <template #default>
        <input
          v-if="!isDemo"
          type="date"
          v-model="asOfDate"
          class="date-input"
          style="font-size:13px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;background:var(--surface);color:var(--text)"
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
          <h2>Statement of Financial Position</h2>
          <div class="e-name">{{ entityName }}</div>
          <div class="e-period">{{ dateMeta }}</div>
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

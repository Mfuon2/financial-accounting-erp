<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { AR_AGEING, AUDIT, APPROVALS } from '@/data/index.js'
import { dashboard } from '@/api/dashboard.js'
import { approvals as approvalsApi } from '@/api/approvals.js'
import { audit } from '@/api/audit.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import Ico from '@/components/primitives/Ico.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import LineChart from '@/components/data-display/LineChart.vue'
import Donut from '@/components/data-display/Donut.vue'
import Banner from '@/components/data-display/Banner.vue'
import TimelineRow from '@/components/data-display/TimelineRow.vue'

const router = useRouter()

// ── Independent per-section state ─────────────────────────────────────────────
// Each section loads on its own so a slow API call never blocks the rest

const kpis = ref(null)
const kpisLoading = ref(true)

const sparklines = ref(null)
const chartLoading = ref(true)

const approvalItems = ref([])
const approvalsLoading = ref(true)

const activityItems = ref([])
const activityLoading = ref(true)

// ── Mount: fire all requests in parallel, independently ───────────────────────
onMounted(() => {
  // KPIs — cash, AR, MTD revenue/expenses
  dashboard.summary()
    .then(d => { kpis.value = d })
    .catch(() => {})
    .finally(() => { kpisLoading.value = false })

  // Sparkline chart data
  dashboard.sparklines()
    .then(d => { sparklines.value = d })
    .catch(() => {})
    .finally(() => { chartLoading.value = false })

  // Approvals queue
  approvalsApi.list()
    .then(d => { approvalItems.value = d ?? [] })
    .catch(() => {})
    .finally(() => { approvalsLoading.value = false })

  // Recent audit trail (last 6 entries)
  audit.list({ size: 6, page: 0 })
    .then(d => {
      const rows = d?.content ?? (Array.isArray(d) ? d : [])
      activityItems.value = rows.slice(0, 6)
    })
    .catch(() => { activityItems.value = AUDIT.slice(0, 6) })
    .finally(() => { activityLoading.value = false })
})

// ── Derived values ─────────────────────────────────────────────────────────────
const mtdRevenue  = computed(() => kpis.value?.mtdRevenue  ?? 2170200)
const mtdExpenses = computed(() => kpis.value?.mtdExpenses ?? 946800)
const mtdNet      = computed(() => mtdRevenue.value - mtdExpenses.value)
const mtdMargin   = computed(() => mtdRevenue.value > 0
  ? ((mtdNet.value / mtdRevenue.value) * 100).toFixed(1) : '0.0')

const chartData = computed(() => ({
  labels: sparklines.value?.labels ?? sparklines.value?.sparkLabels ?? [],
  datasets: [
    { label: 'Revenue',  data: sparklines.value?.revenue  ?? sparklines.value?.sparkRev  ?? [], borderColor: 'var(--accent)' },
    { label: 'Expenses', data: sparklines.value?.expenses ?? sparklines.value?.sparkExp  ?? [], borderColor: 'var(--neg)' },
  ],
}))

const recentActivity = computed(() =>
  activityItems.value.length ? activityItems.value : AUDIT.slice(0, 6)
)

// ── Audit trail formatting ────────────────────────────────────────────────────
const ACTION_VERB = {
  CREATE: 'Created', UPDATE: 'Updated', DELETE: 'Deleted',
  POST: 'Posted', REVERSE: 'Reversed', APPROVE: 'Approved',
  REJECT: 'Rejected', CLOSE: 'Closed', REOPEN: 'Reopened',
  EXPORT: 'Exported', TAX_ADJUSTMENT: 'Tax adjusted',
  SUBMIT: 'Submitted', LOGIN: 'Logged in', CALLBACK: 'Callback',
  TRANSITION: 'Transitioned',
}

const RESOURCE_LABEL = {
  CUSTOMER: 'Customer', SUPPLIER: 'Supplier', JOURNAL_ENTRY: 'Journal entry',
  FIXED_ASSET: 'Fixed asset', INVOICE: 'Invoice', PAYMENT: 'Payment',
  BILL: 'Bill', ACCOUNT: 'Account', PERIOD: 'Period', USER: 'User',
  AP_BILL: 'Bill', AP_PAYMENT: 'Payment', AP_DEBIT_NOTE: 'Debit note',
  FIXED_ASSET_DISPOSAL: 'Asset disposal',
}

function formatActivity(entry) {
  // Demo data already has a clean detail field — use it directly
  if (entry.detail) return entry.detail

  const verb = ACTION_VERB[entry.action] ?? (entry.action ?? 'Changed').replace(/_/g, ' ')
  const type = RESOURCE_LABEL[entry.resourceType]
    ?? (entry.resourceType ?? 'Record').replace(/_/g, ' ').toLowerCase()
    .replace(/^\w/, c => c.toUpperCase())

  // Try to extract a meaningful name from the payload JSON
  let name = ''
  const raw = entry.payloadAfter ?? entry.payloadBefore
  if (raw) {
    try {
      const p = JSON.parse(raw)
      name = p.name ?? p.customerCode ?? p.supplierCode ?? p.assetCode
           ?? p.billNumber ?? p.reference ?? p.accountCode ?? p.username
           ?? p.accountName ?? p.assetName ?? ''
    } catch {}
  }

  // Fall back to target field (demo) or shortened resourceId
  if (!name) name = entry.target ?? (entry.resourceId ? entry.resourceId.slice(0, 8) + '…' : '')

  return name ? `${verb} ${type}: ${name}` : `${verb} ${type}`
}

function formatActor(entry) {
  const actor = entry.actor ?? entry.userId ?? '—'
  // If it's a UUID, show only first 8 chars to avoid clutter
  return /^[0-9a-f]{8}-/i.test(actor) ? actor.slice(0, 8) + '…' : actor
}

function formatTime(entry) {
  const raw = entry.ts ?? entry.createdAt ?? ''
  // ISO "2026-05-15T09:30:47Z" → "09:30" ; space-separated "2026-02-28 23:58" → "23:58"
  return raw.slice(11, 16) || '—'
}

// ── AR Ageing (static for now — own dedicated section loads from AR view) ─────
const totalAR = AR_AGEING.reduce((s, r) => s + r.total, 0)
const ageingBuckets = [
  { l: 'Current',  v: AR_AGEING.reduce((s, r) => s + r.current, 0), c: 'var(--pos)' },
  { l: '1–30 d',   v: AR_AGEING.reduce((s, r) => s + r.b1_30, 0),   c: 'var(--info)' },
  { l: '31–60 d',  v: AR_AGEING.reduce((s, r) => s + r.b31_60, 0),  c: 'var(--warn)' },
  { l: '61–90 d',  v: AR_AGEING.reduce((s, r) => s + r.b61_90, 0),  c: 'var(--warn)' },
  { l: '90+ d',    v: AR_AGEING.reduce((s, r) => s + r.b90, 0),     c: 'var(--neg)' },
]

// ── Trial balance (static widget) ─────────────────────────────────────────────
const tbSegments = [
  { value: 8412500, color: 'var(--accent)' },
  { value: 1984140, color: 'var(--neg)' },
  { value: 6428360, color: 'var(--info)' },
]

// ── Misc ─────────────────────────────────────────────────────────────────────
const exportFlash  = ref(false)
const refreshFlash = ref(false)

function doExport() {
  exportFlash.value = true
  setTimeout(() => exportFlash.value = false, 1800)
}

function reload() {
  refreshFlash.value = true
  kpisLoading.value = true
  chartLoading.value = true
  approvalsLoading.value = true
  activityLoading.value = true

  dashboard.summary()
    .then(d => { kpis.value = d })
    .finally(() => { kpisLoading.value = false })

  dashboard.sparklines()
    .then(d => { sparklines.value = d })
    .finally(() => { chartLoading.value = false })

  approvalsApi.list()
    .then(d => { approvalItems.value = d ?? [] })
    .finally(() => { approvalsLoading.value = false })

  audit.list({ size: 6, page: 0 })
    .then(d => { activityItems.value = (d?.content ?? (Array.isArray(d) ? d : [])).slice(0, 6) })
    .catch(() => {})
    .finally(() => {
      activityLoading.value = false
      setTimeout(() => refreshFlash.value = false, 1200)
    })
}

const steps = [
  'Source Docs', 'Journalize', 'Post to Ledger', 'Trial Balance',
  'Adjusting', 'Adj. Trial Bal.', 'Statements', 'Closing', 'Post-Closing TB',
]
</script>

<template>
  <div class="page">
    <PageHeader
      title="Dashboard"
      meta="QeSuite Consulting Ltd · Fiscal Year 2026 · Functional KES"
    >
      <Button variant="ghost" icon="download" @click="doExport">{{ exportFlash ? 'Exported ✓' : 'Export' }}</Button>
      <Button variant="ghost" icon="refresh" @click="reload" :disabled="kpisLoading">
        {{ refreshFlash ? 'Refreshing…' : 'Refresh' }}
      </Button>
      <Button variant="primary" icon="plus" @click="router.push('/journals')">New journal</Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner kind="warn" icon="warn">
        <strong>Period 2026-02 is in ADJUSTING.</strong>
        <span class="muted"> Complete adjusting entries before closing.</span>
        <template #action>
          <Button variant="ghost" size="sm" @click="router.push('/period-end')">Open tasks →</Button>
        </template>
      </Banner>

      <!-- KPI cards — each shows a skeleton individually while loading -->
      <div class="kpi-grid">
        <Kpi
          label="Cash & Equivalents" icon="banknote"
          :value="kpis?.cashAndEquivalents ?? 2713620"
          unit="KES" :delta="4.2" deltaLabel="vs Jan"
          :spark="kpis?.sparkCash ?? []"
          :loading="kpisLoading"
        />
        <Kpi
          label="Accounts Receivable" icon="inbox"
          :value="kpis?.accountsReceivable ?? totalAR"
          unit="KES" :delta="2.8" deltaLabel="vs Jan"
          :spark="kpis?.sparkAr ?? []"
          :loading="kpisLoading"
        />
        <Kpi
          label="MTD Revenue" icon="trend-up"
          :value="mtdRevenue"
          unit="KES" :delta="9.9" deltaLabel="vs Jan"
          :spark="kpis?.sparkRev ?? []"
          :loading="kpisLoading"
        />
        <Kpi
          label="Operating Expenses" icon="receipt"
          :value="mtdExpenses"
          unit="KES" :delta="-3.1" deltaLabel="vs Jan"
          :spark="kpis?.sparkExp ?? []" sparkColor="var(--neg)"
          :loading="kpisLoading"
        />
      </div>

      <div class="row-2" style="grid-template-columns:1.6fr 1fr">
        <!-- Revenue vs Expenses chart — independent loading state -->
        <div class="card">
          <div class="card-head">
            <Ico name="chart" :size="13" /> Revenue vs Expenses — Last 12 months
            <div class="h-meta">KES · in millions</div>
          </div>
          <div class="card-body" style="display:flex;gap:24px;align-items:center">
            <LineChart v-if="!chartLoading" :data="chartData" :w="520" :h="200" />
            <div v-else class="chart-skeleton" />
            <div class="stack" style="gap:10px;font-size:11.5px;min-width:110px">
              <div class="h-row" style="gap:6px">
                <span style="width:10px;height:2px;background:var(--accent);display:inline-block;border-radius:1px;flex-shrink:0"></span>
                <span>Revenue</span>
                <span class="mono" style="margin-left:auto;font-weight:600">{{ fmt(mtdRevenue / 1e6, { decimals: 2 }) }}M</span>
              </div>
              <div class="h-row" style="gap:6px">
                <span style="width:10px;height:2px;background:var(--neg);display:inline-block;border-radius:1px;flex-shrink:0"></span>
                <span>Expenses</span>
                <span class="mono" style="margin-left:auto;font-weight:600">{{ fmt(mtdExpenses / 1e6, { decimals: 2 }) }}M</span>
              </div>
              <div class="divider" style="margin:2px 0" />
              <div class="h-row">
                <span style="font-weight:600">Net</span>
                <span class="mono pos" style="margin-left:auto;font-weight:600">+{{ fmt(mtdNet / 1e6, { decimals: 2 }) }}M</span>
              </div>
              <div class="muted" style="font-size:10.5px">Margin {{ mtdMargin }}%</div>
            </div>
          </div>
        </div>

        <!-- Approvals queue — independent loading state -->
        <div class="card">
          <div class="card-head">
            <Ico name="approve" :size="13" /> Approvals Queue
            <Badge status="pending" :dot="false" style="margin-left:6px">
              {{ kpis?.pendingApprovals ?? approvalItems.length }} pending
            </Badge>
            <div class="h-meta">
              <span style="cursor:pointer;color:var(--accent)" @click="router.push('/approvals')">View all →</span>
            </div>
          </div>
          <div class="card-body no-pad">
            <div v-if="approvalsLoading" class="section-skeleton" style="height:120px" />
            <template v-else>
              <div v-for="a in approvalItems.slice(0, 4)" :key="a.id" class="approval-row">
                <div>
                  <div class="ar-title">{{ a.title }}</div>
                  <div class="ar-meta">{{ a.type }} · {{ a.ref }} · {{ a.currency }} {{ fmt(a.amount) }} · by {{ a.submittedBy }}</div>
                </div>
                <div class="ar-actions">
                  <Button variant="ghost" size="sm" icon="x" />
                  <Button variant="primary" size="sm" icon="check">Approve</Button>
                </div>
              </div>
              <div v-if="approvalItems.length === 0" class="muted" style="padding:16px;text-align:center;font-size:12px">
                No pending approvals
              </div>
            </template>
          </div>
        </div>
      </div>

      <div class="row-3">
        <!-- AR Ageing — uses static data, no loading needed -->
        <div class="card">
          <div class="card-head">
            <Ico name="clock" :size="13" /> AR Ageing buckets
            <div class="h-meta">{{ fmt(totalAR, { currency: 'KES', compact: true }) }}</div>
          </div>
          <div class="card-body">
            <div v-for="(row, i) in ageingBuckets" :key="i" class="h-bar-row">
              <div>
                <div class="h-row" style="justify-content:space-between;margin-bottom:3px">
                  <span>{{ row.l }}</span>
                  <span class="mono">{{ fmt(row.v, { compact: true }) }}</span>
                </div>
                <div class="h-bar">
                  <span :style="{ width: `${(row.v / Math.max(totalAR, 1)) * 100}%`, background: row.c }" />
                </div>
              </div>
            </div>
            <Button variant="ghost" size="sm" icon="external" style="margin-top:6px" @click="router.push('/ar-ageing')">Full AR ageing report</Button>
          </div>
        </div>

        <!-- TB health widget — static  -->
        <div class="card">
          <div class="card-head">
            <Ico name="ledger" :size="13" /> Trial Balance Health
            <div class="h-meta">Period 2026-02</div>
          </div>
          <div class="card-body stack" style="gap:14px">
            <div class="h-row" style="gap:16px;align-items:center">
              <Donut :segments="tbSegments" :size="110" :thickness="16" />
              <div style="flex:1;font-size:12px">
                <div class="h-row" style="justify-content:space-between"><span>Assets</span><span class="mono">8.41M</span></div>
                <div class="h-row" style="justify-content:space-between"><span>Liabilities</span><span class="mono">1.98M</span></div>
                <div class="h-row" style="justify-content:space-between"><span>Equity</span><span class="mono">6.43M</span></div>
                <div class="divider" style="margin:8px 0 4px" />
                <div class="h-row" style="justify-content:space-between;font-weight:600">
                  <span><Ico name="check" :size="11" style="color:var(--pos)" /> Balanced</span>
                  <span class="mono pos">0.00 KES</span>
                </div>
              </div>
            </div>
            <Button variant="ghost" size="sm" icon="external" @click="router.push('/trial-balance')">Open trial balance</Button>
          </div>
        </div>

        <!-- Recent activity — independent loading state -->
        <div class="card">
          <div class="card-head">
            <Ico name="shield" :size="13" /> Recent activity
            <div class="h-meta">
              <span style="cursor:pointer;color:var(--accent)" @click="router.push('/audit')">Audit trail →</span>
            </div>
          </div>
          <div class="card-body">
            <div v-if="activityLoading" class="section-skeleton" style="height:140px" />
            <div v-else class="timeline">
              <TimelineRow
                v-for="(a, i) in recentActivity.slice(0, 6)"
                :key="a.id ?? a.ts ?? i"
                :time="formatTime(a)"
                :body="formatActivity(a)"
                :actor="formatActor(a)"
              />
            </div>
          </div>
        </div>
      </div>

      <!-- 9-step cycle — static -->
      <div class="card">
        <div class="card-head">
          <Ico name="branch" :size="13" /> 9-Step accounting cycle
          <div class="h-meta">Period 2026-02 progress</div>
        </div>
        <div class="card-body">
          <div class="stepper">
            <div v-for="(s, i) in steps" :key="i" :class="['step', i < 5 ? 'done' : i === 5 ? 'active' : '']">
              <div class="step-num">
                <Ico v-if="i < 5" name="check" :size="10" />
                <span v-else>{{ i + 1 }}</span>
              </div>
              <div>{{ s }}</div>
              <div class="step-line" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chart-skeleton {
  width: 520px;
  height: 200px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--surface-2) 25%, var(--surface-3) 50%, var(--surface-2) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  flex-shrink: 0;
}
.section-skeleton {
  border-radius: 6px;
  background: linear-gradient(90deg, var(--surface-2) 25%, var(--surface-3) 50%, var(--surface-2) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
  margin: 8px 12px;
}
@keyframes shimmer {
  0%   { background-position: 200% 0 }
  100% { background-position: -200% 0 }
}
</style>

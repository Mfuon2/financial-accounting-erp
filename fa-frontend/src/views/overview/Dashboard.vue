<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { dashboard } from '@/api/dashboard.js'
import { invoices as invoicesApi } from '@/api/index.js'
import { approvals as approvalsApi } from '@/api/approvals.js'
import { audit } from '@/api/audit.js'
import { fmt } from '@/utils/format.js'
import { useActivePeriod } from '@/composables/useActivePeriod.js'
import { useOrganization } from '@/composables/useOrganization.js'
import { useAuth } from '@/composables/useAuth.js'
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
const { activePeriod, load: loadPeriod } = useActivePeriod()
const { org, load: loadOrg } = useOrganization()
const { currentUser } = useAuth()

// ── Independent per-section state ─────────────────────────────────────────────

const kpis = ref(null)
const kpisLoading = ref(true)

const sparklines = ref(null)
const chartLoading = ref(true)

const approvalItems = ref([])
const approvalsLoading = ref(true)

const activityItems = ref([])
const activityLoading = ref(true)

const arAgeing = ref(null)
const arLoading = ref(true)

const tbData = ref(null)
const tbLoading = ref(true)

// ── Auto-refresh: reload active period every 30 seconds to catch period transitions ──
let _periodRefreshTimer = null
onMounted(() => {
  _periodRefreshTimer = setInterval(() => loadPeriod(true), 30_000)
})
onUnmounted(() => {
  if (_periodRefreshTimer) clearInterval(_periodRefreshTimer)
})

// ── Mount: fire all requests in parallel, independently ───────────────────────
onMounted(() => {
  loadPeriod()
  loadOrg()

  dashboard.summary()
    .then(d => { kpis.value = d })
    .catch(() => {})
    .finally(() => { kpisLoading.value = false })

  dashboard.sparklines()
    .then(d => { sparklines.value = d })
    .catch(() => {})
    .finally(() => { chartLoading.value = false })

  dashboard.tbSummary()
    .then(d => { tbData.value = d })
    .catch(() => {})
    .finally(() => { tbLoading.value = false })

  const entityId = currentUser.value?.entityId
  if (entityId) {
    const today = new Date().toISOString().slice(0, 10)
    invoicesApi.arAgeing({ entityId, asOfDate: today })
      .then(d => { arAgeing.value = d })
      .catch(() => {})
      .finally(() => { arLoading.value = false })
  } else {
    arLoading.value = false
  }

  approvalsApi.list()
    .then(d => { approvalItems.value = d ?? [] })
    .catch(() => {})
    .finally(() => { approvalsLoading.value = false })

  audit.list({ size: 6, page: 0 })
    .then(d => {
      const rows = d?.content ?? (Array.isArray(d) ? d : [])
      activityItems.value = rows.slice(0, 6)
    })
    .catch(() => {})
    .finally(() => { activityLoading.value = false })
})

// ── Functional currency (from org) ───────────────────────────────────────────
const functionalCcy = computed(() => org.value?.functionalCurrency ?? 'KES')

// ── Derived values ─────────────────────────────────────────────────────────────
const mtdRevenue  = computed(() => kpis.value?.mtdRevenue  ?? 0)
const mtdExpenses = computed(() => kpis.value?.mtdExpenses ?? 0)
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

const recentActivity = computed(() => activityItems.value)

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
  if (entry.actorName) return entry.actorName
  const actor = entry.actor ?? entry.userId ?? '—'
  return /^[0-9a-f]{8}-/i.test(String(actor)) ? String(actor).slice(0, 8) + '…' : String(actor)
}

function formatTime(entry) {
  const raw = entry.ts ?? entry.createdAt ?? ''
  // ISO "2026-05-15T09:30:47Z" → "09:30" ; space-separated "2026-02-28 23:58" → "23:58"
  return raw.slice(11, 16) || '—'
}

// ── AR Ageing ─────────────────────────────────────────────────────────────────
const totalAR = computed(() => Number(arAgeing.value?.totalOutstanding ?? 0))
const ageingBuckets = computed(() => {
  const d = arAgeing.value
  return [
    { l: 'Current',  v: Number(d?.current?.totalAmount         ?? d?.current         ?? 0), c: 'var(--pos)' },
    { l: '31–60 d',  v: Number(d?.thirtyOneToSixty?.totalAmount ?? d?.thirtyOneToSixty ?? 0), c: 'var(--warn)' },
    { l: '61–90 d',  v: Number(d?.sixtyOneToNinety?.totalAmount ?? d?.sixtyOneToNinety ?? 0), c: 'var(--warn)' },
    { l: '90+ d',    v: Number(d?.ninetyPlus?.totalAmount       ?? d?.ninetyPlus       ?? 0), c: 'var(--neg)' },
  ]
})

// ── Fiscal year labels for widget headers (BUG-41) ────────────────────────────
const kpiFY  = computed(() => kpis.value?.fiscalYear  ?? activePeriod.value?.startDate?.slice(0, 4) ?? new Date().getFullYear())
const tbFY   = computed(() => tbData.value?.fiscalYear ?? activePeriod.value?.startDate?.slice(0, 4) ?? new Date().getFullYear())
const chartFY = computed(() => {
  // Revenue chart always says "Last 12 months" so show the end year
  return new Date().getFullYear()
})

// ── Trial balance ─────────────────────────────────────────────────────────────
const tbSegments = computed(() => {
  return [
    { value: Number(tbData.value?.assets      ?? 0), color: 'var(--accent)' },
    { value: Number(tbData.value?.liabilities ?? 0), color: 'var(--neg)' },
    { value: Number(tbData.value?.equity      ?? 0), color: 'var(--info)' },
  ]
})
const tbAssets      = computed(() => Number(tbData.value?.assets      ?? 0))
const tbLiabilities = computed(() => Number(tbData.value?.liabilities ?? 0))
const tbEquity      = computed(() => Number(tbData.value?.equity      ?? 0))
const tbBalanced    = computed(() => tbData.value ? tbData.value.isBalanced : true)
const tbImbalance   = computed(() => tbData.value ? Number(tbData.value.imbalance ?? 0) : 0)

// ── 9-step cycle progress (derived from period-end task completion) ───────────
// Mirrors the task list in PeriodEndTasks.vue — 15 tasks mapped to 9 steps.
const stepsDone = computed(() => {
  const s = activePeriod.value?.status
  if (!s) return 0
  // Compute the same task statuses that PeriodEndTasks.vue uses
  function isDone(...statuses) { return statuses.includes(s) }
  const taskStatuses = [
    isDone('ADJUSTING','CLOSING','CLOSED','REOPENED'), // 1 Unadjusted TB
    isDone('ADJUSTING','CLOSING','CLOSED','REOPENED'), // 2 Transition to ADJUSTING
    false,                                              // 3 Manual accruals (always pending)
    false,                                              // 4 Manual deferrals (always pending)
    false,                                              // 5 Amortize prepaid (always pending)
    false,                                              // 6 Recognize revenue (always pending)
    isDone('ADJUSTING','CLOSING','CLOSED','REOPENED'), // 7 Batch depreciation
    isDone('CLOSING','CLOSED','REOPENED'),              // 8 FX revaluation
    isDone('CLOSING','CLOSED','REOPENED'),              // 9 Adjusted TB
    isDone('CLOSING','CLOSED','REOPENED'),              // 10 P&L statement
    isDone('CLOSING','CLOSED','REOPENED'),              // 11 Balance Sheet
    isDone('CLOSING','CLOSED','REOPENED'),              // 12 Cash Flow
    isDone('CLOSING','CLOSED','REOPENED'),              // 13 Transition to CLOSING
    isDone('CLOSED','REOPENED'),                        // 14 Post closing entries
    isDone('CLOSED','REOPENED'),                        // 15 Post-closing TB
  ]
  const completedCount = taskStatuses.filter(Boolean).length
  return Math.round(completedCount / 15 * 9)
})

// ── Period banner ─────────────────────────────────────────────────────────────
const showBanner = computed(() => {
  const s = activePeriod.value?.status
  return s === 'ADJUSTING' || s === 'CLOSING'
})
const bannerKind = computed(() => activePeriod.value?.status === 'CLOSING' ? 'error' : 'warn')
const bannerText = computed(() => {
  const p = activePeriod.value
  if (!p) return ''
  const name = p.periodName ?? p.code ?? ''
  if (p.status === 'ADJUSTING') return `${name} is in ADJUSTING. Complete adjusting entries before closing.`
  if (p.status === 'CLOSING')   return `${name} is in CLOSING. Post closing entries to finalise the period.`
  return ''
})

// ── Page header meta ──────────────────────────────────────────────────────────
const headerMeta = computed(() => {
  const orgName = org.value?.name ?? 'QeSuite'
  const fy = activePeriod.value?.startDate?.slice(0, 4) ?? new Date().getFullYear()
  const ccy = org.value?.functionalCurrency ?? 'KES'
  return `${orgName} · Fiscal Year ${fy} · Functional ${ccy}`
})

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
  arLoading.value = true
  tbLoading.value = true

  loadPeriod(true)
  loadOrg(true)

  dashboard.summary()
    .then(d => { kpis.value = d })
    .finally(() => { kpisLoading.value = false })

  dashboard.sparklines()
    .then(d => { sparklines.value = d })
    .finally(() => { chartLoading.value = false })

  dashboard.tbSummary()
    .then(d => { tbData.value = d })
    .finally(() => { tbLoading.value = false })

  const entityId = currentUser.value?.entityId
  if (entityId) {
    const today = new Date().toISOString().slice(0, 10)
    invoicesApi.arAgeing({ entityId, asOfDate: today })
      .then(d => { arAgeing.value = d })
      .catch(() => {})
      .finally(() => { arLoading.value = false })
  } else {
    arLoading.value = false
  }

  approvalsApi.list()
    .then(d => { approvalItems.value = d ?? [] })
    .finally(() => { approvalsLoading.value = false })

  audit.list({ size: 6, page: 0 })
    .then(d => { activityItems.value = (d?.content ?? (Array.isArray(d) ? d : [])).slice(0, 6) })
    .catch(() => { activityItems.value = [] })
    .finally(() => {
      activityLoading.value = false
      setTimeout(() => refreshFlash.value = false, 1200)
    })
}

const steps = [
  'Source Docs', 'Journalize', 'Post to Ledger', 'Trial Balance',
  'Adjusting', 'Adjusted Trial Balance', 'Statements', 'Closing', 'Post-Closing Trial Balance',
]
</script>

<template>
  <div class="page">
    <PageHeader
      title="Dashboard"
      :meta="headerMeta"
    >
      <Button variant="ghost" icon="download" @click="doExport">{{ exportFlash ? 'Exported ✓' : 'Export' }}</Button>
      <Button variant="ghost" icon="refresh" @click="reload" :disabled="kpisLoading">
        {{ refreshFlash ? 'Refreshing…' : 'Refresh' }}
      </Button>
      <Button variant="primary" icon="plus" @click="router.push('/journals')">New journal</Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner v-if="showBanner" :kind="bannerKind" icon="warn">
        <strong>{{ bannerText }}</strong>
        <template #action>
          <Button variant="ghost" size="sm" @click="router.push('/period-end')">Open tasks →</Button>
        </template>
      </Banner>

      <!-- KPI cards — each shows a skeleton individually while loading -->
      <div class="kpi-grid">
        <Kpi
          label="Cash & Equivalents" icon="banknote"
          :value="kpis?.cashAndEquivalents ?? 0"
          :unit="functionalCcy"
          :spark="kpis?.sparkCash ?? []"
          :loading="kpisLoading"
        />
        <Kpi
          label="Accounts Receivable" icon="inbox"
          :value="kpis?.accountsReceivable ?? totalAR"
          :unit="functionalCcy"
          :spark="kpis?.sparkAr ?? []"
          :loading="kpisLoading"
        />
        <Kpi
          label="MTD Revenue" icon="trend-up"
          :value="mtdRevenue"
          :unit="functionalCcy"
          :spark="kpis?.sparkRev ?? []"
          :loading="kpisLoading"
        />
        <Kpi
          label="Operating Expenses" icon="receipt"
          :value="mtdExpenses"
          :unit="functionalCcy"
          :spark="kpis?.sparkExp ?? []" sparkColor="var(--neg)"
          :loading="kpisLoading"
        />
      </div>

      <div class="row-2" style="grid-template-columns:1.6fr 1fr">
        <!-- Revenue vs Expenses chart — independent loading state -->
        <div class="card">
          <div class="card-head">
            <Ico name="chart" :size="13" /> Revenue vs Expenses — Last 12 months
            <span class="fy-badge">FY{{ chartFY }}</span>
            <div class="h-meta">{{ functionalCcy }} · in millions</div>
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
                  <div class="ar-meta">
                    {{ { JOURNAL_ENTRY: 'Journal Entry', INVOICE: 'Invoice', BILL: 'Vendor Bill' }[a.type] ?? a.type }}
                    · {{ a.ref }} · {{ a.currency }} {{ fmt(a.amount) }} · by {{ a.submittedBy }}
                  </div>
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
        <!-- AR Ageing -->
        <div class="card">
          <div class="card-head">
            <Ico name="clock" :size="13" /> AR Ageing buckets
            <span class="fy-badge">FY{{ kpiFY }}</span>
            <div class="h-meta">{{ fmt(totalAR, { currency: functionalCcy, compact: true }) }}</div>
          </div>
          <div class="card-body">
            <div v-if="arLoading" class="section-skeleton" style="height:120px" />
            <template v-else>
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
            </template>
            <Button variant="ghost" size="sm" icon="external" style="margin-top:6px" @click="router.push('/ar-ageing')">Full AR ageing report</Button>
          </div>
        </div>

        <!-- TB health widget -->
        <div class="card">
          <div class="card-head">
            <Ico name="ledger" :size="13" /> Trial Balance Health
            <span class="fy-badge">FY{{ tbFY }}</span>
            <div class="h-meta">{{ activePeriod?.periodName ?? activePeriod?.code ?? 'No active period' }}</div>
          </div>
          <div class="card-body stack" style="gap:14px">
            <div v-if="tbLoading" class="section-skeleton" style="height:120px" />
            <div v-else class="h-row" style="gap:16px;align-items:center">
              <Donut :segments="tbSegments" :size="110" :thickness="16" />
              <div style="flex:1;font-size:12px">
                <div class="h-row" style="justify-content:space-between"><span>Assets</span><span class="mono">{{ fmt(tbAssets / 1e6, { decimals: 2 }) }}M</span></div>
                <div class="h-row" style="justify-content:space-between"><span>Liabilities</span><span class="mono">{{ fmt(tbLiabilities / 1e6, { decimals: 2 }) }}M</span></div>
                <div class="h-row" style="justify-content:space-between"><span>Equity</span><span class="mono">{{ fmt(tbEquity / 1e6, { decimals: 2 }) }}M</span></div>
                <div class="divider" style="margin:8px 0 4px" />
                <div class="h-row" style="justify-content:space-between;font-weight:600">
                  <span>
                    <Ico :name="tbBalanced ? 'check' : 'warn'" :size="11" :style="{ color: tbBalanced ? 'var(--pos)' : 'var(--neg)' }" />
                    {{ tbBalanced ? 'Balanced' : 'Imbalance' }}
                  </span>
                  <span :class="['mono', tbBalanced ? 'pos' : 'neg']">{{ fmt(tbImbalance, { decimals: 2 }) }} {{ functionalCcy }}</span>
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

      <!-- 9-step cycle -->
      <div class="card">
        <div class="card-head">
          <Ico name="branch" :size="13" /> 9-Step accounting cycle
          <div class="h-meta">{{ activePeriod?.periodName ?? activePeriod?.code ?? 'No active period' }}</div>
        </div>
        <div class="card-body">
          <div class="stepper">
            <div v-for="(s, i) in steps" :key="i" :class="['step', i < stepsDone ? 'done' : i === stepsDone ? 'active' : '']">
              <div class="step-num">
                <Ico v-if="i < stepsDone" name="check" :size="10" />
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
.fy-badge {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  font-size: 10px;
  font-weight: 600;
  border-radius: 4px;
  background: color-mix(in oklab, var(--accent) 12%, var(--surface-2));
  color: var(--accent);
  letter-spacing: 0.03em;
  vertical-align: middle;
}
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

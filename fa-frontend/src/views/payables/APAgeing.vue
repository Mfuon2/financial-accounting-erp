<script setup>
import { ref, computed, onMounted } from 'vue'
import { AP_AGEING } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { bills as billsApi } from '@/api/index.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')

const data    = ref(isDemo.value ? AP_AGEING : null)
const loading = ref(false)
const error   = ref(null)

async function loadAgeing() {
  loading.value = true
  error.value   = null
  try {
    const res = await billsApi.ageing({ entityId: entityId.value })
    if (res) data.value = res
  } catch (e) {
    if (!data.value) error.value = e?.message ?? 'Failed to load AP ageing data.'
  } finally {
    loading.value = false
  }
}

onMounted(loadAgeing)

// ── Supplier filter ──────────────────────────────────────────────────────────
const supplierFilter = ref('')

// ── Bucket definitions ───────────────────────────────────────────────────────
const BUCKET_KEYS = ['current', 'days1to30', 'days31to60', 'days61to90', 'days90plus']

const BUCKET_COLORS = {
  current:    'var(--success, #10b981)',
  days1to30:  'var(--accent)',
  days31to60: 'var(--warning, #f59e0b)',
  days61to90: 'var(--warning, #f59e0b)',
  days90plus: 'var(--danger)',
}

const buckets = computed(() => {
  if (!data.value) return []
  return BUCKET_KEYS.map(key => ({
    key,
    color: BUCKET_COLORS[key],
    label: data.value[key]?.label ?? key,
    total: data.value[key]?.total ?? 0,
    bills: data.value[key]?.bills ?? [],
  }))
})

const grandTotal = computed(() => data.value?.grandTotal ?? 0)
const asOfDate   = computed(() => data.value?.asOfDate   ?? null)

function bucketPct(bucket) {
  if (!grandTotal.value || !bucket.total) return 0
  return +((bucket.total / grandTotal.value) * 100).toFixed(1)
}

// ── Filtered buckets for detail tables ──────────────────────────────────────
const filteredBuckets = computed(() => {
  const q = supplierFilter.value.trim().toLowerCase()
  return buckets.value.map(b => ({
    ...b,
    bills: q ? b.bills.filter(l => l.supplierName?.toLowerCase().includes(q)) : b.bills,
  })).filter(b => b.bills.length > 0)
})

const totalBillCount = computed(() =>
  filteredBuckets.value.reduce((s, b) => s + b.bills.length, 0)
)
</script>

<template>
  <div class="page">
    <!-- ── Header ────────────────────────────────────────────────────────── -->
    <PageHeader
      title="AP Ageing"
      :meta="asOfDate
        ? `As at ${fmtDate(asOfDate)} · KES ${fmt(grandTotal)} total outstanding`
        : 'AP Ageing Report'"
    >
      <Button
        variant="ghost"
        icon="rotate"
        :loading="loading"
        @click="loadAgeing"
      >
        Refresh
      </Button>
    </PageHeader>

    <div class="page-section stack">

      <!-- ── Loading ───────────────────────────────────────────────────── -->
      <div v-if="loading && !data" class="empty-state">
        <div class="empty-icon" style="opacity:.45">⏳</div>
        <div class="empty-title">Loading AP ageing data…</div>
      </div>

      <!-- ── Error ─────────────────────────────────────────────────────── -->
      <div v-else-if="error && !data" class="empty-state">
        <div class="empty-title" style="color:var(--danger)">{{ error }}</div>
        <Button variant="default" icon="rotate" style="margin-top:12px" @click="loadAgeing">
          Try again
        </Button>
      </div>

      <template v-else-if="data">

        <!-- ── Supplier filter ─────────────────────────────────────────── -->
        <div style="display:flex;align-items:center;gap:10px">
          <div style="position:relative;flex:1;max-width:320px">
            <input
              v-model="supplierFilter"
              type="search"
              placeholder="Filter by supplier…"
              class="form-input"
              style="padding-left:32px;width:100%"
            />
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="14" height="14"
              viewBox="0 0 24 24" fill="none"
              stroke="currentColor" stroke-width="2"
              stroke-linecap="round" stroke-linejoin="round"
              style="position:absolute;left:10px;top:50%;transform:translateY(-50%);color:var(--muted);pointer-events:none"
            >
              <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
            </svg>
          </div>
          <span v-if="supplierFilter" style="font-size:12px;color:var(--muted)">
            {{ totalBillCount }} bill{{ totalBillCount !== 1 ? 's' : '' }} matching
          </span>
        </div>

        <!-- ── KPI summary cards ───────────────────────────────────────── -->
        <div class="ageing-kpi-grid">
          <div
            v-for="b in buckets"
            :key="b.key"
            class="card"
            style="padding:14px 16px"
          >
            <div style="font-size:11px;color:var(--muted);margin-bottom:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">
              {{ b.label }}
            </div>
            <div
              class="mono"
              style="font-size:18px;font-weight:700"
              :style="{ color: b.color }"
            >
              {{ fmt(b.total) }}
            </div>
            <div style="font-size:11px;color:var(--muted);margin-top:2px">
              {{ bucketPct(b) }}% of total
            </div>
            <div style="margin-top:8px;height:3px;background:var(--border);border-radius:2px;overflow:hidden">
              <div
                :style="{
                  width: bucketPct(b) + '%',
                  height: '100%',
                  background: b.color,
                  transition: 'width 0.4s ease',
                }"
              />
            </div>
          </div>
        </div>

        <!-- ── Empty (all paid) ────────────────────────────────────────── -->
        <div v-if="grandTotal === 0" class="empty-state">
          <div class="empty-icon">✓</div>
          <div class="empty-title">No outstanding payables</div>
          <div class="empty-sub">All bills are paid or current.</div>
        </div>

        <!-- ── No filter match ─────────────────────────────────────────── -->
        <div v-else-if="supplierFilter && filteredBuckets.length === 0" class="empty-state">
          <div class="empty-title">No bills match "{{ supplierFilter }}"</div>
          <div class="empty-sub">Try a different supplier name.</div>
        </div>

        <!-- ── Detail tables per bucket ───────────────────────────────── -->
        <template v-else>
          <div
            v-for="b in filteredBuckets"
            :key="b.key"
            class="card"
            style="overflow:hidden"
          >
            <!-- Bucket header -->
            <div
              class="card-head"
              style="display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid"
              :style="{ borderColor: b.color }"
            >
              <span style="font-weight:600" :style="{ color: b.color }">
                {{ b.label }}
              </span>
              <span class="mono" style="font-size:12px;color:var(--muted)">
                KES {{ fmt(b.total) }}
              </span>
            </div>

            <!-- Bill rows -->
            <table class="tbl">
              <thead>
                <tr>
                  <th>Bill #</th>
                  <th>Supplier</th>
                  <th>Bill Date</th>
                  <th>Due Date</th>
                  <th class="num">Total</th>
                  <th class="num">Paid</th>
                  <th class="num">Outstanding</th>
                  <th class="num">Days Overdue</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="line in b.bills" :key="line.billId">
                  <td><code>{{ line.billNumber }}</code></td>
                  <td>{{ line.supplierName }}</td>
                  <td class="mono" style="font-size:12px">{{ fmtDate(line.billDate) }}</td>
                  <td class="mono" style="font-size:12px">{{ fmtDate(line.dueDate) }}</td>
                  <td class="num mono">{{ line.currencyCode }} {{ fmt(line.totalAmount) }}</td>
                  <td class="num mono" style="color:var(--muted)">{{ fmt(line.paidAmount) }}</td>
                  <td
                    class="num mono"
                    :style="line.outstanding > 0
                      ? { color: b.color, fontWeight: 600 }
                      : {}"
                  >
                    {{ fmt(line.outstanding) }}
                  </td>
                  <td
                    class="num mono"
                    :style="line.daysOverdue > 0 ? { color: b.color } : { color: 'var(--muted)' }"
                  >
                    {{ line.daysOverdue > 0 ? line.daysOverdue : '—' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <TableFooter :total="totalBillCount" label="outstanding bill lines" />
        </template>

      </template>
    </div>
  </div>
</template>

<style scoped>
.ageing-kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
}

@media (max-width: 1100px) {
  .ageing-kpi-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

@media (max-width: 640px) {
  .ageing-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}

.empty-icon {
  font-size: 32px;
  margin-bottom: 12px;
}

.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  margin-bottom: 4px;
}

.empty-sub {
  font-size: 13px;
  color: var(--muted);
}
</style>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { invoices as invoicesApi } from '@/api/index.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'demo')

// ── State ────────────────────────────────────────────────────────────────────
const data    = ref(null)
const loading = ref(false)
const error   = ref(null)

// ── Bucket definitions ───────────────────────────────────────────────────────
// Keys mirror backend ArAgeingResponse field names
const BUCKET_DEFS = [
  { key: 'current',          label: 'Current (0–30 days)', color: 'var(--success, #10b981)' },
  { key: 'thirtyOneToSixty', label: '31–60 Days',           color: 'var(--accent)' },
  { key: 'sixtyOneToNinety', label: '61–90 Days',           color: 'var(--warning, #f59e0b)' },
  { key: 'ninetyPlus',       label: '90+ Days',             color: 'var(--danger)' },
]

const BUCKET_COLORS = {
  current:          'var(--success, #10b981)',
  thirtyOneToSixty: 'var(--accent)',
  sixtyOneToNinety: 'var(--warning, #f59e0b)',
  ninetyPlus:       'var(--danger)',
}

// ── Load ageing ───────────────────────────────────────────────────────────────
async function loadAgeing() {
  loading.value = true
  error.value   = null
  try {
    const asOfDate = new Date().toISOString().slice(0, 10)
    const res = await invoicesApi.arAgeing({ entityId: entityId.value, asOfDate })
    if (res) {
      // Backend only includes overdue invoices (dueDate < asOfDate) in the buckets.
      // The "current" bucket from the backend is 0–30 days overdue.
      // We need to replace it with invoices NOT YET DUE (due within next 30 days).
      // Fetch all non-paid/non-void invoices and find those due on or after today.
      if (!isDemo.value) {
        try {
          const today = new Date(asOfDate)
          const thirtyDaysOut = new Date(asOfDate)
          thirtyDaysOut.setDate(thirtyDaysOut.getDate() + 30)
          const invRes = await invoicesApi.list({ entityId: entityId.value, size: 500 })
          const invItems = invRes?.content ?? (Array.isArray(invRes) ? invRes : [])
          // Current = not yet due (dueDate >= today) and outstanding
          const currentInvoices = invItems.filter(i => {
            if (!['SENT', 'APPROVED', 'PARTIALLY_PAID'].includes(i.status ?? '')) return false
            if (!i.dueDate) return false
            const due = new Date(i.dueDate)
            return due >= today
          }).map(i => ({
            invoiceId:       i.id,
            invoiceNumber:   i.invoiceNumber ?? i.ref,
            customerName:    i.customerName ?? i.customer,
            issueDate:       i.issueDate ?? i.date,
            dueDate:         i.dueDate,
            currencyCode:    i.currencyCode ?? i.currency ?? 'KES',
            totalAmount:     i.totalAmount ?? i.total ?? 0,
            paidAmount:      i.paidAmount ?? i.paid ?? 0,
            outstanding:     i.outstandingAmount ?? i.balance ?? 0,
            daysOverdue:     0,
          }))
          const currentTotal = currentInvoices.reduce((s, i) => s + Number(i.outstanding), 0)
          res.current = {
            ...res.current,
            invoiceCount: currentInvoices.length,
            totalAmount:  currentTotal,
            invoices:     currentInvoices,
          }
          // Recalculate totalOutstanding to include current non-overdue invoices
          const overdueTotal = Number(res.thirtyOneToSixty?.totalAmount ?? 0)
            + Number(res.sixtyOneToNinety?.totalAmount ?? 0)
            + Number(res.ninetyPlus?.totalAmount ?? 0)
          res.totalOutstanding = currentTotal + overdueTotal
        } catch { /* silently fall through to original data */ }
      }
      data.value = res
    }
  } catch (e) {
    if (!data.value) error.value = e?.message ?? 'Failed to load AR ageing data.'
  } finally {
    loading.value = false
  }
}

onMounted(loadAgeing)

// ── Derived bucket list ───────────────────────────────────────────────────────
const buckets = computed(() => {
  if (!data.value) return []
  return BUCKET_DEFS.map(def => {
    const b = data.value[def.key] ?? {}
    return {
      key:          def.key,
      color:        def.color,
      label:        b.label ?? def.label,
      invoiceCount: b.invoiceCount ?? 0,
      total:        Number(b.totalAmount ?? 0),
      invoices:     b.invoices ?? [],
    }
  })
})

const grandTotal = computed(() => Number(data.value?.totalOutstanding ?? 0))
const asOfDate   = computed(() => data.value?.asOfDate ?? null)

function bucketPct(bucket) {
  if (!grandTotal.value || !bucket.total) return 0
  return +((bucket.total / grandTotal.value) * 100).toFixed(1)
}

// ── Customer filter ───────────────────────────────────────────────────────────
const customerFilter = ref('')

const filteredBuckets = computed(() => {
  const q = customerFilter.value.trim().toLowerCase()
  return buckets.value.map(b => ({
    ...b,
    invoices: q
      ? b.invoices.filter(i => (i.customerName ?? '').toLowerCase().includes(q))
      : b.invoices,
  })).filter(b => b.invoices.length > 0)
})

const totalInvoiceCount = computed(() =>
  filteredBuckets.value.reduce((s, b) => s + b.invoices.length, 0)
)
</script>

<template>
  <div class="page">
    <!-- ── Header ────────────────────────────────────────────────────────── -->
    <PageHeader
      title="AR Ageing"
      :meta="asOfDate
        ? `As at ${fmtDate(asOfDate)} · KES ${fmt(grandTotal)} total outstanding`
        : 'AR Ageing Report'"
    >
      <Button
        variant="ghost"
        icon="rotate"
        :loading="loading"
        @click="loadAgeing"
      >
        Refresh
      </Button>
      <Button variant="ghost" icon="download">Export</Button>
      <Button variant="primary" icon="envelope">Send statements</Button>
    </PageHeader>

    <div class="page-section stack">

      <!-- ── Loading ───────────────────────────────────────────────────── -->
      <div v-if="loading && !data" class="empty-state">
        <div class="empty-icon" style="opacity:.45">⏳</div>
        <div class="empty-title">Loading AR ageing data…</div>
      </div>

      <!-- ── Error ─────────────────────────────────────────────────────── -->
      <div v-else-if="error && !data" class="empty-state">
        <div class="empty-title" style="color:var(--danger)">{{ error }}</div>
        <Button variant="default" icon="rotate" style="margin-top:12px" @click="loadAgeing">
          Try again
        </Button>
      </div>

      <template v-else-if="data">

        <!-- ── Customer filter ─────────────────────────────────────────── -->
        <div style="display:flex;align-items:center;gap:10px">
          <div style="position:relative;flex:1;max-width:320px">
            <input
              v-model="customerFilter"
              type="search"
              placeholder="Filter by customer…"
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
          <span v-if="customerFilter" style="font-size:12px;color:var(--muted)">
            {{ totalInvoiceCount }} invoice{{ totalInvoiceCount !== 1 ? 's' : '' }} matching
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
              {{ b.invoiceCount }} invoice{{ b.invoiceCount !== 1 ? 's' : '' }} &middot; {{ bucketPct(b) }}% of total
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

        <!-- ── Empty (all clear) ──────────────────────────────────────── -->
        <div v-if="grandTotal === 0" class="empty-state">
          <div class="empty-icon">✓</div>
          <div class="empty-title">No overdue receivables</div>
          <div class="empty-sub">All outstanding invoices are within payment terms.</div>
        </div>

        <!-- ── No filter match ─────────────────────────────────────────── -->
        <div v-else-if="customerFilter && filteredBuckets.length === 0" class="empty-state">
          <div class="empty-title">No invoices match "{{ customerFilter }}"</div>
          <div class="empty-sub">Try a different customer name.</div>
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

            <!-- Invoice rows -->
            <table class="tbl">
              <thead>
                <tr>
                  <th>Invoice #</th>
                  <th>Customer</th>
                  <th>Issue Date</th>
                  <th>Due Date</th>
                  <th class="num">Total</th>
                  <th class="num">Paid</th>
                  <th class="num">Outstanding</th>
                  <th class="num">Days Overdue</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="inv in b.invoices" :key="inv.invoiceId ?? inv.id">
                  <td><code>{{ inv.invoiceNumber ?? inv.ref }}</code></td>
                  <td>{{ inv.customerName ?? inv.customer ?? '—' }}</td>
                  <td class="mono" style="font-size:12px">{{ fmtDate(inv.issueDate ?? inv.date) }}</td>
                  <td class="mono" style="font-size:12px">{{ fmtDate(inv.dueDate ?? inv.due) }}</td>
                  <td class="num mono">{{ inv.currencyCode ?? inv.currency ?? 'KES' }} {{ fmt(inv.totalAmount ?? inv.total ?? 0) }}</td>
                  <td class="num mono" style="color:var(--muted)">{{ fmt(inv.paidAmount ?? inv.paid ?? 0) }}</td>
                  <td
                    class="num mono"
                    :style="(inv.outstanding ?? inv.outstandingAmount ?? 0) > 0
                      ? { color: b.color, fontWeight: 600 }
                      : {}"
                  >
                    {{ fmt(inv.outstanding ?? inv.outstandingAmount ?? 0) }}
                  </td>
                  <td
                    class="num mono"
                    :style="(inv.daysOverdue ?? 0) > 0 ? { color: b.color } : { color: 'var(--muted)' }"
                  >
                    {{ (inv.daysOverdue ?? 0) > 0 ? inv.daysOverdue : '—' }}
                  </td>
                  <td>
                    <Button variant="ghost" size="sm" icon="bell">Remind</Button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <TableFooter :total="totalInvoiceCount" label="outstanding invoice lines" />
        </template>

      </template>
    </div>
  </div>
</template>

<style scoped>
.ageing-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

@media (max-width: 1100px) {
  .ageing-kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .ageing-kpi-grid {
    grid-template-columns: 1fr;
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

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { RECEIPTS } from '@/data/index.js'
import { receipts as receiptsApi, payments as paymentsApi, invoices as invoicesApi, journals as journalsApi, customers as customersApi } from '@/api/index.js'
import { useAuth }    from '@/composables/useAuth.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast }   from '@/composables/useToast.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader    from '@/components/PageHeader.vue'
import Button        from '@/components/primitives/Button.vue'
import Badge         from '@/components/primitives/Badge.vue'
import Modal         from '@/components/overlays/Modal.vue'
import TableToolbar    from '@/components/tables/TableToolbar.vue'
import TableFooter     from '@/components/tables/TableFooter.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

// ── Composables ───────────────────────────────────────────────────────────────
const { currentUser }  = useAuth()
const { isDemo }       = useAppMode()
const { toast }        = useToast()
const route            = useRoute()

const entityId = computed(() => currentUser.value?.entityId ?? null)

// ── List state ────────────────────────────────────────────────────────────────
const list      = ref([...RECEIPTS])
const loading   = ref(false)
const search    = ref('')
const tabFilter = ref('ALL')

// ── Detail drawer ─────────────────────────────────────────────────────────────
const drawer = ref(null)

// Resolved human-readable values for the open drawer
const drawerPaymentNumber = ref('—')
const drawerInvoiceNumber = ref('—')
const drawerJeReference   = ref('—')
const drawerCustomerName  = ref('—')

// Payment number map for the table column (paymentId → paymentNumber)
const paymentNumMap = ref({})

// ── Generate receipt modal ────────────────────────────────────────────────────
const showGenerate     = ref(false)
const genPaymentId     = ref(null)   // UUID selected from dropdown
const genEmail         = ref('')
const genPhone         = ref('')
const genNotes         = ref('')
const generating       = ref(false)

// Posted payments for the dropdown — loaded when the modal opens
const postedPayments   = ref([])    // [{ value: id, label: '...' }]
const loadingPayments  = ref(false)

async function loadPostedPayments() {
  if (isDemo.value) return
  loadingPayments.value = true
  try {
    const data = await paymentsApi.list({ entityId: entityId.value, status: 'POSTED', size: 500 })
    const items = data?.content ?? (Array.isArray(data) ? data : [])
    postedPayments.value = items.map(p => ({
      value: p.id,
      label: `${p.paymentNumber} · ${p.currencyCode} ${fmt(p.paymentAmount)} · ${fmtDate(p.paymentDate)}`,
    }))
  } catch { /* silently skip — user can still type */ }
  finally { loadingPayments.value = false }
}

// ── Void receipt modal ────────────────────────────────────────────────────────
const showVoid   = ref(false)
const voidReason = ref('')
const voiding    = ref(false)

// ── Per-row action guards ─────────────────────────────────────────────────────
const issuingId  = ref(null)   // receipt id currently being issued

// ── Computed: filtered list ───────────────────────────────────────────────────
const TAB_OPTIONS = [
  { id: 'ALL',    label: 'All' },
  { id: 'POSTED', label: 'Posted' },
  { id: 'ISSUED', label: 'Issued' },
  { id: 'VOID',   label: 'Void' },
]

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  return list.value.filter(r => {
    const matchTab = tabFilter.value === 'ALL' || r.status === tabFilter.value
    const matchSearch = !q ||
      (r.receiptNumber ?? r.ref ?? '').toLowerCase().includes(q) ||
      (r.currencyCode  ?? r.currency ?? '').toLowerCase().includes(q)
    return matchTab && matchSearch
  })
})

const tabCounts = computed(() => ({
  ALL:    list.value.length,
  POSTED: list.value.filter(r => r.status === 'POSTED').length,
  ISSUED: list.value.filter(r => r.status === 'ISSUED').length,
  VOID:   list.value.filter(r => r.status === 'VOID').length,
}))

const tabs = computed(() =>
  TAB_OPTIONS.map(t => ({ ...t, count: tabCounts.value[t.id] }))
)

// ── Helpers ───────────────────────────────────────────────────────────────────
function toArray(data) {
  if (!data) return []
  if (Array.isArray(data)) return data
  if (Array.isArray(data.content)) return data.content
  return []
}

function normalise(r) {
  // Bridge demo shape { ref, payment, customer, date, amount, currency, issued, status }
  // to real-API shape (Receipt domain object field names).
  return {
    id:            r.id,
    receiptNumber: r.receiptNumber ?? r.ref,
    paymentId:     r.paymentId     ?? r.payment,
    customerId:    r.customerId,
    receiptDate:   r.receiptDate   ?? r.date,
    receiptAmount: r.receiptAmount ?? r.amount,
    currencyCode:  r.currencyCode  ?? r.currency ?? 'KES',
    status:        r.status,
    issuedAt:      r.issuedAt      ?? (r.issued ? 'issued' : null),
    deliveryEmail: r.deliveryEmail ?? null,
    deliveryPhone: r.deliveryPhone ?? null,
    journalEntryId:r.journalEntryId ?? null,
    invoiceId:     r.invoiceId     ?? null,
    notes:         r.notes         ?? null,
  }
}

// ── Load receipts ─────────────────────────────────────────────────────────────
async function loadReceipts() {
  loading.value = true
  try {
    const params = {}
    if (!isDemo.value && entityId.value) params.entityId = entityId.value
    const data = await receiptsApi.list(params)
    const rows = toArray(data)
    list.value = rows.map(normalise)
  } catch {
    // stays on demo / last-good data; client.js already shows a toast
  } finally {
    loading.value = false
  }
}

async function loadPaymentMap() {
  if (isDemo.value) return
  try {
    const data = await paymentsApi.list({ entityId: entityId.value, size: 500 })
    const items = toArray(data)
    const map = {}
    for (const p of items) if (p.id && p.paymentNumber) map[p.id] = p.paymentNumber
    paymentNumMap.value = map
  } catch { /* non-critical */ }
}

onMounted(async () => {
  await Promise.all([loadReceipts(), loadPaymentMap()])

  // Auto-open by receiptId (from "View receipt" button in Payments)
  const receiptId = route.query.receiptId
  if (receiptId) {
    const match = list.value.find(r => r.id === receiptId)
    if (match) { openDrawer(match); return }
  }

  // Fallback: auto-open by paymentId
  const paymentId = route.query.paymentId
  if (paymentId) {
    const match = list.value.find(r => r.paymentId === paymentId)
    if (match) {
      openDrawer(match)
    } else if (!isDemo.value) {
      const r = await receiptsApi.byPayment(paymentId)
      if (r) {
        openDrawer(normalise(r))
      } else {
        toast.warn('No receipt found for this payment. It will be generated automatically the next time this payment is posted.')
      }
    }
  }
})

// ── Open drawer ───────────────────────────────────────────────────────────────
async function openDrawer(r) {
  drawer.value = r
  showVoid.value   = false
  voidReason.value = ''

  // Seed from map cache first for instant display
  drawerPaymentNumber.value = r.paymentId ? (paymentNumMap.value[r.paymentId] ?? '—') : '—'
  drawerInvoiceNumber.value = '—'
  drawerJeReference.value   = '—'
  drawerCustomerName.value  = '—'

  if (!isDemo.value) {
    if (r.paymentId && !paymentNumMap.value[r.paymentId]) {
      try {
        const p = await paymentsApi.get(r.paymentId)
        drawerPaymentNumber.value = p?.paymentNumber ?? r.paymentId
        if (p?.paymentNumber) paymentNumMap.value[r.paymentId] = p.paymentNumber
      } catch { drawerPaymentNumber.value = r.paymentId }
    } else if (r.paymentId) {
      drawerPaymentNumber.value = paymentNumMap.value[r.paymentId]
    }
    if (r.invoiceId) {
      try {
        const inv = await invoicesApi.get(r.invoiceId)
        drawerInvoiceNumber.value = inv?.invoiceNumber ?? r.invoiceId
      } catch { drawerInvoiceNumber.value = r.invoiceId }
    }
    if (r.journalEntryId) {
      try {
        const je = await journalsApi.get(r.journalEntryId)
        drawerJeReference.value = je?.reference ?? je?.entryNumber ?? r.journalEntryId
      } catch { drawerJeReference.value = r.journalEntryId }
    }
    if (r.customerId) {
      try {
        const c = await customersApi.get(r.customerId)
        drawerCustomerName.value = c?.name ?? c?.customerName ?? '—'
      } catch {}
    }
  }
}

function closeDrawer() {
  drawer.value    = null
  showVoid.value  = false
  voidReason.value = ''
}

// ── Generate receipt ──────────────────────────────────────────────────────────
function openGenerate() {
  genPaymentId.value = null
  genEmail.value     = ''
  genPhone.value     = ''
  genNotes.value     = ''
  showGenerate.value = true
  loadPostedPayments()
}

async function submitGenerate() {
  if (!genPaymentId.value) {
    toast.warn('Select a payment to generate a receipt for')
    return
  }
  if (generating.value) return
  generating.value = true
  try {
    const body = {
      paymentId:     genPaymentId.value,
      entityId:      entityId.value,
      deliveryEmail: genEmail.value.trim() || undefined,
      deliveryPhone: genPhone.value.trim() || undefined,
      notes:         genNotes.value.trim() || undefined,
    }
    const created = await receiptsApi.generate(body)
    toast.success(`Receipt ${created.receiptNumber ?? 'created'} generated`)
    showGenerate.value = false
    await loadReceipts()
  } catch {
    // client.js shows error toast
  } finally {
    generating.value = false
  }
}

// ── Issue receipt ─────────────────────────────────────────────────────────────
async function issueReceipt(r) {
  if (issuingId.value) return
  issuingId.value = r.id
  try {
    const updated = await receiptsApi.issue(r.id)
    applyUpdate(normalise(updated))
    if (drawer.value?.id === r.id) drawer.value = normalise(updated)
    toast.success(`Receipt ${r.receiptNumber} issued to customer`)
  } catch {
    // client.js shows error toast
  } finally {
    issuingId.value = null
  }
}

// ── Void receipt ──────────────────────────────────────────────────────────────
function promptVoid() {
  voidReason.value = ''
  showVoid.value   = true
}

async function confirmVoid() {
  if (!voidReason.value.trim()) {
    toast.warn('A reason is required to void a receipt')
    return
  }
  if (voiding.value) return
  voiding.value = true
  const target = drawer.value
  try {
    const updated = await receiptsApi.void(target.id, voidReason.value.trim())
    applyUpdate(normalise(updated))
    drawer.value    = normalise(updated)
    showVoid.value  = false
    toast.success(`Receipt ${target.receiptNumber} voided`)
  } catch {
    // client.js shows error toast
  } finally {
    voiding.value = false
  }
}

// ── Patch a row in the list after an action ───────────────────────────────────
function applyUpdate(updated) {
  const idx = list.value.findIndex(r => r.id === updated.id)
  if (idx !== -1) list.value[idx] = updated
}

// ── Status badge variant ──────────────────────────────────────────────────────
function statusVariant(status) {
  switch (status) {
    case 'PENDING': return 'warn'
    case 'POSTED':  return 'info'
    case 'ISSUED':  return 'approved'
    case 'VOID':    return 'void'
    default:        return 'outline'
  }
}

// ── Print / download stub ─────────────────────────────────────────────────────
function printReceipt(r) {
  toast.info(`Download / print for receipt ${r.receiptNumber} — coming soon`)
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Receipts"
      :meta="`${filtered.length} of ${list.length} receipts`"
      :tabs="tabs"
      :activeTab="tabFilter"
      @tab="tabFilter = $event"
    >
      <Button variant="ghost" icon="rotate" :loading="loading" @click="loadReceipts">
        Refresh
      </Button>
      <Button variant="primary" icon="plus" @click="openGenerate">
        Generate receipt
      </Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search" />

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Receipt no.</th>
              <th>Payment</th>
              <th>Date</th>
              <th class="num">Amount</th>
              <th>Delivery</th>
              <th>Issued at</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="r in filtered"
              :key="r.id"
              class="row-link"
              @click="openDrawer(r)"
            >
              <td><code>{{ r.receiptNumber }}</code></td>
              <td><code>{{ paymentNumMap[r.paymentId] ?? r.paymentId ?? '—' }}</code></td>
              <td>{{ fmtDate(r.receiptDate) }}</td>
              <td class="num mono">{{ r.currencyCode }} {{ fmt(r.receiptAmount) }}</td>
              <td>
                <span v-if="r.deliveryEmail || r.deliveryPhone" class="muted" style="font-size:12px">
                  {{ r.deliveryEmail || r.deliveryPhone }}
                </span>
                <span v-else class="muted" style="font-style:italic">—</span>
              </td>
              <td>
                <span v-if="r.issuedAt && r.issuedAt !== 'issued'" class="muted" style="font-size:12px">
                  {{ fmtDate(r.issuedAt) }}
                </span>
                <span v-else-if="r.status === 'ISSUED'" class="muted" style="font-style:italic">yes</span>
                <span v-else class="muted" style="font-style:italic">—</span>
              </td>
              <td>
                <Badge :status="statusVariant(r.status)" :dot="false">{{ r.status }}</Badge>
              </td>
              <td @click.stop>
                <div style="display:flex;gap:4px;align-items:center">
                  <!-- Issue button — only for POSTED receipts -->
                  <Button
                    v-if="r.status === 'POSTED'"
                    variant="ghost"
                    size="sm"
                    icon="envelope"
                    :loading="issuingId === r.id"
                    :disabled="!!issuingId"
                    @click="issueReceipt(r)"
                  >Issue</Button>
                  <!-- Resend / print for ISSUED -->
                  <Button
                    v-else-if="r.status === 'ISSUED'"
                    variant="ghost"
                    size="sm"
                    icon="envelope"
                    @click="issueReceipt(r)"
                  >Resend</Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    icon="download"
                    @click="printReceipt(r)"
                  >PDF</Button>
                </div>
              </td>
            </tr>

            <tr v-if="!loading && filtered.length === 0">
              <td colspan="8" class="muted" style="text-align:center;padding:32px;font-style:italic">
                No receipts found
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="receipts" />
    </div>

    <!-- ── Receipt detail drawer ─────────────────────────────────────────── -->
    <Modal
      :open="drawer !== null"
      :title="drawer?.receiptNumber"
      :subtitle="drawer ? `${drawer.currencyCode} ${fmt(drawer.receiptAmount)} · ${drawer.status}` : ''"
      :width="720"
      @close="closeDrawer"
    >
      <template v-if="drawer">
        <div class="kpi-grid" style="grid-template-columns:repeat(3,1fr)">
          <div class="kpi-card">
            <div class="kpi-label">Receipt date</div>
            <div class="kpi-value">{{ fmtDate(drawer.receiptDate) }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Amount</div>
            <div class="kpi-value mono">{{ drawer.currencyCode }} {{ fmt(drawer.receiptAmount) }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Status</div>
            <div class="kpi-value">
              <Badge :status="statusVariant(drawer.status)" :dot="false">{{ drawer.status }}</Badge>
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Payment</div>
            <div class="kpi-value mono">{{ drawerPaymentNumber }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Customer</div>
            <div class="kpi-value">{{ drawerCustomerName }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Invoice</div>
            <div class="kpi-value mono">{{ drawerInvoiceNumber }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Journal entry</div>
            <div class="kpi-value mono">{{ drawerJeReference }}</div>
          </div>
        </div>

        <div v-if="drawer.deliveryEmail || drawer.deliveryPhone || drawer.notes" class="card" style="margin-top:16px">
          <div class="card-head">Delivery &amp; notes</div>
          <table class="tbl">
            <tbody>
              <tr v-if="drawer.deliveryEmail">
                <td class="muted" style="width:140px">Email</td>
                <td>{{ drawer.deliveryEmail }}</td>
              </tr>
              <tr v-if="drawer.deliveryPhone">
                <td class="muted">Phone</td>
                <td>{{ drawer.deliveryPhone }}</td>
              </tr>
              <tr v-if="drawer.issuedAt && drawer.issuedAt !== 'issued'">
                <td class="muted">Issued at</td>
                <td>{{ fmtDate(drawer.issuedAt) }}</td>
              </tr>
              <tr v-if="drawer.notes">
                <td class="muted" style="vertical-align:top">Notes</td>
                <td style="white-space:pre-wrap">{{ drawer.notes }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Void reason input — inline in drawer -->
        <div v-if="showVoid" class="card" style="margin-top:16px">
          <div class="card-head" style="color:var(--neg)">Void receipt</div>
          <div class="field" style="margin-top:12px">
            <label>Reason <span style="color:var(--neg)">*</span></label>
            <input
              v-model="voidReason"
              class="input"
              placeholder="Required — state the reason for voiding"
              style="width:100%"
              autofocus
            />
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button
              variant="danger"
              icon="x"
              :loading="voiding"
              :disabled="!voidReason.trim()"
              @click="confirmVoid"
            >Confirm void</Button>
            <Button variant="ghost" @click="showVoid = false">Cancel</Button>
          </div>
        </div>
      </template>

      <template #footer>
        <!-- POSTED: can issue or void -->
        <template v-if="drawer?.status === 'POSTED'">
          <Button
            variant="primary"
            icon="envelope"
            :loading="issuingId === drawer.id"
            :disabled="showVoid || !!issuingId"
            @click="issueReceipt(drawer)"
          >Issue to customer</Button>
          <Button
            variant="ghost"
            icon="download"
            @click="printReceipt(drawer)"
          >Download PDF</Button>
          <Button
            variant="ghost"
            icon="x"
            :disabled="showVoid"
            @click="promptVoid"
          >Void</Button>
        </template>

        <!-- ISSUED: can resend or void -->
        <template v-else-if="drawer?.status === 'ISSUED'">
          <Button
            variant="primary"
            icon="envelope"
            :loading="issuingId === drawer.id"
            :disabled="showVoid || !!issuingId"
            @click="issueReceipt(drawer)"
          >Resend</Button>
          <Button
            variant="ghost"
            icon="download"
            @click="printReceipt(drawer)"
          >Download PDF</Button>
          <Button
            variant="ghost"
            icon="x"
            :disabled="showVoid"
            @click="promptVoid"
          >Void</Button>
        </template>

        <!-- VOID or PENDING: read-only -->
        <template v-else>
          <Button
            variant="ghost"
            icon="download"
            @click="printReceipt(drawer)"
          >Download PDF</Button>
        </template>

        <Button variant="ghost" @click="closeDrawer">Close</Button>
      </template>
    </Modal>

    <!-- ── Generate Receipt modal ────────────────────────────────────────── -->
    <Modal
      :open="showGenerate"
      title="Generate Receipt"
      subtitle="Create a receipt from a posted payment"
      :width="560"
      @close="showGenerate = false"
    >
      <div class="form-grid cols-1" style="gap:16px">
        <div class="field">
          <label>Payment <span style="color:var(--neg)">*</span></label>
          <SearchableSelect
            v-model="genPaymentId"
            :options="postedPayments"
            :placeholder="loadingPayments ? 'Loading payments…' : postedPayments.length ? 'Search posted payments…' : 'No posted payments found'"
            :disabled="loadingPayments"
            :mono="true"
          />
        </div>
        <div class="field">
          <label>Delivery email <span class="muted">(optional)</span></label>
          <input
            v-model="genEmail"
            class="input"
            type="email"
            placeholder="accounts@customer.com"
            style="width:100%"
          />
        </div>
        <div class="field">
          <label>Delivery phone <span class="muted">(optional)</span></label>
          <input
            v-model="genPhone"
            class="input"
            type="tel"
            placeholder="+254712345678"
            style="width:100%"
          />
        </div>
        <div class="field">
          <label>Notes <span class="muted">(optional)</span></label>
          <textarea
            v-model="genNotes"
            class="input"
            rows="3"
            placeholder="Optional memo"
            style="width:100%;resize:vertical"
          />
        </div>
      </div>

      <template #footer>
        <Button
          variant="primary"
          icon="receipt"
          :loading="generating"
          :disabled="!genPaymentId"
          @click="submitGenerate"
        >Generate</Button>
        <Button variant="ghost" @click="showGenerate = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.kpi-card {
  background: var(--surface-raised, var(--surface, #1e1e1e));
  border: 1px solid var(--border, #2e2e2e);
  border-radius: 6px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.kpi-label {
  font-size: 11px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: .04em;
  color: var(--text-muted, #888);
}
.kpi-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text, #e8e8e8);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>

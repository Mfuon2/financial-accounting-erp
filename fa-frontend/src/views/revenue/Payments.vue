<script setup>
import { ref, computed, onMounted } from 'vue'
import { PAYMENTS } from '@/data/index.js'
import { payments as paymentsApi, customers as customersApi, invoices as invoicesApi, periods as periodsApi, journals as journalsApi, receipts as receiptsApi } from '@/api/index.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast } from '@/composables/useToast.js'
import { useAuth } from '@/composables/useAuth.js'
import { useCategoryCache } from '@/composables/useCategoryCache.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import ChipFilter from '@/components/primitives/ChipFilter.vue'
import AmountInput from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

// ── Composables ───────────────────────────────────────────────────────────────
const { isDemo } = useAppMode()
const { toast }  = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'demo')

// ── State ─────────────────────────────────────────────────────────────────────
const list          = ref([])        // PaymentResponse[]
const customerList  = ref([])        // { value, label }[]
const invoiceList   = ref([])        // { value, label, balance, currency }[]
const invoiceNumMap = ref({})        // id → invoiceNumber lookup (all invoices)
const periodList    = ref([])        // { value, label }[]
const loadingInvoices = ref(false)

const search        = ref('')
const statusFilter  = ref('ALL')

const drawer        = ref(null)      // currently viewed payment
const showCreate    = ref(false)     // Create Payment modal
const showMatch     = ref(false)     // Match-to-invoice inline panel
const showReverse   = ref(false)     // Reverse reason inline panel

// ── Loading / action guards ───────────────────────────────────────────────────
const loadingList   = ref(false)
const creating      = ref(false)
const matchingSave  = ref(false)
const approvingId   = ref(null)
const postingId     = ref(null)
const reversingId   = ref(null)

// ── Payment status state machine (mirrors backend PaymentStatus enum) ─────────
const STATUSES = ['ALL', 'PENDING', 'MATCHED', 'APPROVED', 'POSTED', 'REVERSED']
// Payment methods are entity-managed dynamic data (CLAUDE.md §2) — see shared/categories on
// the backend and setup/Categories.vue for where they're created/edited. Cached at module
// level (useCategoryCache) so this view, Bills.vue and Invoices.vue share one fetch.
const paymentMethodsCache = useCategoryCache('PAYMENT_METHOD')
const PAYMENT_METHOD_OPTIONS = computed(() => paymentMethodsCache.options.value)

// ── Create Payment form ───────────────────────────────────────────────────────
function blankPayment() {
  return {
    customerId:          null,
    invoiceId:           null,
    periodId:            null,
    paymentMethod:       'BANK_TRANSFER',
    paymentAmount:       '',
    currencyCode:        'KES',
    exchangeRate:        '1',
    transactionReference:'',
    paymentDate:         new Date().toISOString().slice(0, 10),
    notes:               '',
  }
}
const newPayment = ref(blankPayment())

// ── Match form ────────────────────────────────────────────────────────────────
const matchInvoiceId    = ref(null)
const matchAmount       = ref('')

// ── Reverse form ──────────────────────────────────────────────────────────────
const reverseReason = ref('')

// ── Lifecycle ─────────────────────────────────────────────────────────────────
onMounted(async () => {
  paymentMethodsCache.load(entityId.value)
  await loadList()
  // Load customers for create-payment form
  try {
    const data = await customersApi.list({ entityId: entityId.value, size: 500 })
    const items = data?.content ?? data
    if (Array.isArray(items)) {
      customerList.value = items
        .filter(c => c.isActive !== false && c.active !== false)
        .map(c => ({ value: c.id, label: c.name ?? c.customerName ?? c.id, currency: c.currency ?? 'KES' }))
    }
  } catch { /* silently skip */ }
  // Load open periods
  try {
    const data = await periodsApi.list({ entityId: entityId.value, size: 50 })
    const items = Array.isArray(data) ? data : (data?.content ?? [])
    periodList.value = items
      .filter(p => p.status !== 'CLOSED')
      .map(p => ({ value: p.id, label: `${p.periodName ?? p.name ?? p.id}${p.status !== 'OPEN' ? ` (${p.status})` : ''}` }))
  } catch { /* silently skip */ }
  // Load invoices for match form (unpaid / partially paid)
  await loadInvoices()
})

async function loadList() {
  loadingList.value = true
  try {
    if (isDemo.value) {
      list.value = [...PAYMENTS]
      return
    }
    const data = await paymentsApi.list({ entityId: entityId.value, page: 0, size: 200 })
    const items = data?.content ?? (Array.isArray(data) ? data : [])
    list.value = items
  } catch {
    toast.error('Failed to load payments.')
  } finally {
    loadingList.value = false
  }
}

async function loadInvoices() {
  loadingInvoices.value = true
  try {
    // Load all non-void invoices; build a number lookup map for all, filter to unpaid for match dropdown
    const data = await invoicesApi.list({ entityId: entityId.value, size: 500 })
    const items = data?.content ?? (Array.isArray(data) ? data : [])
    // Build full id → invoiceNumber map (used in the table and drawer)
    const numMap = {}
    items.forEach(i => { if (i.id && i.invoiceNumber) numMap[i.id] = i.invoiceNumber })
    invoiceNumMap.value = numMap
    const UNPAID = new Set(['APPROVED', 'SENT', 'PARTIALLY_PAID'])
    invoiceList.value = items
      .filter(i => UNPAID.has(i.status) && Number(i.outstandingAmount ?? i.balance ?? i.total) > 0)
      .map(i => ({
        value:      i.id,
        label:      `${i.invoiceNumber ?? i.ref} — ${i.customerName ?? customerLabel(i.customerId)} · ${i.currencyCode ?? i.currency} ${fmt(i.outstandingAmount ?? i.balance ?? i.total)} outstanding`,
        balance:    Number(i.outstandingAmount ?? i.balance ?? i.total ?? 0),
        currency:   i.currencyCode ?? i.currency ?? 'KES',
        customerId: i.customerId,
      }))
  } catch { /* silently skip */ }
  finally { loadingInvoices.value = false }
}

// Resolve customer UUID → name from loaded customerList
function customerLabel(id) {
  if (!id) return ''
  const c = customerList.value.find(x => x.value === id)
  return c?.label ?? ''
}

// Invoices filtered to the selected customer (shows all if no customer chosen yet)
const invoiceOpts = computed(() => {
  if (!newPayment.value.customerId) return invoiceList.value
  return invoiceList.value.filter(i => i.customerId === newPayment.value.customerId)
})

// Invoices filtered to the drawer payment's customer for the match form
const matchInvoiceOpts = computed(() => {
  const cid = drawer.value?.customerId
  if (!cid) return invoiceList.value
  return invoiceList.value.filter(i => i.customerId === cid)
})

// ── Filtered list ─────────────────────────────────────────────────────────────
const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return list.value.filter(p => {
    const matchStatus = statusFilter.value === 'ALL' || p.status === statusFilter.value
    const ref = p.paymentNumber ?? p.ref ?? ''
    const cust = p.customerName ?? p.customer ?? p.customerId ?? ''
    const matchSearch = !q || ref.toLowerCase().includes(q) || String(cust).toLowerCase().includes(q)
    return matchStatus && matchSearch
  })
})

const totalAmount = computed(() =>
  list.value.reduce((s, p) => s + Number(p.paymentAmount ?? p.amount ?? 0), 0)
)

const currencies = computed(() => {
  const set = new Set(list.value.map(p => p.currencyCode ?? p.currency).filter(Boolean))
  return [...set].join(', ') || 'KES'
})

// ── Status badge mapping ──────────────────────────────────────────────────────
function statusBadge(status) {
  const map = {
    PENDING:  'outline',
    MATCHED:  'submitted',
    APPROVED: 'approved',
    POSTED:   'posted',
    REVERSED: 'void',
    // legacy demo
    PENDING_APPROVAL: 'outline',
    DRAFT:    'outline',
  }
  return map[status] ?? 'outline'
}

function methodBadge(method) {
  return method === 'MPESA' ? 'm-pesa' : 'bank-transfer'
}

// ── Resolved drawer detail state ──────────────────────────────────────────────
const drawerCustomerName  = ref('—')
const drawerInvoiceNumber = ref('—')
const drawerJeReference   = ref('—')
const drawerReceiptId     = ref(null)   // resolved receipt ID for "View receipt" button

// ── Open drawer + reset inline panels ────────────────────────────────────────
async function openDrawer(p) {
  drawer.value = p
  showMatch.value   = false
  showReverse.value = false
  matchInvoiceId.value = p.invoiceId ?? null
  matchAmount.value    = String(p.paymentAmount ?? p.amount ?? '')
  reverseReason.value  = ''
  drawerReceiptId.value = null

  // Reset resolved fields immediately so stale data doesn't flash
  drawerCustomerName.value  = customerLabel(p.customerId) || '—'
  drawerInvoiceNumber.value = p.invoiceId ? (invoiceNumMap.value[p.invoiceId] ?? p.invoiceId) : '—'
  drawerJeReference.value   = '—'

  if (!isDemo.value) {
    if (p.customerId && !customerLabel(p.customerId)) {
      try {
        const c = await customersApi.get(p.customerId)
        drawerCustomerName.value = c?.name ?? c?.customerName ?? '—'
      } catch { }
    }
    if (p.invoiceId && !invoiceNumMap.value[p.invoiceId]) {
      try {
        const inv = await invoicesApi.get(p.invoiceId)
        drawerInvoiceNumber.value = inv?.invoiceNumber ?? p.invoiceId
        if (inv?.invoiceNumber) invoiceNumMap.value[p.invoiceId] = inv.invoiceNumber
      } catch { drawerInvoiceNumber.value = p.invoiceId }
    }
    if (p.journalEntryId) {
      try {
        const je = await journalsApi.get(p.journalEntryId)
        drawerJeReference.value = je?.reference ?? je?.entryNumber ?? p.journalEntryId
      } catch { drawerJeReference.value = p.journalEntryId }
    }
    // Look up receipt silently — makes "View receipt" button link directly to the receipt
    if (p.id) {
      try {
        const rct = await receiptsApi.byPayment(p.id)
        drawerReceiptId.value = rct?.id ?? null
      } catch { /* non-critical */ }
    }
  }
}

// ── Create Payment ────────────────────────────────────────────────────────────
async function submitCreate() {
  if (creating.value) return
  const f = newPayment.value
  if (!f.customerId) { toast.error('Select a customer.'); return }
  if (!f.paymentAmount || Number(f.paymentAmount) <= 0) { toast.error('Enter a valid amount.'); return }
  if (!f.paymentDate) { toast.error('Payment date is required.'); return }

  creating.value = true
  const idempotencyKey = crypto.randomUUID()
  const payload = {
    entityId:             entityId.value,
    periodId:             f.periodId || undefined,
    customerId:           f.customerId,
    invoiceId:            f.invoiceId || undefined,
    paymentMethod:        f.paymentMethod,
    paymentAmount:        Number(f.paymentAmount),
    currencyCode:         f.currencyCode,
    exchangeRate:         Number(f.exchangeRate) || 1,
    transactionReference: f.transactionReference || undefined,
    paymentDate:          f.paymentDate,
    notes:                f.notes || undefined,
  }
  try {
    const created = await paymentsApi.create(payload, idempotencyKey)
    showCreate.value = false
    newPayment.value = blankPayment()
    toast.success(`Payment created.`)
    await loadList()          // reload to get server-normalised shape
    await loadInvoices()      // refresh invoice dropdown (outstanding amounts may have changed)
  } catch { /* client.js already toasted */ }
  finally { creating.value = false }
}

// ── Match to invoice ──────────────────────────────────────────────────────────
async function submitMatch() {
  if (matchingSave.value || !drawer.value) return
  if (!matchInvoiceId.value)  { toast.error('Select an invoice to match.'); return }
  const amt = Number(matchAmount.value)
  if (!amt || amt <= 0)       { toast.error('Enter a valid matched amount.'); return }

  matchingSave.value = true
  try {
    await paymentsApi.match(drawer.value.id, {
      invoiceId:     matchInvoiceId.value,
      matchedAmount: amt,
    })
    showMatch.value = false
    toast.success('Payment matched to invoice.')
    await loadList()
  } catch (err) { toast.error(err?.message ?? 'Failed to match payment.') }
  finally { matchingSave.value = false }
}

// ── Approve ───────────────────────────────────────────────────────────────────
async function approvePayment(id) {
  if (approvingId.value) return
  approvingId.value = id
  try {
    await paymentsApi.approve(id)
    toast.success('Payment approved.')
    await loadList()
  } catch { /* handled */ }
  finally { approvingId.value = null }
}

// ── Post to GL ────────────────────────────────────────────────────────────────
async function postPayment(id) {
  if (postingId.value) return
  postingId.value = id
  try {
    await paymentsApi.post(id)
    toast.success('Payment posted to GL. Receivable cleared.')
    await loadList()
  } catch { /* handled */ }
  finally { postingId.value = null }
}

// ── Reverse ───────────────────────────────────────────────────────────────────
async function submitReverse() {
  if (reversingId.value || !drawer.value) return
  if (!reverseReason.value.trim()) { toast.error('A reason is required to reverse a payment.'); return }

  reversingId.value = drawer.value.id
  try {
    await paymentsApi.reverse(drawer.value.id, { reason: reverseReason.value.trim() })
    showReverse.value = false
    reverseReason.value = ''
    drawer.value = null
    toast.success('Payment reversed. Reversing journal entry created.')
    await loadList()
  } catch { /* handled */ }
  finally { reversingId.value = null }
}

// ── Helper: patch list + open drawer ─────────────────────────────────────────
function applyUpdate(id, patch) {
  const idx = list.value.findIndex(p => p.id === id)
  if (idx !== -1) list.value[idx] = { ...list.value[idx], ...patch }
  if (drawer.value?.id === id) drawer.value = { ...drawer.value, ...patch }
}

// ── Display helpers ───────────────────────────────────────────────────────────
function paymentRef(p)    { return p.paymentNumber ?? p.ref ?? p.id }
function paymentCust(p)   { return p.customerName  ?? p.customer ?? customerLabel(p.customerId) ?? '—' }
function paymentAmount(p) { return Number(p.paymentAmount ?? p.amount ?? 0) }
function paymentCurrency(p){ return p.currencyCode ?? p.currency ?? 'KES' }
function paymentMatched(p){ return Number(p.matched ?? 0) }
function paymentInvoice(p){ return p.invoiceId ?? p.invoice ?? null }
function paymentMethod(p) { return p.paymentMethod ?? p.method ?? '—' }
function invoiceNumber(id) {
  if (!id) return id
  return invoiceNumMap.value[id] ?? (invoiceList.value.find(i => i.value === id)?.label.split(' —')[0]) ?? id
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Payments"
      :meta="`${list.length} payments · ${currencies} ${fmt(totalAmount)}`"
    >
      <Button variant="ghost" icon="globe">M-Pesa callback log</Button>
      <Button variant="primary" icon="plus" @click="showCreate = true; newPayment = blankPayment()">
        Record payment
      </Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search">
        <ChipFilter
          v-for="s in STATUSES"
          :key="s"
          :active="statusFilter === s"
          @click="statusFilter = s"
        >
          {{ s === 'ALL' ? 'All' : s.replace('_', ' ') }}
        </ChipFilter>
      </TableToolbar>

      <div class="card">
        <div v-if="loadingList" style="padding:24px;text-align:center;color:var(--muted);font-size:13px">
          Loading…
        </div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Ref</th>
              <th>Customer</th>
              <th>Date</th>
              <th>Method</th>
              <th class="num">Amount</th>
              <th>Against invoice</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="p in filtered"
              :key="p.id"
              class="row-link"
              @click="openDrawer(p)"
            >
              <td><code>{{ paymentRef(p) }}</code></td>
              <td>{{ paymentCust(p) }}</td>
              <td>{{ fmtDate(p.paymentDate ?? p.date) }}</td>
              <td>
                <Badge :status="methodBadge(paymentMethod(p))" :dot="false">
                  {{ paymentMethod(p).replace('_', ' ') }}
                </Badge>
              </td>
              <td class="num mono">{{ paymentCurrency(p) }} {{ fmt(paymentAmount(p)) }}</td>
              <td>
                <code v-if="paymentInvoice(p)">{{ invoiceNumber(paymentInvoice(p)) }}</code>
                <span v-else class="muted" style="font-style:italic">unmatched</span>
              </td>
              <td>
                <Badge :status="statusBadge(p.status)" :dot="false">
                  {{ (p.status ?? '').replace('_', ' ') }}
                </Badge>
              </td>
              <td @click.stop>
                <IconBtn icon="dots" @click="openDrawer(p)" />
              </td>
            </tr>
            <tr v-if="!loadingList && filtered.length === 0">
              <td colspan="8" style="text-align:center;color:var(--muted);font-style:italic;padding:24px">
                No payments found.
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="payments" />
    </div>

    <!-- ── Payment Detail Drawer ──────────────────────────────────────────── -->
    <Modal
      :open="drawer !== null"
      :title="paymentRef(drawer ?? {})"
      :subtitle="drawer ? `${drawerCustomerName} · ${paymentCurrency(drawer)} ${fmt(paymentAmount(drawer))}` : ''"
      :width="820"
      @close="drawer = null"
    >
      <template v-if="drawer">
        <div class="kpi-grid" style="grid-template-columns:repeat(4,1fr)">
          <div class="kpi-card">
            <div class="kpi-label">Date</div>
            <div class="kpi-value">{{ fmtDate(drawer.paymentDate ?? drawer.date) }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Method</div>
            <div class="kpi-value">{{ paymentMethod(drawer).replace(/_/g, ' ') }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Amount</div>
            <div class="kpi-value mono">{{ paymentCurrency(drawer) }} {{ fmt(paymentAmount(drawer)) }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Status</div>
            <div class="kpi-value">
              <Badge :status="statusBadge(drawer.status)" :dot="false">
                {{ (drawer.status ?? '').replace(/_/g, ' ') }}
              </Badge>
            </div>
          </div>
        </div>

        <!-- Summary details -->
        <div class="card">
          <div class="card-head">Details</div>
          <table class="tbl">
            <tbody>
              <tr>
                <td style="color:var(--muted);width:160px">Customer</td>
                <td>{{ drawerCustomerName }}</td>
              </tr>
              <tr>
                <td style="color:var(--muted)">Against invoice</td>
                <td>
                  <code v-if="drawer.invoiceId">{{ drawerInvoiceNumber }}</code>
                  <span v-else class="muted" style="font-style:italic">unmatched</span>
                </td>
              </tr>
              <tr v-if="drawer.transactionReference">
                <td style="color:var(--muted)">Transaction ref</td>
                <td class="mono" style="font-size:13px">{{ drawer.transactionReference }}</td>
              </tr>
              <tr v-if="drawer.mpesaReceiptNumber">
                <td style="color:var(--muted)">M-Pesa receipt</td>
                <td class="mono" style="font-size:13px">{{ drawer.mpesaReceiptNumber }}</td>
              </tr>
              <tr v-if="drawer.journalEntryId">
                <td style="color:var(--muted)">Journal entry</td>
                <td><code>{{ drawerJeReference }}</code></td>
              </tr>
              <tr v-if="drawer.notes">
                <td style="color:var(--muted)">Notes</td>
                <td>{{ drawer.notes }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- GL posting preview -->
        <div class="card">
          <div class="card-head">GL posting preview (DR Cash / CR AR)</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>Account</th>
                <th>Description</th>
                <th class="num">Debit</th>
                <th class="num">Credit</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>1-1100</code> Cash &amp; Bank</td>
                <td>Payment received</td>
                <td class="num mono">{{ fmt(paymentAmount(drawer)) }}</td>
                <td class="num mono">—</td>
              </tr>
              <tr>
                <td><code>1-1200</code> Accounts Receivable</td>
                <td>Clear receivable</td>
                <td class="num mono">—</td>
                <td class="num mono">{{ fmt(paymentAmount(drawer)) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- ── Match to invoice inline form ─────────────────────────────── -->
        <div v-if="showMatch" class="inline-card">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">Match to invoice</div>
          <div class="form-grid cols-2">
            <div class="field" style="grid-column:1/-1">
              <label>Invoice <span style="color:var(--danger)">*</span></label>
              <SearchableSelect
                v-model="matchInvoiceId"
                :options="matchInvoiceOpts"
                :loading="loadingInvoices"
                placeholder="Search by invoice number or customer…"
              />
            </div>
            <div class="field">
              <label>Matched amount <span style="color:var(--danger)">*</span></label>
              <AmountInput class="input mono" v-model="matchAmount" />
            </div>
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button variant="primary" icon="link" :loading="matchingSave" @click="submitMatch">
              Confirm match
            </Button>
            <Button variant="ghost" @click="showMatch = false">Cancel</Button>
          </div>
        </div>

        <!-- ── Reverse inline form ────────────────────────────────────────── -->
        <div v-if="showReverse" class="inline-card">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">Reverse payment</div>
          <div class="field">
            <label>Reason <span style="color:var(--danger)">*</span></label>
            <input
              class="input"
              type="text"
              v-model="reverseReason"
              placeholder="e.g. Duplicate payment / incorrect amount"
            />
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button
              variant="danger"
              icon="rotate"
              :loading="reversingId === drawer.id"
              @click="submitReverse"
            >
              Confirm reversal
            </Button>
            <Button variant="ghost" @click="showReverse = false">Cancel</Button>
          </div>
        </div>
      </template>

      <template #footer>
        <!-- PENDING: can match or approve (if already manually matched) -->
        <template v-if="drawer?.status === 'PENDING'">
          <Button
            variant="primary"
            icon="link"
            @click="showMatch = !showMatch; showReverse = false"
          >
            {{ showMatch ? 'Cancel match' : 'Match to invoice' }}
          </Button>
        </template>

        <!-- MATCHED: can approve -->
        <template v-else-if="drawer?.status === 'MATCHED'">
          <Button
            variant="primary"
            icon="approve"
            :loading="approvingId === drawer.id"
            @click="approvePayment(drawer.id)"
          >
            Approve
          </Button>
          <Button
            variant="ghost"
            icon="link"
            @click="showMatch = !showMatch; showReverse = false"
          >
            Re-match
          </Button>
        </template>

        <!-- APPROVED: can post to GL -->
        <template v-else-if="drawer?.status === 'APPROVED'">
          <Button
            variant="primary"
            icon="approve"
            :loading="postingId === drawer.id"
            @click="postPayment(drawer.id)"
          >
            Post payment (DR Cash / CR AR)
          </Button>
        </template>

        <!-- POSTED: can reverse -->
        <template v-else-if="drawer?.status === 'POSTED'">
          <Button
            variant="ghost"
            icon="receipt"
            @click="$router.push(drawerReceiptId ? `/receipts?receiptId=${drawerReceiptId}` : `/receipts?paymentId=${drawer.id}`)"
          >View receipt</Button>
          <Button
            variant="ghost"
            icon="rotate"
            style="color:var(--danger)"
            @click="showReverse = !showReverse; showMatch = false"
          >
            {{ showReverse ? 'Cancel' : 'Reverse payment' }}
          </Button>
        </template>

        <!-- REVERSED / terminal -->
        <template v-else-if="drawer?.status === 'REVERSED'">
          <span style="font-size:13px;color:var(--muted)">This payment has been reversed.</span>
        </template>

        <!-- Legacy demo statuses fallback -->
        <template v-else-if="drawer?.status === 'PENDING_APPROVAL'">
          <Button
            variant="primary"
            icon="approve"
            :loading="approvingId === drawer?.id"
            @click="approvePayment(drawer.id)"
          >
            Approve
          </Button>
          <Button
            variant="ghost"
            icon="link"
            @click="showMatch = !showMatch; showReverse = false"
          >
            Match
          </Button>
        </template>

        <Button variant="ghost" @click="drawer = null">Close</Button>
      </template>
    </Modal>

    <!-- ── Create Payment Modal ────────────────────────────────────────────── -->
    <Modal
      :open="showCreate"
      title="Record Payment"
      subtitle="Customer payment received — creates PENDING payment"
      :width="680"
      @close="showCreate = false; newPayment = blankPayment()"
    >
      <div class="form-grid cols-2">
        <!-- Customer -->
        <div class="field" style="grid-column:1/-1">
          <label>Customer <span style="color:var(--danger)">*</span></label>
          <SearchableSelect
            v-if="customerList.length"
            v-model="newPayment.customerId"
            :options="customerList"
            placeholder="Search customer…"
            @update:modelValue="id => {
              const c = customerList.find(x => x.value === id)
              if (c?.currency) newPayment.currencyCode = c.currency
            }"
          />
          <input
            v-else
            class="input mono"
            type="text"
            v-model="newPayment.customerId"
            placeholder="Paste customer UUID"
            style="font-size:12px"
          />
        </div>

        <!-- Invoice (optional) -->
        <div class="field" style="grid-column:1/-1">
          <label>Invoice <span style="color:var(--muted);font-size:11px">(optional — match later)</span></label>
          <SearchableSelect
            v-model="newPayment.invoiceId"
            :options="invoiceOpts"
            :placeholder="invoiceOpts.length ? 'Search unpaid invoices…' : newPayment.customerId ? 'No unpaid invoices for this customer' : 'Select a customer first'"
            @update:modelValue="id => {
              const inv = invoiceOpts.find(x => x.value === id)
              if (inv) {
                newPayment.currencyCode = inv.currency
                if (!newPayment.paymentAmount) newPayment.paymentAmount = String(inv.balance)
              }
            }"
          />
        </div>

        <!-- Period -->
        <div class="field">
          <label>Accounting period <span style="color:var(--muted);font-size:11px">(optional)</span></label>
          <SearchableSelect
            v-if="periodList.length"
            v-model="newPayment.periodId"
            :options="periodList"
            placeholder="Select open period…"
          />
          <input
            v-else
            class="input mono"
            type="text"
            v-model="newPayment.periodId"
            placeholder="Period UUID (optional)"
            style="font-size:12px"
          />
        </div>

        <!-- Payment date -->
        <div class="field">
          <label>Payment date <span style="color:var(--danger)">*</span></label>
          <input class="input" type="date" v-model="newPayment.paymentDate" />
        </div>

        <!-- Payment method -->
        <div class="field">
          <label>Payment method <span style="color:var(--danger)">*</span></label>
          <SearchableSelect
            v-model="newPayment.paymentMethod"
            :options="PAYMENT_METHOD_OPTIONS"
            placeholder="Select method"
          />
        </div>

        <!-- Currency -->
        <div class="field">
          <label>Currency</label>
          <SearchableSelect
            v-model="newPayment.currencyCode"
            :options="[
              { value: 'KES', label: 'KES — Kenyan Shilling' },
              { value: 'USD', label: 'USD — US Dollar' },
              { value: 'EUR', label: 'EUR — Euro' },
              { value: 'GBP', label: 'GBP — British Pound' },
            ]"
            placeholder="Select currency"
          />
        </div>

        <!-- Amount -->
        <div class="field">
          <label>Amount <span style="color:var(--danger)">*</span></label>
          <AmountInput class="input mono" v-model="newPayment.paymentAmount" />
        </div>

        <!-- Exchange rate (shown when non-KES) -->
        <div v-if="newPayment.currencyCode !== 'KES'" class="field">
          <label>Exchange rate to KES</label>
          <AmountInput class="input mono" v-model="newPayment.exchangeRate" />
        </div>

        <!-- Transaction reference -->
        <div class="field" :style="newPayment.currencyCode !== 'KES' ? '' : 'grid-column:1/-1'">
          <label>Transaction reference</label>
          <input
            class="input"
            type="text"
            v-model="newPayment.transactionReference"
            placeholder="EFT ref / cheque no / M-Pesa code"
          />
        </div>

        <!-- Notes -->
        <div class="field" style="grid-column:1/-1">
          <label>Notes</label>
          <input class="input" type="text" v-model="newPayment.notes" placeholder="Optional notes" />
        </div>
      </div>

      <template #footer>
        <Button variant="primary" icon="approve" :loading="creating" @click="submitCreate">
          Create payment
        </Button>
        <Button variant="ghost" @click="showCreate = false; newPayment = blankPayment()">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.inline-card {
  margin-top: 12px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
}
.kpi-card {
  background: var(--surface, #fff);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 14px 16px;
}
.kpi-label {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 4px;
  text-transform: uppercase;
  letter-spacing: .04em;
}
.kpi-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
}
</style>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { INVOICES } from '@/data/index.js'
import { invoices as invoicesApi, customers as customersApi, accounts as accountsApi, periods as periodsApi, tax as taxApi, payments as paymentsApi } from '@/api/index.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast } from '@/composables/useToast.js'
import { useAuth } from '@/composables/useAuth.js'
import { useCategoryCache } from '@/composables/useCategoryCache.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import AmountInput from '@/components/primitives/AmountInput.vue'
import Badge from '@/components/primitives/Badge.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import Segmented from '@/components/primitives/Segmented.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'
import ChipFilter from '@/components/primitives/ChipFilter.vue'

// ── Auth / mode ───────────────────────────────────────────────────────────────
const { isDemo } = useAppMode()
const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')

// ── State ─────────────────────────────────────────────────────────────────────
const loadingList = ref(false)
const list        = ref([])
const customerOpts = ref([])   // [{ value: id, label: name }]
const accountOpts  = ref([])   // revenue accounts for line item picker
const taxCodeOpts  = ref([])   // [{ value: id, label: code+name }]
const periodOpts   = ref([])   // [{ value: id, label: name }]

const statusFilter = ref('ALL')
const search       = ref('')
const drawer       = ref(null)

// modal visibility
const createModal    = ref(false)
const voidModal      = ref(false)
const creditNoteModal = ref(false)
const payModal       = ref(false)

// loading / saving guards
const saving         = ref(false)  // Create Invoice
const approvingId    = ref(null)
const voidingId      = ref(null)
const cnSaving       = ref(false)
const paySaving      = ref(false)

// ── Void form ─────────────────────────────────────────────────────────────────
const voidReason = ref('')

// ── Credit-note form ──────────────────────────────────────────────────────────
const cnAmount = ref('')
const cnReason = ref('')

// ── Payment form ──────────────────────────────────────────────────────────────
const payAmount    = ref('')
const payMethod    = ref('BANK_TRANSFER')
const payDate      = ref(new Date().toISOString().slice(0, 10))
const payPeriodId  = ref(null)
const payRef       = ref('')
// Payment methods are entity-managed dynamic data (CLAUDE.md §2) — see shared/categories on
// the backend and setup/Categories.vue for where they're created/edited. Cached at module
// level (useCategoryCache) so this view, Bills.vue and Payments.vue share one fetch.
const paymentMethodsCache = useCategoryCache('PAYMENT_METHOD')
const PAYMENT_METHOD_OPTIONS = computed(() => paymentMethodsCache.options.value)

// ── New invoice form ──────────────────────────────────────────────────────────
function blankInvoice() {
  return {
    customerId: null,
    periodId: null,
    issueDate: new Date().toISOString().slice(0, 10),
    dueDate: '',
    currencyCode: 'KES',
    exchangeRate: '1',
    discountAmount: '0',
    notes: '',
    lines: [blankLine()],
  }
}
function blankLine() {
  return { accountId: null, description: '', quantity: '1', unitPrice: '', taxCodeId: null, recognitionType: 'POINT_IN_TIME' }
}
const newInvoice = ref(blankInvoice())

// ── Computed totals for create modal ─────────────────────────────────────────
const newSubtotal = computed(() =>
  newInvoice.value.lines.reduce((s, l) => s + (parseFloat(l.quantity) || 0) * (parseFloat(l.unitPrice) || 0), 0)
)
const newTax = computed(() => {
  // approximate: use the selected tax code rate if all lines share the same code, else 16%
  return newSubtotal.value * 0.16
})
const newTotal = computed(() => newSubtotal.value + newTax.value - (parseFloat(newInvoice.value.discountAmount) || 0))

// ── List helpers ──────────────────────────────────────────────────────────────
const STATUSES = ['ALL', 'DRAFT', 'APPROVED', 'SENT', 'PARTIALLY_PAID', 'PAID', 'VOID', 'CREDIT_NOTE']

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return list.value.filter(i => {
    const matchStatus = statusFilter.value === 'ALL' || i.status === statusFilter.value
    const matchSearch = !q ||
      (i.invoiceNumber ?? '').toLowerCase().includes(q) ||
      (i.customerName  ?? '').toLowerCase().includes(q) ||
      customerLabel(i.customerId).toLowerCase().includes(q)
    return matchStatus && matchSearch
  })
})

const totalUnpaid = computed(() =>
  list.value
    .filter(i => i.status !== 'PAID' && i.status !== 'VOID' && i.status !== 'CREDIT_NOTE')
    .reduce((s, i) => s + (Number(i.outstandingAmount) || 0), 0)
)

// ── Bootstrap ─────────────────────────────────────────────────────────────────
async function loadList() {
  if (isDemo.value) { list.value = [...INVOICES].map(normaliseDemo); return }
  loadingList.value = true
  try {
    const data = await invoicesApi.list({ entityId: entityId.value, page: 0, size: 200 })
    const items = data?.content ?? (Array.isArray(data) ? data : [])
    list.value = items
  } catch {
    toast.error('Failed to load invoices.')
  } finally {
    loadingList.value = false
  }
}

onMounted(async () => {
  paymentMethodsCache.load(entityId.value)
  await loadList()

  // Load customer list for select picker
  try {
    const data = await customersApi.list({ entityId: entityId.value, size: 500 })
    const items = data?.content ?? data
    if (Array.isArray(items)) {
      customerOpts.value = items.map(c => ({ value: c.id, label: c.customerName ?? c.name ?? c.id }))
    }
  } catch { /* silently skip */ }

  // Load revenue accounts for line item picker
  try {
    const data = await accountsApi.list({ entityId: entityId.value, size: 500 })
    const items = Array.isArray(data) ? data : (data?.content ?? [])
    const REVENUE_SUBTYPES = new Set(['OPERATING_REVENUE', 'OTHER_INCOME', 'FINANCE_INCOME'])
    accountOpts.value = items
      .filter(a => a.isActive !== false && !a.isHeader && (a.accountType === 'REVENUE' || REVENUE_SUBTYPES.has(a.accountSubtype)))
      .map(a => ({
        value: a.id,
        label: `${a.accountCode} — ${a.accountName}`,
        group: a.accountSubtype ?? a.accountType,
      }))
    // Fallback: if no revenue-typed accounts returned, expose all postable accounts
    if (!accountOpts.value.length) {
      accountOpts.value = items
        .filter(a => a.isActive !== false && !a.isHeader && a.accountCode)
        .map(a => ({ value: a.id, label: `${a.accountCode} — ${a.accountName}` }))
    }
  } catch { /* silently skip */ }

  // Load open periods
  try {
    const data = await periodsApi.list({ entityId: entityId.value, size: 200 })
    const items = Array.isArray(data) ? data : (data?.content ?? [])
    const today = new Date()
    today.setHours(0, 0, 0, 0)
    periodOpts.value = items
      .filter(p => p.status !== 'CLOSED')
      .map(p => {
        const name = p.periodName ?? p.id
        // Determine label suffix based on actual calendar position, not the status field name
        let suffix = ''
        if (p.status === 'OPEN') {
          // Past open periods get no label; only genuinely future periods get (FUTURE)
          if (p.startDate) {
            const start = new Date(p.startDate + 'T00:00:00')
            if (start > today) suffix = ' (FUTURE)'
          }
        } else if (p.status !== 'OPEN') {
          // ADJUSTING, CLOSING, REOPENED etc.
          suffix = ` (${p.status})`
        }
        return { value: p.id, label: `${name}${suffix}` }
      })
  } catch { /* silently skip */ }

  // Load tax codes
  try {
    const data = await taxApi.list(entityId.value)
    const items = Array.isArray(data) ? data : (data?.content ?? [])
    taxCodeOpts.value = items.map(t => ({ value: t.id ?? t.code, label: `${t.code} — ${t.name}` }))
  } catch { /* silently skip — tax codes are optional on lines */ }
})

// ── Create invoice ────────────────────────────────────────────────────────────
async function submitNewInvoice() {
  if (saving.value) return
  const { customerId, periodId, issueDate, dueDate, currencyCode, exchangeRate, discountAmount, notes, lines } = newInvoice.value
  if (!customerId) { toast.error('Customer is required.'); return }
  if (!issueDate)  { toast.error('Invoice date is required.'); return }
  if (!dueDate)    { toast.error('Due date is required.'); return }
  const validLines = lines.filter(l => l.description && l.unitPrice)
  if (!validLines.length) { toast.error('At least one complete line item is required.'); return }
  const missingAccount = validLines.find(l => !l.accountId)
  if (missingAccount) { toast.error(`Line "${missingAccount.description}": select a revenue account.`); return }

  saving.value = true
  const payload = {
    entityId: entityId.value,
    customerId,
    periodId: periodId || undefined,
    issueDate,
    dueDate,
    currencyCode: currencyCode || 'KES',
    exchangeRate: parseFloat(exchangeRate) || 1,
    discountAmount: parseFloat(discountAmount) || 0,
    notes: notes || undefined,
    lines: validLines.map((l, idx) => ({
      lineNumber: idx + 1,
      accountId: l.accountId,
      description: l.description,
      quantity: parseFloat(l.quantity) || 1,
      unitPrice: parseFloat(l.unitPrice),
      taxCodeId: l.taxCodeId || undefined,
      recognitionType: l.recognitionType || 'POINT_IN_TIME',
    })),
  }
  try {
    const created = await invoicesApi.create(payload)
    list.value.unshift(created)
    createModal.value = false
    newInvoice.value = blankInvoice()
    toast.success('Invoice created as DRAFT.')
  } catch { /* handled by client */ }
  finally { saving.value = false }
}

// ── Approve ───────────────────────────────────────────────────────────────────
async function approveInvoice(id) {
  if (approvingId.value) return
  approvingId.value = id
  try {
    await invoicesApi.approve(id)
    toast.success('Invoice approved — AR journal entry posted.')
    drawer.value = null
    await loadList()
  } catch { /* handled */ }
  finally { approvingId.value = null }
}

// ── Void ──────────────────────────────────────────────────────────────────────
function openVoidModal(inv) {
  voidReason.value = ''
  voidModal.value = true
}

async function submitVoid() {
  if (voidingId.value || !drawer.value) return
  if (!voidReason.value.trim()) { toast.error('A void reason is required.'); return }
  voidingId.value = drawer.value.id
  try {
    await invoicesApi.void(drawer.value.id, voidReason.value.trim())
    voidModal.value = false
    voidReason.value = ''
    drawer.value = null
    toast.success('Invoice voided.')
    await loadList()
  } catch { /* handled */ }
  finally { voidingId.value = null }
}

// ── Credit note ───────────────────────────────────────────────────────────────
function openCreditNoteModal() {
  cnAmount.value = ''
  cnReason.value = ''
  creditNoteModal.value = true
}

async function submitCreditNote() {
  if (cnSaving.value || !drawer.value) return
  const amount = parseFloat(cnAmount.value)
  if (isNaN(amount) || amount <= 0) { toast.error('Enter a valid credit note amount.'); return }
  if (!cnReason.value.trim()) { toast.error('A reason is required for the credit note.'); return }
  cnSaving.value = true
  try {
    const currency = drawer.value.currencyCode
    await invoicesApi.createCreditNote(drawer.value.id, {
      creditNoteAmount: amount,
      reason: cnReason.value.trim(),
    })
    creditNoteModal.value = false
    cnAmount.value = ''
    cnReason.value = ''
    drawer.value = null
    toast.success(`Credit note of ${currency} ${fmt(amount)} issued.`)
    await loadList()    // reload so both credit note row and updated invoice row appear
  } catch { /* handled */ }
  finally { cnSaving.value = false }
}

// ── Apply payment (quick) — creates a real Payment record via the payments module
async function submitPayment() {
  if (paySaving.value || !drawer.value) return
  const amount = parseFloat(payAmount.value)
  if (isNaN(amount) || amount <= 0) { toast.error('Enter a valid payment amount.'); return }
  if (!payMethod.value)  { toast.error('Select a payment method.'); return }
  if (!payDate.value)    { toast.error('Payment date is required.'); return }

  paySaving.value = true
  try {
    const idempotencyKey = crypto.randomUUID()
    // Step 1: create payment record
    const payment = await paymentsApi.create({
      entityId:             entityId.value,
      periodId:             payPeriodId.value || undefined,
      customerId:           drawer.value.customerId,
      invoiceId:            drawer.value.id,
      paymentMethod:        payMethod.value,
      paymentAmount:        amount,
      currencyCode:         drawer.value.currencyCode ?? 'KES',
      exchangeRate:         Number(drawer.value.exchangeRate) || 1,
      transactionReference: payRef.value.trim() || undefined,
      paymentDate:          payDate.value,
    }, idempotencyKey)

    // Step 2: match to invoice
    await paymentsApi.match(payment.id, { invoiceId: drawer.value.id, matchedAmount: amount })
    // Step 3: approve
    await paymentsApi.approve(payment.id)
    // Step 4: post to ledger
    await paymentsApi.post(payment.id)

    payModal.value  = false
    payAmount.value = ''
    payRef.value    = ''
    toast.success(`Payment of ${drawer.value.currencyCode} ${fmt(amount)} applied and posted.`)
    await loadList()
  } catch { /* client.js already toasted */ }
  finally { paySaving.value = false }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function applyUpdate(id, patch) {
  const idx = list.value.findIndex(i => i.id === id)
  if (idx !== -1) list.value[idx] = { ...list.value[idx], ...patch }
  if (drawer.value?.id === id) drawer.value = { ...drawer.value, ...patch }
}

function addNewLine() {
  newInvoice.value.lines.push(blankLine())
}
function removeNewLine(i) {
  if (newInvoice.value.lines.length > 1) newInvoice.value.lines.splice(i, 1)
}

function statusVariant(status) {
  const map = {
    DRAFT: 'outline', APPROVED: 'info', SENT: 'submitted',
    PARTIALLY_PAID: 'warn', PAID: 'approved', VOID: 'void', CREDIT_NOTE: 'rejected',
  }
  return map[status] ?? 'outline'
}

function ageColor(age) {
  if (age == null) return ''
  if (age <= 0) return 'var(--pos)'   // not yet due
  if (age > 30) return 'var(--neg)'   // severely overdue
  if (age > 14) return 'var(--warn)'  // approaching / overdue
  return 'var(--neg)'                  // overdue
}

/** Normalise demo INVOICES fixture into backend Invoice shape */
function normaliseDemo(i) {
  if (i.invoiceNumber) return i  // already normalised (came from real API)
  return {
    id: i.id,
    entityId: 'demo',
    periodId: null,
    invoiceNumber: i.ref,
    customerId: i.customer,
    customerName: i.customerName,
    issueDate: i.date,
    dueDate: i.due,
    currencyCode: i.currency,
    exchangeRate: 1,
    subtotal: i.subtotal,
    taxAmount: i.tax,
    discountAmount: 0,
    totalAmount: i.total,
    paidAmount: i.paid ?? 0,
    outstandingAmount: i.balance,
    status: i.status === 'POSTED' ? 'SENT' : i.status,
    notes: null,
    journalEntryId: null,
    recognition: i.recognition,   // kept for display badge
    discount: i.discount,         // kept for display badge
    aging: i.aging,               // kept for age column
    lines: (i.lines ?? []).map((l, idx) => ({
      id: `${i.id}-L${idx + 1}`,
      lineNumber: idx + 1,
      accountId: null,
      description: l.desc,
      quantity: l.qty,
      unitPrice: l.unit,
      lineSubtotal: l.qty * l.unit,
      lineTax: l.qty * l.unit * 0.16,
      lineTotal: l.qty * l.unit * 1.16,
      recognitionType: i.recognition ?? 'POINT_IN_TIME',
    })),
  }
}

// Resolve customer UUID → display name using the already-loaded customerOpts list
function customerLabel(id) {
  if (!id) return '—'
  const opt = customerOpts.value.find(c => c.value === id)
  return opt?.label ?? id
}

// Resolve account UUID → display label (code — name)
function accountLabel(id) {
  if (!id) return 'Revenue'
  const opt = accountOpts.value.find(a => a.value === id)
  return opt?.label ?? id
}

// Group drawer lines by revenue accountId for posting preview
const previewRevenueLines = computed(() => {
  const lines = drawer.value?.lines
  if (!lines?.length) {
    const total = Number(drawer.value?.subtotal ?? drawer.value?.totalAmount ?? 0)
    return [{ label: accountLabel(null), amount: total }]
  }
  const byAccount = {}
  for (const l of lines) {
    const id = l.accountId
    const subtotal = Number(l.lineSubtotal ?? (Number(l.lineTotal ?? 0) - Number(l.lineTax ?? 0)))
    if (!byAccount[id]) byAccount[id] = { label: accountLabel(id), amount: 0 }
    byAccount[id].amount += subtotal
  }
  return Object.values(byAccount)
})

// Compute aging days from dueDate for real-API rows that don't include it
function computeAging(inv) {
  if (inv.aging != null) return inv.aging
  if (!inv.dueDate) return null
  if (inv.status === 'PAID' || inv.status === 'VOID') return null
  const due = new Date(inv.dueDate)
  const today = new Date()
  return Math.floor((today - due) / 86400000)
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Invoices"
      :meta="`${list.length} invoices · KES ${fmt(totalUnpaid)} unpaid`"
    >
      <Button variant="ghost" icon="download">Export</Button>
      <Button variant="primary" icon="plus" @click="createModal = true; newInvoice = blankInvoice()">New invoice</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search">
        <ChipFilter v-for="s in STATUSES" :key="s" :active="statusFilter === s" @click="statusFilter = s">
          {{ s.replace(/_/g, ' ') }}
        </ChipFilter>
      </TableToolbar>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Invoice</th>
              <th>Customer</th>
              <th>Date</th>
              <th>Due</th>
              <th>Recognition</th>
              <th class="num">Subtotal</th>
              <th class="num">Tax</th>
              <th class="num">Total</th>
              <th class="num">Outstanding</th>
              <th>Status</th>
              <th>Age</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loadingList">
              <td colspan="11" style="text-align:center;padding:32px;color:var(--muted)">Loading invoices…</td>
            </tr>
            <tr v-else-if="!filtered.length">
              <td colspan="11" style="text-align:center;padding:32px;color:var(--muted)">No invoices found.</td>
            </tr>
            <tr v-for="inv in filtered" v-else :key="inv.id" class="row-link" @click="drawer = inv; payModal = false; creditNoteModal = false; voidModal = false">
              <td><code>{{ inv.invoiceNumber }}</code></td>
              <td>{{ inv.customerName ?? customerLabel(inv.customerId) }}</td>
              <td>{{ fmtDate(inv.issueDate) }}</td>
              <td>{{ fmtDate(inv.dueDate) }}</td>
              <td>
                <Badge
                  v-if="inv.recognition || (inv.lines?.[0]?.recognitionType)"
                  :status="(inv.recognition ?? inv.lines?.[0]?.recognitionType) === 'OVER_TIME' ? 'over-time' : 'point-in-time'"
                  :dot="false"
                >{{ inv.recognition ?? inv.lines?.[0]?.recognitionType }}</Badge>
                <Badge v-if="inv.discount" status="warn" :dot="false" style="margin-left:4px">{{ inv.discount }}% disc</Badge>
              </td>
              <td class="num mono">{{ fmt(inv.subtotal ?? inv.subtotal) }}</td>
              <td class="num mono">{{ fmt(inv.taxAmount) }}</td>
              <td class="num mono">{{ inv.currencyCode }} {{ fmt(inv.totalAmount) }}</td>
              <td class="num mono" :style="Number(inv.outstandingAmount) > 0 ? 'color:var(--danger)' : ''">
                {{ fmt(inv.outstandingAmount) }}
              </td>
              <td><Badge :status="statusVariant(inv.status)" :dot="false">{{ inv.status.replace(/_/g, ' ') }}</Badge></td>
              <td class="mono" :style="{ color: ageColor(computeAging(inv)), fontSize: '12px' }">
                {{ computeAging(inv) == null ? '—' : computeAging(inv) <= 0 ? `Due in ${Math.abs(computeAging(inv))}d` : `${computeAging(inv)}d overdue` }}
              </td>
              <td @click.stop>
                <IconBtn icon="dots" @click="drawer = inv; payModal = false; creditNoteModal = false; voidModal = false" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="invoices" />
    </div>

    <!-- ── Invoice Detail Drawer ──────────────────────────────────────────────── -->
    <Modal
      :open="drawer !== null"
      :title="drawer?.invoiceNumber"
      :subtitle="drawer ? `${drawer.customerName ?? customerLabel(drawer.customerId)} · ${drawer.currencyCode} ${fmt(drawer.totalAmount)}` : ''"
      :width="900"
      @close="drawer = null; payModal = false; creditNoteModal = false; voidModal = false"
    >
      <template v-if="drawer">
        <div class="inv-stats">
          <div class="istat">
            <div class="istat-label">Customer</div>
            <div class="istat-value">{{ drawer.customerName ?? customerLabel(drawer.customerId) }}</div>
          </div>
          <div class="istat">
            <div class="istat-label">Issue Date</div>
            <div class="istat-value">{{ fmtDate(drawer.issueDate) }}</div>
          </div>
          <div class="istat">
            <div class="istat-label">Due Date</div>
            <div class="istat-value">{{ fmtDate(drawer.dueDate) }}</div>
          </div>
          <div class="istat">
            <div class="istat-label">Outstanding</div>
            <div class="istat-value" :style="Number(drawer.outstandingAmount) > 0 ? 'color:var(--danger)' : ''">
              {{ drawer.currencyCode }} {{ fmt(drawer.outstandingAmount) }}
            </div>
          </div>
          <div class="istat">
            <div class="istat-label">Status</div>
            <div class="istat-value">
              <Badge :status="statusVariant(drawer.status)" :dot="false">{{ drawer.status?.replace(/_/g, ' ') }}</Badge>
            </div>
          </div>
        </div>

        <!-- Line items -->
        <div class="card" style="margin-top:16px">
          <div class="card-head">Line Items</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>#</th>
                <th>Description</th>
                <th>Recognition</th>
                <th class="num">Qty</th>
                <th class="num">Unit price</th>
                <th class="num">Tax</th>
                <th class="num">Line total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="line in drawer.lines" :key="line.id ?? line.lineNumber">
                <td class="mono" style="color:var(--muted);font-size:11px">{{ line.lineNumber }}</td>
                <td>{{ line.description }}</td>
                <td>
                  <Badge v-if="line.recognitionType" :status="line.recognitionType === 'OVER_TIME' ? 'over-time' : 'point-in-time'" :dot="false" style="font-size:10px">
                    {{ line.recognitionType }}
                  </Badge>
                </td>
                <td class="num mono">{{ line.quantity }}</td>
                <td class="num mono">{{ fmt(line.unitPrice) }}</td>
                <td class="num mono">{{ fmt(line.lineTax ?? 0) }}</td>
                <td class="num mono">{{ fmt(line.lineTotal ?? line.lineSubtotal) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr>
                <td colspan="6" class="num" style="font-size:12px;color:var(--muted)">Subtotal</td>
                <td class="num mono">{{ fmt(drawer.subtotal ?? drawer.totalAmount) }}</td>
              </tr>
              <tr>
                <td colspan="6" class="num" style="font-size:12px;color:var(--muted)">Tax</td>
                <td class="num mono">{{ fmt(drawer.taxAmount) }}</td>
              </tr>
              <tr v-if="Number(drawer.discountAmount) > 0">
                <td colspan="6" class="num" style="font-size:12px;color:var(--muted)">Discount</td>
                <td class="num mono" style="color:var(--neg)">({{ fmt(drawer.discountAmount) }})</td>
              </tr>
              <tr style="font-weight:700">
                <td colspan="6" class="num">Total</td>
                <td class="num mono">{{ drawer.currencyCode }} {{ fmt(drawer.totalAmount) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>

        <!-- Journal preview (for DRAFT) -->
        <div v-if="drawer.status === 'DRAFT'" class="card" style="margin-top:16px">
          <div class="card-head">Posting Preview (on Approve)</div>
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
                <td><code>1-1200</code> Accounts Receivable</td>
                <td>{{ drawer.customerName ?? customerLabel(drawer.customerId) }}</td>
                <td class="num mono">{{ fmt(drawer.totalAmount) }}</td>
                <td class="num mono">—</td>
              </tr>
              <tr v-for="rev in previewRevenueLines" :key="rev.label">
                <td>
                  <code>{{ rev.label.split(' — ')[0] }}</code>
                  {{ rev.label.split(' — ').slice(1).join(' — ') }}
                </td>
                <td>Revenue recognition</td>
                <td class="num mono">—</td>
                <td class="num mono">{{ fmt(rev.amount) }}</td>
              </tr>
              <tr v-if="Number(drawer.taxAmount) > 0">
                <td><code>2-2100</code> VAT Payable</td>
                <td>Output VAT</td>
                <td class="num mono">—</td>
                <td class="num mono">{{ fmt(drawer.taxAmount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Notes -->
        <div v-if="drawer.notes" style="font-size:13px;color:var(--muted);margin-top:12px">{{ drawer.notes }}</div>

        <!-- Inline: Apply payment form -->
        <div v-if="payModal" class="inline-card" style="margin-top:16px">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">Record Payment</div>
          <div class="form-grid cols-2">
            <div class="field" style="grid-column:1/-1">
              <label>Amount ({{ drawer.currencyCode }}) — outstanding {{ fmt(drawer.outstandingAmount) }}</label>
              <AmountInput class="input mono" v-model="payAmount" :placeholder="`max ${fmt(drawer.outstandingAmount)}`" />
            </div>
            <div class="field">
              <label>Payment Method</label>
              <select class="input" v-model="payMethod">
                <option v-for="m in PAYMENT_METHOD_OPTIONS" :key="m.value" :value="m.value">{{ m.label }}</option>
              </select>
            </div>
            <div class="field">
              <label>Payment Date</label>
              <input class="input" type="date" v-model="payDate" />
            </div>
            <div class="field">
              <label>Period <span style="color:var(--muted);font-size:11px">(optional)</span></label>
              <SearchableSelect
                v-if="periodOpts.length"
                v-model="payPeriodId"
                :options="periodOpts"
                placeholder="Select period…"
              />
              <input v-else class="input" v-model="payPeriodId" placeholder="Period UUID (optional)" />
            </div>
            <div class="field">
              <label>Reference <span style="color:var(--muted);font-size:11px">(optional)</span></label>
              <input class="input" type="text" v-model="payRef" placeholder="Bank ref / M-Pesa code" />
            </div>
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button variant="primary" icon="approve" :loading="paySaving" @click="submitPayment">Apply &amp; Post Payment</Button>
            <Button variant="ghost" @click="payModal = false">Cancel</Button>
          </div>
        </div>

        <!-- Inline: Credit note form -->
        <div v-if="creditNoteModal" class="inline-card" style="margin-top:16px">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">Issue Credit Note</div>
          <div class="form-grid cols-2">
            <div class="field">
              <label>Amount ({{ drawer.currencyCode }})</label>
              <AmountInput class="input mono" v-model="cnAmount" :placeholder="`max ${fmt(drawer.outstandingAmount)}`" />
            </div>
            <div class="field">
              <label>Reason</label>
              <input class="input" type="text" v-model="cnReason" placeholder="Customer return / billing error" />
            </div>
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button variant="primary" icon="reject" :loading="cnSaving" @click="submitCreditNote">Issue Credit Note</Button>
            <Button variant="ghost" @click="creditNoteModal = false">Cancel</Button>
          </div>
        </div>

        <!-- Inline: Void confirmation -->
        <div v-if="voidModal" class="inline-card" style="margin-top:16px;border-color:var(--danger)">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px;color:var(--danger)">Void Invoice</div>
          <div class="field">
            <label>Void reason <span style="color:var(--danger)">*</span></label>
            <input class="input" type="text" v-model="voidReason" placeholder="Customer cancelled order before delivery" />
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button variant="ghost" icon="x" style="color:var(--danger)" :loading="voidingId === drawer.id" @click="submitVoid">Confirm void</Button>
            <Button variant="ghost" @click="voidModal = false">Cancel</Button>
          </div>
        </div>
      </template>

      <template #footer>
        <template v-if="drawer?.status === 'DRAFT'">
          <Button
            variant="primary"
            icon="approve"
            :loading="approvingId === drawer.id"
            @click="approveInvoice(drawer.id)"
          >Approve &amp; post</Button>
          <Button
            variant="ghost"
            icon="x"
            style="color:var(--danger)"
            @click="openVoidModal(drawer)"
          >Void</Button>
        </template>
        <template v-else-if="drawer?.status === 'APPROVED' || drawer?.status === 'SENT' || drawer?.status === 'PARTIALLY_PAID'">
          <Button
            variant="primary"
            icon="card"
            @click="payModal = !payModal; creditNoteModal = false; voidModal = false"
          >{{ payModal ? 'Cancel' : 'Apply payment' }}</Button>
          <Button
            variant="ghost"
            icon="doc"
            @click="openCreditNoteModal(); payModal = false; voidModal = false"
          >{{ creditNoteModal ? 'Cancel' : 'Credit note' }}</Button>
          <Button
            variant="ghost"
            icon="x"
            style="color:var(--danger)"
            @click="voidModal = !voidModal; payModal = false; creditNoteModal = false; voidReason = ''"
          >Void</Button>
        </template>
        <Button variant="ghost" @click="drawer = null; payModal = false; creditNoteModal = false; voidModal = false">Close</Button>
      </template>
    </Modal>

    <!-- ── New Invoice Modal ──────────────────────────────────────────────────── -->
    <Modal
      :open="createModal"
      title="New Invoice"
      subtitle="IFRS 15 — Revenue from contracts with customers"
      :width="960"
      @close="createModal = false; newInvoice = blankInvoice()"
    >
      <div class="form-grid cols-3">
        <div class="field" style="grid-column:1/-1">
          <label>Customer <span style="color:var(--danger)">*</span></label>
          <SearchableSelect
            v-if="customerOpts.length"
            v-model="newInvoice.customerId"
            :options="customerOpts"
            placeholder="Search customer…"
          />
          <input
            v-else
            class="input mono"
            type="text"
            v-model="newInvoice.customerId"
            placeholder="Customer UUID"
            style="font-size:12px"
          />
        </div>
        <div class="field">
          <label>Period</label>
          <SearchableSelect
            v-if="periodOpts.length"
            v-model="newInvoice.periodId"
            :options="periodOpts"
            placeholder="Select period…"
          />
          <input
            v-else
            class="input mono"
            type="text"
            v-model="newInvoice.periodId"
            placeholder="Period UUID (optional)"
            style="font-size:12px"
          />
        </div>
        <div class="field">
          <label>Invoice date <span style="color:var(--danger)">*</span></label>
          <input class="input" type="date" v-model="newInvoice.issueDate" />
        </div>
        <div class="field">
          <label>Due date <span style="color:var(--danger)">*</span></label>
          <input class="input" type="date" v-model="newInvoice.dueDate" />
        </div>
        <div class="field">
          <label>Currency</label>
          <SearchableSelect
            v-model="newInvoice.currencyCode"
            :options="[
              { value: 'KES', label: 'KES — Kenyan Shilling' },
              { value: 'USD', label: 'USD — US Dollar' },
              { value: 'EUR', label: 'EUR — Euro' },
              { value: 'GBP', label: 'GBP — British Pound' },
            ]"
            placeholder="Select currency"
          />
        </div>
        <div class="field">
          <label>Exchange rate</label>
          <AmountInput class="input mono" v-model="newInvoice.exchangeRate" />
        </div>
        <div class="field">
          <label>Header discount</label>
          <AmountInput class="input mono" v-model="newInvoice.discountAmount" placeholder="0.00" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Notes</label>
          <input class="input" type="text" v-model="newInvoice.notes" placeholder="Standard terms NET 30" />
        </div>
      </div>

      <!-- Line items -->
      <div style="margin-top:20px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
          <span class="section-label">Line Items</span>
          <Button variant="ghost" icon="plus" style="font-size:12px;padding:4px 8px" @click="addNewLine">Add line</Button>
        </div>

        <div class="li-grid li-head">
          <div>Description</div>
          <div>Revenue account</div>
          <div class="num">Qty</div>
          <div class="num">Unit price</div>
          <div>Tax code</div>
          <div>Recognition</div>
          <div></div>
        </div>

        <div v-for="(line, i) in newInvoice.lines" :key="i" class="li-grid li-row">
          <div>
            <input class="input li-input" type="text" v-model="line.description" placeholder="Service description" />
          </div>
          <div>
            <SearchableSelect
              v-if="accountOpts.length"
              v-model="line.accountId"
              :options="accountOpts"
              placeholder="Select account…"
              :compact="true"
              :mono="true"
            />
            <input v-else class="input li-input mono" type="text" v-model="line.accountId" placeholder="Account UUID" />
          </div>
          <div>
            <AmountInput class="input li-input mono" v-model="line.quantity" style="text-align:right" />
          </div>
          <div>
            <AmountInput class="input li-input mono" v-model="line.unitPrice" placeholder="0.00" style="text-align:right" />
          </div>
          <div>
            <SearchableSelect
              v-if="taxCodeOpts.length"
              v-model="line.taxCodeId"
              :options="taxCodeOpts"
              placeholder="Tax code…"
              :compact="true"
            />
            <input v-else class="input li-input mono" type="text" v-model="line.taxCodeId" placeholder="Tax code ID" />
          </div>
          <div>
            <SearchableSelect
              v-model="line.recognitionType"
              :options="[
                { value: 'POINT_IN_TIME', label: 'Point in time' },
                { value: 'OVER_TIME',     label: 'Over time (deferred)' },
              ]"
              :compact="true"
            />
          </div>
          <div style="display:flex;align-items:center;justify-content:center">
            <IconBtn icon="x" @click="removeNewLine(i)" />
          </div>
        </div>

        <div class="li-totals">
          <span>Subtotal</span><span class="mono">{{ fmt(newSubtotal) }}</span>
          <span>VAT ~16%</span><span class="mono">{{ fmt(newTax) }}</span>
          <span style="font-weight:700">Total</span><span class="mono" style="font-weight:700">KES {{ fmt(newTotal) }}</span>
        </div>
      </div>

      <template #footer>
        <Button variant="primary" icon="approve" :loading="saving" @click="submitNewInvoice">Create Invoice (DRAFT)</Button>
        <Button variant="ghost" @click="createModal = false; newInvoice = blankInvoice()">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.inv-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.istat {
  background: var(--surface-raised, #f8f8f8);
  border: 1px solid var(--border, #e8e8e8);
  border-radius: 10px;
  padding: 12px 14px;
}
.istat-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: .5px;
  margin-bottom: 5px;
}
.istat-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text);
  line-height: 1.3;
}
.section-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: .06em;
  color: var(--muted);
  display: block;
  margin-bottom: 8px;
}
.inline-card {
  margin-top: 12px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
}

/* ── Line items grid ─────────────────────────────────────────────────────── */
.li-grid {
  display: grid;
  grid-template-columns: minmax(120px, 1.5fr) 200px 60px 90px 150px 160px 32px;
  gap: 0 6px;
  align-items: center;
}
.li-head {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: .05em;
  color: var(--muted);
  padding: 4px 2px 6px;
  border-bottom: 1px solid var(--border);
}
.li-row {
  padding: 4px 0;
  border-bottom: 1px solid var(--border-light, var(--border));
}
.li-row:last-of-type { border-bottom: none; }
.li-input {
  width: 100%;
  margin: 0;
  padding: 4px 6px;
  font-size: 13px;
  box-sizing: border-box;
}

/* ── Totals summary ──────────────────────────────────────────────────────── */
.li-totals {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 2px 16px;
  justify-items: end;
  padding: 10px 2px 0;
  border-top: 2px solid var(--border);
  margin-top: 4px;
  font-size: 13px;
}
.li-totals span { padding: 2px 0; }
</style>

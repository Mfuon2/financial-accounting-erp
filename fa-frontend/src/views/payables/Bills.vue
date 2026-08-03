<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { BILLS } from '@/data/index.js'
import { bills as billsApi, suppliers as suppliersApi, sourceDocs as sourceDocsApi, accounts as accountsApi } from '@/api/index.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast } from '@/composables/useToast.js'
import { useAuth } from '@/composables/useAuth.js'
import { useCategoryCache } from '@/composables/useCategoryCache.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import ChipFilter    from '@/components/primitives/ChipFilter.vue'
import AmountInput   from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { isDemo } = useAppMode()
const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')

const list        = ref([...BILLS])
const supplierList  = ref([])
const sourceDocList = ref([])
const accountOpts   = ref([])   // expense accounts for line item account picker
const drawer      = ref(null)
const search      = ref('')
const statusFilter = ref('ALL')
const payments    = ref([])
const loadingPay  = ref(false)
const saving      = ref(false)   // Create Bill modal
const approvingId = ref(null)    // tracks which bill is being approved
const voidingId   = ref(null)    // tracks which bill is being voided
const payingSaving = ref(false)  // Record Payment form
const dnSaving    = ref(false)   // Debit Note form
const runSaving   = ref(false)   // Payment Run modal

// ── Modal visibility toggles ──────────────────────────────────────────────────
const createModal     = ref(false)
const payModal        = ref(false)
const debitNoteModal  = ref(false)
const payRunModal     = ref(false)

// ── Payment form ──────────────────────────────────────────────────────────────
const payAmount  = ref('')
const payRef     = ref('')
const payMethod  = ref('BANK_TRANSFER')

// ── Debit note form ───────────────────────────────────────────────────────────
const dnAmount = ref('')
const dnReason = ref('')

// ── Payment run form ──────────────────────────────────────────────────────────
const runSelection = ref([])  // bill IDs selected for payment run
const runDate      = ref(new Date().toISOString().slice(0, 10))
const runMethod    = ref('BANK_TRANSFER')
const runRef       = ref('')

// ── New bill form ─────────────────────────────────────────────────────────────
const newBill = ref(blankBill())
function blankBill() {
  return {
    supplierId: null,
    supplierName: '',
    billDate: new Date().toISOString().slice(0, 10),
    dueDate: '',
    description: '',
    currencyCode: 'KES',
    sourceDocumentId: '',
    items: [{ description: '', quantity: 1, unitPrice: '', taxRate: 0, accountCode: '' }],
  }
}

// Payment methods are entity-managed dynamic data (CLAUDE.md §2) — see shared/categories on
// the backend and setup/Categories.vue for where they're created/edited. Cached at module
// level (useCategoryCache) so this view, Payments.vue and Invoices.vue share one fetch. Codes
// match the backend PaymentMethod enum's constant names (minus DEBIT_NOTE, which is a bill
// lifecycle state, not a selectable payment method) since they still deserialize into that enum.
const paymentMethodsCache = useCategoryCache('PAYMENT_METHOD')
const PAYMENT_METHOD_OPTIONS = computed(() => paymentMethodsCache.options.value.map(o => ({
  value: o.value, label: o.label,
})))
const statuses = ['ALL', 'DRAFT', 'APPROVED', 'PARTIALLY_PAID', 'PAID', 'VOID', 'DEBIT_NOTE']

onMounted(async () => {
  paymentMethodsCache.load(entityId.value)
  try {
    const data = await billsApi.list({ entityId: entityId.value, page: 0, size: 200 })
    const items = data?.content ?? data
    if (Array.isArray(items)) list.value = items
  } catch { /* stays on demo */ }
  try {
    const data = await suppliersApi.list({ entityId: entityId.value, size: 200 })
    const items = data?.content ?? data
    if (Array.isArray(items)) {
      supplierList.value = items.map(s => ({ value: s.id, label: s.name ?? s.supplierName ?? s.id }))
    }
  } catch { /* silently skip */ }
  try {
    const data = await sourceDocsApi.list({ entityId: entityId.value, size: 200 })
    const items = Array.isArray(data) ? data : (data?.content ?? [])
    sourceDocList.value = items
      .filter(d => d.status !== 'VOID')
      .map(d => {
        const ref    = d.referenceNumber ?? d.ref ?? d.id
        const type   = d.type ?? ''
        const party  = d.supplier ?? d.description ?? ''
        const amt    = d.amount ? ` · ${d.currencyCode ?? d.currency ?? ''} ${fmt(d.amount)}` : ''
        return { value: d.id, label: `${ref} — ${type}${party ? ' · ' + party : ''}${amt}` }
      })
  } catch { /* silently skip */ }
  try {
    const data = await accountsApi.list({ entityId: entityId.value, size: 500 })
    const items = Array.isArray(data) ? data : (data?.content ?? [])
    const EXPENSE_SUBTYPES = ['COGS','OPERATING_EXPENSES','DEPRECIATION','AMORTISATION','FINANCE_COST','TAX_EXPENSE']
    const SUBTYPE_LABEL = {
      COGS: 'Cost of Sales', OPERATING_EXPENSES: 'Operating Expenses',
      DEPRECIATION: 'Depreciation', AMORTISATION: 'Amortisation',
      FINANCE_COST: 'Finance Costs', TAX_EXPENSE: 'Tax Expense',
    }
    accountOpts.value = items
      .filter(a => a.isActive !== false && EXPENSE_SUBTYPES.includes(a.accountSubtype))
      .map(a => ({
        value: a.accountCode,
        label: `${a.accountCode} — ${a.accountName}`,
        group: SUBTYPE_LABEL[a.accountSubtype] ?? a.accountSubtype,
      }))
  } catch { /* silently skip */ }
})

// Load payment history when drawer opens on active bills
watch(drawer, async (bill) => {
  payments.value = []
  payModal.value = false
  debitNoteModal.value = false
  if (!bill?.id || bill.status === 'DRAFT' || bill.status === 'VOID' || bill.status === 'DEBIT_NOTE') return
  loadingPay.value = true
  try {
    if (!isDemo.value) {
      const data = await billsApi.payments(bill.id)
      payments.value = Array.isArray(data) ? data : (data?.data ?? [])
    }
  } catch { /* silently skip */ }
  finally { loadingPay.value = false }
})

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return list.value.filter(b => {
    const matchStatus = statusFilter.value === 'ALL' || b.status === statusFilter.value
    const matchSearch = !q || b.billNumber?.toLowerCase().includes(q) || b.supplierName?.toLowerCase().includes(q)
    return matchStatus && matchSearch
  })
})

const totalOutstanding = computed(() =>
  list.value.filter(b => b.status !== 'PAID' && b.status !== 'VOID' && b.status !== 'DEBIT_NOTE')
    .reduce((s, b) => s + ((b.totalAmount ?? 0) - (b.paidAmount ?? 0)), 0)
)

// Bills eligible for payment run
const payableBills = computed(() =>
  list.value.filter(b => b.status === 'APPROVED' || b.status === 'PARTIALLY_PAID')
)
const runTotal = computed(() =>
  payableBills.value
    .filter(b => runSelection.value.includes(b.id))
    .reduce((s, b) => s + ((b.totalAmount ?? 0) - (b.paidAmount ?? 0)), 0)
)
function toggleRunSelection(id) {
  const idx = runSelection.value.indexOf(id)
  if (idx === -1) runSelection.value.push(id)
  else runSelection.value.splice(idx, 1)
}
function selectAllPayable() {
  runSelection.value = payableBills.value.map(b => b.id)
}

// ── New Bill ──────────────────────────────────────────────────────────────────
function addLine() {
  newBill.value.items.push({ description: '', quantity: 1, unitPrice: '', taxRate: 0, accountCode: '' })
}
function removeLine(i) {
  if (newBill.value.items.length > 1) newBill.value.items.splice(i, 1)
}
const newBillTotal = computed(() =>
  newBill.value.items.reduce((s, it) => {
    const sub = (parseFloat(it.quantity) || 0) * (parseFloat(it.unitPrice) || 0)
    return s + sub * (1 + (parseFloat(it.taxRate) || 0) / 100)
  }, 0)
)

async function submitNewBill() {
  if (saving.value) return                          // hard guard — blocks re-entry before DOM disables the button
  if (!newBill.value.supplierName || !newBill.value.billDate) {
    toast.error('Supplier name and bill date are required.')
    return
  }
  saving.value = true                               // set synchronously — any subsequent call hits the guard above
  const payload = {
    entityId: entityId.value,
    supplierId: newBill.value.supplierId || undefined,
    supplierName: newBill.value.supplierName,
    billDate: newBill.value.billDate,
    dueDate: newBill.value.dueDate || undefined,
    currencyCode: newBill.value.currencyCode,
    description: newBill.value.description || undefined,
    sourceDocumentId: newBill.value.sourceDocumentId || undefined,
    items: newBill.value.items
      .filter(it => it.description && it.unitPrice)
      .map(it => ({
        description: it.description,
        quantity: parseFloat(it.quantity) || 1,
        unitPrice: parseFloat(it.unitPrice),
        taxRate: (parseFloat(it.taxRate) || 0) / 100,
        accountCode: it.accountCode || undefined,
      })),
  }
  try {
    const resp = await billsApi.create(payload)
    list.value.unshift(resp)
    createModal.value = false
    newBill.value = blankBill()
    toast.success('Bill created.')
  } catch { /* handled */ }
  finally { saving.value = false }
}

// ── Approve / Void ────────────────────────────────────────────────────────────
async function approveBill(id) {
  approvingId.value = id
  try {
    const updated = await billsApi.approve(id)
    applyUpdate(id, updated ?? { status: 'APPROVED' })
    toast.success('Bill approved and posted to GL.')
  } catch { /* handled */ }
  finally { approvingId.value = null }
}

async function voidBill(id) {
  voidingId.value = id
  try {
    const updated = await billsApi.void(id)
    applyUpdate(id, updated ?? { status: 'VOID' })
    toast.success('Bill voided.')
  } catch { /* handled */ }
  finally { voidingId.value = null }
}

// ── Single payment ────────────────────────────────────────────────────────────
async function submitPayment() {
  if (!drawer.value || !payAmount.value) return
  const amount = parseFloat(payAmount.value)
  if (isNaN(amount) || amount <= 0) { toast.error('Enter a valid amount.'); return }
  payingSaving.value = true
  try {
    const pmt = await billsApi.recordPayment(drawer.value.id, {
      paymentDate: new Date().toISOString().slice(0, 10),
      amount,
      reference: payRef.value || undefined,
      paymentMethod: payMethod.value || undefined,
    })
    const newPaid   = (drawer.value.paidAmount ?? 0) + amount
    const newStatus = newPaid >= (drawer.value.totalAmount ?? 0) ? 'PAID' : 'PARTIALLY_PAID'
    applyUpdate(drawer.value.id, { paidAmount: newPaid, status: newStatus })
    if (pmt) payments.value.push(pmt)
    payModal.value = false; payAmount.value = ''; payRef.value = ''
    toast.success(`Payment of KES ${fmt(amount)} recorded.`)
  } catch { /* handled */ }
  finally { payingSaving.value = false }
}

// ── Debit note ────────────────────────────────────────────────────────────────
async function submitDebitNote() {
  if (!drawer.value) return
  const amount = dnAmount.value ? parseFloat(dnAmount.value) : undefined
  if (amount !== undefined && (isNaN(amount) || amount <= 0)) {
    toast.error('Enter a valid debit note amount.'); return
  }
  dnSaving.value = true
  try {
    const dn = await billsApi.debitNote(drawer.value.id, {
      amount: amount || undefined,
      reason: dnReason.value || undefined,
    })
    list.value.unshift(dn)
    // Reduce original bill's outstanding
    const credited = amount ?? outstandingAmt(drawer.value)
    const newPaid  = (drawer.value.paidAmount ?? 0) + credited
    const newStatus = newPaid >= (drawer.value.totalAmount ?? 0) ? 'PAID' : 'PARTIALLY_PAID'
    applyUpdate(drawer.value.id, { paidAmount: newPaid, status: newStatus })
    debitNoteModal.value = false; dnAmount.value = ''; dnReason.value = ''
    toast.success(`Debit note raised for KES ${fmt(credited)}.`)
  } catch { /* handled */ }
  finally { dnSaving.value = false }
}

// ── Payment run ───────────────────────────────────────────────────────────────
async function submitPaymentRun() {
  if (!runSelection.value.length) { toast.error('Select at least one bill.'); return }
  runSaving.value = true
  try {
    await billsApi.paymentRun({
      entityId: entityId.value,
      billIds: runSelection.value,
      paymentDate: runDate.value,
      paymentMethod: runMethod.value || undefined,
      reference: runRef.value || undefined,
    })
    // Mark selected bills as PAID
    runSelection.value.forEach(id => applyUpdate(id, { status: 'PAID', paidAmount: list.value.find(b => b.id === id)?.totalAmount }))
    payRunModal.value = false; runSelection.value = []; runRef.value = ''
    toast.success(`Payment run processed — ${runSelection.value.length ?? 'all selected'} bills marked PAID.`)
  } catch { /* handled */ }
  finally { runSaving.value = false }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function applyUpdate(id, patch) {
  const idx = list.value.findIndex(b => b.id === id)
  if (idx !== -1) list.value[idx] = { ...list.value[idx], ...patch }
  if (drawer.value?.id === id) drawer.value = { ...drawer.value, ...patch }
}
function outstandingAmt(b) { return (b.totalAmount ?? 0) - (b.paidAmount ?? 0) }
function statusColor(status) {
  const map = { DRAFT: 'outline', APPROVED: 'info', PARTIALLY_PAID: 'submitted', PAID: 'approved', VOID: 'void', DEBIT_NOTE: 'rejected' }
  return map[status] ?? 'outline'
}
function isOverdue(b) {
  return b.status !== 'PAID' && b.status !== 'VOID' && b.dueDate && new Date(b.dueDate) < new Date()
}
</script>

<template>
  <div class="page">
    <PageHeader title="Vendor Bills" :meta="`${list.length} bills · KES ${fmt(totalOutstanding)} outstanding`">
      <Button variant="ghost" icon="card" @click="payRunModal = true; runSelection = []">Payment run</Button>
      <Button variant="primary" icon="plus" @click="createModal = true">New bill</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search">
        <ChipFilter v-for="s in statuses" :key="s" :active="statusFilter === s" @click="statusFilter = s">
          {{ s.replace('_', ' ') }}
        </ChipFilter>
      </TableToolbar>

      <div class="card">
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
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in filtered" :key="b.id" class="row-link" @click="drawer = b">
              <td>
                <code>{{ b.billNumber }}</code>
                <span v-if="b.isDebitNote" style="font-size:10px;color:var(--info);margin-left:4px">DN</span>
              </td>
              <td>{{ b.supplierName }}</td>
              <td>{{ fmtDate(b.billDate) }}</td>
              <td>
                <span :style="isOverdue(b) ? 'color:var(--danger);font-weight:600' : ''">
                  {{ fmtDate(b.dueDate) }}
                </span>
              </td>
              <td class="num mono">{{ b.currencyCode }} {{ fmt(b.totalAmount) }}</td>
              <td class="num mono">{{ fmt(b.paidAmount ?? 0) }}</td>
              <td class="num mono" :style="outstandingAmt(b) > 0 ? 'color:var(--danger)' : ''">
                {{ fmt(outstandingAmt(b)) }}
              </td>
              <td><Badge :status="statusColor(b.status)" :dot="false">{{ b.status.replace('_', ' ') }}</Badge></td>
              <td @click.stop><IconBtn icon="dots" @click="drawer = b" /></td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="bills" />
    </div>

    <!-- ── Bill Detail Drawer ───────────────────────────────────────────────── -->
    <Modal :open="drawer !== null" :title="drawer?.billNumber" :subtitle="drawer?.supplierName" :width="820" @close="drawer = null">
      <template v-if="drawer">
        <div class="form-grid cols-3">
          <div class="field">
            <label>Bill Date</label>
            <input class="input" type="text" :value="fmtDate(drawer.billDate)" readonly />
          </div>
          <div class="field">
            <label>Due Date</label>
            <input class="input" :style="isOverdue(drawer) ? 'color:var(--danger)' : ''" type="text" :value="fmtDate(drawer.dueDate)" readonly />
          </div>
          <div class="field">
            <label>Status</label>
            <input class="input" type="text" :value="drawer.status.replace('_', ' ')" readonly />
          </div>
          <div class="field">
            <label>Total</label>
            <input class="input mono" type="text" :value="`${drawer.currencyCode} ${fmt(drawer.totalAmount)}`" readonly />
          </div>
          <div class="field">
            <label>Paid / Credited</label>
            <input class="input mono" type="text" :value="fmt(drawer.paidAmount ?? 0)" readonly />
          </div>
          <div class="field">
            <label>Outstanding</label>
            <input class="input mono" type="text" :value="fmt(outstandingAmt(drawer))"
              :style="outstandingAmt(drawer) > 0 ? 'color:var(--danger)' : ''" readonly />
          </div>
          <div v-if="drawer.sourceDocumentId" class="field" style="grid-column:1/-1">
            <label>Source Document</label>
            <input class="input mono" type="text"
              :value="sourceDocList.find(d => d.value === drawer.sourceDocumentId)?.label ?? drawer.sourceDocumentId"
              readonly style="font-size:12px" />
          </div>
        </div>

        <div v-if="drawer.description" style="font-size:13px;color:var(--muted);margin-bottom:16px">{{ drawer.description }}</div>

        <!-- Line items -->
        <div v-if="drawer.items?.length" style="margin-bottom:20px">
          <div class="section-label">Line Items</div>
          <table class="tbl" style="font-size:13px">
            <thead>
              <tr>
                <th>Description</th>
                <th>Account</th>
                <th class="num">Qty</th>
                <th class="num">Unit Price</th>
                <th class="num">Tax %</th>
                <th class="num">Line Total</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, i) in drawer.items" :key="i">
                <td>{{ item.description }}</td>
                <td><code style="font-size:11px">{{ item.accountCode ?? '—' }}</code></td>
                <td class="num mono">{{ fmt(item.quantity) }}</td>
                <td class="num mono">{{ fmt(item.unitPrice) }}</td>
                <td class="num">{{ item.taxRate ? `${(item.taxRate * 100).toFixed(0)}%` : '—' }}</td>
                <td class="num mono">{{ fmt(item.lineTotal) }}</td>
              </tr>
            </tbody>
            <tfoot>
              <tr style="font-weight:700">
                <td colspan="5" class="num" style="font-size:12px;color:var(--muted)">Total</td>
                <td class="num mono">{{ drawer.currencyCode }} {{ fmt(drawer.totalAmount) }}</td>
              </tr>
            </tfoot>
          </table>
        </div>

        <!-- Payment history -->
        <div v-if="['APPROVED','PARTIALLY_PAID','PAID'].includes(drawer.status)" style="margin-bottom:20px">
          <div class="section-label">Payment History</div>
          <div v-if="loadingPay" style="font-size:13px;color:var(--muted)">Loading…</div>
          <div v-else-if="payments.length === 0" style="font-size:13px;color:var(--muted)">No payments recorded yet.</div>
          <table v-else class="tbl" style="font-size:13px">
            <thead>
              <tr>
                <th>Date</th>
                <th>Reference</th>
                <th>Method</th>
                <th>Run</th>
                <th class="num">Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in payments" :key="p.id">
                <td>{{ fmtDate(p.paymentDate) }}</td>
                <td>{{ p.reference ?? '—' }}</td>
                <td>{{ p.paymentMethod?.replace('_', ' ') ?? '—' }}</td>
                <td style="font-size:11px;color:var(--muted)">{{ p.paymentRunId ? 'Batch run' : 'Manual' }}</td>
                <td class="num mono">{{ fmt(p.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- Single payment form -->
        <div v-if="payModal" class="inline-card">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">Record Payment</div>
          <div class="form-grid cols-2">
            <div class="field">
              <label>Amount ({{ drawer.currencyCode }})</label>
              <AmountInput class="input mono" v-model="payAmount" />
            </div>
            <div class="field">
              <label>Payment Method</label>
              <SearchableSelect
                v-model="payMethod"
                :options="PAYMENT_METHOD_OPTIONS"
                placeholder="Select method"
              />
            </div>
            <div class="field" style="grid-column:1/-1">
              <label>Reference</label>
              <input class="input" type="text" v-model="payRef" placeholder="EFT / Cheque / MPESA ref" />
            </div>
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button variant="primary" icon="approve" :loading="payingSaving" @click="submitPayment">Confirm payment</Button>
            <Button variant="ghost" @click="payModal = false">Cancel</Button>
          </div>
        </div>

        <!-- Debit note form -->
        <div v-if="debitNoteModal" class="inline-card">
          <div style="font-weight:600;font-size:14px;margin-bottom:12px">Raise Debit Note (Purchase Credit)</div>
          <div class="form-grid cols-2">
            <div class="field">
              <label>Amount ({{ drawer.currencyCode }}) — leave blank for full outstanding</label>
              <AmountInput class="input mono" v-model="dnAmount"
                :placeholder="`max ${fmt(outstandingAmt(drawer))}`" />
            </div>
            <div class="field">
              <label>Reason</label>
              <input class="input" type="text" v-model="dnReason" placeholder="Goods returned / overcharge" />
            </div>
          </div>
          <div style="display:flex;gap:8px;margin-top:12px">
            <Button variant="primary" icon="reject" :loading="dnSaving" @click="submitDebitNote">Raise Debit Note</Button>
            <Button variant="ghost" @click="debitNoteModal = false">Cancel</Button>
          </div>
        </div>
      </template>

      <template #footer>
        <template v-if="drawer?.status === 'DRAFT'">
          <Button variant="primary" icon="approve" :loading="approvingId === drawer.id" @click="approveBill(drawer.id)">Approve</Button>
          <Button variant="ghost" icon="reject" :loading="voidingId === drawer.id" @click="voidBill(drawer.id)">Void</Button>
        </template>
        <template v-else-if="drawer?.status === 'APPROVED' || drawer?.status === 'PARTIALLY_PAID'">
          <Button variant="primary" icon="card" @click="payModal = !payModal; debitNoteModal = false">
            {{ payModal ? 'Cancel' : 'Record payment' }}
          </Button>
          <Button variant="ghost" icon="reject" @click="debitNoteModal = !debitNoteModal; payModal = false">
            {{ debitNoteModal ? 'Cancel' : 'Debit note' }}
          </Button>
          <Button variant="ghost" icon="reject" :loading="voidingId === drawer.id" style="color:var(--danger)" @click="voidBill(drawer.id)">Void</Button>
        </template>
        <Button variant="ghost" @click="drawer = null">Close</Button>
      </template>
    </Modal>

    <!-- ── New Bill Modal ──────────────────────────────────────────────────── -->
    <Modal :open="createModal" title="New Vendor Bill" :width="900" @close="createModal = false; newBill = blankBill()">
      <div class="form-grid cols-2">
        <div class="field" style="grid-column:1/-1">
          <label>Supplier <span style="color:var(--danger)">*</span></label>
          <SearchableSelect
            v-if="supplierList.length"
            v-model="newBill.supplierId"
            :options="supplierList"
            placeholder="Search supplier…"
            @update:modelValue="id => { const s = supplierList.find(x => x.value === id); if (s) newBill.supplierName = s.label }"
          />
          <input
            v-else
            class="input"
            type="text"
            v-model="newBill.supplierName"
            placeholder="Widget Manufacturing Ltd"
          />
        </div>
        <div class="field">
          <label>Bill Date <span style="color:var(--danger)">*</span></label>
          <input class="input" type="date" v-model="newBill.billDate" />
        </div>
        <div class="field">
          <label>Due Date <span style="color:var(--muted);font-size:11px">(auto from supplier terms)</span></label>
          <input class="input" type="date" v-model="newBill.dueDate" />
        </div>
        <div class="field">
          <label>Currency</label>
          <SearchableSelect
            v-model="newBill.currencyCode"
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
          <label>Description</label>
          <input class="input" type="text" v-model="newBill.description" placeholder="Office supplies May 2026" />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Source Document <span style="color:var(--muted);font-size:11px">(optional — attach an uploaded document)</span></label>
          <SearchableSelect
            v-if="sourceDocList.length"
            v-model="newBill.sourceDocumentId"
            :options="sourceDocList"
            placeholder="Search uploaded source documents…"
            search-placeholder="Type ref, type or supplier…"
          />
          <input
            v-else
            class="input mono"
            type="text"
            v-model="newBill.sourceDocumentId"
            placeholder="Paste source document UUID"
            style="font-size:12px"
          />
        </div>
      </div>

      <div style="margin-top:20px">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
          <span class="section-label">Line Items</span>
          <Button variant="ghost" icon="plus" style="font-size:12px;padding:4px 8px" @click="addLine">Add line</Button>
        </div>

        <!-- Header row -->
        <div class="li-grid li-head">
          <div>Description</div>
          <div>Account</div>
          <div class="num">Qty</div>
          <div class="num">Unit Price</div>
          <div class="num">Tax %</div>
          <div class="num">Total</div>
          <div></div>
        </div>

        <!-- Item rows -->
        <div v-for="(item, i) in newBill.items" :key="i" class="li-grid li-row">
          <div>
            <input class="input li-input" type="text" v-model="item.description" placeholder="Item description" />
          </div>
          <div>
            <SearchableSelect
              v-if="accountOpts.length"
              v-model="item.accountCode"
              :options="accountOpts"
              placeholder="Select account…"
              search-placeholder="Search code or name…"
              :compact="true"
              :mono="true"
            />
            <input v-else class="input li-input mono" type="text" v-model="item.accountCode" placeholder="5-2000" />
          </div>
          <div>
            <AmountInput class="input li-input mono" v-model="item.quantity" style="text-align:right" />
          </div>
          <div>
            <AmountInput class="input li-input mono" v-model="item.unitPrice" placeholder="0.00" style="text-align:right" />
          </div>
          <div>
            <input class="input li-input mono" type="number" v-model="item.taxRate" min="0" max="100" step="1" placeholder="0" style="text-align:right" />
          </div>
          <div class="num mono li-total">
            {{ fmt(((parseFloat(item.quantity)||0)*(parseFloat(item.unitPrice)||0))*(1+(parseFloat(item.taxRate)||0)/100)) }}
          </div>
          <div style="display:flex;align-items:center;justify-content:center">
            <IconBtn icon="reject" style="color:var(--danger)" @click="removeLine(i)" />
          </div>
        </div>

        <!-- Total row -->
        <div class="li-grid li-footer">
          <div style="grid-column:1/6;text-align:right;font-size:12px;color:var(--muted)">Total</div>
          <div class="num mono" style="font-weight:700;font-size:14px">{{ newBill.currencyCode }} {{ fmt(newBillTotal) }}</div>
          <div></div>
        </div>
      </div>

      <template #footer>
        <Button variant="primary" icon="approve" :loading="saving" @click="submitNewBill">Create Bill</Button>
        <Button variant="ghost" @click="createModal = false; newBill = blankBill()">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Payment Run Modal ───────────────────────────────────────────────── -->
    <Modal :open="payRunModal" title="Vendor Payment Run" subtitle="Select bills to pay in batch" :width="760" @close="payRunModal = false; runSelection = []">
      <div class="form-grid cols-3" style="margin-bottom:20px">
        <div class="field">
          <label>Payment Date</label>
          <input class="input" type="date" v-model="runDate" />
        </div>
        <div class="field">
          <label>Payment Method</label>
          <SearchableSelect
            v-model="runMethod"
            :options="PAYMENT_METHOD_OPTIONS"
            placeholder="Select method"
          />
        </div>
        <div class="field">
          <label>Reference</label>
          <input class="input" type="text" v-model="runRef" placeholder="Batch ref / cheque run" />
        </div>
      </div>

      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
        <span class="section-label">Select Bills to Pay</span>
        <Button variant="ghost" style="font-size:12px;padding:4px 8px" @click="selectAllPayable">Select all</Button>
      </div>

      <div v-if="payableBills.length === 0" style="font-size:13px;color:var(--muted);padding:12px 0">
        No payable bills (APPROVED or PARTIALLY_PAID) found.
      </div>

      <table v-else class="tbl" style="font-size:13px">
        <thead>
          <tr>
            <th style="width:36px"></th>
            <th>Bill #</th>
            <th>Supplier</th>
            <th>Due Date</th>
            <th class="num">Outstanding</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in payableBills" :key="b.id" class="row-link" @click="toggleRunSelection(b.id)">
            <td @click.stop>
              <input type="checkbox" :checked="runSelection.includes(b.id)" @change="toggleRunSelection(b.id)" style="cursor:pointer" />
            </td>
            <td><code>{{ b.billNumber }}</code></td>
            <td>{{ b.supplierName }}</td>
            <td :style="isOverdue(b) ? 'color:var(--danger);font-weight:600' : ''">{{ fmtDate(b.dueDate) }}</td>
            <td class="num mono" style="color:var(--danger)">{{ b.currencyCode }} {{ fmt(outstandingAmt(b)) }}</td>
          </tr>
        </tbody>
        <tfoot v-if="runSelection.length">
          <tr style="font-weight:700;font-size:14px">
            <td colspan="4" class="num" style="font-size:12px;color:var(--muted)">
              {{ runSelection.length }} bill(s) selected
            </td>
            <td class="num mono" style="color:var(--danger)">KES {{ fmt(runTotal) }}</td>
          </tr>
        </tfoot>
      </table>

      <template #footer>
        <Button variant="primary" icon="card" :loading="runSaving" :disabled="!runSelection.length" @click="submitPaymentRun">
          Process Payment Run ({{ runSelection.length }} bills)
        </Button>
        <Button variant="ghost" @click="payRunModal = false; runSelection = []">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
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

/* ── Line items grid (replaces overflow table) ───────────────────────────── */
.li-grid {
  display: grid;
  grid-template-columns: minmax(120px, 1fr) 220px 64px 90px 58px 90px 32px;
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
.li-footer {
  padding: 8px 2px 0;
  border-top: 2px solid var(--border);
  margin-top: 2px;
}
.li-input {
  width: 100%;
  margin: 0;
  padding: 4px 6px;
  font-size: 13px;
  box-sizing: border-box;
}
.li-total {
  font-weight: 600;
  font-size: 13px;
  padding-right: 2px;
}
</style>

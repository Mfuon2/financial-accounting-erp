<script setup>
import { ref, computed, onMounted } from 'vue'
import { isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { invoices as invoicesApi, customers as customersApi, journals as journalsApi, creditNotes as creditNotesApi } from '@/api/index.js'
import { useToast } from '@/composables/useToast.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import AmountInput from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'demo')

// ── State ────────────────────────────────────────────────────────────────────
const list    = ref([])
const loading = ref(false)
const error   = ref(null)
const search  = ref('')
const drawer  = ref(null)  // credit note detail modal
const showNew = ref(false) // create credit note modal

// ── Lookup maps (id → label) ─────────────────────────────────────────────────
const invoiceNumMap   = ref({})  // invoiceId → invoiceNumber
const customerNameMap = ref({})  // customerId → customerName

async function loadLookupMaps() {
  if (isDemo.value) return
  try {
    const invRes = await invoicesApi.list({ entityId: entityId.value, size: 500 })
    const invItems = invRes?.content ?? (Array.isArray(invRes) ? invRes : [])
    const numMap = {}
    invItems.forEach(i => { if (i.id && i.invoiceNumber) numMap[i.id] = i.invoiceNumber })
    invoiceNumMap.value = numMap
  } catch { /* non-critical */ }
  try {
    const custRes = await customersApi.list({ entityId: entityId.value, size: 500 })
    const custItems = custRes?.content ?? (Array.isArray(custRes) ? custRes : [])
    const nameMap = {}
    custItems.forEach(c => { if (c.id) nameMap[c.id] = c.name ?? c.customerName ?? c.id })
    customerNameMap.value = nameMap
  } catch { /* non-critical */ }
}

function resolveInvoiceNum(cn) {
  if (cn.originalInvoiceRef ?? cn.invoice) return cn.originalInvoiceRef ?? cn.invoice
  if (cn.invoiceId && invoiceNumMap.value[cn.invoiceId]) return invoiceNumMap.value[cn.invoiceId]
  if (cn.invoiceId) return cn.invoiceId
  return null
}

function resolveCustomerName(cn) {
  if (cn.customerName ?? cn.customer) return cn.customerName ?? cn.customer
  if (cn.customerId && customerNameMap.value[cn.customerId]) return customerNameMap.value[cn.customerId]
  return '—'
}

// resolved detail fields
const drawerCustomerName  = ref('—')
const drawerOriginalInv   = ref('—')
const drawerJeReference   = ref('—')

function parseOriginalInvoice(notes) {
  if (!notes) return '—'
  const m = notes.match(/Credit note for ([^:]+):/)
  return m ? m[1].trim() : '—'
}

async function openDrawer(cn) {
  drawer.value = cn
  drawerCustomerName.value = '—'
  drawerOriginalInv.value  = parseOriginalInvoice(cn.notes)
  drawerJeReference.value  = '—'

  if (!isDemo.value) {
    if (cn.customerId) {
      try {
        const c = await customersApi.get(cn.customerId)
        drawerCustomerName.value = c?.name ?? c?.customerName ?? '—'
      } catch { }
    }
    if (cn.journalEntryId) {
      try {
        const je = await journalsApi.get(cn.journalEntryId)
        drawerJeReference.value = je?.reference ?? je?.entryNumber ?? cn.journalEntryId
      } catch { }
    }
  }
}

// ── Load credit notes ────────────────────────────────────────────────────────
async function loadCreditNotes() {
  loading.value = true
  error.value   = null
  try {
    // Standalone /api/v1/credit-notes resource (backed by InvoiceService's
    // status=CREDIT_NOTE filter) rather than filtering the general invoices list.
    const res = await creditNotesApi.list({ entityId: entityId.value })
    if (res?.content) list.value = res.content
    else if (Array.isArray(res)) list.value = res
    else list.value = []
  } catch (e) {
    if (!list.value.length) error.value = e?.message ?? 'Failed to load credit notes.'
    if (list.value.length) toast.warn('Could not refresh credit notes.')
  } finally {
    loading.value = false
  }
}

onMounted(() => { loadCreditNotes(); loadLookupMaps() })

// ── Filtered list ────────────────────────────────────────────────────────────
const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return list.value
  return list.value.filter(cn => {
    const num  = (cn.invoiceNumber ?? cn.ref ?? '').toLowerCase()
    const cust = (cn.customerName  ?? cn.customer ?? '').toLowerCase()
    const orig = (cn.originalInvoiceRef ?? cn.invoice ?? '').toLowerCase()
    return num.includes(q) || cust.includes(q) || orig.includes(q)
  })
})

// ── Approved invoices for the "create" picker ────────────────────────────────
const approvedInvoices = ref([])

async function loadApprovedInvoices() {
  if (isDemo.value) {
    // Use demo invoices that are in SENT/APPROVED/PARTIALLY_PAID state
    const res = await invoicesApi.list({ entityId: entityId.value })
    const rows = res?.content ?? (Array.isArray(res) ? res : [])
    approvedInvoices.value = rows.filter(i =>
      ['SENT', 'APPROVED', 'PARTIALLY_PAID', 'POSTED'].includes(i.status ?? i.invoiceStatus)
    )
  } else {
    try {
      const res = await invoicesApi.list({ entityId: entityId.value, status: 'SENT', size: 100 })
      approvedInvoices.value = res?.content ?? []
    } catch {
      approvedInvoices.value = []
    }
  }
}

// ── New credit note form ─────────────────────────────────────────────────────
const newForm = ref({
  invoiceId:        '',
  creditNoteAmount: '',
  reason:           '',
})
const saving = ref(false)

const invoiceOptions = computed(() =>
  approvedInvoices.value.map(inv => ({
    value: inv.id,
    label: `${inv.invoiceNumber ?? inv.ref} — ${inv.customerName ?? inv.customer ?? ''} (${inv.currencyCode ?? inv.currency} ${fmt(inv.totalAmount ?? inv.total ?? 0)})`,
  }))
)

const selectedInvoice = computed(() =>
  approvedInvoices.value.find(i => i.id === newForm.value.invoiceId)
)

function openNew() {
  newForm.value = { invoiceId: '', creditNoteAmount: '', reason: '' }
  showNew.value = true
  loadApprovedInvoices()
}

async function submitCreditNote() {
  if (!newForm.value.invoiceId) {
    toast.warn('Select the invoice this credit note is against.')
    return
  }
  if (!newForm.value.creditNoteAmount || Number(newForm.value.creditNoteAmount) <= 0) {
    toast.warn('Credit note amount must be greater than zero.')
    return
  }
  if (!newForm.value.reason.trim()) {
    toast.warn('A reason is required for the credit note.')
    return
  }

  saving.value = true
  try {
    await invoicesApi.createCreditNote(newForm.value.invoiceId, {
      creditNoteAmount: Number(newForm.value.creditNoteAmount),
      reason:           newForm.value.reason.trim(),
    })
    toast.success('Credit note created successfully.')
    showNew.value = false
    await loadCreditNotes()
  } catch (e) {
    toast.error(e?.message ?? 'Failed to create credit note.')
  } finally {
    saving.value = false
  }
}

// ── Status color helper ───────────────────────────────────────────────────────
function cnStatus(cn) {
  const s = (cn.status ?? '').toLowerCase()
  if (s === 'credit_note' || s === 'posted') return 'posted'
  if (s === 'draft')  return 'draft'
  if (s === 'void')   return 'void'
  return 'outline'
}
</script>

<template>
  <div class="page">
    <!-- ── Header ────────────────────────────────────────────────────────── -->
    <PageHeader
      title="Credit Notes"
      :meta="list.length ? `${list.length} credit note${list.length !== 1 ? 's' : ''}` : 'Credit Notes'"
    >
      <Button
        variant="ghost"
        icon="rotate"
        :loading="loading"
        @click="loadCreditNotes"
      >
        Refresh
      </Button>
      <Button variant="primary" icon="plus" @click="openNew">New credit note</Button>
    </PageHeader>

    <div class="page-section stack">

      <!-- ── Loading ───────────────────────────────────────────────────── -->
      <div v-if="loading && !list.length" class="empty-state">
        <div class="empty-icon" style="opacity:.45">⏳</div>
        <div class="empty-title">Loading credit notes…</div>
      </div>

      <!-- ── Error ─────────────────────────────────────────────────────── -->
      <div v-else-if="error && !list.length" class="empty-state">
        <div class="empty-title" style="color:var(--danger)">{{ error }}</div>
        <Button variant="default" icon="rotate" style="margin-top:12px" @click="loadCreditNotes">
          Try again
        </Button>
      </div>

      <template v-else>
        <!-- ── Search toolbar ─────────────────────────────────────────── -->
        <TableToolbar v-model:search="search" />

        <!-- ── Table ──────────────────────────────────────────────────── -->
        <div class="card">
          <table class="tbl">
            <thead>
              <tr>
                <th>Ref</th>
                <th>Against invoice</th>
                <th>Customer</th>
                <th>Date</th>
                <th class="num">Amount</th>
                <th>Reason</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="cn in filtered"
                :key="cn.id"
                class="row-link"
                @click="openDrawer(cn)"
              >
                <td><code>{{ cn.invoiceNumber ?? cn.ref ?? cn.id }}</code></td>
                <td>
                  <code v-if="resolveInvoiceNum(cn)">{{ resolveInvoiceNum(cn) }}</code>
                  <span v-else class="muted" style="font-style:italic">—</span>
                </td>
                <td>{{ resolveCustomerName(cn) }}</td>
                <td>{{ fmtDate(cn.issueDate ?? cn.date) }}</td>
                <td class="num mono">
                  {{ cn.currencyCode ?? cn.currency ?? 'KES' }}
                  {{ fmt(cn.totalAmount ?? cn.amount ?? 0) }}
                </td>
                <td style="max-width:220px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                  {{ cn.notes ?? cn.reason ?? '—' }}
                </td>
                <td>
                  <Badge :status="cnStatus(cn)" :dot="false">
                    {{ cn.status ?? '—' }}
                  </Badge>
                </td>
                <td @click.stop>
                  <IconBtn icon="dots" @click="openDrawer(cn)" />
                </td>
              </tr>
              <tr v-if="!filtered.length && !loading">
                <td colspan="8" class="muted" style="text-align:center;padding:32px;font-style:italic">
                  {{ search ? `No credit notes matching "${search}"` : 'No credit notes yet.' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <TableFooter :total="filtered.length" label="credit notes" />
      </template>
    </div>

    <!-- ── Detail drawer ─────────────────────────────────────────────────── -->
    <Modal
      :open="drawer !== null"
      :title="drawer?.invoiceNumber ?? drawer?.ref ?? 'Credit Note'"
      :subtitle="drawer
        ? `${drawerCustomerName} · ${drawer.currencyCode ?? drawer.currency ?? 'KES'} ${fmt(drawer.totalAmount ?? drawer.amount ?? 0)}`
        : ''"
      :width="700"
      @close="drawer = null"
    >
      <template v-if="drawer">
        <div class="kpi-grid" style="grid-template-columns:repeat(3,1fr)">
          <div class="kpi-card">
            <div class="kpi-label">Date</div>
            <div class="kpi-value">{{ fmtDate(drawer.issueDate ?? drawer.date) }}</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Amount</div>
            <div class="kpi-value mono">
              {{ drawer.currencyCode ?? drawer.currency ?? 'KES' }} {{ fmt(drawer.totalAmount ?? drawer.amount ?? 0) }}
            </div>
          </div>
          <div class="kpi-card">
            <div class="kpi-label">Status</div>
            <div class="kpi-value">
              <Badge :status="cnStatus(drawer)" :dot="false">{{ drawer.status ?? '—' }}</Badge>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">Details</div>
          <table class="tbl">
            <tbody>
              <tr>
                <td style="color:var(--muted);width:160px">Against invoice</td>
                <td><code>{{ drawerOriginalInv }}</code></td>
              </tr>
              <tr>
                <td style="color:var(--muted)">Customer</td>
                <td>{{ drawerCustomerName }}</td>
              </tr>
              <tr>
                <td style="color:var(--muted)">Reason</td>
                <td>{{ drawer.notes ?? drawer.reason ?? '—' }}</td>
              </tr>
              <tr v-if="drawer.journalEntryId">
                <td style="color:var(--muted)">Journal entry</td>
                <td><code>{{ drawerJeReference }}</code></td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template #footer>
        <Button variant="ghost" icon="download">Download PDF</Button>
        <Button variant="ghost" @click="drawer = null">Close</Button>
      </template>
    </Modal>

    <!-- ── New credit note modal ──────────────────────────────────────────── -->
    <Modal
      :open="showNew"
      title="New Credit Note"
      subtitle="Issue a credit note against a posted invoice"
      :width="600"
      @close="showNew = false"
    >
      <div class="form-grid cols-1" style="gap:16px">
        <div class="field">
          <label>Invoice <span style="color:var(--danger)">*</span></label>
          <SearchableSelect
            v-model="newForm.invoiceId"
            :options="invoiceOptions"
            placeholder="Select invoice to credit…"
          />
          <div
            v-if="selectedInvoice"
            style="margin-top:6px;font-size:12px;color:var(--muted)"
          >
            Outstanding:
            <strong>
              {{ selectedInvoice.currencyCode ?? selectedInvoice.currency ?? 'KES' }}
              {{ fmt(selectedInvoice.outstandingAmount ?? selectedInvoice.balance ?? 0) }}
            </strong>
          </div>
        </div>

        <div class="field">
          <label>Credit note amount <span style="color:var(--danger)">*</span></label>
          <AmountInput
            v-model="newForm.creditNoteAmount"
            class="input mono"
            placeholder="0.00"
            style="width:100%"
          />
          <div style="margin-top:4px;font-size:12px;color:var(--muted)">
            Enter a positive amount — the system will apply the debit sign automatically.
          </div>
        </div>

        <div class="field">
          <label>Reason <span style="color:var(--danger)">*</span></label>
          <textarea
            v-model="newForm.reason"
            class="input"
            rows="3"
            placeholder="e.g. Customer return — defective goods"
            style="width:100%;resize:vertical"
          />
        </div>
      </div>

      <template #footer>
        <Button
          variant="primary"
          icon="doc"
          :loading="saving"
          @click="submitCreditNote"
        >
          Create credit note
        </Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
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
}
.kpi-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  text-align: center;
}
.empty-icon  { font-size: 32px; margin-bottom: 12px; }
.empty-title { font-size: 15px; font-weight: 600; color: var(--text); margin-bottom: 4px; }
</style>

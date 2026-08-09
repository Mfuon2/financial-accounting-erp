<script setup>
import { ref, computed, onMounted } from 'vue'
import { expenseClaims as expensesApi, accounts as accountsApi, users as usersApi } from '@/api/index.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { fmt } from '@/utils/format.js'
import PageHeader   from '@/components/PageHeader.vue'
import Button       from '@/components/primitives/Button.vue'
import IconBtn      from '@/components/primitives/IconBtn.vue'
import Badge        from '@/components/primitives/Badge.vue'
import Kpi          from '@/components/data-display/Kpi.vue'
import Modal        from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter  from '@/components/tables/TableFooter.vue'
import ChipFilter   from '@/components/primitives/ChipFilter.vue'
import AmountInput      from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')
// Filing/editing/submitting your own claim is a PREPARER-tier action (matches
// ExpenseClaimController's role gates) — any employee, including DATA_ENTRY, can do this.
const canFile = computed(() => ['DATA_ENTRY', 'ACCOUNTANT', 'SENIOR_ACCOUNTANT', 'CONTROLLER_CFO', 'SYSTEM_ADMIN'].includes(currentUser.value?.role))
// Approve/reject is APPROVER-tier — segregation of duties (matches ExpenseClaimController).
const canApprove = computed(() => ['SENIOR_ACCOUNTANT', 'CONTROLLER_CFO', 'SYSTEM_ADMIN'].includes(currentUser.value?.role))

// ── State ─────────────────────────────────────────────────────────────────────
const claimList   = ref([])
const accountOpts = ref([])
const employeeOpts = ref([])
const loading     = ref(false)
const search      = ref('')
const statusFilter = ref('ALL')
const STATUSES = ['ALL', 'DRAFT', 'SUBMITTED', 'APPROVED', 'REIMBURSED', 'REJECTED']

const filtered = computed(() => claimList.value.filter(c => {
  if (statusFilter.value !== 'ALL' && c.status !== statusFilter.value) return false
  if (search.value) {
    const needle = search.value.toLowerCase()
    if (!c.employeeName?.toLowerCase().includes(needle) && !c.id?.toLowerCase().includes(needle) && !(c.notes ?? '').toLowerCase().includes(needle)) return false
  }
  return true
}))

const draftCount = computed(() => claimList.value.filter(c => c.status === 'DRAFT').length)
const pendingApprovalCount = computed(() => claimList.value.filter(c => c.status === 'SUBMITTED').length)
const totalReimbursed = computed(() => claimList.value.filter(c => c.status === 'REIMBURSED').reduce((s, c) => s + c.totalAmount, 0))

// ── Load ──────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  try {
    const [res, accts, emps] = await Promise.all([
      expensesApi.list({ entityId: entityId.value }),
      accountsApi.list({ entityId: entityId.value }),
      usersApi.list({ entityId: entityId.value }),
    ])
    claimList.value = Array.isArray(res) ? res : (res?.content ?? [])
    const acctArr = Array.isArray(accts) ? accts : (accts?.content ?? [])
    accountOpts.value = acctArr
      .filter(a => !a.isHeader && a.accountType === 'EXPENSE')
      .map(a => ({ value: a.id, label: `${a.accountCode} · ${a.accountName}` }))
    const empArr = Array.isArray(emps) ? emps : (emps?.content ?? [])
    employeeOpts.value = empArr.map(u => ({ value: u.id, label: u.fullName ?? u.username ?? u.email }))
  } catch {
    toast.error('Failed to load expense claims.')
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ── New / Edit claim ─────────────────────────────────────────────────────────
const showForm = ref(false)
const formSaving = ref(false)
const editingId = ref(null)
const form = ref({ employeeId: '', claimDate: '', notes: '', lines: [] })

function todayIso() { return new Date().toISOString().slice(0, 10) }
function emptyLine() { return { accountId: '', description: '', amount: '', dateIncurred: todayIso(), receiptReference: '' } }

function openNew() {
  editingId.value = null
  form.value = { employeeId: currentUser.value?.id ?? '', claimDate: todayIso(), notes: '', lines: [emptyLine()] }
  showForm.value = true
}

function openEdit(c) {
  editingId.value = c.id
  form.value = {
    employeeId: c.employeeId,
    claimDate: c.claimDate,
    notes: c.notes ?? '',
    lines: c.lines.map(l => ({ accountId: l.accountId, description: l.description, amount: l.amount, dateIncurred: l.dateIncurred, receiptReference: l.receiptReference ?? '' })),
  }
  showForm.value = true
}

function addLine() { form.value.lines.push(emptyLine()) }
function removeLine(idx) { form.value.lines.splice(idx, 1) }

const formTotal = computed(() => form.value.lines.reduce((s, l) => s + (Number(l.amount) || 0), 0))

async function saveForm() {
  if (!form.value.employeeId) return toast.warn('An employee is required.')
  if (!form.value.claimDate) return toast.warn('A claim date is required.')
  if (!form.value.lines.length) return toast.warn('At least one line is required.')
  if (form.value.lines.some(l => !l.accountId || !l.description.trim() || l.amount === '' || !l.dateIncurred)) {
    return toast.warn('Every line needs an account, description, amount, and date incurred.')
  }
  formSaving.value = true
  try {
    const body = {
      entityId: entityId.value,
      employeeId: form.value.employeeId,
      claimDate: form.value.claimDate,
      notes: form.value.notes.trim() || null,
      lines: form.value.lines.map(l => ({ ...l, receiptReference: l.receiptReference?.trim() || null })),
    }
    if (editingId.value) {
      await expensesApi.update(editingId.value, body)
      toast.success('Expense claim updated.')
    } else {
      await expensesApi.create(body)
      toast.success('Expense claim created.')
    }
    showForm.value = false
    await load()
  } catch {
    /* handled by client */
  } finally {
    formSaving.value = false
  }
}

// ── Submit / Approve / Reject / Reopen ──────────────────────────────────────
async function submit(c) {
  try {
    await expensesApi.submit(c.id)
    toast.success(`Claim ${c.id} submitted for approval.`)
    await load()
  } catch { /* handled */ }
}

async function approve(c) {
  try {
    await expensesApi.approve(c.id)
    toast.success(`Claim ${c.id} approved and reimbursement posted.`)
    await load()
  } catch { /* handled */ }
}

const rejectTarget = ref(null)
const rejectReason = ref('')
const rejecting = ref(false)

function openReject(c) { rejectTarget.value = c; rejectReason.value = '' }

async function confirmReject() {
  if (!rejectReason.value.trim()) return toast.warn('A reason is required to reject an expense claim.')
  rejecting.value = true
  try {
    await expensesApi.reject(rejectTarget.value.id, rejectReason.value.trim())
    toast.success('Expense claim rejected.')
    rejectTarget.value = null
    await load()
  } catch { /* handled */ } finally { rejecting.value = false }
}

async function reopen(c) {
  try {
    await expensesApi.reopen(c.id)
    toast.success(`Claim ${c.id} reopened for correction.`)
    await load()
  } catch { /* handled */ }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Expense Claims"
      meta="Employee expense claims, approval routing, and reimbursement posting — Project.md Domain 1 (Financial Operations)"
    >
      <Button v-if="canFile" variant="primary" icon="plus" @click="openNew">New claim</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="kpi-grid">
        <Kpi label="Draft claims"        icon="doc"   :value="draftCount" />
        <Kpi label="Pending approval"    icon="clock" :value="pendingApprovalCount" />
        <Kpi label="Total reimbursed"    icon="coin"  :value="totalReimbursed" />
        <Kpi label="All claims"          icon="docs"  :value="claimList.length" />
      </div>

      <TableToolbar v-model:search="search">
        <ChipFilter v-for="s in STATUSES" :key="s" :active="statusFilter === s" @click="statusFilter = s">
          {{ s }}
        </ChipFilter>
      </TableToolbar>

      <div class="card">
        <div v-if="loading" class="empty-state">Loading…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Employee</th>
              <th>Claim date</th>
              <th>Status</th>
              <th>Lines</th>
              <th class="num">Total amount</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in filtered" :key="c.id">
              <td class="fw-500">{{ c.employeeName }}</td>
              <td class="muted small">{{ c.claimDate }}</td>
              <td><Badge :status="c.status" :dot="false" /></td>
              <td class="muted small">{{ c.lines.length }}</td>
              <td class="num mono">{{ fmt(c.totalAmount) }}</td>
              <td class="row-actions">
                <Button v-if="canFile && c.status === 'DRAFT'" variant="ghost" size="sm" icon="edit" @click="openEdit(c)">Edit</Button>
                <Button v-if="canFile && c.status === 'DRAFT'" variant="primary" size="sm" icon="approve" @click="submit(c)">Submit</Button>
                <Button v-if="canApprove && c.status === 'SUBMITTED'" variant="primary" size="sm" icon="approve" @click="approve(c)">Approve</Button>
                <Button v-if="canApprove && c.status === 'SUBMITTED'" variant="ghost" size="sm" icon="reject" @click="openReject(c)">Reject</Button>
                <Button v-if="canFile && c.status === 'REJECTED'" variant="ghost" size="sm" icon="edit" @click="reopen(c)">Reopen</Button>
              </td>
            </tr>
            <tr v-if="!loading && !filtered.length">
              <td colspan="6" class="empty-state">No expense claims found.</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="filtered.length" label="expense claims" />
      </div>
    </div>

    <!-- New/Edit claim -->
    <Modal :open="showForm" :title="editingId ? 'Edit expense claim' : 'New expense claim'" :width="760" @close="showForm = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Employee <span class="req">*</span></label>
          <SearchableSelect v-model="form.employeeId" :options="employeeOpts" placeholder="Select employee…" />
        </div>
        <div class="field">
          <label>Claim date <span class="req">*</span></label>
          <input class="input" type="date" v-model="form.claimDate" />
        </div>
      </div>
      <div class="field" style="margin-top:12px">
        <label>Notes</label>
        <input v-model="form.notes" class="input" placeholder="Optional" />
      </div>

      <div class="stack" style="margin-top:16px">
        <div class="line-header">
          <span>Account</span>
          <span>Description</span>
          <span>Date incurred</span>
          <span>Amount</span>
          <span>Receipt ref.</span>
          <span></span>
        </div>
        <div v-for="(line, idx) in form.lines" :key="idx" class="line-row">
          <SearchableSelect v-model="line.accountId" :options="accountOpts" placeholder="Select account…" />
          <input v-model="line.description" class="input" placeholder="What was it for?" />
          <input v-model="line.dateIncurred" class="input" type="date" />
          <AmountInput class="input mono" v-model="line.amount" placeholder="0.00" />
          <input v-model="line.receiptReference" class="input" placeholder="Optional" />
          <IconBtn icon="x" :disabled="form.lines.length <= 1" @click="removeLine(idx)" />
        </div>
        <Button variant="ghost" icon="plus" @click="addLine">Add line</Button>
        <div class="line-total">Total: <strong>{{ fmt(formTotal) }}</strong></div>
      </div>

      <p class="muted small" style="margin-top:12px">
        Receipt reference is a plain text/URL pointer only — a document-upload/OCR pipeline is not
        yet built (tracked as future work).
      </p>

      <template #footer>
        <Button variant="primary" :loading="formSaving" @click="saveForm">{{ editingId ? 'Save' : 'Create' }}</Button>
        <Button variant="ghost" @click="showForm = false">Cancel</Button>
      </template>
    </Modal>

    <!-- Reject -->
    <Modal :open="!!rejectTarget" title="Reject expense claim" :width="480" @close="rejectTarget = null">
      <div class="info-box" style="border-color:oklch(0.55 0.18 15);background:oklch(0.98 0.02 15);margin-bottom:16px">
        This will reject {{ rejectTarget?.employeeName }}'s claim ({{ fmt(rejectTarget?.totalAmount) }}). They can reopen it for correction and resubmission.
      </div>
      <div class="field">
        <label>Reason <span class="req">*</span></label>
        <input v-model="rejectReason" class="input" placeholder="e.g. Missing receipts" />
      </div>
      <template #footer>
        <Button variant="danger" :loading="rejecting" @click="confirmReject">Confirm reject</Button>
        <Button variant="ghost" @click="rejectTarget = null">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.req { color: var(--danger, oklch(0.55 0.18 15)); }
.empty-state { text-align: center; padding: 40px; color: var(--muted); font-size: 13px; }
.muted { color: var(--muted); }
.fw-500 { font-weight: 500; }
.row-actions { display: flex; gap: 4px; justify-content: flex-end; flex-wrap: wrap; }
.line-header, .line-row {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 140px 120px 1fr 32px;
  gap: 8px;
  align-items: center;
}
.line-header { font-size: 11px; text-transform: uppercase; color: var(--muted); letter-spacing: .04em; }
.line-total { text-align: right; font-size: 13px; margin-top: 4px; }
</style>

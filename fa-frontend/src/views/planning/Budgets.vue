<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { budgets as budgetsApi, accounts as accountsApi, periods as periodsApi } from '@/api/index.js'
import { isDemo } from '@/composables/useAppMode.js'
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
const canManage = computed(() => ['ACCOUNTANT', 'SENIOR_ACCOUNTANT', 'CONTROLLER_CFO', 'SYSTEM_ADMIN'].includes(currentUser.value?.role))
const canApprove = computed(() => ['SENIOR_ACCOUNTANT', 'CONTROLLER_CFO', 'SYSTEM_ADMIN'].includes(currentUser.value?.role))

// ── State ─────────────────────────────────────────────────────────────────────
const budgetList  = ref([])
const accountOpts = ref([])
const periodOpts  = ref([])
const loading     = ref(false)
const search      = ref('')
const statusFilter = ref('ALL')
const STATUSES = ['ALL', 'DRAFT', 'APPROVED', 'VOID']

const filtered = computed(() => budgetList.value.filter(b => {
  if (statusFilter.value !== 'ALL' && b.status !== statusFilter.value) return false
  if (search.value && !b.name.toLowerCase().includes(search.value.toLowerCase())) return false
  return true
}))

const draftCount = computed(() => budgetList.value.filter(b => b.status === 'DRAFT').length)
const approvedCount = computed(() => budgetList.value.filter(b => b.status === 'APPROVED').length)
const totalBudgeted = computed(() => budgetList.value.filter(b => b.status === 'APPROVED').reduce((s, b) => s + b.totalAmount, 0))

// ── Load ──────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  try {
    const [res, accts, pers] = await Promise.all([
      budgetsApi.list({ entityId: entityId.value }),
      accountsApi.list({ entityId: entityId.value }),
      periodsApi.list({ entityId: entityId.value }),
    ])
    budgetList.value = Array.isArray(res) ? res : (res?.content ?? [])
    const acctArr = Array.isArray(accts) ? accts : (accts?.content ?? [])
    accountOpts.value = acctArr.filter(a => !a.isHeader).map(a => ({ value: a.id, label: `${a.accountCode} · ${a.accountName}` }))
    const perArr = Array.isArray(pers) ? pers : (pers?.content ?? [])
    periodOpts.value = perArr.map(p => ({ value: p.id, label: p.periodName }))
  } catch {
    toast.error('Failed to load budgets.')
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ── New / Edit budget ────────────────────────────────────────────────────────
const showForm = ref(false)
const formSaving = ref(false)
const editingId = ref(null)
const form = ref({ name: '', notes: '', lines: [] })

function emptyLine() { return { accountId: '', periodId: '', amount: '' } }

function openNew() {
  editingId.value = null
  form.value = { name: '', notes: '', lines: [emptyLine()] }
  showForm.value = true
}

function openEdit(b) {
  editingId.value = b.id
  form.value = {
    name: b.name,
    notes: b.notes ?? '',
    lines: b.lines.map(l => ({ accountId: l.accountId, periodId: l.periodId, amount: l.amount })),
  }
  showForm.value = true
}

function addLine() { form.value.lines.push(emptyLine()) }
function removeLine(idx) { form.value.lines.splice(idx, 1) }

const formTotal = computed(() => form.value.lines.reduce((s, l) => s + (Number(l.amount) || 0), 0))

async function saveForm() {
  if (!form.value.name.trim()) return toast.warn('Budget name is required.')
  if (!form.value.lines.length) return toast.warn('At least one line is required.')
  if (form.value.lines.some(l => !l.accountId || !l.periodId || l.amount === '')) {
    return toast.warn('Every line needs an account, a period, and an amount.')
  }
  formSaving.value = true
  try {
    const body = { entityId: entityId.value, name: form.value.name.trim(), notes: form.value.notes.trim() || null, lines: form.value.lines }
    if (editingId.value) {
      await budgetsApi.update(editingId.value, body)
      toast.success('Budget updated.')
    } else {
      await budgetsApi.create(body)
      toast.success('Budget created.')
    }
    showForm.value = false
    await load()
  } catch {
    /* handled by client */
  } finally {
    formSaving.value = false
  }
}

// ── Approve / Void ───────────────────────────────────────────────────────────
async function approve(b) {
  try {
    await budgetsApi.approve(b.id)
    toast.success(`${b.name} approved.`)
    await load()
  } catch { /* handled */ }
}

const voidTarget = ref(null)
const voidReason = ref('')
const voiding = ref(false)

function openVoid(b) { voidTarget.value = b; voidReason.value = '' }

async function confirmVoid() {
  if (!voidReason.value.trim()) return toast.warn('A reason is required to void a budget.')
  voiding.value = true
  try {
    await budgetsApi.void(voidTarget.value.id, voidReason.value.trim())
    toast.success('Budget voided.')
    voidTarget.value = null
    await load()
  } catch { /* handled */ } finally { voiding.value = false }
}

// ── Variance report ──────────────────────────────────────────────────────────
const varianceTarget = ref(null)
const varianceReport = ref(null)
const varianceLoading = ref(false)

async function openVariance(b) {
  varianceTarget.value = b
  varianceLoading.value = true
  try {
    varianceReport.value = await budgetsApi.variance(b.id)
  } catch {
    toast.error('Failed to load variance report.')
    varianceTarget.value = null
  } finally {
    varianceLoading.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Budgets"
      meta="Budget planning and budget-vs-actual variance — Project.md Domain 1 (Financial Operations)"
    >
      <Button v-if="canManage" variant="primary" icon="plus" @click="openNew">New budget</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="kpi-grid">
        <Kpi label="Draft budgets"    icon="doc"      :value="draftCount" />
        <Kpi label="Approved budgets" icon="check"    :value="approvedCount" />
        <Kpi label="Total approved (budgeted)" icon="coin" :value="totalBudgeted" />
        <Kpi label="All budgets"     icon="docs"      :value="budgetList.length" />
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
              <th>Name</th>
              <th>Status</th>
              <th>Lines</th>
              <th class="num">Total amount</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in filtered" :key="b.id">
              <td class="fw-500">{{ b.name }}</td>
              <td><Badge :status="b.status" :dot="false" /></td>
              <td class="muted small">{{ b.lines.length }}</td>
              <td class="num mono">{{ fmt(b.totalAmount) }}</td>
              <td class="row-actions">
                <Button variant="ghost" size="sm" icon="chart" @click="openVariance(b)">Variance</Button>
                <Button v-if="canManage && b.status === 'DRAFT'" variant="ghost" size="sm" icon="edit" @click="openEdit(b)">Edit</Button>
                <Button v-if="canApprove && b.status === 'DRAFT'" variant="primary" size="sm" icon="approve" @click="approve(b)">Approve</Button>
                <Button v-if="canApprove && b.status !== 'VOID'" variant="ghost" size="sm" icon="reject" @click="openVoid(b)">Void</Button>
              </td>
            </tr>
            <tr v-if="!loading && !filtered.length">
              <td colspan="5" class="empty-state">No budgets found.</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="filtered.length" label="budgets" />
      </div>
    </div>

    <!-- New/Edit budget -->
    <Modal :open="showForm" :title="editingId ? 'Edit budget' : 'New budget'" :width="680" @close="showForm = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Name <span class="req">*</span></label>
          <input v-model="form.name" class="input" placeholder="e.g. FY2026 Operating Budget" />
        </div>
        <div class="field">
          <label>Notes</label>
          <input v-model="form.notes" class="input" placeholder="Optional" />
        </div>
      </div>

      <div class="stack" style="margin-top:16px">
        <div class="line-header">
          <span>Account</span>
          <span>Period</span>
          <span>Amount</span>
          <span></span>
        </div>
        <div v-for="(line, idx) in form.lines" :key="idx" class="line-row">
          <SearchableSelect v-model="line.accountId" :options="accountOpts" placeholder="Select account…" />
          <SearchableSelect v-model="line.periodId" :options="periodOpts" placeholder="Select period…" />
          <AmountInput class="input mono" v-model="line.amount" placeholder="0.00" />
          <IconBtn icon="x" :disabled="form.lines.length <= 1" @click="removeLine(idx)" />
        </div>
        <Button variant="ghost" icon="plus" @click="addLine">Add line</Button>
        <div class="line-total">Total: <strong>{{ fmt(formTotal) }}</strong></div>
      </div>

      <template #footer>
        <Button variant="primary" :loading="formSaving" @click="saveForm">{{ editingId ? 'Save' : 'Create' }}</Button>
        <Button variant="ghost" @click="showForm = false">Cancel</Button>
      </template>
    </Modal>

    <!-- Void -->
    <Modal :open="!!voidTarget" title="Void budget" :width="480" @close="voidTarget = null">
      <div class="info-box" style="border-color:oklch(0.55 0.18 15);background:oklch(0.98 0.02 15);margin-bottom:16px">
        This will void <strong>{{ voidTarget?.name }}</strong>. This cannot be undone.
      </div>
      <div class="field">
        <label>Reason <span class="req">*</span></label>
        <input v-model="voidReason" class="input" placeholder="e.g. Superseded by revised budget" />
      </div>
      <template #footer>
        <Button variant="danger" :loading="voiding" @click="confirmVoid">Confirm void</Button>
        <Button variant="ghost" @click="voidTarget = null">Cancel</Button>
      </template>
    </Modal>

    <!-- Variance report -->
    <Modal :open="!!varianceTarget" :title="`Variance — ${varianceTarget?.name ?? ''}`" :width="760" @close="varianceTarget = null; varianceReport = null">
      <div v-if="varianceLoading" class="empty-state">Loading…</div>
      <table v-else-if="varianceReport" class="tbl">
        <thead>
          <tr>
            <th>Account</th>
            <th>Period</th>
            <th class="num">Budgeted</th>
            <th class="num">Actual</th>
            <th class="num">Variance</th>
            <th class="num">%</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in varianceReport.lines" :key="`${l.accountId}-${l.periodId}`">
            <td>{{ l.accountCode }} · {{ l.accountName }}</td>
            <td class="muted small">{{ l.periodName }}</td>
            <td class="num mono">{{ fmt(l.budgetedAmount) }}</td>
            <td class="num mono">{{ fmt(l.actualAmount) }}</td>
            <td class="num mono" :class="l.variance >= 0 ? 'text-pos' : 'text-neg'">{{ fmt(l.variance) }}</td>
            <td class="num mono muted small">{{ l.variancePercent != null ? l.variancePercent.toFixed(1) + '%' : '—' }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="2" class="fw-500">Total</td>
            <td class="num mono fw-500">{{ fmt(varianceReport.totalBudgeted) }}</td>
            <td class="num mono fw-500">{{ fmt(varianceReport.totalActual) }}</td>
            <td class="num mono fw-500" :class="varianceReport.totalVariance >= 0 ? 'text-pos' : 'text-neg'">{{ fmt(varianceReport.totalVariance) }}</td>
            <td></td>
          </tr>
        </tfoot>
      </table>
      <p class="muted small" style="margin-top:12px">
        Variance = actual − budgeted. Whether a positive variance is favorable depends on account
        type (e.g. favorable for revenue, unfavorable for expense).
      </p>
      <template #footer>
        <Button variant="ghost" @click="varianceTarget = null; varianceReport = null">Close</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.req { color: var(--danger, oklch(0.55 0.18 15)); }
.empty-state { text-align: center; padding: 40px; color: var(--muted); font-size: 13px; }
.muted { color: var(--muted); }
.fw-500 { font-weight: 500; }
.row-actions { display: flex; gap: 4px; justify-content: flex-end; }
.line-header, .line-row {
  display: grid;
  grid-template-columns: 1fr 1fr 140px 32px;
  gap: 8px;
  align-items: center;
}
.line-header { font-size: 11px; text-transform: uppercase; color: var(--muted); letter-spacing: .04em; }
.line-total { text-align: right; font-size: 13px; margin-top: 4px; }
.text-pos { color: oklch(0.55 0.15 145); }
.text-neg { color: oklch(0.55 0.18 15); }
</style>

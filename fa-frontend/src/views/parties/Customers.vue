<script setup>
import { ref, computed, onMounted } from 'vue'
import { CUSTOMERS } from '@/data/index.js'
import { customers as customersApi, codes as codesApi } from '@/api/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { useCategoryCache } from '@/composables/useCategoryCache.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader   from '@/components/PageHeader.vue'
import Button       from '@/components/primitives/Button.vue'
import IconBtn      from '@/components/primitives/IconBtn.vue'
import Badge        from '@/components/primitives/Badge.vue'
import Ico          from '@/components/primitives/Ico.vue'
import Kpi          from '@/components/data-display/Kpi.vue'
import Modal        from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter  from '@/components/tables/TableFooter.vue'
import ChipFilter   from '@/components/primitives/ChipFilter.vue'
import AmountInput  from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { currentUser } = useAuth()
const { toast } = useToast()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')
const userId   = computed(() => currentUser.value?.userId)

// ── State ──────────────────────────────────────────────────────────────────────
const customers    = ref([])
const loading      = ref(false)
const search       = ref('')
const showInactive = ref(false)
const drawer       = ref(null)      // currently viewed customer
const editMode     = ref(false)

// New customer modal
const showNew    = ref(false)
const newSaving  = ref(false)
const newForm    = ref({})
function resetNewForm() {
  newForm.value = {
    customerCode: '', name: '', taxNumber: '',
    email: '', phone: '', creditLimit: null, paymentTerms: 'NET_30',
  }
}

// Edit form (inside drawer)
const editSaving = ref(false)
const editForm   = ref({})

// Deactivate modal
const showDeactivate   = ref(false)
const deactivating     = ref(false)
const deactivateReason = ref('')

// Payment terms are entity-managed dynamic data (CLAUDE.md §2) — see shared/categories on the
// backend and setup/Categories.vue for where they're created/edited. Cached at module level
// (useCategoryCache) so this view, Suppliers.vue and any other consumer share one fetch.
const paymentTermsCache = useCategoryCache('PAYMENT_TERM')
const PAYMENT_TERM_OPTIONS = computed(() => [
  { value: '', label: '— None —' },
  ...paymentTermsCache.options.value,
])

// ── Load ───────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  paymentTermsCache.load(entityId.value)
  if (isDemo.value) {
    customers.value = CUSTOMERS.map(normDemo)
    loading.value = false
    return
  }
  try {
    const res = await customersApi.list({ entityId: entityId.value, activeOnly: false, size: 500 })
    const arr = Array.isArray(res) ? res : (res?.content ?? [])
    customers.value = arr
  } catch {
    toast.error('Failed to load customers.')
  } finally { loading.value = false }
}
onMounted(load)

function normDemo(d) {
  return {
    id: d.id, entityId: d.id,
    customerCode: d.code, name: d.name,
    email: d.email, phone: d.phone,
    creditLimit: d.creditLimit ?? 0,
    paymentTerms: d.terms?.replace('-', '_') ?? null,
    taxNumber: null,
    isActive: d.active ?? true,
    createdAt: null,
  }
}

// ── Computed ───────────────────────────────────────────────────────────────────
const activeCustomers   = computed(() => customers.value.filter(c => c.isActive))
const inactiveCustomers = computed(() => customers.value.filter(c => !c.isActive))
const totalCreditLimit  = computed(() => activeCustomers.value.reduce((s, c) => s + Number(c.creditLimit ?? 0), 0))

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return customers.value.filter(c => {
    if (!showInactive.value && !c.isActive) return false
    if (!q) return true
    return (c.name         || '').toLowerCase().includes(q) ||
           (c.customerCode || '').toLowerCase().includes(q) ||
           (c.email        || '').toLowerCase().includes(q) ||
           (c.taxNumber    || '').toLowerCase().includes(q)
  })
})

// ── Create ─────────────────────────────────────────────────────────────────────
const codeLoading = ref(false)

async function openNew() {
  resetNewForm()
  showNew.value = true
  codeLoading.value = true
  try {
    const res = await codesApi.next(entityId.value, 'CU', 'CUSTOMER')
    newForm.value.customerCode = res?.code ?? ''
  } catch { /* non-critical */ } finally {
    codeLoading.value = false
  }
}

async function saveNew() {
  if (!newForm.value.name?.trim()) return toast.warn('Customer name is required.')
  newSaving.value = true
  try {
    const created = await customersApi.create({
      entityId:     entityId.value,
      customerCode: '',
      name:         newForm.value.name.trim(),
      taxNumber:    newForm.value.taxNumber?.trim() || null,
      email:        newForm.value.email?.trim()     || null,
      phone:        newForm.value.phone?.trim()     || null,
      creditLimit:  newForm.value.creditLimit       || 0,
      paymentTerms: newForm.value.paymentTerms      || null,
    })
    customers.value = [created, ...customers.value]
    showNew.value = false
    toast.success(`Customer ${created.customerCode} created.`)
  } catch { /* handled by client */ } finally { newSaving.value = false }
}

// ── View / Edit ─────────────────────────────────────────────────────────────────
function openDrawer(c) {
  drawer.value  = c
  editMode.value = false
  editForm.value = {
    email:        c.email        ?? '',
    phone:        c.phone        ?? '',
    creditLimit:  Number(c.creditLimit ?? 0),
    paymentTerms: c.paymentTerms ?? '',
  }
}

async function saveEdit() {
  editSaving.value = true
  try {
    const updated = await customersApi.update(drawer.value.id, {
      email:        editForm.value.email?.trim()     || null,
      phone:        editForm.value.phone?.trim()     || null,
      creditLimit:  editForm.value.creditLimit       || 0,
      paymentTerms: editForm.value.paymentTerms      || null,
    })
    const merged = { ...drawer.value, ...updated }
    const idx = customers.value.findIndex(c => c.id === drawer.value.id)
    if (idx !== -1) customers.value[idx] = merged
    drawer.value   = merged
    editMode.value = false
    toast.success('Customer updated.')
  } catch { /* handled */ } finally { editSaving.value = false }
}

// ── Deactivate ─────────────────────────────────────────────────────────────────
function openDeactivate() {
  deactivateReason.value = ''
  showDeactivate.value = true
}

async function confirmDeactivate() {
  if (!deactivateReason.value.trim()) return toast.warn('Reason is required.')
  deactivating.value = true
  try {
    const updated = await customersApi.deactivate(drawer.value.id, {
      reason:        deactivateReason.value.trim(),
      deactivatedBy: userId.value,
    })
    const merged = { ...drawer.value, ...updated, isActive: false }
    const idx = customers.value.findIndex(c => c.id === drawer.value.id)
    if (idx !== -1) customers.value[idx] = merged
    drawer.value = merged
    showDeactivate.value = false
    toast.success('Customer deactivated.')
  } catch { /* handled */ } finally { deactivating.value = false }
}

function termsLabel(t) {
  if (!t) return '—'
  return t.replace(/_/g, ' ')
}

function utilPct(c) {
  const limit = Number(c.creditLimit ?? 0)
  return limit > 0 ? Math.min(100, Math.round((Number(c.balance ?? 0) / limit) * 100)) : 0
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Customers"
      :meta="`${activeCustomers.length} active · ${customers.length} total`"
    >
      <Button variant="ghost" icon="download">Export</Button>
      <Button variant="primary" icon="plus" @click="openNew">New customer</Button>
    </PageHeader>

    <div class="page-section stack">
      <!-- KPI row -->
      <div class="kpi-grid">
        <Kpi label="Active customers"    icon="users"  :value="activeCustomers.length" />
        <Kpi label="Inactive customers"  icon="archive" :value="inactiveCustomers.length" />
        <Kpi label="Total credit limit"  icon="gauge"  :value="totalCreditLimit" unit="KES" />
        <Kpi label="With payment terms"  icon="clock"  :value="activeCustomers.filter(c => c.paymentTerms).length" />
      </div>

      <TableToolbar v-model:search="search">
        <ChipFilter :active="showInactive" @click="showInactive = !showInactive">
          Show inactive
        </ChipFilter>
        <span class="toolbar-count">{{ filtered.length }} customers</span>
      </TableToolbar>

      <div class="card">
        <div v-if="loading" class="empty-state">Loading…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Customer</th>
              <th>Tax PIN</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Terms</th>
              <th class="num">Credit limit</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in filtered" :key="c.id">
              <td><span class="code-cell">{{ c.customerCode }}</span></td>
              <td class="fw-500">{{ c.name }}</td>
              <td class="muted small mono">{{ c.taxNumber || '—' }}</td>
              <td class="muted small">{{ c.email || '—' }}</td>
              <td class="muted small">{{ c.phone || '—' }}</td>
              <td><span v-if="c.paymentTerms" class="chip">{{ termsLabel(c.paymentTerms) }}</span><span v-else class="muted">—</span></td>
              <td class="num mono">{{ c.creditLimit > 0 ? fmt(Number(c.creditLimit)) : '—' }}</td>
              <td>
                <Badge :status="c.isActive ? 'active' : 'inactive'" :dot="false" />
              </td>
              <td>
                <IconBtn icon="dots" @click="openDrawer(c)" />
              </td>
            </tr>
            <tr v-if="!loading && !filtered.length">
              <td colspan="9" class="empty-state">No customers found.</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="filtered.length" label="customers" />
      </div>
    </div>

    <!-- ── New customer modal ──────────────────────────────────────────────── -->
    <Modal :open="showNew" title="New customer" :width="600" @close="showNew = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Customer code</label>
          <input
            :value="codeLoading ? '' : newForm.customerCode"
            class="input mono ro"
            :placeholder="codeLoading ? 'Generating…' : ''"
            disabled
          />
        </div>
        <div class="field">
          <label>Name <span class="req">*</span></label>
          <input v-model="newForm.name" class="input" placeholder="Acme Corporation" />
        </div>
        <div class="field">
          <label>Tax PIN / KRA PIN</label>
          <input v-model="newForm.taxNumber" class="input mono" placeholder="A001234567A" />
        </div>
        <div class="field">
          <label>Email</label>
          <input v-model="newForm.email" class="input" type="email" placeholder="accounts@acme.com" />
        </div>
        <div class="field">
          <label>Phone</label>
          <input v-model="newForm.phone" class="input" placeholder="+254712345678" />
        </div>
        <div class="field">
          <label>Payment terms</label>
          <SearchableSelect
            v-model="newForm.paymentTerms"
            :options="PAYMENT_TERM_OPTIONS"
            placeholder="Select terms"
          />
        </div>
        <div class="field" style="grid-column:1/-1">
          <label>Credit limit (KES)</label>
          <AmountInput v-model="newForm.creditLimit" class="input mono" placeholder="0.00" />
        </div>
      </div>
      <template #footer>
        <Button variant="primary" icon="plus" :loading="newSaving" @click="saveNew">Create customer</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Customer drawer ────────────────────────────────────────────────── -->
    <Modal
      :open="!!drawer"
      :title="drawer?.name"
      :subtitle="drawer?.customerCode"
      :width="680"
      @close="drawer = null; editMode = false"
    >
      <div v-if="drawer">
        <!-- Summary stats -->
        <div class="drawer-stats">
          <div class="dstat">
            <div class="dstat-label">Credit limit</div>
            <div class="dstat-value mono">{{ drawer.creditLimit > 0 ? fmt(Number(drawer.creditLimit)) : '—' }} <span class="dstat-unit">KES</span></div>
          </div>
          <div class="dstat">
            <div class="dstat-label">Payment terms</div>
            <div class="dstat-value">{{ termsLabel(drawer.paymentTerms) }}</div>
          </div>
          <div class="dstat">
            <div class="dstat-label">Status</div>
            <div class="dstat-value">
              <Badge :status="drawer.isActive ? 'active' : 'inactive'" :dot="false" />
            </div>
          </div>
        </div>

        <!-- Read-only fields -->
        <div v-if="!editMode">
          <div class="form-grid cols-2" style="margin-bottom:16px">
            <div class="field"><label>Customer code</label><div class="input ro mono">{{ drawer.customerCode }}</div></div>
            <div class="field"><label>Legal name</label><div class="input ro">{{ drawer.name }}</div></div>
            <div class="field"><label>Tax PIN</label><div class="input ro mono">{{ drawer.taxNumber || '—' }}</div></div>
            <div class="field"><label>Email</label><div class="input ro">{{ drawer.email || '—' }}</div></div>
            <div class="field"><label>Phone</label><div class="input ro">{{ drawer.phone || '—' }}</div></div>
            <div class="field"><label>Payment terms</label><div class="input ro">{{ termsLabel(drawer.paymentTerms) }}</div></div>
            <div class="field"><label>Created</label><div class="input ro">{{ fmtDate(drawer.createdAt) || '—' }}</div></div>
            <div class="field"><label>Last updated</label><div class="input ro">{{ fmtDate(drawer.modifiedAt) || '—' }}</div></div>
          </div>
          <div v-if="drawer.deactivationReason" class="info-box" style="border-color:oklch(0.55 0.18 15);background:oklch(0.98 0.02 15)">
            <strong>Deactivated:</strong> {{ drawer.deactivationReason }}
          </div>
        </div>

        <!-- Edit form -->
        <div v-else>
          <div class="info-box" style="margin-bottom:12px">
            <strong>Note:</strong> Customer code and name are immutable after creation.
          </div>
          <div class="form-grid cols-2">
            <div class="field">
              <label>Email</label>
              <input v-model="editForm.email" class="input" type="email" placeholder="accounts@acme.com" />
            </div>
            <div class="field">
              <label>Phone</label>
              <input v-model="editForm.phone" class="input" placeholder="+254712345678" />
            </div>
            <div class="field">
              <label>Payment terms</label>
              <SearchableSelect
                v-model="editForm.paymentTerms"
                :options="PAYMENT_TERM_OPTIONS"
                placeholder="Select terms"
              />
            </div>
            <div class="field">
              <label>Credit limit (KES)</label>
              <AmountInput v-model="editForm.creditLimit" class="input mono" placeholder="0.00" />
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <template v-if="!editMode">
          <Button v-if="drawer?.isActive" variant="primary" icon="edit" @click="editMode = true">Edit</Button>
          <Button v-if="drawer?.isActive" variant="danger"  @click="openDeactivate">Deactivate</Button>
          <Button variant="ghost" @click="drawer = null; editMode = false">Close</Button>
        </template>
        <template v-else>
          <Button variant="primary" :loading="editSaving" @click="saveEdit">Save changes</Button>
          <Button variant="ghost" @click="editMode = false">Cancel</Button>
        </template>
      </template>
    </Modal>

    <!-- ── Deactivate confirmation ─────────────────────────────────────────── -->
    <Modal :open="showDeactivate" title="Deactivate customer" :width="480" @close="showDeactivate = false">
      <div>
        <div class="info-box" style="border-color:oklch(0.55 0.18 15);background:oklch(0.98 0.02 15);margin-bottom:16px">
          This will soft-delete <strong>{{ drawer?.name }}</strong>. All historical records are preserved and this action is audit-logged.
        </div>
        <div class="field">
          <label>Reason <span class="req">*</span></label>
          <input v-model="deactivateReason" class="input" placeholder="e.g. Duplicate record / customer closed account" />
        </div>
      </div>
      <template #footer>
        <Button variant="danger" :loading="deactivating" @click="confirmDeactivate">Confirm deactivation</Button>
        <Button variant="ghost" @click="showDeactivate = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.drawer-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 20px;
}
.dstat {
  background: var(--surface-raised, #f8f8f8);
  border: 1px solid var(--border, #e8e8e8);
  border-radius: 10px;
  padding: 14px 16px;
}
.dstat-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: .5px;
  margin-bottom: 6px;
}
.dstat-value {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.2;
  color: var(--text);
}
.dstat-value.mono { font-family: monospace; }
.dstat-unit { font-size: 13px; font-weight: 500; color: var(--muted); }
.ro { cursor: default; background: var(--surface-raised, #f8f8f8); }
.chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 99px;
  background: var(--accent-muted, #e8f0ff);
  color: var(--accent, #3b5bdb);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .3px;
}
.req { color: var(--danger, oklch(0.55 0.18 15)); }
.empty-state { text-align: center; padding: 40px; color: var(--muted); font-size: 13px; }
</style>

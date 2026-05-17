<script setup>
import { ref, computed, onMounted } from 'vue'
import { SUPPLIERS } from '@/data/index.js'
import { suppliers as suppliersApi, codes as codesApi } from '@/api/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader   from '@/components/PageHeader.vue'
import Button       from '@/components/primitives/Button.vue'
import IconBtn      from '@/components/primitives/IconBtn.vue'
import Badge        from '@/components/primitives/Badge.vue'
import Kpi          from '@/components/data-display/Kpi.vue'
import Modal        from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter  from '@/components/tables/TableFooter.vue'
import ChipFilter   from '@/components/primitives/ChipFilter.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { currentUser } = useAuth()
const { toast } = useToast()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')
const userId   = computed(() => currentUser.value?.userId)

// ── State ──────────────────────────────────────────────────────────────────────
const suppliers    = ref([])
const loading      = ref(false)
const search       = ref('')
const showInactive = ref(false)
const drawer       = ref(null)
const editMode     = ref(false)

// New supplier modal
const showNew   = ref(false)
const newSaving = ref(false)
const newForm   = ref({})
function resetNewForm() {
  newForm.value = { supplierCode: '', name: '', taxNumber: '', email: '', phone: '', paymentTerms: 'NET_30' }
}

// Edit form
const editSaving = ref(false)
const editForm   = ref({})

// Deactivate
const showDeactivate   = ref(false)
const deactivating     = ref(false)
const deactivateReason = ref('')

const PAYMENT_TERMS = ['DUE_ON_RECEIPT', 'NET_15', 'NET_30', 'NET_45', 'NET_60', 'NET_90']

// ── Load ───────────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  if (isDemo.value) {
    suppliers.value = SUPPLIERS.map(normDemo)
    loading.value = false
    return
  }
  try {
    const res = await suppliersApi.list({ entityId: entityId.value, activeOnly: false, size: 500 })
    const arr = Array.isArray(res) ? res : (res?.content ?? [])
    suppliers.value = arr
  } catch {
    toast.error('Failed to load suppliers.')
  } finally { loading.value = false }
}
onMounted(load)

function normDemo(d) {
  return {
    id: d.id, entityId: d.id,
    supplierCode: d.code, name: d.name,
    email: d.email, phone: d.phone,
    paymentTerms: d.terms?.replace('-', '_') ?? null,
    taxNumber: null,
    isActive: d.active ?? true,
    createdAt: null,
  }
}

// ── Computed ───────────────────────────────────────────────────────────────────
const activeSuppliers   = computed(() => suppliers.value.filter(s => s.isActive))
const inactiveSuppliers = computed(() => suppliers.value.filter(s => !s.isActive))
const withTerms         = computed(() => activeSuppliers.value.filter(s => s.paymentTerms).length)

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  return suppliers.value.filter(s => {
    if (!showInactive.value && !s.isActive) return false
    if (!q) return true
    return (s.name         || '').toLowerCase().includes(q) ||
           (s.supplierCode || '').toLowerCase().includes(q) ||
           (s.email        || '').toLowerCase().includes(q) ||
           (s.taxNumber    || '').toLowerCase().includes(q)
  })
})

// ── Create ─────────────────────────────────────────────────────────────────────
const codeLoading = ref(false)

async function openNew() {
  resetNewForm()
  showNew.value = true
  codeLoading.value = true
  try {
    const res = await codesApi.next(entityId.value, 'SUPP', 'SUPPLIER')
    newForm.value.supplierCode = res?.code ?? ''
  } catch { /* non-critical */ } finally {
    codeLoading.value = false
  }
}

async function saveNew() {
  if (!newForm.value.name?.trim()) return toast.warn('Supplier name is required.')
  newSaving.value = true
  try {
    const created = await suppliersApi.create({
      entityId:     entityId.value,
      supplierCode: '',
      name:         newForm.value.name.trim(),
      taxNumber:    newForm.value.taxNumber?.trim() || null,
      email:        newForm.value.email?.trim()     || null,
      phone:        newForm.value.phone?.trim()     || null,
      paymentTerms: newForm.value.paymentTerms      || null,
    })
    suppliers.value = [created, ...suppliers.value]
    showNew.value = false
    toast.success(`Supplier ${created.supplierCode} created.`)
  } catch { /* handled by client */ } finally { newSaving.value = false }
}

// ── View / Edit ─────────────────────────────────────────────────────────────────
function openDrawer(s) {
  drawer.value   = s
  editMode.value = false
  editForm.value = {
    email:        s.email        ?? '',
    phone:        s.phone        ?? '',
    paymentTerms: s.paymentTerms ?? '',
  }
}

async function saveEdit() {
  editSaving.value = true
  try {
    const updated = await suppliersApi.update(drawer.value.id, {
      email:        editForm.value.email?.trim()  || null,
      phone:        editForm.value.phone?.trim()  || null,
      paymentTerms: editForm.value.paymentTerms   || null,
    })
    const merged = { ...drawer.value, ...updated }
    const idx = suppliers.value.findIndex(s => s.id === drawer.value.id)
    if (idx !== -1) suppliers.value[idx] = merged
    drawer.value   = merged
    editMode.value = false
    toast.success('Supplier updated.')
  } catch { /* handled */ } finally { editSaving.value = false }
}

// ── Deactivate ─────────────────────────────────────────────────────────────────
function openDeactivate() { deactivateReason.value = ''; showDeactivate.value = true }

async function confirmDeactivate() {
  if (!deactivateReason.value.trim()) return toast.warn('Reason is required.')
  deactivating.value = true
  try {
    const updated = await suppliersApi.deactivate(drawer.value.id, {
      reason:        deactivateReason.value.trim(),
      deactivatedBy: userId.value,
    })
    const merged = { ...drawer.value, ...updated, isActive: false }
    const idx = suppliers.value.findIndex(s => s.id === drawer.value.id)
    if (idx !== -1) suppliers.value[idx] = merged
    drawer.value = merged
    showDeactivate.value = false
    toast.success('Supplier deactivated.')
  } catch { /* handled */ } finally { deactivating.value = false }
}

function termsLabel(t) { return t ? t.replace(/_/g, ' ') : '—' }
</script>

<template>
  <div class="page">
    <PageHeader
      title="Suppliers"
      :meta="`${activeSuppliers.length} active · ${suppliers.length} total`"
    >
      <Button variant="ghost" icon="download">Export</Button>
      <Button variant="primary" icon="plus" @click="openNew">New supplier</Button>
    </PageHeader>

    <div class="page-section stack">
      <!-- KPI row -->
      <div class="kpi-grid">
        <Kpi label="Active suppliers"   icon="users"   :value="activeSuppliers.length" />
        <Kpi label="Inactive suppliers" icon="archive" :value="inactiveSuppliers.length" />
        <Kpi label="With payment terms" icon="clock"   :value="withTerms" />
        <Kpi label="Total suppliers"    icon="docs"    :value="suppliers.length" />
      </div>

      <TableToolbar v-model:search="search">
        <ChipFilter :active="showInactive" @click="showInactive = !showInactive">
          Show inactive
        </ChipFilter>
        <span class="toolbar-count">{{ filtered.length }} suppliers</span>
      </TableToolbar>

      <div class="card">
        <div v-if="loading" class="empty-state">Loading…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Supplier</th>
              <th>Tax PIN</th>
              <th>Email</th>
              <th>Phone</th>
              <th>Terms</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in filtered" :key="s.id">
              <td><span class="code-cell">{{ s.supplierCode }}</span></td>
              <td class="fw-500">{{ s.name }}</td>
              <td class="muted small mono">{{ s.taxNumber || '—' }}</td>
              <td class="muted small">{{ s.email || '—' }}</td>
              <td class="muted small">{{ s.phone || '—' }}</td>
              <td>
                <span v-if="s.paymentTerms" class="chip">{{ termsLabel(s.paymentTerms) }}</span>
                <span v-else class="muted">—</span>
              </td>
              <td>
                <Badge :status="s.isActive ? 'active' : 'inactive'" :dot="false" />
              </td>
              <td>
                <IconBtn icon="dots" @click="openDrawer(s)" />
              </td>
            </tr>
            <tr v-if="!loading && !filtered.length">
              <td colspan="8" class="empty-state">No suppliers found.</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="filtered.length" label="suppliers" />
      </div>
    </div>

    <!-- ── New supplier modal ──────────────────────────────────────────────── -->
    <Modal :open="showNew" title="New supplier" :width="600" @close="showNew = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Supplier code</label>
          <input
            :value="codeLoading ? '' : newForm.supplierCode"
            class="input mono ro"
            :placeholder="codeLoading ? 'Generating…' : ''"
            disabled
          />
        </div>
        <div class="field">
          <label>Name <span class="req">*</span></label>
          <input v-model="newForm.name" class="input" placeholder="Widget Manufacturing Ltd" />
        </div>
        <div class="field">
          <label>Tax PIN / KRA PIN</label>
          <input v-model="newForm.taxNumber" class="input mono" placeholder="P001234567B" />
        </div>
        <div class="field">
          <label>Email</label>
          <input v-model="newForm.email" class="input" type="email" placeholder="orders@supplier.com" />
        </div>
        <div class="field">
          <label>Phone</label>
          <input v-model="newForm.phone" class="input" placeholder="+254712345678" />
        </div>
        <div class="field">
          <label>Payment terms</label>
          <SearchableSelect
            v-model="newForm.paymentTerms"
            :options="[{ value: '', label: '— None —' }, ...PAYMENT_TERMS.map(t => ({ value: t, label: termsLabel(t) }))]"
            placeholder="Select terms"
          />
        </div>
      </div>
      <template #footer>
        <Button variant="primary" icon="plus" :loading="newSaving" @click="saveNew">Create supplier</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Supplier drawer ────────────────────────────────────────────────── -->
    <Modal
      :open="!!drawer"
      :title="drawer?.name"
      :subtitle="drawer?.supplierCode"
      :width="640"
      @close="drawer = null; editMode = false"
    >
      <div v-if="drawer">
        <!-- Summary stats -->
        <div class="drawer-stats">
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
          <div class="dstat">
            <div class="dstat-label">Created</div>
            <div class="dstat-value">{{ fmtDate(drawer.createdAt) || '—' }}</div>
          </div>
        </div>

        <!-- Read-only view -->
        <div v-if="!editMode">
          <div class="form-grid cols-2" style="margin-bottom:16px">
            <div class="field"><label>Supplier code</label><div class="input ro mono">{{ drawer.supplierCode }}</div></div>
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
            <strong>Note:</strong> Supplier code and name are immutable after creation.
          </div>
          <div class="form-grid cols-2">
            <div class="field">
              <label>Email</label>
              <input v-model="editForm.email" class="input" type="email" />
            </div>
            <div class="field">
              <label>Phone</label>
              <input v-model="editForm.phone" class="input" />
            </div>
            <div class="field" style="grid-column:1/-1">
              <label>Payment terms</label>
              <SearchableSelect
                v-model="editForm.paymentTerms"
                :options="[{ value: '', label: '— None —' }, ...PAYMENT_TERMS.map(t => ({ value: t, label: termsLabel(t) }))]"
                placeholder="Select terms"
              />
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
    <Modal :open="showDeactivate" title="Deactivate supplier" :width="480" @close="showDeactivate = false">
      <div>
        <div class="info-box" style="border-color:oklch(0.55 0.18 15);background:oklch(0.98 0.02 15);margin-bottom:16px">
          This will soft-delete <strong>{{ drawer?.name }}</strong>. All historical records are preserved and this action is audit-logged.
        </div>
        <div class="field">
          <label>Reason <span class="req">*</span></label>
          <input v-model="deactivateReason" class="input" placeholder="e.g. Duplicate / supplier ceased trading" />
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

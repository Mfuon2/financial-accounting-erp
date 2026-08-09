<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { categories as categoriesApi } from '@/api/index.js'
import { invalidateAllCategoryCaches } from '@/composables/useCategoryCache.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader   from '@/components/PageHeader.vue'
import Button       from '@/components/primitives/Button.vue'
import Badge        from '@/components/primitives/Badge.vue'
import IconBtn      from '@/components/primitives/IconBtn.vue'
import Modal        from '@/components/overlays/Modal.vue'

const { currentUser } = useAuth()
const { toast } = useToast()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')
const canManage = computed(() => ['SYSTEM_ADMIN', 'CONTROLLER_CFO'].includes(currentUser.value?.role))

const TABS = [
  { id: 'PAYMENT_TERM', label: 'Payment Terms' },
  { id: 'PAYMENT_METHOD', label: 'Payment Methods' },
  { id: 'DOCUMENT_TYPE', label: 'Document Types' },
]

const tab = ref('PAYMENT_TERM')
const list = ref([])
const loading = ref(false)

const tabs = computed(() => TABS.map(t => ({ ...t, count: t.id === tab.value ? list.value.length : undefined })))

async function load() {
  loading.value = true
  try {
    const data = await categoriesApi.list(entityId.value, tab.value, false)
    list.value = Array.isArray(data) ? [...data].sort((a, b) => a.sortOrder - b.sortOrder) : []
  } catch {
    toast.error('Failed to load categories.')
  } finally {
    loading.value = false
  }
}
onMounted(load)
watch(tab, load)

const active = computed(() => list.value.filter(c => c.isActive))
const inactive = computed(() => list.value.filter(c => !c.isActive))

// ── Create ─────────────────────────────────────────────────────────────────────
const showNew = ref(false)
const newSaving = ref(false)
const newForm = ref({ code: '', label: '' })

function openNew() {
  newForm.value = { code: '', label: '' }
  showNew.value = true
}

async function saveNew() {
  if (!newForm.value.code.trim() || !newForm.value.label.trim()) {
    return toast.warn('Code and label are required.')
  }
  newSaving.value = true
  try {
    const created = await categoriesApi.create(entityId.value, tab.value, newForm.value.code.trim(), newForm.value.label.trim())
    list.value = [...list.value, created].sort((a, b) => a.sortOrder - b.sortOrder)
    invalidateAllCategoryCaches()
    showNew.value = false
    toast.success(`${created.code} added.`)
  } catch { /* handled by client */ } finally { newSaving.value = false }
}

// ── Edit ───────────────────────────────────────────────────────────────────────
const editItem = ref(null)
const editForm = ref({ label: '' })
const editSaving = ref(false)

function openEdit(c) {
  editItem.value = c
  editForm.value = { label: c.label }
}

async function saveEdit() {
  if (!editForm.value.label.trim()) return toast.warn('Label is required.')
  editSaving.value = true
  try {
    const updated = await categoriesApi.update(editItem.value.id, { label: editForm.value.label.trim() })
    const idx = list.value.findIndex(c => c.id === editItem.value.id)
    if (idx !== -1) list.value[idx] = { ...list.value[idx], ...updated }
    invalidateAllCategoryCaches()
    editItem.value = null
    toast.success('Category updated.')
  } catch { /* handled */ } finally { editSaving.value = false }
}

// ── Reorder ────────────────────────────────────────────────────────────────────
async function move(c, direction) {
  const siblings = active.value // reorder only within the active set, top-to-bottom
  const idx = siblings.findIndex(s => s.id === c.id)
  const swapWith = siblings[idx + direction]
  if (!swapWith) return
  try {
    const [a, b] = await Promise.all([
      categoriesApi.update(c.id, { sortOrder: swapWith.sortOrder }),
      categoriesApi.update(swapWith.id, { sortOrder: c.sortOrder }),
    ])
    const ia = list.value.findIndex(x => x.id === c.id)
    const ib = list.value.findIndex(x => x.id === swapWith.id)
    if (ia !== -1) list.value[ia] = { ...list.value[ia], ...a }
    if (ib !== -1) list.value[ib] = { ...list.value[ib], ...b }
    list.value = [...list.value].sort((x, y) => x.sortOrder - y.sortOrder)
    invalidateAllCategoryCaches()
  } catch { /* handled */ }
}

// ── Deactivate / activate ──────────────────────────────────────────────────────
const deactivateItem = ref(null)
const deactivateReason = ref('')
const deactivating = ref(false)

function openDeactivate(c) { deactivateItem.value = c; deactivateReason.value = '' }

async function confirmDeactivate() {
  if (!deactivateReason.value.trim()) return toast.warn('Reason is required.')
  deactivating.value = true
  try {
    const updated = await categoriesApi.deactivate(deactivateItem.value.id, deactivateReason.value.trim())
    const idx = list.value.findIndex(c => c.id === deactivateItem.value.id)
    if (idx !== -1) list.value[idx] = { ...list.value[idx], ...updated, isActive: false }
    invalidateAllCategoryCaches()
    deactivateItem.value = null
    toast.success('Category deactivated.')
  } catch { /* handled */ } finally { deactivating.value = false }
}

async function activate(c) {
  try {
    const updated = await categoriesApi.activate(c.id)
    const idx = list.value.findIndex(x => x.id === c.id)
    if (idx !== -1) list.value[idx] = { ...list.value[idx], ...updated, isActive: true }
    invalidateAllCategoryCaches()
    toast.success('Category reactivated.')
  } catch { /* handled */ }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Categories"
      meta="Payment terms · payment methods · document types — entity-managed values used across Customers, Suppliers, Bills, Payments, Invoices and Source Documents"
      :tabs="tabs"
      :activeTab="tab"
      @tab="tab = $event"
    >
      <Button v-if="canManage" variant="primary" icon="plus" @click="openNew">New value</Button>
    </PageHeader>

    <div class="page-section stack">
      <div v-if="!canManage" class="info-box">
        Read-only — creating, editing, and deactivating category values requires the
        Controller/CFO or System Admin role.
      </div>

      <div class="card">
        <div v-if="loading" class="empty-state">Loading…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th style="width:70px">Order</th>
              <th>Code</th>
              <th>Label</th>
              <th>Status</th>
              <th v-if="canManage">Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(c, i) in active" :key="c.id">
              <td>
                <div class="order-controls">
                  <IconBtn icon="chev-right" style="transform:rotate(-90deg)" :disabled="!canManage || i === 0" @click="move(c, -1)" />
                  <IconBtn icon="chev-right" style="transform:rotate(90deg)" :disabled="!canManage || i === active.length - 1" @click="move(c, 1)" />
                </div>
              </td>
              <td><span class="code-cell">{{ c.code }}</span></td>
              <td class="fw-500">{{ c.label }}</td>
              <td><Badge status="active" :dot="false" /></td>
              <td v-if="canManage">
                <Button variant="ghost" size="sm" icon="edit" @click="openEdit(c)">Edit</Button>
                <Button variant="ghost" size="sm" icon="reject" @click="openDeactivate(c)">Deactivate</Button>
              </td>
            </tr>
            <tr v-for="c in inactive" :key="c.id" class="muted">
              <td></td>
              <td><span class="code-cell">{{ c.code }}</span></td>
              <td>{{ c.label }}</td>
              <td><Badge status="inactive" :dot="false" /></td>
              <td v-if="canManage">
                <Button variant="ghost" size="sm" icon="plus" @click="activate(c)">Reactivate</Button>
              </td>
            </tr>
            <tr v-if="!loading && !list.length">
              <td :colspan="canManage ? 5 : 4" class="empty-state">No category values yet.</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- New value modal -->
    <Modal :open="showNew" :title="`New ${TABS.find(t => t.id === tab)?.label.slice(0, -1)}`" :width="480" @close="showNew = false">
      <div class="form-grid cols-2">
        <div class="field">
          <label>Code <span class="req">*</span></label>
          <input v-model="newForm.code" class="input mono" style="text-transform:uppercase" placeholder="e.g. NET_120" />
        </div>
        <div class="field">
          <label>Label <span class="req">*</span></label>
          <input v-model="newForm.label" class="input" placeholder="e.g. Net 120" />
        </div>
      </div>
      <template #footer>
        <Button variant="primary" icon="plus" :loading="newSaving" @click="saveNew">Add</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- Edit modal -->
    <Modal :open="!!editItem" :title="`Edit — ${editItem?.code}`" :width="420" @close="editItem = null">
      <div class="field">
        <label>Label</label>
        <input v-model="editForm.label" class="input" />
      </div>
      <div class="info-box" style="margin-top:12px">
        <strong>Note:</strong> The code ({{ editItem?.code }}) is immutable — it may already be
        stored on existing records. Only the display label can change.
      </div>
      <template #footer>
        <Button variant="primary" :loading="editSaving" @click="saveEdit">Save</Button>
        <Button variant="ghost" @click="editItem = null">Cancel</Button>
      </template>
    </Modal>

    <!-- Deactivate modal -->
    <Modal :open="!!deactivateItem" title="Deactivate category value" :width="480" @close="deactivateItem = null">
      <div>
        <div class="info-box" style="border-color:oklch(0.55 0.18 15);background:oklch(0.98 0.02 15);margin-bottom:16px">
          This will soft-delete <strong>{{ deactivateItem?.label }}</strong>. Existing records
          referencing it are unaffected and keep resolving correctly.
        </div>
        <div class="field">
          <label>Reason <span class="req">*</span></label>
          <input v-model="deactivateReason" class="input" placeholder="e.g. No longer used by the business" />
        </div>
      </div>
      <template #footer>
        <Button variant="danger" :loading="deactivating" @click="confirmDeactivate">Confirm deactivation</Button>
        <Button variant="ghost" @click="deactivateItem = null">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.order-controls { display: flex; gap: 2px; }
.req { color: var(--danger, oklch(0.55 0.18 15)); }
.empty-state { text-align: center; padding: 40px; color: var(--muted); font-size: 13px; }
.muted { color: var(--muted); }
.fw-500 { font-weight: 500; }
</style>

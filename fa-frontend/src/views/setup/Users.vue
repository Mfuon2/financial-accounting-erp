<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { users as usersApi } from '@/api/index.js'
import { fmtDate } from '@/utils/format.js'
import { useToast } from '@/composables/useToast.js'
import { useAuth } from '@/composables/useAuth.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Ico from '@/components/primitives/Ico.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { toast } = useToast()
const { currentUser } = useAuth()

const ROLES = ['SYSTEM_ADMIN', 'CONTROLLER_CFO', 'SENIOR_ACCOUNTANT', 'ACCOUNTANT', 'AUDITOR', 'DATA_ENTRY']
const roleStatus = { SYSTEM_ADMIN: 'solid-fg', CONTROLLER_CFO: 'pending', SENIOR_ACCOUNTANT: 'info', ACCOUNTANT: 'info', AUDITOR: 'pending', DATA_ENTRY: 'outline' }

const usersList = ref([])
const drawer = ref(null)
const form = ref(null)
const showNew = ref(false)
const saving = ref(false)
const newForm = ref({ fullName: '', email: '', username: '', role: 'ACCOUNTANT', welcomeMessage: '' })
const search = ref('')
const deactivateReason = ref('')
const showDeactivate = ref(false)

onMounted(async () => {
  try {
    const entityId = currentUser.value?.entityId ?? 'current'
    const data = await usersApi.list({ entityId })
    if (Array.isArray(data)) usersList.value = data
    else if (data?.content) usersList.value = data.content
  } catch { /* leave list empty on API error */ }
})

watch(drawer, (u) => {
  if (u) form.value = { ...u }
  else { form.value = null; showDeactivate.value = false; deactivateReason.value = '' }
})

const filtered = computed(() => {
  const q = search.value.toLowerCase()
  if (!q) return usersList.value
  return usersList.value.filter(u =>
    u.fullName.toLowerCase().includes(q) ||
    u.username.toLowerCase().includes(q) ||
    u.email.toLowerCase().includes(q)
  )
})

function initials(name) {
  return name.split(' ').map(p => p[0]).join('').slice(0, 2)
}

function permissions(role) {
  return [
    { label: 'Post journal entries',       allow: ['ACCOUNTANT','SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN'].includes(role) },
    { label: 'Approve journal entries',    allow: ['SENIOR_ACCOUNTANT','CONTROLLER_CFO','SYSTEM_ADMIN'].includes(role) },
    { label: 'Close accounting periods',   allow: ['CONTROLLER_CFO','SYSTEM_ADMIN'].includes(role) },
    { label: 'Generate financial statements', allow: ['SENIOR_ACCOUNTANT','CONTROLLER_CFO','AUDITOR','SYSTEM_ADMIN'].includes(role) },
    { label: 'Manage users & API keys',    allow: role === 'SYSTEM_ADMIN' },
    { label: 'Read-only audit access',     allow: ['AUDITOR','SYSTEM_ADMIN'].includes(role) },
    { label: 'Data entry only',            allow: role === 'DATA_ENTRY' },
  ]
}

async function save() {
  if (!form.value) return
  saving.value = true
  try {
    await Promise.all([
      usersApi.updateProfile(form.value.id, form.value.fullName),
      usersApi.updateRole(form.value.id, form.value.role),
    ])
    const idx = usersList.value.findIndex(u => u.id === form.value.id)
    if (idx !== -1) usersList.value[idx] = { ...usersList.value[idx], ...form.value }
    drawer.value = { ...usersList.value.find(u => u.id === form.value.id) }
    toast.success(`${form.value.fullName} updated.`)
  } catch { /* toast from apiFetch */ } finally {
    saving.value = false
  }
}

async function toggleActive() {
  if (!form.value) return
  if (form.value.active) {
    // Deactivating — need a reason
    if (!showDeactivate.value) { showDeactivate.value = true; return }
    if (!deactivateReason.value.trim()) { toast.warn('Please enter a reason for deactivation.'); return }
    try {
      await usersApi.deactivate(form.value.id, deactivateReason.value.trim())
      const idx = usersList.value.findIndex(u => u.id === form.value.id)
      if (idx !== -1) usersList.value[idx] = { ...usersList.value[idx], active: false }
      drawer.value = { ...usersList.value[idx] }
      toast.success(`${form.value.fullName} deactivated.`)
      showDeactivate.value = false
      deactivateReason.value = ''
    } catch { /* handled */ }
  } else {
    try {
      await usersApi.reactivate(form.value.id)
      const idx = usersList.value.findIndex(u => u.id === form.value.id)
      if (idx !== -1) usersList.value[idx] = { ...usersList.value[idx], active: true }
      drawer.value = { ...usersList.value[idx] }
      toast.success(`${form.value.fullName} reactivated.`)
    } catch { /* handled */ }
  }
}

async function resetPassword() {
  if (!form.value) return
  try {
    await usersApi.resetPassword(form.value.id)
    toast.success('Password reset email sent.')
  } catch { /* handled */ }
}

async function sendInvite() {
  if (!newForm.value.fullName || !newForm.value.email || !newForm.value.role) {
    toast.warn('Full name, email, and role are required.')
    return
  }
  try {
    const created = await usersApi.create({
      fullName: newForm.value.fullName,
      email: newForm.value.email,
      username: newForm.value.username || undefined,
      role: newForm.value.role,
    })
    if (created) usersList.value.unshift(created)
    toast.success(`Invite sent to ${newForm.value.email}.`)
    newForm.value = { fullName: '', email: '', username: '', role: 'ACCOUNTANT', welcomeMessage: '' }
    showNew.value = false
  } catch { /* handled */ }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Users & Roles"
      :meta="`${usersList.filter(u => u.active).length} active members · RBAC enforced`"
    >
      <Button variant="ghost" icon="envelope">Invite</Button>
      <Button variant="primary" icon="plus" @click="showNew = true">New user</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search"/>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>User</th>
              <th>Email</th>
              <th>Role</th>
              <th>MFA</th>
              <th>Last login</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="u in filtered" :key="u.id" class="row-link" @click="drawer = u">
              <td>
                <div class="h-row" style="gap:8px">
                  <div class="avatar">{{ initials(u.fullName) }}</div>
                  <div>
                    <div>{{ u.fullName }}</div>
                    <div class="muted" style="font-size:11px">@{{ u.username }}</div>
                  </div>
                </div>
              </td>
              <td class="muted">{{ u.email }}</td>
              <td><Badge :status="roleStatus[u.role] || 'outline'" :dot="false">{{ u.role }}</Badge></td>
              <td>
                <Badge :status="u.mfa ? 'posted' : 'draft'" :dot="false">{{ u.mfa ? 'ON' : 'OFF' }}</Badge>
              </td>
              <td class="muted" style="font-size:12px">{{ u.lastLogin }}</td>
              <td><Badge :status="u.active ? 'active' : 'inactive'" :dot="false" /></td>
              <td @click.stop>
                <IconBtn icon="dots" @click="drawer = u"/>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="users"/>
    </div>

    <!-- Edit user drawer -->
    <Modal
      :open="drawer !== null"
      :title="drawer?.fullName"
      :subtitle="`${drawer?.role} · ${drawer?.email}`"
      :width="780"
      @close="drawer = null"
    >
      <template v-if="form">
        <div class="form-grid cols-2">
          <div class="field">
            <label>Full name</label>
            <input class="input" v-model="form.fullName"/>
          </div>
          <div class="field">
            <label>Username</label>
            <input class="input mono" :value="form.username" disabled/>
          </div>
          <div class="field" style="grid-column:span 2">
            <label>Email</label>
            <input class="input" :value="form.email" disabled/>
          </div>
          <div class="field">
            <label>Role</label>
            <SearchableSelect
              v-model="form.role"
              :options="ROLES.map(r => ({ value: r, label: r }))"
              placeholder="Select role"
            />
          </div>
        </div>

        <!-- Deactivation reason (shown inline when toggling active → inactive) -->
        <div v-if="showDeactivate && form.active" class="deact-box">
          <label class="field-label">Reason for deactivation <span class="req">*</span></label>
          <input class="input" v-model="deactivateReason" placeholder="e.g. Left the company" autofocus />
        </div>

        <div class="card">
          <div class="card-head">Permissions</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>Permission</th>
                <th>Access</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="p in permissions(form.role)" :key="p.label">
                <td>{{ p.label }}</td>
                <td>
                  <Badge :status="p.allow ? 'posted' : 'void'" :dot="false">{{ p.allow ? 'ALLOW' : 'DENY' }}</Badge>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template #footer>
        <Button variant="primary" :loading="saving" @click="save">Save</Button>
        <Button variant="ghost" icon="key" @click="resetPassword">Reset password</Button>
        <div style="flex:1"/>
        <Button :variant="form?.active ? 'danger' : 'ghost'" icon="lock" @click="toggleActive">
          {{ form?.active ? (showDeactivate ? 'Confirm deactivate' : 'Deactivate') : 'Reactivate' }}
        </Button>
      </template>
    </Modal>

    <!-- Invite new user -->
    <Modal
      :open="showNew"
      title="Invite new user"
      subtitle="New member will receive an email invite"
      :width="560"
      @close="showNew = false"
    >
      <div class="form-grid cols-2">
        <div class="field">
          <label>Full name <span class="req">*</span></label>
          <input class="input" v-model="newForm.fullName" placeholder="Jane Muriuki"/>
        </div>
        <div class="field">
          <label>Email <span class="req">*</span></label>
          <input class="input" v-model="newForm.email" placeholder="jane@apollo.co.ke"/>
        </div>
        <div class="field">
          <label>Username</label>
          <input class="input mono" v-model="newForm.username" placeholder="j.muriuki"/>
        </div>
        <div class="field">
          <label>Role <span class="req">*</span></label>
          <SearchableSelect
            v-model="newForm.role"
            :options="ROLES.map(r => ({ value: r, label: r }))"
            placeholder="Select role"
          />
        </div>
        <div class="field" style="grid-column:span 2">
          <label>Welcome message</label>
          <textarea class="textarea" rows="3" v-model="newForm.welcomeMessage" placeholder="Optional note to include in the invite email..."></textarea>
        </div>
      </div>

      <template #footer>
        <Button variant="primary" icon="envelope" @click="sendInvite">Send invite</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.deact-box {
  margin: 12px 0;
  padding: 12px 14px;
  background: color-mix(in oklab, oklch(0.5 0.22 25) 8%, var(--surface));
  border: 1px solid color-mix(in oklab, oklch(0.5 0.22 25) 30%, transparent);
  border-radius: 6px;
}
.field-label { font-size: 12px; font-weight: 600; display: block; margin-bottom: 6px; }
.req { color: oklch(0.5 0.22 25); }
</style>

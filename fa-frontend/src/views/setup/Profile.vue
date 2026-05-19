<script setup>
import { ref, computed } from 'vue'
import { users as usersApi, auth } from '@/api/index.js'
import { useToast } from '@/composables/useToast.js'
import { useAuth } from '@/composables/useAuth.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import Ico from '@/components/primitives/Ico.vue'
import Modal from '@/components/overlays/Modal.vue'

const { toast } = useToast()
const { currentUser, updateUser, logout } = useAuth()

const editingName  = ref(false)
const nameInput    = ref('')
const savingName   = ref(false)
const showPwd      = ref(false)
const pwdForm      = ref({ current: '', next: '', confirm: '' })
const savingPwd    = ref(false)

const initials = computed(() => {
  const name = currentUser.value?.fullName ?? ''
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length >= 2) return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  return name.slice(0, 2).toUpperCase() || '?'
})

const roleLabel = computed(() => (currentUser.value?.role ?? '').replace(/_/g, ' '))

const statusVariant = computed(() => {
  switch (currentUser.value?.status) {
    case 'ACTIVE':   return 'posted'
    case 'INACTIVE': return 'void'
    default:         return 'outline'
  }
})

function startEditName() {
  nameInput.value = currentUser.value?.fullName ?? ''
  editingName.value = true
}

function cancelEditName() {
  editingName.value = false
  nameInput.value = ''
}

async function saveName() {
  const name = nameInput.value.trim()
  if (!name || name === currentUser.value?.fullName) { cancelEditName(); return }
  savingName.value = true
  try {
    await usersApi.updateProfile(currentUser.value.id, name)
    updateUser({ fullName: name })
    toast.success('Display name updated.')
    editingName.value = false
  } catch {
    toast.error('Failed to update name.')
  } finally {
    savingName.value = false
  }
}

async function changePassword() {
  if (!pwdForm.value.current || !pwdForm.value.next) {
    toast.warn('Current and new passwords are required.')
    return
  }
  if (pwdForm.value.next !== pwdForm.value.confirm) {
    toast.warn('New passwords do not match.')
    return
  }
  if (pwdForm.value.next.length < 8) {
    toast.warn('New password must be at least 8 characters.')
    return
  }
  savingPwd.value = true
  try {
    await auth.changePassword({ currentPassword: pwdForm.value.current, newPassword: pwdForm.value.next })
    toast.success('Password changed. You will be signed out.')
    showPwd.value = false
    pwdForm.value = { current: '', next: '', confirm: '' }
    setTimeout(() => logout(), 1500)
  } catch {
    toast.error('Failed to change password. Check your current password.')
  } finally {
    savingPwd.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader title="My Profile" meta="Account details and password" />

    <div class="page-section stack">
      <div class="row-2" style="grid-template-columns:1fr 1fr">

        <!-- Identity card -->
        <div class="card">
          <div class="card-head">
            <Ico name="users" :size="16"/>
            <div class="card-title">Identity</div>
          </div>
          <div class="card-body">
            <div style="display:flex;align-items:center;gap:16px;margin-bottom:20px">
              <div class="user-avatar" style="width:52px;height:52px;font-size:18px;flex-shrink:0">
                {{ initials }}
              </div>
              <div>
                <div style="font-size:15px;font-weight:600;line-height:1.2">{{ currentUser?.fullName ?? '—' }}</div>
                <div class="muted" style="font-size:12px">{{ currentUser?.email ?? '—' }}</div>
              </div>
            </div>

            <div class="form-grid cols-1" style="gap:10px">
              <div class="field">
                <label class="label">Display name</label>
                <div v-if="!editingName" class="h-row" style="gap:8px">
                  <span style="font-size:13px">{{ currentUser?.fullName ?? '—' }}</span>
                  <button class="link-btn" @click="startEditName">Edit</button>
                </div>
                <div v-else class="h-row" style="gap:6px">
                  <input
                    v-model="nameInput"
                    class="inp"
                    style="flex:1"
                    placeholder="Full name"
                    @keyup.enter="saveName"
                    @keyup.escape="cancelEditName"
                  />
                  <Button size="sm" :loading="savingName" @click="saveName">Save</Button>
                  <Button size="sm" variant="ghost" @click="cancelEditName">Cancel</Button>
                </div>
              </div>

              <div class="field">
                <label class="label">Email</label>
                <span class="muted" style="font-size:13px">{{ currentUser?.email ?? '—' }}</span>
              </div>

              <div class="field">
                <label class="label">Role</label>
                <span style="font-size:13px;font-weight:500">{{ roleLabel }}</span>
              </div>

              <div class="field">
                <label class="label">Status</label>
                <Badge :status="statusVariant" :dot="false">{{ currentUser?.status ?? '—' }}</Badge>
              </div>
            </div>
          </div>
        </div>

        <!-- Security card -->
        <div class="card">
          <div class="card-head">
            <Ico name="lock" :size="16"/>
            <div class="card-title">Password</div>
          </div>
          <div class="card-body">
            <div class="muted" style="font-size:12.5px;margin-bottom:16px">
              Use a strong password you don't use anywhere else. After changing your password you will be signed out of all other sessions.
            </div>
            <Button variant="ghost" icon="lock" @click="showPwd = true">Change password</Button>
          </div>
        </div>
      </div>
    </div>

    <!-- Change password modal -->
    <Modal
      :open="showPwd"
      title="Change password"
      subtitle="You will be signed out of all other sessions"
      :width="480"
      @close="showPwd = false"
    >
      <div class="form-grid cols-1">
        <div class="field">
          <label class="label">Current password</label>
          <input v-model="pwdForm.current" type="password" class="inp" placeholder="Current password" autocomplete="current-password"/>
        </div>
        <div class="field">
          <label class="label">New password</label>
          <input v-model="pwdForm.next" type="password" class="inp" placeholder="At least 8 characters" autocomplete="new-password"/>
        </div>
        <div class="field">
          <label class="label">Confirm new password</label>
          <input v-model="pwdForm.confirm" type="password" class="inp" placeholder="Repeat new password" autocomplete="new-password" @keyup.enter="changePassword"/>
        </div>
      </div>
      <template #footer>
        <Button variant="ghost" @click="showPwd = false">Cancel</Button>
        <Button :loading="savingPwd" @click="changePassword">Update password</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.link-btn {
  background: none;
  border: none;
  color: var(--accent);
  cursor: pointer;
  font-size: 12px;
  padding: 0;
  text-decoration: underline;
}
.link-btn:hover { opacity: 0.75; }
</style>

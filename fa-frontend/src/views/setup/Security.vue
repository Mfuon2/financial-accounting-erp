<script setup>
import { ref, onMounted } from 'vue'
import { SESSIONS } from '@/data/index.js'
import { auth } from '@/api/index.js'
import { useToast } from '@/composables/useToast.js'
import { useAuth } from '@/composables/useAuth.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Ico from '@/components/primitives/Ico.vue'
import Banner from '@/components/data-display/Banner.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const { toast } = useToast()
const { getSessionStart } = useAuth()

const sessions = ref([...SESSIONS])
const showPwd = ref(false)
const pwdForm = ref({ current: '', next: '', confirm: '' })
const savingPwd = ref(false)
const revokingAll = ref(false)

onMounted(async () => {
  try {
    const data = await auth.sessions()
    if (data && data.length) {
      // Identify current session: find the one whose issuedAt is closest to our
      // stored session start timestamp (set when we last logged in or refreshed).
      const sessionStart = getSessionStart()
      let currentId = null
      if (sessionStart) {
        let minDiff = Infinity
        data.forEach(s => {
          const diff = Math.abs(new Date(s.issuedAt).getTime() - sessionStart)
          if (diff < minDiff) { minDiff = diff; currentId = s.id }
        })
      } else {
        // Fallback: newest session is most likely current
        const sorted = [...data].sort((a, b) => new Date(b.issuedAt) - new Date(a.issuedAt))
        currentId = sorted[0]?.id
      }
      sessions.value = data.map(s => ({ ...s, current: s.id === currentId }))
    }
  } catch { /* stays on demo */ }
})

function formatSession(s) {
  const ua = s.userAgent ?? s.device ?? 'Unknown device'
  const ip = s.clientIp  ?? s.ip       ?? '—'
  const ts = s.issuedAt  ?? s.started  ?? '—'
  return { ...s, device: ua, ip, started: ts }
}

async function revokeSession(s) {
  try {
    await auth.revokeSession(s.id)
    sessions.value = sessions.value.filter(x => x.id !== s.id)
    toast.success('Session revoked.')
  } catch { /* handled */ }
}

async function revokeAllOthers() {
  revokingAll.value = true
  try {
    await auth.revokeAllOthers()
    // Keep only the current session in the list
    sessions.value = sessions.value.filter(s => s.current)
    toast.success('All other sessions signed out.')
  } catch { /* handled */ } finally {
    revokingAll.value = false
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
    await auth.changePassword({
      currentPassword: pwdForm.value.current,
      newPassword: pwdForm.value.next,
    })
    toast.success('Password changed. Please log in again on other devices.')
    pwdForm.value = { current: '', next: '', confirm: '' }
    showPwd.value = false
    // Backend revokes all sessions after password change
    sessions.value = sessions.value.filter(s => s.current)
  } catch { /* handled */ } finally {
    savingPwd.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Security"
      meta="Password, MFA & active sessions"
    />

    <div class="page-section stack">
      <div class="row-2" style="grid-template-columns:1fr 1fr">
        <div class="card">
          <div class="card-head">
            <Ico name="lock" :size="16"/>
            <div class="card-title">Password</div>
          </div>
          <div class="card-body">
            <div class="muted" style="font-size:12.5px;margin-bottom:12px">
              Use a strong password you don't use anywhere else. We recommend at least 16 characters.
            </div>
            <Button variant="ghost" icon="lock" @click="showPwd = true">Change password</Button>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <Ico name="shield" :size="16"/>
            <div class="card-title">Two-factor authentication</div>
          </div>
          <div class="card-body">
            <div class="h-row" style="justify-content:space-between;padding:8px 0;border-bottom:1px solid var(--border)">
              <div>
                <div style="font-size:13px;font-weight:500">Authenticator app</div>
                <div class="muted" style="font-size:11.5px">TOTP via Google Authenticator or Authy</div>
              </div>
              <Badge status="posted" :dot="false">ENABLED</Badge>
            </div>
            <div class="h-row" style="justify-content:space-between;padding-top:10px">
              <div>
                <div style="font-size:13px;font-weight:500">Recovery codes</div>
                <div class="muted" style="font-size:11.5px">8 of 10 remaining</div>
              </div>
              <Button variant="ghost" size="sm" icon="eye">View codes</Button>
            </div>
          </div>
        </div>
      </div>

      <div class="card" style="margin-top:var(--gap)">
        <div class="card-head">
          <Ico name="globe" :size="16"/>
          <div class="card-title">Active sessions · {{ sessions.length }}</div>
        </div>
        <table class="tbl">
          <thead>
            <tr>
              <th>Device</th>
              <th>IP address</th>
              <th>Location</th>
              <th>Started</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in sessions" :key="s.id">
              <td>
                <div class="h-row" style="gap:6px">
                  {{ formatSession(s).device }}
                  <Badge v-if="s.current" status="posted" :dot="false" style="font-size:10px">This device</Badge>
                </div>
              </td>
              <td class="mono muted" style="font-size:12px">{{ formatSession(s).ip }}</td>
              <td class="muted" style="font-size:12px">{{ s.location ?? '—' }}</td>
              <td class="muted" style="font-size:12px">{{ formatSession(s).started }}</td>
              <td>
                <Button v-if="!s.current" variant="ghost" size="sm" icon="x" @click="revokeSession(s)">Revoke</Button>
              </td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="sessions.length" :defaultSize="10" label="sessions"/>
        <div class="drawer-foot" style="border-top:1px solid var(--border);padding-top:12px;margin-top:4px">
          <Button variant="danger" icon="logout" :loading="revokingAll" @click="revokeAllOthers">
            Sign out all other sessions
          </Button>
        </div>
      </div>
    </div>

    <Modal
      :open="showPwd"
      title="Change password"
      subtitle="You will be signed out of all other sessions"
      :width="480"
      @close="showPwd = false"
    >
      <div class="form-grid cols-2">
        <div class="field" style="grid-column:span 2">
          <label>Current password</label>
          <input class="input" type="password" v-model="pwdForm.current" placeholder="••••••••••••"/>
        </div>
        <div class="field">
          <label>New password</label>
          <input class="input" type="password" v-model="pwdForm.next" placeholder="••••••••••••"/>
        </div>
        <div class="field">
          <label>Confirm new password</label>
          <input class="input" type="password" v-model="pwdForm.confirm" placeholder="••••••••••••"/>
        </div>
      </div>
      <Banner kind="info">Changing your password will sign you out of all other active sessions.</Banner>

      <template #footer>
        <Button variant="primary" icon="lock" :loading="savingPwd" @click="changePassword">Update password</Button>
        <Button variant="ghost" @click="showPwd = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

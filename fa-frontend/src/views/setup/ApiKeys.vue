<script setup>
import { ref, computed, onMounted } from 'vue'
import { users as usersApi } from '@/api/index.js'
import { fmtDate } from '@/utils/format.js'
import { useToast } from '@/composables/useToast.js'
import { useAuth } from '@/composables/useAuth.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import Banner from '@/components/data-display/Banner.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { toast } = useToast()
const { currentUser } = useAuth()

const SCOPE_OPTIONS = [
  { value: 'read:*',                        label: 'read:*' },
  { value: 'read:invoices, write:payments', label: 'read:invoices, write:payments' },
  { value: 'callback:payments',             label: 'callback:payments' },
  { value: 'read:*, write:*',               label: 'read:*, write:*' },
]

const EXPIRY_OPTIONS = [
  { value: 'never',  label: 'Never' },
  { value: '30d',    label: '30 days' },
  { value: '90d',    label: '90 days' },
  { value: '1y',     label: '1 year' },
  { value: '2y',     label: '2 years' },
]

const apiKeysList = ref([])
const showNew = ref(false)
const newKey = ref(null)
const search = ref('')
const saving = ref(false)

const newKeyForm = ref({ name: '', scope: 'read:*', expires: 'never' })

// Revocation state
const revokingKey = ref(null)
const revokeReason = ref('')
const revoking = ref(false)

// Rotation state
const rotatingKey = ref(null)
const rotating = ref(false)

async function loadKeys() {
  try {
    const entityId = currentUser.value?.entityId
    if (!entityId) return
    const data = await usersApi.apiKeys({ entityId })
    if (Array.isArray(data)) {
      apiKeysList.value = data
    } else if (data?.content) {
      apiKeysList.value = data.content
    }
  } catch {
    apiKeysList.value = []
  }
}

onMounted(() => {
  loadKeys()
})

const filtered = computed(() => {
  const q = search.value.toLowerCase().trim()
  if (!q) return apiKeysList.value
  return apiKeysList.value.filter(k =>
    (k.name ?? '').toLowerCase().includes(q) ||
    (k.keyId ?? '').toLowerCase().includes(q) ||
    (k.scopes ?? []).some(s => s.toLowerCase().includes(q))
  )
})

function getExpiresAt(expires) {
  if (!expires || expires === 'never') return null
  let ms = 0
  if (expires === '30d') ms = 30 * 24 * 60 * 60 * 1000
  else if (expires === '90d') ms = 90 * 24 * 60 * 60 * 1000
  else if (expires === '1y') ms = 365 * 24 * 60 * 60 * 1000
  else if (expires === '2y') ms = 2 * 365 * 24 * 60 * 60 * 1000
  return new Date(Date.now() + ms).toISOString()
}

async function onGenerate() {
  if (!newKeyForm.value.name.trim()) {
    toast.warn('Please enter a label for the API key.')
    return
  }
  saving.value = true
  try {
    const scopesList = newKeyForm.value.scope.split(',').map(s => s.trim())
    const created = await usersApi.createApiKey({
      entityId: currentUser.value?.entityId,
      name: newKeyForm.value.name.trim(),
      role: currentUser.value?.role || 'DATA_ENTRY',
      scopes: scopesList,
      expiresAt: getExpiresAt(newKeyForm.value.expires)
    })
    newKey.value = created
    await loadKeys()
    toast.success('API key generated successfully.')
  } catch {
    // Handled by fetch client
  } finally {
    saving.value = false
  }
}

function onCopied() {
  showNew.value = false
  newKey.value = null
  newKeyForm.value = { name: '', scope: 'read:*', expires: 'never' }
}

function startRevoke(key) {
  revokingKey.value = key
  revokeReason.value = ''
}

async function confirmRevoke() {
  if (!revokeReason.value.trim()) {
    toast.warn('Please enter a reason for revocation.')
    return
  }
  revoking.value = true
  try {
    await usersApi.revokeApiKey(revokingKey.value.id, revokeReason.value.trim())
    await loadKeys()
    toast.success('API key revoked successfully.')
    revokingKey.value = null
  } catch {
    // Handled
  } finally {
    revoking.value = false
  }
}

function startRotate(key) {
  rotatingKey.value = key
}

async function confirmRotate() {
  rotating.value = true
  try {
    const result = await usersApi.rotateApiKey(rotatingKey.value.id)
    newKey.value = result
    await loadKeys()
    toast.success('API key rotated successfully.')
    rotatingKey.value = null
  } catch {
    // Handled
  } finally {
    rotating.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="API Keys"
      :meta="`${apiKeysList.filter(k => k.status === 'ACTIVE').length} active · used by third-party integrations`"
    >
      <Button variant="ghost" icon="external">API docs</Button>
      <Button variant="primary" icon="plus" @click="showNew = true">Generate key</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search"/>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Label</th>
              <th>Key prefix</th>
              <th>Scope</th>
              <th>Created</th>
              <th>Last used</th>
              <th>Expires</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="k in filtered" :key="k.id">
              <td>{{ k.name }}</td>
              <td><code class="code-cell">{{ k.keyId }}…</code></td>
              <td class="mono muted" style="font-size:11px">{{ k.scopes?.join(', ') }}</td>
              <td class="muted" style="font-size:12px">{{ fmtDate(k.createdAt) }}</td>
              <td class="muted" style="font-size:12px">{{ k.lastUsedAt ? fmtDate(k.lastUsedAt) : '—' }}</td>
              <td class="muted" style="font-size:12px">{{ k.expiresAt ? fmtDate(k.expiresAt) : 'never' }}</td>
              <td>
                <Badge :status="k.status === 'ACTIVE' ? 'posted' : 'void'" :dot="false">
                  {{ k.status === 'ACTIVE' ? 'Active' : k.status === 'REVOKED' ? 'Revoked' : 'Expired' }}
                </Badge>
              </td>
              <td @click.stop>
                <div class="h-row" style="gap:4px">
                  <Button variant="ghost" size="sm" icon="rotate" @click="startRotate(k)" :disabled="k.status !== 'ACTIVE'">Rotate</Button>
                  <Button variant="ghost" size="sm" icon="x" @click="startRevoke(k)" :disabled="k.status !== 'ACTIVE'">Revoke</Button>
                </div>
              </td>
            </tr>
            <tr v-if="filtered.length === 0">
              <td colspan="8" class="text-center muted" style="padding: 24px;">No API keys found.</td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="filtered.length" label="keys"/>
    </div>

    <!-- Generate API Key modal -->
    <Modal
      :open="showNew && !newKey"
      title="Generate API key"
      subtitle="Keys are scoped and can be revoked at any time"
      :width="520"
      @close="showNew = false"
    >
      <div class="form-grid cols-2">
        <div class="field" style="grid-column:span 2">
          <label>Label <span class="req">*</span></label>
          <input class="input" v-model="newKeyForm.name" placeholder="e.g. Mobile App — Production"/>
        </div>
        <div class="field">
          <label>Scope</label>
          <SearchableSelect
            v-model="newKeyForm.scope"
            :options="SCOPE_OPTIONS"
            placeholder="Select scope"
          />
        </div>
        <div class="field">
          <label>Expires</label>
          <SearchableSelect
            v-model="newKeyForm.expires"
            :options="EXPIRY_OPTIONS"
            placeholder="Select expiry"
          />
        </div>
      </div>

      <template #footer>
        <Button variant="primary" :loading="saving" @click="onGenerate">Generate</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- Key secret displayed once modal -->
    <Modal
      :open="!!newKey"
      title="Key generated — copy now"
      subtitle="This is the only time the secret will be visible"
      :width="520"
      @close="onCopied"
    >
      <Banner kind="warn" icon="warn">Save this key immediately. It will not be shown again.</Banner>
      <div class="field">
        <label>Secret key</label>
        <input class="input mono" :value="newKey?.fullKeyOnce" readonly style="user-select:all"/>
      </div>

      <template #footer>
        <Button variant="primary" @click="onCopied">I've copied it</Button>
      </template>
    </Modal>

    <!-- Revoke API Key Modal -->
    <Modal
      :open="revokingKey !== null"
      title="Revoke API Key"
      :subtitle="`Are you sure you want to revoke the key &quot;${revokingKey?.name}&quot;?`"
      :width="480"
      @close="revokingKey = null"
    >
      <Banner kind="warn" icon="warn">
        This action is permanent and cannot be undone. Any application using this key will immediately fail to authenticate.
      </Banner>
      <div class="field" style="margin-top: 16px;">
        <label>Reason for revocation <span class="req">*</span></label>
        <input class="input" v-model="revokeReason" placeholder="e.g. Decommissioned server" autofocus />
      </div>

      <template #footer>
        <Button variant="danger" :loading="revoking" @click="confirmRevoke">Confirm Revoke</Button>
        <Button variant="ghost" @click="revokingKey = null">Cancel</Button>
      </template>
    </Modal>

    <!-- Rotate API Key Modal -->
    <Modal
      :open="rotatingKey !== null"
      title="Rotate API Key"
      :subtitle="`Are you sure you want to rotate the key &quot;${rotatingKey?.name}&quot;?`"
      :width="480"
      @close="rotatingKey = null"
    >
      <Banner kind="warn" icon="warn">
        Rotating this key will immediately revoke the current key and issue a new one. The new key secret will be displayed once.
      </Banner>

      <template #footer>
        <Button variant="primary" :loading="rotating" @click="confirmRotate">Confirm Rotate</Button>
        <Button variant="ghost" @click="rotatingKey = null">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { API_KEYS } from '@/data/index.js'
import { fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Ico from '@/components/primitives/Ico.vue'
import Banner from '@/components/data-display/Banner.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

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

const newKeyForm = ref({ scope: 'read:*', expires: 'never' })

const showNew = ref(false)
const newKey = ref(null)
const search = ref('')

function generateSecret() {
  return `qek_live_${Math.random().toString(36).slice(2, 14)}${Math.random().toString(36).slice(2, 14)}`
}

function onGenerate() {
  newKey.value = { secret: generateSecret() }
}

function onCopied() {
  showNew.value = false
  newKey.value = null
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="API Keys"
      :meta="`${API_KEYS.filter(k => k.active).length} active · used by third-party integrations`"
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
            <tr v-for="k in API_KEYS" :key="k.id">
              <td>{{ k.label }}</td>
              <td><code class="code-cell">{{ k.prefix }}…</code></td>
              <td class="mono muted" style="font-size:11px">{{ k.scope }}</td>
              <td class="muted" style="font-size:12px">{{ fmtDate(k.created) }}</td>
              <td class="muted" style="font-size:12px">{{ k.lastUsed || '—' }}</td>
              <td class="muted" style="font-size:12px">{{ k.expires ? fmtDate(k.expires) : 'never' }}</td>
              <td><Badge :status="k.active ? 'posted' : 'void'" :dot="false">{{ k.active ? 'Active' : 'Revoked' }}</Badge></td>
              <td @click.stop>
                <div class="h-row" style="gap:4px">
                  <Button variant="ghost" size="sm" icon="rotate">Rotate</Button>
                  <Button variant="ghost" size="sm" icon="x">Revoke</Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="API_KEYS.length" label="keys"/>
    </div>

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
          <input class="input" placeholder="e.g. Mobile App — Production"/>
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
        <Button variant="primary" @click="onGenerate">Generate</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

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
        <input class="input mono" :value="newKey?.secret" readonly style="user-select:all"/>
      </div>

      <template #footer>
        <Button variant="primary" @click="onCopied">I've copied it</Button>
      </template>
    </Modal>
  </div>
</template>

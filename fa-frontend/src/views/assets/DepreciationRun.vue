<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ASSETS } from '@/data/index.js'
import { assets as assetsApi, periods as periodsApi } from '@/api/index.js'
import { useAuth } from '@/composables/useAuth.js'
import { isDemo } from '@/composables/useAppMode.js'
import { fmt } from '@/utils/format.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'
import Banner from '@/components/data-display/Banner.vue'

const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')

const allAssets  = ref([])
const periodOpts = ref([])
const periodId   = ref('')
const runDate    = ref(new Date().toISOString().slice(0, 10))
const running    = ref(false)

onMounted(async () => {
  if (isDemo.value) {
    allAssets.value = ASSETS
    return
  }
  const [aRes, pRes] = await Promise.allSettled([
    assetsApi.list(entityId.value),
    periodsApi.list({ entityId: entityId.value, size: 50 }),
  ])
  if (aRes.status === 'fulfilled') {
    const d = aRes.value
    allAssets.value = d?.content ?? (Array.isArray(d) ? d : [])
  }
  if (pRes.status === 'fulfilled') {
    const d = pRes.value
    const list = d?.content ?? d ?? []
    periodOpts.value = Array.isArray(list) ? list : []
    const open = periodOpts.value.find(p => p.status === 'OPEN')
    if (open) periodId.value = open.id
  }
})

const eligible = computed(() =>
  allAssets.value.filter(a => a.status === 'ACTIVE' || a.status === 'IN_USE')
)

const selected = ref(new Set())
const initialized = ref(false)
watch(eligible, (list) => {
  if (!initialized.value && list.length > 0) {
    selected.value = new Set(list.map(a => a.id))
    initialized.value = true
  }
}, { immediate: true })

function toggleAll(e) {
  selected.value = e.target.checked ? new Set(eligible.value.map(a => a.id)) : new Set()
}
function toggleRow(id) {
  const n = new Set(selected.value)
  n.has(id) ? n.delete(id) : n.add(id)
  selected.value = n
}

const selectedTotal = computed(() =>
  eligible.value
    .filter(a => selected.value.has(a.id))
    .reduce((s, a) => s + +(a.monthlyDepreciation ?? a.monthlyDep ?? 0), 0)
)

async function runDepreciation() {
  if (!periodId.value && !isDemo.value) {
    toast.error('Select an accounting period first.')
    return
  }
  running.value = true
  try {
    await assetsApi.batchDepreciate({ entityId: entityId.value, periodId: periodId.value, date: runDate.value })
    toast.success('Depreciation run completed.')
    const data = await assetsApi.list(entityId.value)
    allAssets.value = data?.content ?? (Array.isArray(data) ? data : [])
  } catch { /* toast handled by client */ } finally { running.value = false }
}
</script>

<template>
  <div class="page">
    <PageHeader title="Depreciation Run" :meta="isDemo ? 'Demo mode' : `${eligible.length} asset(s) eligible`">
      <Button variant="ghost" icon="arrow-left" @click="$router.back()">Back</Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner kind="info" icon="info">
        Running depreciation posts journal entries for the selected assets:
        <strong>DR Depreciation Expense / CR Accumulated Depreciation</strong>.
      </Banner>

      <div v-if="!isDemo" class="form-grid" style="grid-template-columns:1fr 1fr;max-width:520px">
        <div class="field">
          <label>Accounting period <span style="color:oklch(0.5 0.22 25)">*</span></label>
          <SearchableSelect
            v-model="periodId"
            :options="periodOpts.map(p => ({ value: p.id, label: `${p.periodName} (${p.status})` }))"
            placeholder="Select period…"
          />
        </div>
        <div class="field">
          <label>Run date</label>
          <input class="input" type="date" v-model="runDate" />
        </div>
      </div>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th style="width:36px">
                <input type="checkbox" :checked="selected.size === eligible.length && eligible.length > 0" @change="toggleAll" />
              </th>
              <th>Code</th>
              <th>Asset</th>
              <th>Method</th>
              <th class="num">Monthly dep.</th>
              <th class="num">Accum. after run</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in eligible" :key="a.id">
              <td><input type="checkbox" :checked="selected.has(a.id)" @change="toggleRow(a.id)" /></td>
              <td><span class="code-cell">{{ a.assetCode ?? a.tag }}</span></td>
              <td>
                <div class="fw-500">{{ a.assetName ?? a.name }}</div>
                <div class="muted small">{{ a.category }}</div>
              </td>
              <td><span class="chip-sm">{{ (a.depreciationMethod ?? a.method) === 'STRAIGHT_LINE' ? 'SL' : 'DDB' }}</span></td>
              <td class="num">{{ fmt(a.monthlyDepreciation ?? a.monthlyDep ?? 0) }}</td>
              <td class="num">{{ fmt((+(a.accumulatedDepreciation ?? a.accum ?? 0)) + (+(a.monthlyDepreciation ?? a.monthlyDep ?? 0))) }}</td>
            </tr>
            <tr v-if="eligible.length === 0">
              <td colspan="6" class="muted" style="text-align:center;padding:16px">No active assets found.</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td colspan="4" class="fw-600">Total ({{ selected.size }} selected)</td>
              <td class="num fw-600">{{ fmt(selectedTotal) }}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>

      <div style="display:flex;align-items:center;gap:12px;padding:4px 0">
        <Button
          variant="primary"
          icon="play"
          :loading="running"
          :disabled="selected.size === 0"
          @click="runDepreciation"
        >Run batch depreciation</Button>
        <Button variant="ghost" @click="$router.back()">Cancel</Button>
        <span class="muted small" style="margin-left:auto">
          {{ fmt(selectedTotal, { currency: 'KES' }) }} across {{ selected.size }} asset(s)
        </span>
      </div>
    </div>
  </div>
</template>

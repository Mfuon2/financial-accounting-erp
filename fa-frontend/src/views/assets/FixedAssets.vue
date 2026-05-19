<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ASSETS } from '@/data/index.js'
import { assets as assetsApi, accounts as accountsApi, codes as codesApi, periods as periodsApi } from '@/api/index.js'
import { useAuth } from '@/composables/useAuth.js'
import { isDemo } from '@/composables/useAppMode.js'
import { fmt, fmtDate } from '@/utils/format.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import ChipFilter from '@/components/primitives/ChipFilter.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import Modal from '@/components/overlays/Modal.vue'
import AmountInput      from '@/components/primitives/AmountInput.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const { toast } = useToast()
const { currentUser } = useAuth()
const entityId = computed(() => currentUser.value?.entityId ?? 'current')

const METHODS = ['STRAIGHT_LINE', 'DOUBLE_DECLINING_BALANCE']

// ── List ──────────────────────────────────────────────────────────────────────
const assetList = ref([])
const loading   = ref(false)

async function loadAssets() {
  if (isDemo.value) { assetList.value = ASSETS; return }
  loading.value = true
  try {
    const data = await assetsApi.list(entityId.value)
    assetList.value = data?.content ?? (Array.isArray(data) ? data : [])
  } catch { assetList.value = [] } finally { loading.value = false }
}

onMounted(loadAssets)

// ── Filters ───────────────────────────────────────────────────────────────────
const search         = ref('')
const categoryFilter = ref('All')
const statusFilter   = ref('All')

const categories = computed(() => ['All', ...new Set(assetList.value.map(a => a.category).filter(Boolean))])

const filtered = computed(() =>
  assetList.value.filter(a => {
    if (categoryFilter.value !== 'All' && a.category !== categoryFilter.value) return false
    if (statusFilter.value !== 'All' && a.status !== statusFilter.value) return false
    if (search.value) {
      const q = search.value.toLowerCase()
      const name = (a.assetName ?? a.name ?? '').toLowerCase()
      const code = (a.assetCode ?? a.tag ?? '').toLowerCase()
      if (!name.includes(q) && !code.includes(q)) return false
    }
    return true
  })
)

// ── KPIs ──────────────────────────────────────────────────────────────────────
const totalCost  = computed(() => assetList.value.reduce((s, a) => s + +( a.acquisitionCost ?? a.cost ?? 0), 0))
const totalAccum = computed(() => assetList.value.reduce((s, a) => s + +(a.accumulatedDepreciation ?? a.accum ?? 0), 0))
const totalNet   = computed(() => assetList.value.reduce((s, a) => s + +(a.netBookValue ?? a.netBook ?? 0), 0))
const eligible   = computed(() => assetList.value.filter(a => a.status === 'ACTIVE' || a.status === 'IN_USE'))

function statusVariant(status) {
  return (status === 'ACTIVE' || status === 'IN_USE') ? 'active' : 'inactive'
}

function utilPct(a) {
  const cost  = +(a.acquisitionCost ?? a.cost ?? 0)
  const accum = +(a.accumulatedDepreciation ?? a.accum ?? 0)
  return cost > 0 ? Math.min(100, Math.round(accum / cost * 100)) : 0
}

// ── Drawer / detail ───────────────────────────────────────────────────────────
const drawer        = ref(null)
const isEditing     = ref(false)
const editForm      = ref(null)
const saving        = ref(false)
const scheduleRows  = ref([])
const scheduleLoading = ref(false)

watch(drawer, async (a) => {
  isEditing.value  = false
  scheduleRows.value = []
  editForm.value = a ? {
    assetName:         a.assetName        ?? a.name,
    salvageValue:      a.salvageValue      ?? a.salvage      ?? 0,
    usefulLifeMonths:  a.usefulLifeMonths  ?? a.life         ?? 60,
    depreciationMethod: a.depreciationMethod ?? a.method ?? 'STRAIGHT_LINE',
    category:   a.category   ?? '',
    assignedTo: a.assignedTo ?? '',
  } : null

  if (!a) return
  scheduleLoading.value = true
  try {
    if (isDemo.value) {
      scheduleRows.value = localSchedule(a, 6)
    } else {
      const data = await assetsApi.schedule(a.id, 6)
      scheduleRows.value = Array.isArray(data) ? data : (data?.data ?? [])
    }
  } catch {} finally { scheduleLoading.value = false }
})

function localSchedule(a, n) {
  const cost    = +(a.acquisitionCost ?? a.cost ?? 0)
  const salvage = +(a.salvageValue ?? a.salvage ?? 0)
  const life    = +(a.usefulLifeMonths ?? a.life ?? 60)
  const monthly = life > 0 ? (cost - salvage) / life : 0
  let accum = +(a.accumulatedDepreciation ?? a.accum ?? 0)
  const rows = []
  for (let i = 1; i <= n; i++) {
    const remaining = (cost - salvage) - accum
    if (remaining <= 0) break
    const dep = Math.min(monthly, remaining)
    const open = cost - accum
    accum += dep
    const d = new Date()
    d.setDate(1)
    d.setMonth(d.getMonth() + i)
    rows.push({
      period:                  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`,
      openingNbv:              open,
      depreciation:            dep,
      accumulatedDepreciation: accum,
      closingNbv:              cost - accum,
    })
  }
  return rows
}

function startEdit()  { isEditing.value = true }
function cancelEdit() {
  isEditing.value = false
  if (drawer.value) editForm.value = {
    assetName:         drawer.value.assetName        ?? drawer.value.name,
    salvageValue:      drawer.value.salvageValue      ?? drawer.value.salvage      ?? 0,
    usefulLifeMonths:  drawer.value.usefulLifeMonths  ?? drawer.value.life         ?? 60,
    depreciationMethod: drawer.value.depreciationMethod ?? drawer.value.method ?? 'STRAIGHT_LINE',
    category:   drawer.value.category   ?? '',
    assignedTo: drawer.value.assignedTo ?? '',
  }
}

async function saveAsset() {
  if (!drawer.value || !editForm.value) return
  saving.value = true
  try {
    const body = {
      assetName:         editForm.value.assetName,
      salvageValue:      Number(editForm.value.salvageValue),
      usefulLifeMonths:  Number(editForm.value.usefulLifeMonths),
      depreciationMethod: editForm.value.depreciationMethod,
      category:   editForm.value.category   || null,
      assignedTo: editForm.value.assignedTo || null,
    }
    const updated = await assetsApi.update(drawer.value.id, body)
    const idx = assetList.value.findIndex(a => a.id === drawer.value.id)
    if (updated && idx !== -1) {
      assetList.value[idx] = { ...assetList.value[idx], ...updated }
      drawer.value = { ...assetList.value[idx] }
    }
    toast.success(`${editForm.value.assetName} saved.`)
    isEditing.value = false
  } catch { /* toast handled by client */ } finally { saving.value = false }
}

// ── New asset modal ───────────────────────────────────────────────────────────
const showNew      = ref(false)
const newForm      = ref({})
const newSaving    = ref(false)
const accountOpts  = ref([])
const newCodeLoading = ref(false)

const BLANK_FORM = () => ({
  assetCode:          '',
  assetName:          '',
  category:           '',
  assignedTo:         '',
  acquisitionDate:    new Date().toISOString().slice(0, 10),
  acquisitionCost:    '',
  salvageValue:       0,
  usefulLifeMonths:   60,
  depreciationMethod: 'STRAIGHT_LINE',
  costAccountId:      '',
  accumDepAccountId:  '',
  depExpenseAccountId:'',
})

async function openNew() {
  newForm.value = BLANK_FORM()
  showNew.value = true
  newCodeLoading.value = true
  const [acctRes, codeRes] = await Promise.allSettled([
    accountsApi.list({ entityId: entityId.value, size: 500 }),
    codesApi.next(entityId.value, 'FA', 'FIXED_ASSET'),
  ])
  if (acctRes.status === 'fulfilled') {
    const list = acctRes.value?.content ?? acctRes.value ?? []
    accountOpts.value = Array.isArray(list) ? list : []
  }
  if (codeRes.status === 'fulfilled') {
    newForm.value.assetCode = codeRes.value?.code ?? ''
  }
  newCodeLoading.value = false
}

async function saveNew() {
  if (!newForm.value.assetName?.trim())       return toast.error('Asset name is required.')
  if (!newForm.value.acquisitionCost)         return toast.error('Acquisition cost is required.')
  if (!newForm.value.costAccountId)           return toast.error('Asset cost account is required.')
  if (!newForm.value.accumDepAccountId)       return toast.error('Accumulated depreciation account is required.')
  if (!newForm.value.depExpenseAccountId)     return toast.error('Depreciation expense account is required.')
  newSaving.value = true
  try {
    const body = {
      entityId:           entityId.value,
      assetCode:          '',
      assetName:          newForm.value.assetName,
      category:           newForm.value.category   || null,
      assignedTo:         newForm.value.assignedTo || null,
      acquisitionDate:    newForm.value.acquisitionDate,
      acquisitionCost:    Number(newForm.value.acquisitionCost),
      salvageValue:       Number(newForm.value.salvageValue),
      usefulLifeMonths:   Number(newForm.value.usefulLifeMonths),
      depreciationMethod: newForm.value.depreciationMethod,
      costAccountId:      newForm.value.costAccountId,
      accumDepAccountId:  newForm.value.accumDepAccountId,
      depExpenseAccountId:newForm.value.depExpenseAccountId,
    }
    const created = await assetsApi.create(body)
    if (created) assetList.value.unshift(created)
    toast.success(`${newForm.value.assetName} created.`)
    showNew.value = false
  } catch { /* toast handled by client */ } finally { newSaving.value = false }
}

// ── Dispose asset ─────────────────────────────────────────────────────────────
const showDispose       = ref(false)
const disposeSaving     = ref(false)
const disposeForm       = ref({ disposalDate: '', proceedsAmount: '', proceedsAccountId: '', periodId: '' })
const disposeAccountOpts = ref([])
const disposePeriodOpts  = ref([])

const disposeNbv = computed(() => {
  if (!drawer.value) return 0
  const cost  = +(drawer.value.acquisitionCost ?? drawer.value.cost ?? 0)
  const accum = +(drawer.value.accumulatedDepreciation ?? drawer.value.accum ?? 0)
  return cost - accum
})

const disposeGainLoss = computed(() => {
  const proceeds = +disposeForm.value.proceedsAmount || 0
  return proceeds - disposeNbv.value
})

async function openDispose() {
  disposeForm.value = {
    disposalDate:       new Date().toISOString().slice(0, 10),
    proceedsAmount:     '',
    proceedsAccountId:  '',
    periodId:           '',
  }
  showDispose.value = true
  const [acctRes, perRes] = await Promise.allSettled([
    accountsApi.list({ entityId: entityId.value, size: 500 }),
    periodsApi.list({ entityId: entityId.value, size: 50 }),
  ])
  if (acctRes.status === 'fulfilled') {
    const list = acctRes.value?.content ?? acctRes.value ?? []
    disposeAccountOpts.value = Array.isArray(list) ? list : []
  }
  if (perRes.status === 'fulfilled') {
    const list = perRes.value?.content ?? perRes.value ?? []
    disposePeriodOpts.value = Array.isArray(list) ? list : []
    const open = disposePeriodOpts.value.find(p => p.status === 'OPEN')
    if (open) disposeForm.value.periodId = open.id
  }
}

async function confirmDispose() {
  if (!disposeForm.value.proceedsAccountId) return toast.error('Select a proceeds account.')
  if (!disposeForm.value.periodId)          return toast.error('Select an accounting period.')
  if (!+disposeForm.value.proceedsAmount)   return toast.error('Proceeds amount is required.')
  disposeSaving.value = true
  try {
    const disposed = await assetsApi.dispose(drawer.value.id, {
      periodId:         disposeForm.value.periodId,
      disposalDate:     disposeForm.value.disposalDate,
      proceedsAmount:   Number(disposeForm.value.proceedsAmount),
      proceedsAccountId: disposeForm.value.proceedsAccountId,
    })
    const idx = assetList.value.findIndex(a => a.id === drawer.value.id)
    if (idx !== -1 && disposed) assetList.value[idx] = { ...assetList.value[idx], ...disposed }
    toast.success(`${drawer.value.assetName ?? drawer.value.name} disposed. Journal entry posted.`)
    showDispose.value = false
    drawer.value = disposed ?? null
  } catch { /* toast handled by client */ } finally { disposeSaving.value = false }
}

// ── Run Depreciation modal ─────────────────────────────────────────────────────
const showRunDep    = ref(false)
const runDepForm    = ref({ date: '', periodId: '' })
const periodOpts    = ref([])
const runDepSaving  = ref(false)

async function openRunDep() {
  runDepForm.value = { date: new Date().toISOString().slice(0, 10), periodId: '' }
  showRunDep.value = true
  try {
    const data = await periodsApi.list({ entityId: entityId.value, size: 50 })
    const list = data?.content ?? data ?? []
    periodOpts.value = Array.isArray(list) ? list : []
    const open = periodOpts.value.find(p => p.status === 'OPEN')
    if (open) runDepForm.value.periodId = open.id
  } catch {}
}

async function submitRunDep() {
  runDepSaving.value = true
  try {
    await assetsApi.batchDepreciate({
      entityId: entityId.value,
      periodId: runDepForm.value.periodId,
      date:     runDepForm.value.date,
    })
    toast.success('Depreciation run completed.')
    showRunDep.value = false
    await loadAssets()
  } catch { /* toast handled by client */ } finally { runDepSaving.value = false }
}
</script>

<template>
  <div class="page">
    <PageHeader title="Fixed Assets" meta="IAS 16 · IAS 36">
      <Button variant="ghost" icon="download">Export</Button>
      <Button variant="ghost" icon="play" @click="openRunDep">Run depreciation</Button>
      <Button variant="primary" icon="plus" @click="openNew">New asset</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="kpi-grid">
        <Kpi label="Total cost"              icon="layers"        :value="totalCost"    unit="KES" />
        <Kpi label="Accumulated depreciation" icon="trending-down" :value="totalAccum"   unit="KES" />
        <Kpi label="Net book value"           icon="box"           :value="totalNet"     unit="KES" />
        <Kpi label="Active assets"            icon="check-circle"  :value="eligible.length" />
      </div>

      <TableToolbar v-model:search="search">
        <ChipFilter
          v-for="cat in categories" :key="cat"
          :active="categoryFilter === cat"
          @click="categoryFilter = cat"
        >{{ cat }}</ChipFilter>
        <ChipFilter :active="statusFilter === 'ACTIVE' || statusFilter === 'IN_USE'" @click="statusFilter = statusFilter === 'All' ? 'ACTIVE' : 'All'">
          Active only
        </ChipFilter>
      </TableToolbar>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Asset</th>
              <th>Category</th>
              <th>Acquired</th>
              <th>Method</th>
              <th class="num">Cost</th>
              <th class="num">Monthly dep.</th>
              <th class="num">Accum. dep.</th>
              <th class="num">Net book</th>
              <th>Utilization</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in filtered" :key="a.id">
              <td><span class="code-cell">{{ a.assetCode ?? a.tag }}</span></td>
              <td>
                <div class="fw-500">{{ a.assetName ?? a.name }}</div>
                <div class="muted small">{{ a.assignedTo }}</div>
              </td>
              <td>{{ a.category }}</td>
              <td>{{ fmtDate(a.acquisitionDate ?? a.acquired) }}</td>
              <td>
                <span class="chip-sm">{{ (a.depreciationMethod ?? a.method) === 'STRAIGHT_LINE' ? 'SL' : 'DDB' }}</span>
              </td>
              <td class="num">{{ fmt(a.acquisitionCost ?? a.cost, { compact: true }) }}</td>
              <td class="num">{{ fmt(a.monthlyDepreciation ?? a.monthlyDep ?? 0) }}</td>
              <td class="num">{{ fmt(a.accumulatedDepreciation ?? a.accum, { compact: true }) }}</td>
              <td class="num fw-600">{{ fmt(a.netBookValue ?? a.netBook, { compact: true }) }}</td>
              <td style="min-width:80px">
                <div class="bar-wrap">
                  <div class="bar"><span :style="{ width: utilPct(a) + '%' }" /></div>
                  <span class="bar-label">{{ utilPct(a) }}%</span>
                </div>
              </td>
              <td>
                <Badge :status="statusVariant(a.status)" :dot="false">{{ a.status }}</Badge>
              </td>
              <td>
                <Button variant="ghost" size="sm" @click="drawer = a">Detail</Button>
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td colspan="5" class="fw-600">Totals</td>
              <td class="num fw-600">{{ fmt(totalCost,  { compact: true }) }}</td>
              <td class="num"></td>
              <td class="num fw-600">{{ fmt(totalAccum, { compact: true }) }}</td>
              <td class="num fw-600">{{ fmt(totalNet,   { compact: true }) }}</td>
              <td colspan="3"></td>
            </tr>
          </tfoot>
        </table>
        <TableFooter :total="filtered.length" label="assets" />
      </div>
    </div>

    <!-- ── Detail drawer ─────────────────────────────────────────────────── -->
    <Modal
      :open="!!drawer"
      :title="drawer?.assetCode ?? drawer?.tag"
      :subtitle="drawer?.assetName ?? drawer?.name"
      :width="820"
      @close="drawer = null"
    >
      <div v-if="drawer">
        <div class="kpi-grid" style="grid-template-columns:repeat(3,1fr)">
          <Kpi label="Cost"          :value="drawer.acquisitionCost ?? drawer.cost"             unit="KES" />
          <Kpi label="Net book value" :value="drawer.netBookValue ?? drawer.netBook"             unit="KES" />
          <Kpi label="Monthly dep."  :value="drawer.monthlyDepreciation ?? drawer.monthlyDep ?? 0" unit="KES" />
        </div>

        <template v-if="!isEditing">
          <div class="form-grid" style="grid-template-columns:1fr 1fr">
            <div class="field"><label>Category</label><div class="input ro">{{ drawer.category }}</div></div>
            <div class="field"><label>Method</label><div class="input ro">{{ drawer.depreciationMethod ?? drawer.method }}</div></div>
            <div class="field"><label>Useful life</label><div class="input ro">{{ drawer.usefulLifeMonths ?? drawer.life }} months</div></div>
            <div class="field"><label>Salvage value</label><div class="input ro">{{ fmt(drawer.salvageValue ?? drawer.salvage ?? 0, { currency: 'KES' }) }}</div></div>
            <div class="field"><label>Acquired</label><div class="input ro">{{ fmtDate(drawer.acquisitionDate ?? drawer.acquired) }}</div></div>
            <div class="field"><label>Assigned to</label><div class="input ro">{{ drawer.assignedTo }}</div></div>
          </div>
        </template>

        <template v-else-if="editForm">
          <div class="form-grid" style="grid-template-columns:1fr 1fr">
            <div class="field" style="grid-column:span 2">
              <label>Asset name <span class="req">*</span></label>
              <input class="input" v-model="editForm.assetName" />
            </div>
            <div class="field">
              <label>Depreciation method</label>
              <SearchableSelect
                v-model="editForm.depreciationMethod"
                :options="METHODS.map(m => ({ value: m, label: m.replace(/_/g, ' ') }))"
                placeholder="Select method…"
              />
            </div>
            <div class="field">
              <label>Useful life (months) <span class="req">*</span></label>
              <input class="input" type="number" min="1" v-model="editForm.usefulLifeMonths" />
            </div>
            <div class="field">
              <label>Salvage value (KES)</label>
              <AmountInput class="input" v-model="editForm.salvageValue" placeholder="0.00" />
            </div>
            <div class="field">
              <label>Assigned to</label>
              <input class="input" v-model="editForm.assignedTo" placeholder="Person or department" />
            </div>
          </div>
          <div class="info-box">
            Changes to useful life and salvage value adjust the depreciation charge <strong>prospectively</strong> from the next run (IAS 16.61).
            Acquisition cost and acquisition date cannot be changed after initial recognition.
          </div>
        </template>

        <div class="card" style="margin-top:16px">
          <div class="card-head">Depreciation schedule — next 6 months</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>Period</th>
                <th class="num">Opening NBV</th>
                <th class="num">Dep.</th>
                <th class="num">Accum. dep.</th>
                <th class="num">Closing NBV</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="scheduleLoading">
                <td colspan="5" class="muted" style="text-align:center;padding:12px">Loading…</td>
              </tr>
              <tr v-else-if="scheduleRows.length === 0">
                <td colspan="5" class="muted" style="text-align:center;padding:12px">Asset fully depreciated.</td>
              </tr>
              <tr v-for="r in scheduleRows" :key="r.period">
                <td>{{ r.period }}</td>
                <td class="num">{{ fmt(r.openingNbv) }}</td>
                <td class="num neg-text">({{ fmt(r.depreciation) }})</td>
                <td class="num">{{ fmt(r.accumulatedDepreciation) }}</td>
                <td class="num fw-600">{{ fmt(r.closingNbv) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <template #footer>
        <template v-if="!isEditing">
          <Button
            v-if="drawer?.status === 'ACTIVE' || drawer?.status === 'IN_USE'"
            variant="danger"
            icon="trash-2"
            @click="openDispose"
          >Dispose</Button>
          <Button variant="primary" icon="edit" @click="startEdit">Edit asset</Button>
          <Button variant="ghost" @click="drawer = null">Close</Button>
        </template>
        <template v-else>
          <Button variant="primary" icon="save" :loading="saving" @click="saveAsset">Save changes</Button>
          <Button variant="ghost" @click="cancelEdit">Cancel</Button>
        </template>
      </template>
    </Modal>

    <!-- ── Dispose asset modal ──────────────────────────────────────────── -->
    <Modal :open="showDispose" title="Dispose fixed asset" :width="560" @close="showDispose = false">
      <div class="stack" style="gap:16px">
        <!-- Financial summary -->
        <div class="disposal-summary">
          <div class="ds-row">
            <span>Acquisition cost</span>
            <span class="fw-600">{{ fmt(drawer?.acquisitionCost ?? drawer?.cost ?? 0) }}</span>
          </div>
          <div class="ds-row">
            <span>Accumulated depreciation</span>
            <span class="neg-text">({{ fmt(drawer?.accumulatedDepreciation ?? drawer?.accum ?? 0) }})</span>
          </div>
          <div class="ds-row ds-nbv">
            <span class="fw-600">Net book value</span>
            <span class="fw-600">{{ fmt(disposeNbv) }}</span>
          </div>
          <div class="ds-row" v-if="+disposeForm.proceedsAmount">
            <span>Disposal proceeds</span>
            <span>{{ fmt(+disposeForm.proceedsAmount) }}</span>
          </div>
          <div class="ds-row ds-gainloss" v-if="+disposeForm.proceedsAmount">
            <span class="fw-600">{{ disposeGainLoss >= 0 ? 'Estimated gain' : 'Estimated loss' }}</span>
            <span :class="disposeGainLoss >= 0 ? 'pos-text fw-600' : 'neg-text fw-600'">
              {{ disposeGainLoss >= 0 ? '' : '(' }}{{ fmt(Math.abs(disposeGainLoss)) }}{{ disposeGainLoss >= 0 ? '' : ')' }}
            </span>
          </div>
        </div>

        <!-- Journal preview -->
        <div class="info-box" style="margin:0">
          Journal entry will post:
          <strong>DR Proceeds account · DR Accum. Dep. · CR Asset at Cost</strong>
          {{ disposeGainLoss > 0 ? '· CR Gain on Disposal' : disposeGainLoss < 0 ? '· DR Loss on Disposal' : '' }}
        </div>

        <!-- Fields -->
        <div class="form-grid" style="grid-template-columns:1fr 1fr">
          <div class="field">
            <label>Disposal date <span class="req">*</span></label>
            <input class="input" type="date" v-model="disposeForm.disposalDate" />
          </div>
          <div class="field">
            <label>Proceeds amount <span class="req">*</span></label>
            <AmountInput class="input" v-model="disposeForm.proceedsAmount" placeholder="0.00" />
          </div>
          <div class="field" style="grid-column:span 2">
            <label>Proceeds account (Cash / Bank) <span class="req">*</span></label>
            <SearchableSelect
              v-model="disposeForm.proceedsAccountId"
              :options="disposeAccountOpts.map(a => ({ value: a.id, label: `${a.accountCode} — ${a.accountName}` }))"
              placeholder="Select cash or bank account…"
            />
          </div>
          <div class="field" style="grid-column:span 2">
            <label>Accounting period <span class="req">*</span></label>
            <SearchableSelect
              v-model="disposeForm.periodId"
              :options="disposePeriodOpts.map(p => ({ value: p.id, label: `${p.periodName} (${p.status})` }))"
              placeholder="Select period…"
            />
          </div>
        </div>

        <div class="warn-box">
          Disposal is irreversible. The asset status will be set to <strong>DISPOSED</strong> and no further depreciation will be charged.
        </div>
      </div>

      <template #footer>
        <Button variant="danger" icon="trash-2" :loading="disposeSaving" @click="confirmDispose">
          Confirm disposal
        </Button>
        <Button variant="ghost" @click="showDispose = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── New asset modal ──────────────────────────────────────────────── -->
    <Modal :open="showNew" title="New fixed asset" :width="700" @close="showNew = false">
      <div class="form-grid" style="grid-template-columns:1fr 1fr">
        <div class="field">
          <label>Asset code</label>
          <input class="input" disabled :value="newCodeLoading ? '' : newForm.assetCode" placeholder="Auto-generated" />
        </div>
        <div class="field">
          <label>Asset name <span class="req">*</span></label>
          <input class="input" v-model="newForm.assetName" placeholder="e.g. MacBook Pro 14" />
        </div>
        <div class="field">
          <label>Category</label>
          <input class="input" v-model="newForm.category" placeholder="e.g. IT Equipment" />
        </div>
        <div class="field">
          <label>Assigned to</label>
          <input class="input" v-model="newForm.assignedTo" placeholder="Person or department" />
        </div>
        <div class="field">
          <label>Acquisition date <span class="req">*</span></label>
          <input class="input" type="date" v-model="newForm.acquisitionDate" />
        </div>
        <div class="field">
          <label>Acquisition cost (KES) <span class="req">*</span></label>
          <AmountInput class="input" v-model="newForm.acquisitionCost" placeholder="0.00" />
        </div>
        <div class="field">
          <label>Salvage value (KES)</label>
          <AmountInput class="input" v-model="newForm.salvageValue" placeholder="0.00" />
        </div>
        <div class="field">
          <label>Useful life (months) <span class="req">*</span></label>
          <input class="input" type="number" min="1" v-model="newForm.usefulLifeMonths" />
        </div>
        <div class="field" style="grid-column:span 2">
          <label>Depreciation method</label>
          <SearchableSelect
            v-model="newForm.depreciationMethod"
            :options="METHODS.map(m => ({ value: m, label: m.replace(/_/g, ' ') }))"
            placeholder="Select method…"
          />
        </div>
        <div class="field" style="grid-column:span 2">
          <label>Asset cost account <span class="req">*</span></label>
          <SearchableSelect
            v-model="newForm.costAccountId"
            :options="accountOpts.map(a => ({ value: a.id, label: `${a.accountCode} — ${a.accountName}` }))"
            placeholder="Select account…"
          />
        </div>
        <div class="field" style="grid-column:span 2">
          <label>Accumulated depreciation account <span class="req">*</span></label>
          <SearchableSelect
            v-model="newForm.accumDepAccountId"
            :options="accountOpts.map(a => ({ value: a.id, label: `${a.accountCode} — ${a.accountName}` }))"
            placeholder="Select account…"
          />
        </div>
        <div class="field" style="grid-column:span 2">
          <label>Depreciation expense account <span class="req">*</span></label>
          <SearchableSelect
            v-model="newForm.depExpenseAccountId"
            :options="accountOpts.map(a => ({ value: a.id, label: `${a.accountCode} — ${a.accountName}` }))"
            placeholder="Select account…"
          />
        </div>
      </div>

      <template #footer>
        <Button variant="primary" icon="save" :loading="newSaving" @click="saveNew">Create asset</Button>
        <Button variant="ghost" @click="showNew = false">Cancel</Button>
      </template>
    </Modal>

    <!-- ── Run Depreciation modal ────────────────────────────────────────── -->
    <Modal :open="showRunDep" title="Run depreciation" :width="460" @close="showRunDep = false">
      <div class="stack" style="gap:12px">
        <div class="info-box">
          Posts depreciation journal entries for all active assets of this entity for the selected period and date.
        </div>
        <div class="form-grid" style="grid-template-columns:1fr">
          <div class="field">
            <label>Accounting period <span class="req">*</span></label>
            <SearchableSelect
              v-model="runDepForm.periodId"
              :options="periodOpts.map(p => ({ value: p.id, label: `${p.periodName} (${p.status})` }))"
              placeholder="Select period…"
            />
          </div>
          <div class="field">
            <label>Run date <span class="req">*</span></label>
            <input class="input" type="date" v-model="runDepForm.date" />
          </div>
        </div>
        <div class="muted small">
          {{ eligible.length }} active asset(s) eligible for this run.
        </div>
      </div>

      <template #footer>
        <Button
          variant="primary"
          icon="play"
          :loading="runDepSaving"
          :disabled="!runDepForm.periodId"
          @click="submitRunDep"
        >Run depreciation</Button>
        <Button variant="ghost" @click="showRunDep = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.input.ro {
  cursor: default;
  background: var(--surface-2);
  color: var(--fg-2);
}
.req { color: oklch(0.5 0.22 25); }
.info-box {
  margin: 4px 0 12px;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  background: color-mix(in oklab, var(--accent) 6%, var(--surface));
  border: 1px solid color-mix(in oklab, var(--accent) 20%, transparent);
  border-radius: 6px;
  color: var(--fg-2);
}

/* Disposal summary card */
.disposal-summary {
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 4px 0;
}
.ds-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 7px 14px;
  font-size: 13px;
}
.ds-nbv {
  border-top: 1px solid var(--border);
  border-bottom: 1px dashed var(--border);
  background: var(--surface);
  margin: 2px 0;
}
.ds-gainloss {
  border-top: 1px solid var(--border);
}
.pos-text { color: oklch(0.55 0.18 145); }

/* Warning box */
.warn-box {
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.6;
  background: color-mix(in oklab, var(--neg) 8%, var(--surface));
  border: 1px solid color-mix(in oklab, var(--neg) 30%, transparent);
  border-radius: 6px;
  color: color-mix(in oklab, var(--neg) 70%, var(--fg));
}
</style>

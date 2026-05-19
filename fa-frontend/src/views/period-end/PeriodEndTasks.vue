<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { periods as periodsApi, adjustments as adjustmentsApi, cycle as cycleApi } from '@/api/index.js'
import { useAuth }    from '@/composables/useAuth.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast }   from '@/composables/useToast.js'
import { useRouter }  from 'vue-router'
import { fmtDate }    from '@/utils/format.js'
import PageHeader     from '@/components/PageHeader.vue'
import Button         from '@/components/primitives/Button.vue'
import Badge          from '@/components/primitives/Badge.vue'
import Banner         from '@/components/data-display/Banner.vue'
import Modal          from '@/components/overlays/Modal.vue'
import TableFooter    from '@/components/tables/TableFooter.vue'

const router = useRouter()
const { currentUser } = useAuth()
const { isDemo }      = useAppMode()
const { toast }       = useToast()

const entityId = computed(() => currentUser.value?.entityId ?? null)

const activePeriod   = ref(null)
const loading        = ref(false)
const validating     = ref(false)
const showRunConfirm = ref(false)
const running        = ref(false)
const amortizing     = ref(false)
const recognizing    = ref(false)
const transitioning  = ref(false)

async function loadActivePeriod() {
  if (!entityId.value) return
  loading.value = true
  try {
    const data = await periodsApi.list({ entityId: entityId.value, size: 50 })
    const items = data?.content ?? (Array.isArray(data) ? data : [])
    const priority = ['ADJUSTING','CLOSING','OPEN','REOPENED','CLOSED']
    const pri = (s) => { const i = priority.indexOf(s); return i === -1 ? Infinity : i }
    const sorted = [...items].sort((a, b) => pri(a.status) - pri(b.status))
    activePeriod.value = sorted[0] ?? null
  } catch { } finally { loading.value = false }
}

onMounted(() => { if (!isDemo.value) loadActivePeriod() })
watch(entityId, (v) => { if (v && !isDemo.value) loadActivePeriod() })

const status = computed(() => activePeriod.value?.status ?? null)

const tasks = computed(() => {
  const s = status.value
  function done(...statuses)   { return statuses.includes(s) ? 'DONE'    : 'PENDING' }
  function ready(...statuses)  { return statuses.includes(s) ? 'READY'   : 'BLOCKED' }
  function closed(...statuses) { return statuses.includes(s) ? 'DONE'    : 'BLOCKED' }
  return [
    { id: 1,  name: 'Unadjusted Trial Balance',               status: done('ADJUSTING','CLOSING','CLOSED','REOPENED'),  link: '/trial-balance', action: null },
    { id: 2,  name: 'Transition period to ADJUSTING',         status: done('ADJUSTING','CLOSING','CLOSED','REOPENED'),  link: '/periods',       action: s === 'OPEN' ? 'transition-adjusting' : null },
    { id: 3,  name: 'Record manual accrual entries',          status: 'PENDING',                                        link: '/journals',      action: null },
    { id: 4,  name: 'Record manual deferral entries',         status: 'PENDING',                                        link: '/journals',      action: null },
    { id: 5,  name: 'Amortize prepaid expenses (batch)',      status: ['ADJUSTING','CLOSING','CLOSED','REOPENED'].includes(s) ? 'PENDING' : 'BLOCKED', link: '/journals', action: ['ADJUSTING','OPEN'].includes(s) ? 'amortize' : null },
    { id: 6,  name: 'Recognize unearned revenue (IFRS 15)',   status: ['ADJUSTING','CLOSING','CLOSED','REOPENED'].includes(s) ? 'PENDING' : 'BLOCKED', link: '/journals', action: ['ADJUSTING','OPEN'].includes(s) ? 'recognize' : null },
    { id: 7,  name: 'Batch depreciation run',                 status: done('ADJUSTING','CLOSING','CLOSED','REOPENED'),  link: '/depreciation',  action: null },
    { id: 8,  name: 'FX Revaluation (IAS 21)',                status: done('CLOSING','CLOSED','REOPENED'),              link: '/fx',            action: null },
    { id: 9,  name: 'Adjusted Trial Balance',                 status: done('CLOSING','CLOSED','REOPENED'),              link: '/trial-balance',  action: null },
    { id: 10, name: 'Profit & Loss statement',                status: ready('CLOSING','CLOSED','REOPENED'),             link: '/pnl',            action: null },
    { id: 11, name: 'Balance Sheet statement',                status: ready('CLOSING','CLOSED','REOPENED'),             link: '/balance-sheet',  action: null },
    { id: 12, name: 'Cash Flow Statement (IAS 7)',            status: ready('CLOSING','CLOSED','REOPENED'),             link: '/cash-flow',      action: null },
    { id: 13, name: 'Transition period to CLOSING',           status: done('CLOSING','CLOSED','REOPENED'),              link: '/periods',        action: s === 'ADJUSTING' ? 'transition-closing' : null },
    { id: 14, name: 'Post closing entries (zero temp accts)', status: closed('CLOSED','REOPENED'),                      link: '/close',          action: null },
    { id: 15, name: 'Post-closing Trial Balance',             status: closed('CLOSED','REOPENED'),                      link: '/trial-balance',  action: null },
  ]
})

const doneCount = computed(() => tasks.value.filter(t => t.status === 'DONE').length)

function statusVariant(s) {
  const map = { DONE: 'approved', PENDING: 'pending', READY: 'info', BLOCKED: 'outline', PENDING_APPROVAL: 'warn' }
  return map[s] ?? 'outline'
}

async function validateCycle() {
  if (!activePeriod.value) { toast.warn('No active period found'); return }
  validating.value = true
  try {
    await cycleApi.validateStep(entityId.value, activePeriod.value.id, 'CLOSED')
    toast.success('Cycle validation passed — period can proceed to CLOSED.')
  } catch (e) {
    const msg = e?.message ?? ''
    if (msg.includes('must be in CLOSING state') || msg.includes('must be in ADJUSTING state')) {
      const s = activePeriod.value?.status
      if (s === 'OPEN') {
        toast.warn('Begin Adjusting before running the close cycle.')
      } else if (s === 'ADJUSTING') {
        toast.warn('Begin Closing before running the close cycle.')
      } else {
        toast.warn('Begin Adjusting before running the close cycle.')
      }
    } else {
      toast.error(msg || 'Cycle validation failed')
    }
  } finally { validating.value = false }
}

async function runCycle() {
  if (!activePeriod.value) return
  running.value = true
  showRunConfirm.value = false
  try {
    await cycleApi.run({ entityId: entityId.value, periodId: activePeriod.value.id })
    toast.success('9-step accounting cycle complete. Period is now CLOSED.')
    await loadActivePeriod()
  } catch { } finally { running.value = false }
}

async function doAmortize() {
  if (!activePeriod.value) return
  amortizing.value = true
  try {
    await adjustmentsApi.amortizePrepayments(entityId.value, activePeriod.value.id)
    toast.success('Prepaid expense amortisation entries created.')
  } catch { } finally { amortizing.value = false }
}

async function doRecognize() {
  if (!activePeriod.value) return
  recognizing.value = true
  try {
    await adjustmentsApi.recognizeUnearnedRevenue(entityId.value, activePeriod.value.id)
    toast.success('Unearned revenue recognition entries created.')
  } catch { } finally { recognizing.value = false }
}

async function transitionAdjusting() {
  if (!activePeriod.value) return
  transitioning.value = true
  try {
    await periodsApi.transition(activePeriod.value.id, 'ADJUSTING')
    toast.success('Period transitioned to ADJUSTING.')
    await loadActivePeriod()
  } catch { } finally { transitioning.value = false }
}

async function transitionClosing() {
  if (!activePeriod.value) return
  transitioning.value = true
  try {
    await periodsApi.transition(activePeriod.value.id, 'CLOSING')
    toast.success('Period transitioned to CLOSING.')
    await loadActivePeriod()
  } catch { } finally { transitioning.value = false }
}
</script>

<template>
  <div class="page">
    <PageHeader
      :title="`Period-End Workflow · ${activePeriod?.periodName ?? '…'}`"
      :meta="activePeriod ? `${activePeriod.status} · ${doneCount}/${tasks.length} tasks complete` : 'Loading…'"
    >
      <Button variant="ghost" icon="branch" :loading="validating" @click="validateCycle">Validate cycle</Button>
      <Button variant="primary" icon="play" :loading="running" :disabled="!activePeriod" @click="showRunConfirm = true">Run 9-step cycle</Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner v-if="!isDemo && !activePeriod && !loading" kind="warn" icon="warn">
        No active accounting period found. Please create or open a period first.
      </Banner>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th style="width:40px">#</th>
              <th>Task</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in tasks" :key="t.id">
              <td class="muted">{{ t.id }}</td>
              <td>{{ t.name }}</td>
              <td><Badge :status="statusVariant(t.status)" :dot="false">{{ t.status.replace('_', ' ') }}</Badge></td>
              <td style="display:flex;gap:4px;align-items:center;padding:6px 8px">
                <Button v-if="t.action === 'amortize'" size="sm" variant="ghost" icon="play" :loading="amortizing" @click="doAmortize">Run</Button>
                <Button v-if="t.action === 'recognize'" size="sm" variant="ghost" icon="play" :loading="recognizing" @click="doRecognize">Run</Button>
                <Button v-if="t.action === 'transition-adjusting'" size="sm" variant="ghost" icon="arrow-right" :loading="transitioning" @click="transitionAdjusting">Transition</Button>
                <Button v-if="t.action === 'transition-closing'" size="sm" variant="ghost" icon="arrow-right" :loading="transitioning" @click="transitionClosing">Transition</Button>
                <a v-if="t.link" :href="'#' + t.link" style="font-size:11px;color:var(--accent)">Open</a>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="tasks.length" label="tasks" />
    </div>

    <Modal
      :open="showRunConfirm"
      title="Run Full Accounting Cycle"
      subtitle="This will execute Steps 3–9 and lock the period"
      :width="480"
      @close="showRunConfirm = false"
    >
      <Banner kind="warn" icon="warn">
        This will post all closing entries and transition the period to CLOSED. This action cannot be undone without reopening the period.
      </Banner>
      <template #footer>
        <Button variant="danger" icon="play" :loading="running" @click="runCycle">Confirm — Run Cycle</Button>
        <Button variant="ghost" @click="showRunConfirm = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

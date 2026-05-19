<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { TRIAL_BALANCE } from '@/data/index.js'
import { reports } from '@/api/index.js'
import { useAuth }    from '@/composables/useAuth.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { fmt } from '@/utils/format.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Banner from '@/components/data-display/Banner.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import Modal from '@/components/overlays/Modal.vue'
import TAccount from '@/components/data-display/TAccount.vue'

const { toast }       = useToast()
const { currentUser } = useAuth()
const { isDemo }      = useAppMode()

const entityId = computed(() => currentUser.value?.entityId ?? null)

const DEMO_REPORT = {
  entityId: 'demo', asOfDate: '2026-02-28',
  rows: TRIAL_BALANCE.map(r => ({ accountCode: r.code, accountName: r.name, debitBalance: r.dr || 0, creditBalance: r.cr || 0 })),
  totalDebits:  TRIAL_BALANCE.reduce((s, r) => s + (r.dr  || 0), 0),
  totalCredits: TRIAL_BALANCE.reduce((s, r) => s + (r.cr  || 0), 0),
}

const mode             = ref('TRIAL_BALANCE')
const asOfDate         = ref(new Date().toISOString().slice(0, 10))
const report           = ref(isDemo.value ? DEMO_REPORT : null)
const loading          = ref(false)
const outOfBalance     = ref(false)
const tacctDrawer      = ref(null)
const showZeroBalances = ref(false)

const tabs = [
  { id: 'TRIAL_BALANCE', label: 'Trial Balance' },
  { id: 'POST_CLOSING',  label: 'Post-closing' },
]

async function load() {
  if (isDemo.value) {
    report.value = DEMO_REPORT
    outOfBalance.value = false
    return
  }
  if (!entityId.value) return
  loading.value    = true
  outOfBalance.value = false
  try {
    const res = await reports.trialBalance({ entityId: entityId.value, asOfDate: asOfDate.value })
    if (res) report.value = res
  } catch {
    outOfBalance.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(asOfDate, load)
watch(entityId, (val) => { if (val) load() })

const rows = computed(() => {
  if (!report.value?.rows) return []
  let base = report.value.rows
  if (mode.value === 'POST_CLOSING') {
    base = base.filter(r => !r.accountCode.startsWith('4') && !r.accountCode.startsWith('5'))
  }
  if (!showZeroBalances.value) {
    base = base.filter(r => (r.debitBalance || 0) !== 0 || (r.creditBalance || 0) !== 0)
  }
  return base
})

const drTotal  = computed(() => rows.value.reduce((s, r) => s + (r.debitBalance  || 0), 0))
const crTotal  = computed(() => rows.value.reduce((s, r) => s + (r.creditBalance || 0), 0))

function rowStyle(r) {
  const indent = r.depth * 16
  if (r.isHeader && r.depth === 0) return { fontWeight: '700', textTransform: 'uppercase', fontSize: '11px', letterSpacing: '0.04em', color: 'var(--text-muted)' }
  if (r.isHeader) return { paddingLeft: indent + 'px', fontWeight: '600' }
  return { paddingLeft: indent + 'px' }
}

function trStyle(r) {
  if (r.isHeader && r.depth === 0) return { background: 'var(--surface-alt, var(--surface))', borderTop: '2px solid var(--border)' }
  if (r.isHeader) return { background: 'color-mix(in oklab, var(--border) 30%, transparent)' }
  return {}
}
const balanced = computed(() => {
  if (outOfBalance.value) return false
  if (!report.value) return true
  return Math.abs((report.value.totalDebits || 0) - (report.value.totalCredits || 0)) < 0.01
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="Trial Balance"
      :meta="`As at ${report?.asOfDate ?? asOfDate}`"
      :tabs="tabs"
      :activeTab="mode"
      @tab="mode = $event"
    >
      <input
        type="date"
        v-model="asOfDate"
        class="date-input"
        style="font-size:13px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;background:var(--surface);color:var(--text)"
      />
      <Button
        :variant="showZeroBalances ? 'primary' : 'ghost'"
        icon="ledger"
        @click="showZeroBalances = !showZeroBalances"
        :title="showZeroBalances ? 'Showing all accounts (including zero balances)' : 'Showing active balances only — click to include zero balances'"
      >{{ showZeroBalances ? 'All accounts' : 'Active only' }}</Button>
      <Button variant="ghost" icon="download">Export</Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner v-if="outOfBalance" kind="error" icon="warn">
        Trial balance is out of balance — raw ledger debits ≠ credits. This indicates a posting pipeline integrity error. Contact your system administrator.
      </Banner>
      <Banner v-else-if="report && balanced" kind="success" icon="check">
        Trial balance is balanced — debits equal credits.
      </Banner>
      <Banner v-else-if="report && !balanced" kind="warn" icon="warn">
        Out of balance — review posting pipeline integrity.
      </Banner>

      <div v-if="loading && !report" class="card" style="margin-top:var(--gap);padding:48px;text-align:center;color:var(--text-muted)">
        Loading trial balance…
      </div>

      <div v-else-if="report" class="card" style="margin-top:var(--gap)">
        <table class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Account</th>
              <th class="num">Debit</th>
              <th class="num">Credit</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.accountCode" :style="trStyle(r)">
              <td style="white-space:nowrap"><code style="font-size:11px">{{ r.accountCode }}</code></td>
              <td :style="rowStyle(r)">{{ r.accountName }}</td>
              <td class="num mono">{{ r.debitBalance  ? fmt(r.debitBalance)  : '—' }}</td>
              <td class="num mono">{{ r.creditBalance ? fmt(r.creditBalance) : '—' }}</td>
              <td>
                <IconBtn v-if="!r.isHeader" icon="ledger" @click="tacctDrawer = r" />
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td colspan="2" class="fw-600">
                Totals
                <Badge :status="balanced ? 'approved' : 'error'" :dot="false" style="margin-left:8px">
                  {{ balanced ? 'Balanced' : 'Unbalanced' }}
                </Badge>
              </td>
              <td class="num mono fw-600">{{ fmt(drTotal) }}</td>
              <td class="num mono fw-600">{{ fmt(crTotal) }}</td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>

      <TableFooter v-if="report" :total="rows.length" :label="showZeroBalances ? 'accounts (incl. zero balances)' : 'accounts with active balances'" />
    </div>

    <Modal
      :open="tacctDrawer !== null"
      :title="tacctDrawer?.accountCode"
      :subtitle="tacctDrawer?.accountName"
      :width="800"
      @close="tacctDrawer = null"
    >
      <template v-if="tacctDrawer">
        <TAccount
          :accountCode="tacctDrawer.accountCode"
          :accountName="tacctDrawer.accountName"
          :drLines="tacctDrawer.debitBalance  ? [{ date: report.asOfDate, ref: 'Balance', amount: tacctDrawer.debitBalance }]  : []"
          :crLines="tacctDrawer.creditBalance ? [{ date: report.asOfDate, ref: 'Balance', amount: tacctDrawer.creditBalance }] : []"
        />
      </template>
    </Modal>
  </div>
</template>

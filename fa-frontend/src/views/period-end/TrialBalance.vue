<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { TRIAL_BALANCE } from '@/data/index.js'
import { reports } from '@/api/index.js'
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

const { toast } = useToast()

const DEMO_REPORT = {
  entityId: 'demo', asOfDate: '2026-02-28',
  rows: TRIAL_BALANCE.map(r => ({ accountCode: r.code, accountName: r.name, debitBalance: r.dr || 0, creditBalance: r.cr || 0 })),
  totalDebits:  TRIAL_BALANCE.reduce((s, r) => s + (r.dr  || 0), 0),
  totalCredits: TRIAL_BALANCE.reduce((s, r) => s + (r.cr  || 0), 0),
}

const mode     = ref('TRIAL_BALANCE')
const asOfDate = ref(new Date().toISOString().slice(0, 10))
const report   = ref(DEMO_REPORT)
const loading  = ref(false)
const tacctDrawer = ref(null)

const tabs = [
  { id: 'TRIAL_BALANCE', label: 'Trial Balance' },
  { id: 'POST_CLOSING',  label: 'Post-closing' },
]

async function load() {
  loading.value = true
  try {
    const res = await reports.trialBalance({ entityId: 'current', asOfDate: asOfDate.value })
    if (res) report.value = res
  } catch { /* stays on demo */ } finally {
    loading.value = false
  }
}

onMounted(load)
watch(asOfDate, load)

const rows = computed(() => {
  if (!report.value?.rows) return []
  if (mode.value === 'POST_CLOSING') {
    return report.value.rows.filter(r => !r.accountCode.startsWith('4') && !r.accountCode.startsWith('5'))
  }
  return report.value.rows
})

const drTotal  = computed(() => rows.value.reduce((s, r) => s + (r.debitBalance  || 0), 0))
const crTotal  = computed(() => rows.value.reduce((s, r) => s + (r.creditBalance || 0), 0))
const balanced = computed(() => Math.abs((report.value?.totalDebits || 0) - (report.value?.totalCredits || 0)) < 0.01)
</script>

<template>
  <div class="page">
    <PageHeader
      title="Trial Balance"
      :meta="`As at ${report.asOfDate ?? asOfDate}`"
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
      <Button variant="ghost" icon="download">Export</Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner :kind="balanced ? 'success' : 'warn'" :icon="balanced ? 'check' : 'warn'">
        <template v-if="balanced">Trial balance is balanced — debits equal credits.</template>
        <template v-else>Out of balance — review posting pipeline integrity.</template>
      </Banner>

      <div class="card" style="margin-top:var(--gap)">
        <table class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Account</th>
              <th class="num">Debit (KES)</th>
              <th class="num">Credit (KES)</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.accountCode">
              <td><code>{{ r.accountCode }}</code></td>
              <td>{{ r.accountName }}</td>
              <td class="num mono">{{ r.debitBalance  ? fmt(r.debitBalance)  : '—' }}</td>
              <td class="num mono">{{ r.creditBalance ? fmt(r.creditBalance) : '—' }}</td>
              <td>
                <IconBtn icon="ledger" @click="tacctDrawer = r" />
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

      <TableFooter :total="rows.length" label="accounts" />
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

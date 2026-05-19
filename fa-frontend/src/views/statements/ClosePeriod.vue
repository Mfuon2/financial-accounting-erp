<script setup>
import { ref, computed, onMounted } from 'vue'
import { reports, periods as periodsApi } from '@/api/index.js'
import { useAuth }    from '@/composables/useAuth.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { useToast }   from '@/composables/useToast.js'
import { fmt }        from '@/utils/format.js'
import PageHeader     from '@/components/PageHeader.vue'
import Button         from '@/components/primitives/Button.vue'
import Badge          from '@/components/primitives/Badge.vue'
import Banner         from '@/components/data-display/Banner.vue'

const { currentUser } = useAuth()
const { isDemo }      = useAppMode()
const { toast }       = useToast()

const entityId = computed(() => currentUser.value?.entityId ?? null)

const activePeriod = ref(null)
const preview      = ref(null)
const loading      = ref(true)
const running      = ref(false)

async function loadActivePeriod() {
  if (!entityId.value) return
  try {
    const data = await periodsApi.list({ entityId: entityId.value, size: 50 })
    const items = data?.content ?? (Array.isArray(data) ? data : [])
    activePeriod.value =
      items.find(p => p.status === 'CLOSING') ??
      items.find(p => p.status === 'ADJUSTING') ??
      null
  } catch { }
}

async function loadPreview() {
  loading.value = true
  try {
    const data = isDemo.value
      ? await reports.closingPreview({})
      : await reports.closingPreview({ entityId: entityId.value, periodId: activePeriod.value?.id })
    if (data) preview.value = data
  } catch { } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!isDemo.value) await loadActivePeriod()
  await loadPreview()
})

const allLines = computed(() => {
  if (!preview.value) return []
  return [
    ...(preview.value.revenueLines  ?? []).map(l => ({ ...l, memo: 'Close revenue' })),
    ...(preview.value.expenseLines  ?? []).map(l => ({ ...l, memo: 'Close expense' })),
    ...(preview.value.dividendLines ?? []).map(l => ({ ...l, memo: 'Close dividends' })),
    { accountCode: '3-2000', accountName: 'Retained Earnings', memo: 'Net income to equity',
      debit: null, credit: preview.value.netIncome },
  ]
})

const drTotal  = computed(() => allLines.value.reduce((s, l) => s + (l.debit  ?? 0), 0))
const crTotal  = computed(() => allLines.value.reduce((s, l) => s + (l.credit ?? 0), 0))
const balanced = computed(() => Math.abs(drTotal.value - crTotal.value) < 0.01)

const gates = computed(() => [
  { label: 'Adjusted Trial Balance balanced',                  pass: balanced.value },
  { label: 'Net income computed (revenue − expenses)',          pass: !!preview.value?.netIncome },
  { label: 'Revenue accounts have balances to close',          pass: (preview.value?.revenueLines?.length ?? 0) > 0 },
  { label: 'Expense accounts have balances to close',          pass: (preview.value?.expenseLines?.length ?? 0) > 0 },
  { label: 'Preview is balanced (DR = CR)',                    pass: balanced.value },
])

const allGatesPass = computed(() => gates.value.every(g => g.pass))

async function runClosing() {
  if (!allGatesPass.value) {
    toast.warn('Not all pre-close gates are passing. Review before closing.')
    return
  }
  running.value = true
  try {
    isDemo.value
      ? await reports.runClosing({})
      : await reports.runClosing({ entityId: entityId.value, periodId: activePeriod.value?.id })
    toast.success('Closing entries posted. Period is now CLOSED.')
    if (!isDemo.value) await loadActivePeriod()
    await loadPreview()
  } catch { } finally {
    running.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      :title="`Close Period · ${activePeriod?.periodName ?? preview?.periodCode ?? '…'}`"
      meta="Phase: CLOSING (after adjusting)"
    >
      <Button variant="primary" icon="lock" :loading="running" :disabled="!allGatesPass" @click="runClosing">
        Post closing entries
      </Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner kind="warn" icon="warn">
        Closing entries are irreversible. Once posted, all temporary accounts (revenue and expense) will be zeroed and the period will be locked. Ensure all gates below are passing before proceeding.
      </Banner>

      <div class="card" style="margin-top:var(--gap)">
        <div class="card-head">
          Closing journal entry preview
          <div class="h-meta">
            Net income: <strong>{{ preview ? fmt(preview.netIncome) : '—' }}</strong>
          </div>
        </div>
        <div v-if="loading" style="padding:24px;text-align:center;color:var(--muted)">Computing preview…</div>
        <table v-else class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Account</th>
              <th>Memo</th>
              <th class="num">Debit (KES)</th>
              <th class="num">Credit (KES)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(e, i) in allLines" :key="i">
              <td><code>{{ e.accountCode }}</code></td>
              <td>{{ e.accountName }}</td>
              <td class="muted">{{ e.memo }}</td>
              <td class="num mono">{{ e.debit  ? fmt(e.debit)  : '—' }}</td>
              <td class="num mono">{{ e.credit ? fmt(e.credit) : '—' }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td colspan="3" class="fw-600">Totals</td>
              <td class="num mono fw-600">{{ fmt(drTotal) }}</td>
              <td class="num mono fw-600">{{ fmt(crTotal) }}</td>
            </tr>
          </tfoot>
        </table>
      </div>

      <div class="card" style="margin-top:var(--gap)">
        <div class="card-head">
          Pre-close gates
          <div class="h-meta">{{ gates.filter(g => g.pass).length }}/{{ gates.length }} ready</div>
        </div>
        <table class="tbl">
          <tbody>
            <tr v-for="(g, i) in gates" :key="i">
              <td>{{ g.label }}</td>
              <td style="width:110px">
                <Badge :status="g.pass ? 'approved' : 'pending'" :dot="false">{{ g.pass ? 'Pass' : 'Attention' }}</Badge>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

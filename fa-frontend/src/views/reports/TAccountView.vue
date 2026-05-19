<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ledger } from '@/api/index.js'
import { useAuth }         from '@/composables/useAuth.js'
import { useAppMode }      from '@/composables/useAppMode.js'
import { useAccountCache } from '@/composables/useAccountCache.js'
import { COA } from '@/data/index.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import TAccount from '@/components/data-display/TAccount.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const { currentUser } = useAuth()
const { isDemo }      = useAppMode()
const acctCache       = useAccountCache()

const entityId = computed(() => currentUser.value?.entityId ?? null)

const accountOptions = computed(() => {
  if (isDemo.value) {
    return COA.filter(a => a.type === 'POST').map(a => ({ value: a.code, label: `${a.code} — ${a.name}` }))
  }
  return acctCache.accounts.value.map(a => ({ value: a.id, label: `${a.accountCode} — ${a.accountName}` }))
})

const acct      = ref('')
const startDate = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1).toISOString().slice(0, 10))
const endDate   = ref(new Date().toISOString().slice(0, 10))

const loading = ref(false)
const drLines = ref([])
const crLines = ref([])

const DEMO_DATA = {
  '1-1100': {
    dr: [
      { date: '2026-02-01', ref: 'OB',            amount: 1833980 },
      { date: '2026-02-12', ref: 'PAY-2026-0011', amount: 371200 },
      { date: '2026-02-22', ref: 'PAY-2026-0012', amount: 100000 },
      { date: '2026-02-15', ref: 'JE-2026-0041',  amount: 4500 },
    ],
    cr: [
      { date: '2026-02-25', ref: 'JE-2026-0042',  amount: 553000 },
      { date: '2026-02-04', ref: 'PAY-OUT-0001',  amount: 18400 },
    ],
  },
}

// When the account list loads, default to the first account
watch(accountOptions, (opts) => {
  if (!acct.value && opts.length) acct.value = opts[0].value
}, { immediate: true })

async function load() {
  if (!acct.value) return
  if (isDemo.value) {
    const d = DEMO_DATA[acct.value] ?? { dr: [], cr: [] }
    drLines.value = d.dr
    crLines.value = d.cr
    return
  }
  if (!entityId.value) return
  loading.value = true
  try {
    const res = await ledger.tAccount(acct.value, { startDate: startDate.value, endDate: endDate.value })
    drLines.value = (res?.debitLines ?? []).map(l => ({ date: l.date ?? l.transactionDate, ref: l.reference ?? l.ref, amount: l.amount }))
    crLines.value = (res?.creditLines ?? []).map(l => ({ date: l.date ?? l.transactionDate, ref: l.reference ?? l.ref, amount: l.amount }))
  } catch {
    drLines.value = []
    crLines.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!isDemo.value && entityId.value) acctCache.load(entityId.value)
  load()
})
watch(entityId, (v) => { if (v && !isDemo.value) acctCache.load(v) })
watch(acct, load)
watch([startDate, endDate], load)

const drTotal = computed(() => drLines.value.reduce((s, l) => s + (l.amount || 0), 0))
const crTotal = computed(() => crLines.value.reduce((s, l) => s + (l.amount || 0), 0))

const selectedLabel = computed(() => accountOptions.value.find(a => a.value === acct.value)?.label ?? acct.value)

// For the TAccount header: use the human-readable label directly, not the raw UUID
// accountOptions labels are already "{code} — {name}" so pass them split into code/name
const selectedOption = computed(() => accountOptions.value.find(a => a.value === acct.value))
const displayCode = computed(() => {
  if (!selectedOption.value) return acct.value
  const parts = selectedOption.value.label.split(' — ')
  return parts[0] ?? acct.value
})
const displayName = computed(() => {
  if (!selectedOption.value) return ''
  const parts = selectedOption.value.label.split(' — ')
  return parts.slice(1).join(' — ')
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="T-Account View"
      meta="Visual debit/credit ledger for any account"
    />

    <div class="page-section stack">
      <div class="card">
        <div class="card-head" style="display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid var(--border);flex-wrap:wrap">
          <label style="font-size:12px;font-weight:600;color:var(--text-muted)">Account</label>
          <SearchableSelect
            v-model="acct"
            :options="accountOptions"
            style="min-width:260px;font-size:13px"
          />
          <label style="font-size:12px;font-weight:600;color:var(--text-muted);margin-left:8px">From</label>
          <input type="date" v-model="startDate" class="date-input" style="font-size:13px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;background:var(--surface);color:var(--text)" />
          <label style="font-size:12px;font-weight:600;color:var(--text-muted)">To</label>
          <input type="date" v-model="endDate" class="date-input" style="font-size:13px;padding:4px 8px;border:1px solid var(--border);border-radius:6px;background:var(--surface);color:var(--text)" />
          <span style="margin-left:auto;font-size:11px;color:var(--text-muted)">
            Balance: {{ fmt(drTotal - crTotal) }} · Dr {{ fmt(drTotal) }} / Cr {{ fmt(crTotal) }}
          </span>
        </div>
        <div v-if="loading" style="padding:48px;text-align:center;color:var(--text-muted)">Loading…</div>
        <div v-else class="card-body" style="padding:16px">
          <TAccount
            :accountCode="displayCode"
            :accountName="displayName"
            :drLines="drLines"
            :crLines="crLines"
          />
        </div>
      </div>
    </div>
  </div>
</template>

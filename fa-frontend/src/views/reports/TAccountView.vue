<script setup>
import { ref, computed } from 'vue'
import { COA } from '@/data/index.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import TAccount from '@/components/data-display/TAccount.vue'
import SearchableSelect from '@/components/primitives/SearchableSelect.vue'

const acct = ref('1-1100')

const accounts = COA.filter(a => a.type === 'POST')

const sampleData = {
  '1-1100': {
    dr: [
      { date: '2026-02-01', ref: 'OB',             amount: 1833980 },
      { date: '2026-02-12', ref: 'PAY-2026-0011',  amount: 371200 },
      { date: '2026-02-22', ref: 'PAY-2026-0012',  amount: 100000 },
      { date: '2026-02-15', ref: 'JE-2026-0041',   amount: 4500 },
    ],
    cr: [
      { date: '2026-02-25', ref: 'JE-2026-0042',   amount: 553000 },
      { date: '2026-02-04', ref: 'PAY-OUT-0001',   amount: 18400 },
    ],
  },
}

const data = computed(() => sampleData[acct.value] || { dr: [], cr: [] })
const drT = computed(() => data.value.dr.reduce((s, l) => s + l.amount, 0))
const crT = computed(() => data.value.cr.reduce((s, l) => s + l.amount, 0))

const selectedAccount = computed(() => accounts.find(a => a.code === acct.value))
</script>

<template>
  <div class="page">
    <PageHeader
      title="T-Account View"
      meta="Visual debit/credit ledger"
    />

    <div class="page-section stack">
      <div class="card">
        <div class="card-head" style="display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid var(--border)">
          <label style="font-size:12px;font-weight:600;color:var(--muted)">Account</label>
          <SearchableSelect
            v-model="acct"
            :options="accounts.map(a => ({ value: a.accountCode ?? a.id, label: `${a.accountCode ?? a.code} — ${a.accountName ?? a.name}` }))"
            style="font-size:13px"
          />
          <span style="margin-left:auto;font-size:11px;color:var(--muted)">
            Balance: {{ fmt(drT - crT) }} KES · Dr {{ fmt(drT) }} / Cr {{ fmt(crT) }}
          </span>
        </div>
        <div class="card-body" style="padding:16px">
          <TAccount
            :accountCode="acct"
            :accountName="selectedAccount?.name || ''"
            :drLines="data.dr"
            :crLines="data.cr"
          />
        </div>
      </div>
    </div>
  </div>
</template>

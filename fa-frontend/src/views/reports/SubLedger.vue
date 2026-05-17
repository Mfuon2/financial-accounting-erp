<script setup>
import { ref, computed } from 'vue'
import { CUSTOMERS, SUPPLIERS, ASSETS } from '@/data/index.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const tab = ref('AR')

const tabs = [
  { id: 'AR', label: 'Customer AR' },
  { id: 'AP', label: 'Supplier AP' },
  { id: 'FA', label: 'Fixed Asset' },
]

function stableCount(id, min, range) {
  return min + (Math.abs(id.charCodeAt(0) % range))
}

function stableDso(id) {
  return 22 + (Math.abs(id.charCodeAt(0) % 21))
}

const arCustomers = computed(() => CUSTOMERS.filter(c => c.balance > 0))
const apSuppliers = computed(() => SUPPLIERS.filter(s => s.balance > 0))
</script>

<template>
  <div class="page">
    <PageHeader
      title="Sub-Ledgers"
      meta="Customer AR & Supplier AP balances by counterparty"
      :tabs="tabs"
      :activeTab="tab"
      @tab="tab = $event"
    />

    <div class="page-section stack">
      <div v-if="tab === 'AR'" class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Customer</th>
              <th>Currency</th>
              <th class="num">Open invoices</th>
              <th class="num">Balance</th>
              <th class="num">DSO</th>
              <th style="width:140px">Credit utilization</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in arCustomers" :key="c.id">
              <td>
                <div>{{ c.name }}</div>
                <div class="muted" style="font-size:11px">{{ c.code }}</div>
              </td>
              <td>{{ c.currency }}</td>
              <td class="num">{{ stableCount(c.id, 2, 3) }}</td>
              <td class="num mono">{{ fmt(c.balance) }}</td>
              <td class="num">{{ stableDso(c.id) }}d</td>
              <td>
                <div style="display:flex;align-items:center;gap:6px">
                  <div style="flex:1;height:5px;background:var(--border);border-radius:99px;overflow:hidden">
                    <div
                      :style="{
                        width: Math.min(100, Math.round(c.balance / c.creditLimit * 100)) + '%',
                        height: '100%',
                        background: c.balance / c.creditLimit > 0.8 ? 'var(--neg)' : 'var(--accent)',
                        borderRadius: '99px',
                      }"
                    />
                  </div>
                  <span style="font-size:11px;color:var(--muted)">
                    {{ Math.min(100, Math.round(c.balance / c.creditLimit * 100)) }}%
                  </span>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="arCustomers.length" label="customers" />
      </div>

      <div v-if="tab === 'AP'" class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Supplier</th>
              <th>Currency</th>
              <th class="num">Open bills</th>
              <th class="num">Balance owed</th>
              <th>Terms</th>
              <th>Next due</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="s in apSuppliers" :key="s.id">
              <td>
                <div>{{ s.name }}</div>
                <div class="muted" style="font-size:11px">{{ s.code }}</div>
              </td>
              <td>{{ s.currency }}</td>
              <td class="num">{{ stableCount(s.id, 1, 3) }}</td>
              <td class="num mono">{{ fmt(s.balance) }}</td>
              <td>{{ s.terms }}</td>
              <td>{{ fmtDate(s.lastBill) }}</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="apSuppliers.length" label="suppliers" />
      </div>

      <div v-if="tab === 'FA'" class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Tag</th>
              <th>Asset</th>
              <th class="num">Cost</th>
              <th class="num">Accum. dep.</th>
              <th class="num">Net Book Value</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in ASSETS" :key="a.id">
              <td><code>{{ a.tag }}</code></td>
              <td>
                <div>{{ a.name }}</div>
                <div class="muted" style="font-size:11px">{{ a.category }}</div>
              </td>
              <td class="num mono">{{ fmt(a.cost) }}</td>
              <td class="num mono">{{ fmt(a.accum) }}</td>
              <td class="num mono">{{ fmt(a.netBook) }}</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="ASSETS.length" label="assets" />
      </div>
    </div>
  </div>
</template>

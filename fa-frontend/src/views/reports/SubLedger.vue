<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { customers as customersApi, suppliers as suppliersApi, assets as assetsApi, ledger } from '@/api/index.js'
import { useAuth }    from '@/composables/useAuth.js'
import { useAppMode } from '@/composables/useAppMode.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import Modal from '@/components/overlays/Modal.vue'
import Button from '@/components/primitives/Button.vue'

const { currentUser } = useAuth()
const { isDemo }      = useAppMode()

const entityId = computed(() => currentUser.value?.entityId ?? null)

const tab = ref('AR')
const tabs = [
  { id: 'AR', label: 'Customer AR' },
  { id: 'AP', label: 'Supplier AP' },
  { id: 'FA', label: 'Fixed Assets' },
]

const loading     = ref(false)
const arList      = ref([])
const apList      = ref([])
const faList      = ref([])

const drawerParty   = ref(null)
const drawerEntries = ref([])
const drawerLoading = ref(false)

function toArray(v) {
  if (!v) return []
  if (Array.isArray(v)) return v
  if (v.content) return v.content
  return []
}

async function loadTab(t) {
  if (isDemo.value || !entityId.value) return
  loading.value = true
  try {
    if (t === 'AR') {
      const res = await customersApi.list({ entityId: entityId.value, size: 200 })
      arList.value = toArray(res).filter(c => (c.balance ?? c.outstandingBalance ?? 0) > 0)
    } else if (t === 'AP') {
      const res = await suppliersApi.list({ entityId: entityId.value, size: 200 })
      apList.value = toArray(res).filter(s => (s.balance ?? s.outstandingBalance ?? 0) > 0)
    } else if (t === 'FA') {
      const res = await assetsApi.list({ entityId: entityId.value, size: 200 })
      faList.value = toArray(res)
    }
  } catch { /* leave list empty on error */ } finally {
    loading.value = false
  }
}

onMounted(() => loadTab(tab.value))
watch(tab, loadTab)
watch(entityId, (v) => { if (v) loadTab(tab.value) })

async function openLedger(party, type) {
  drawerParty.value   = { ...party, _type: type }
  drawerEntries.value = []
  drawerLoading.value = true
  try {
    if (isDemo.value) {
      drawerEntries.value = []
    } else if (type === 'AR') {
      const res = await ledger.customerSubsidiary(party.id)
      drawerEntries.value = res?.entries ?? toArray(res)
    } else {
      const res = await ledger.supplierSubsidiary(party.id)
      drawerEntries.value = res?.entries ?? toArray(res)
    }
  } catch { drawerEntries.value = [] } finally {
    drawerLoading.value = false
  }
}

function closeLedger() { drawerParty.value = null; drawerEntries.value = [] }

function balanceOf(item) {
  return item.balance ?? item.outstandingBalance ?? item.openBalance ?? 0
}

function creditUtil(item) {
  const lim = item.creditLimit ?? 0
  if (!lim) return 0
  return Math.min(100, Math.round(balanceOf(item) / lim * 100))
}

function runningBalance(entries) {
  let bal = 0
  return entries.map(e => {
    bal += (e.debit ?? 0) - (e.credit ?? 0)
    return { ...e, running: bal }
  })
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Sub-Ledgers"
      meta="Customer AR · Supplier AP · Fixed Assets"
      :tabs="tabs"
      :activeTab="tab"
      @tab="tab = $event"
    />

    <div class="page-section stack">

      <!-- AR -->
      <div v-if="tab === 'AR'">
        <div v-if="loading && !arList.length" class="card" style="padding:48px;text-align:center;color:var(--text-muted)">Loading…</div>
        <div v-else class="card">
          <table class="tbl">
            <thead>
              <tr>
                <th>Customer</th>
                <th>Currency</th>
                <th class="num">Balance</th>
                <th class="num">DSO</th>
                <th style="width:140px">Credit utilization</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="c in arList" :key="c.id">
                <td>
                  <div>{{ c.name }}</div>
                  <div class="muted" style="font-size:11px">{{ c.code ?? c.customerCode }}</div>
                </td>
                <td>{{ c.currency ?? c.currencyCode ?? '—' }}</td>
                <td class="num mono">{{ fmt(balanceOf(c)) }}</td>
                <td class="num muted">—</td>
                <td>
                  <div style="display:flex;align-items:center;gap:6px">
                    <div style="flex:1;height:5px;background:var(--border);border-radius:99px;overflow:hidden">
                      <div
                        :style="{
                          width: creditUtil(c) + '%',
                          height: '100%',
                          background: creditUtil(c) > 80 ? 'var(--neg)' : 'var(--accent)',
                          borderRadius: '99px',
                        }"
                      />
                    </div>
                    <span style="font-size:11px;color:var(--text-muted)">{{ creditUtil(c) }}%</span>
                  </div>
                </td>
                <td>
                  <Button variant="ghost" size="sm" icon="ledger" @click="openLedger(c, 'AR')">Ledger</Button>
                </td>
              </tr>
            </tbody>
          </table>
          <TableFooter :total="arList.length" label="customers" />
        </div>
      </div>

      <!-- AP -->
      <div v-if="tab === 'AP'">
        <div v-if="loading && !apList.length" class="card" style="padding:48px;text-align:center;color:var(--text-muted)">Loading…</div>
        <div v-else class="card">
          <table class="tbl">
            <thead>
              <tr>
                <th>Supplier</th>
                <th>Currency</th>
                <th class="num">Balance owed</th>
                <th>Terms</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="s in apList" :key="s.id">
                <td>
                  <div>{{ s.name }}</div>
                  <div class="muted" style="font-size:11px">{{ s.code ?? s.supplierCode }}</div>
                </td>
                <td>{{ s.currency ?? s.currencyCode ?? '—' }}</td>
                <td class="num mono">{{ fmt(balanceOf(s)) }}</td>
                <td>{{ s.terms ?? s.paymentTerms ?? '—' }}</td>
                <td>
                  <Button variant="ghost" size="sm" icon="ledger" @click="openLedger(s, 'AP')">Ledger</Button>
                </td>
              </tr>
            </tbody>
          </table>
          <TableFooter :total="apList.length" label="suppliers" />
        </div>
      </div>

      <!-- Fixed Assets -->
      <div v-if="tab === 'FA'">
        <div v-if="loading && !faList.length" class="card" style="padding:48px;text-align:center;color:var(--text-muted)">Loading…</div>
        <div v-else class="card">
          <table class="tbl">
            <thead>
              <tr>
                <th>Tag</th>
                <th>Asset</th>
                <th class="num">Cost</th>
                <th class="num">Accum. dep.</th>
                <th class="num">Net Book Value</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in faList" :key="a.id">
                <td><code>{{ a.tag ?? a.assetTag ?? a.assetCode }}</code></td>
                <td>
                  <div>{{ a.name ?? a.assetName }}</div>
                  <div class="muted" style="font-size:11px">{{ a.category ?? a.categoryName }}</div>
                </td>
                <td class="num mono">{{ fmt(a.cost ?? a.acquisitionCost ?? 0) }}</td>
                <td class="num mono">{{ fmt(a.accum ?? a.accumulatedDepreciation ?? 0) }}</td>
                <td class="num mono">{{ fmt(a.netBook ?? a.netBookValue ?? (a.cost ?? 0) - (a.accum ?? 0)) }}</td>
                <td>
                  <span class="badge" :class="'badge--' + (a.status === 'ACTIVE' ? 'active' : a.status === 'DISPOSED' ? 'error' : 'info')">
                    {{ a.status ?? '—' }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
          <TableFooter :total="faList.length" label="assets" />
        </div>
      </div>
    </div>

    <!-- Subsidiary ledger drawer -->
    <Modal
      :open="drawerParty !== null"
      :title="drawerParty?.name"
      :subtitle="`${drawerParty?._type === 'AR' ? 'AR' : 'AP'} Subsidiary Ledger — ${drawerParty?.code ?? drawerParty?.customerCode ?? drawerParty?.supplierCode ?? ''}`"
      :width="860"
      @close="closeLedger"
    >
      <div v-if="drawerLoading" style="padding:48px;text-align:center;color:var(--text-muted)">Loading ledger…</div>
      <div v-else-if="!drawerEntries.length" style="padding:32px;text-align:center;color:var(--text-muted)">
        No ledger entries found for this period.
      </div>
      <table v-else class="tbl">
        <thead>
          <tr>
            <th>Date</th>
            <th>Reference</th>
            <th>Description</th>
            <th class="num">Debit</th>
            <th class="num">Credit</th>
            <th class="num">Balance</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(e, i) in runningBalance(drawerEntries)" :key="i">
            <td>{{ fmtDate(e.date ?? e.transactionDate) }}</td>
            <td><code>{{ e.reference ?? e.ref ?? '—' }}</code></td>
            <td>{{ e.description ?? e.narrative ?? '—' }}</td>
            <td class="num mono">{{ e.debit ? fmt(e.debit) : '—' }}</td>
            <td class="num mono">{{ e.credit ? fmt(e.credit) : '—' }}</td>
            <td class="num mono fw-600">{{ fmt(e.running) }}</td>
          </tr>
        </tbody>
      </table>
    </Modal>
  </div>
</template>

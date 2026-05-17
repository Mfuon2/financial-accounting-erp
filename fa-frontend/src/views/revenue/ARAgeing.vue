<script setup>
import { computed } from 'vue'
import { AR_AGEING } from '@/data/index.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const totals = computed(() => ({
  current: AR_AGEING.reduce((s, r) => s + r.current, 0),
  b1_30:   AR_AGEING.reduce((s, r) => s + r.b1_30, 0),
  b31_60:  AR_AGEING.reduce((s, r) => s + r.b31_60, 0),
  b61_90:  AR_AGEING.reduce((s, r) => s + r.b61_90, 0),
  b90:     AR_AGEING.reduce((s, r) => s + r.b90, 0),
  total:   AR_AGEING.reduce((s, r) => s + r.total, 0),
}))
</script>

<template>
  <div class="page">
    <PageHeader
      title="AR Ageing Report"
      meta="As at 28 Feb 2026 · KES functional"
    >
      <Button variant="ghost" icon="download">Export</Button>
      <Button variant="primary" icon="envelope">Send statements</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="kpi-grid" style="grid-template-columns:repeat(5,1fr)">
        <Kpi label="Current" :value="totals.current" />
        <Kpi label="1–30 days" :value="totals.b1_30" />
        <Kpi label="31–60 days" :value="totals.b31_60" />
        <Kpi label="61–90 days" :value="totals.b61_90" />
        <Kpi label="90+ days" :value="totals.b90" />
      </div>

      <div class="card" style="margin-top:var(--gap)">
        <table class="tbl">
          <thead>
            <tr>
              <th>Customer</th>
              <th class="num">Current</th>
              <th class="num">1–30</th>
              <th class="num">31–60</th>
              <th class="num">61–90</th>
              <th class="num">90+</th>
              <th class="num">Total</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in AR_AGEING" :key="row.customer">
              <td>{{ row.customer }}</td>
              <td class="num mono">{{ row.current ? fmt(row.current) : '—' }}</td>
              <td class="num mono">{{ row.b1_30 ? fmt(row.b1_30) : '—' }}</td>
              <td class="num mono">{{ row.b31_60 ? fmt(row.b31_60) : '—' }}</td>
              <td
                class="num mono"
                :style="{ color: row.b61_90 ? 'var(--warn)' : '' }"
              >{{ row.b61_90 ? fmt(row.b61_90) : '—' }}</td>
              <td
                class="num mono"
                :style="{ color: row.b90 ? 'var(--neg)' : '' }"
              >{{ row.b90 ? fmt(row.b90) : '—' }}</td>
              <td class="num mono"><strong>{{ fmt(row.total) }}</strong></td>
              <td>
                <Button variant="ghost" size="sm" icon="bell">Remind</Button>
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td><strong>Total</strong></td>
              <td class="num mono"><strong>{{ fmt(totals.current) }}</strong></td>
              <td class="num mono"><strong>{{ fmt(totals.b1_30) }}</strong></td>
              <td class="num mono"><strong>{{ fmt(totals.b31_60) }}</strong></td>
              <td class="num mono" :style="{ color: totals.b61_90 ? 'var(--warn)' : '' }">
                <strong>{{ fmt(totals.b61_90) }}</strong>
              </td>
              <td class="num mono" :style="{ color: totals.b90 ? 'var(--neg)' : '' }">
                <strong>{{ fmt(totals.b90) }}</strong>
              </td>
              <td class="num mono"><strong>{{ fmt(totals.total) }}</strong></td>
              <td></td>
            </tr>
          </tfoot>
        </table>
      </div>

      <TableFooter :total="AR_AGEING.length" label="customers" />
    </div>
  </div>
</template>

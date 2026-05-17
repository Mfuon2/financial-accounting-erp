<script setup>
import { computed } from 'vue'
import { FX_RATES } from '@/data/index.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Banner from '@/components/data-display/Banner.vue'
import Kpi from '@/components/data-display/Kpi.vue'

const items = [
  { code: "1-1105", name: "Cash & Bank — USD", currency: "USD", balance_fc: 4200,  prior_rate: 130.0200, new_rate: 129.4500, prior_lc: 546084,  new_lc: 543690,  delta: -2394 },
  { code: "1-1200", name: "Accounts Receivable", currency: "USD", balance_fc: 11900, prior_rate: 130.0200, new_rate: 129.4500, prior_lc: 1547238, new_lc: 1540455, delta: -6783 },
  { code: "2-1100", name: "Accounts Payable",    currency: "USD", balance_fc: 2140,  prior_rate: 130.0200, new_rate: 129.4500, prior_lc: 278242,  new_lc: 277023,  delta: 1219 },
  { code: "1-1200", name: "Accounts Receivable", currency: "EUR", balance_fc: 950,   prior_rate: 141.3800, new_rate: 140.2210, prior_lc: 134311,  new_lc: 133210,  delta: -1101 },
]

const total = computed(() => items.reduce((s, i) => s + i.delta, 0))
</script>

<template>
  <div class="page">
    <PageHeader
      title="FX Revaluation (IAS 21)"
      meta="Month-end · 28 Feb 2026 · CBK rates"
    >
      <Button variant="ghost" icon="refresh">Refresh rates</Button>
      <Button variant="primary" icon="play">Post revaluation JE</Button>
    </PageHeader>

    <div class="page-section stack">
      <Banner kind="info" icon="info">
        IAS 21 requires monetary items denominated in foreign currency to be retranslated at the closing rate. Any resulting exchange differences are recognised in profit or loss for the period.
      </Banner>

      <div class="kpi-grid" style="grid-template-columns:repeat(3,1fr);margin-top:var(--gap)">
        <Kpi label="USD closing rate" :value="FX_RATES[0].rate.toFixed(4)" unit="KES" />
        <Kpi label="EUR closing rate" :value="FX_RATES[1].rate.toFixed(4)" unit="KES" />
        <Kpi label="GBP closing rate" :value="FX_RATES[2].rate.toFixed(4)" unit="KES" />
      </div>

      <div class="card" style="margin-top:var(--gap)">
        <table class="tbl">
          <thead>
            <tr>
              <th>Code</th>
              <th>Account</th>
              <th>CCY</th>
              <th class="num">Balance (FC)</th>
              <th class="num">Prior rate</th>
              <th class="num">New rate</th>
              <th class="num">Prior LC</th>
              <th class="num">New LC</th>
              <th class="num">Delta (KES)</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, i) in items" :key="i">
              <td><code>{{ item.code }}</code></td>
              <td>{{ item.name }}</td>
              <td>{{ item.currency }}</td>
              <td class="num mono">{{ fmt(item.balance_fc) }}</td>
              <td class="num mono">{{ item.prior_rate.toFixed(4) }}</td>
              <td class="num mono">{{ item.new_rate.toFixed(4) }}</td>
              <td class="num mono">{{ fmt(item.prior_lc) }}</td>
              <td class="num mono">{{ fmt(item.new_lc) }}</td>
              <td class="num mono" :style="{ color: item.delta < 0 ? 'var(--neg)' : 'var(--pos)' }">
                {{ fmt(item.delta, { signed: true }) }}
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr class="total-row">
              <td colspan="8" class="fw-600">Net FX gain / (loss)</td>
              <td class="num mono fw-600" :style="{ color: total < 0 ? 'var(--neg)' : 'var(--pos)' }">
                {{ fmt(total, { signed: true }) }}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { CASHFLOW } from '@/data/index.js'
import { reports } from '@/api/index.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import StRow from '@/components/data-display/StRow.vue'

const router = useRouter()
const { toast } = useToast()

async function exportPdf() {
  try {
    await reports.pdfCashFlow({}, 'cash-flow.pdf')
    toast.success('PDF downloaded.')
  } catch { /* handled by client */ }
}
</script>

<template>
  <div class="page">
    <PageHeader title="Statement of Cash Flows (IAS 7 — Indirect)" :meta="CASHFLOW.period">
      <template #default>
        <Button size="sm" icon="download" variant="ghost" @click="exportPdf">PDF</Button>
        <Button size="sm" icon="download" variant="ghost">Excel</Button>
        <Button size="sm" icon="external" variant="ghost" @click="router.push('/ias1')">IAS 1 check</Button>
      </template>
    </PageHeader>
    <div class="page-section">
      <div class="statement">
        <div class="statement-head">
          <h2>Statement of Cash Flows (IAS 7 — Indirect)</h2>
          <div class="e-name">Apollo Enterprises Limited · ORG-1A3F</div>
          <div class="e-period">{{ CASHFLOW.period }}</div>
        </div>
        <div class="st-row st-header" style="margin-top:16px;margin-bottom:6px;font-size:10.5px;color:var(--muted);font-weight:700;letter-spacing:0.05em;text-transform:uppercase">
          <div></div>
          <div class="st-num">Current period</div>
          <div class="st-num dim">Prior period</div>
        </div>
        <StRow v-for="(r, i) in CASHFLOW.sections" :key="i" v-bind="r" />
        <div style="margin-top:24px;font-size:10.5px;color:var(--muted);border-top:1px solid var(--border);padding-top:12px;display:flex;justify-content:space-between">
          <span>All figures in Kenyan Shillings (KES) · Functional &amp; reporting currency</span>
          <span class="mono">Generated 28 Feb 2026 · QeSuite IFRS</span>
        </div>
      </div>
    </div>
  </div>
</template>

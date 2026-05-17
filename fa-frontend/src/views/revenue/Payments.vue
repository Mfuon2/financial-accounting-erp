<script setup>
import { ref } from 'vue'
import { PAYMENTS } from '@/data/index.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import Kpi from '@/components/data-display/Kpi.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableToolbar from '@/components/tables/TableToolbar.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import ChipFilter from '@/components/primitives/ChipFilter.vue'

const search = ref('')
const drawer = ref(null)

const currencies = [...new Set(PAYMENTS.map(p => p.currency))].join(', ')
</script>

<template>
  <div class="page">
    <PageHeader
      title="Payments"
      :meta="`${PAYMENTS.length} payments · ${currencies}`"
    >
      <Button variant="ghost" icon="globe">M-Pesa callback log</Button>
      <Button variant="primary" icon="plus">Record payment</Button>
    </PageHeader>

    <div class="page-section stack">
      <TableToolbar v-model:search="search">
        <ChipFilter :active="true">Method: any</ChipFilter>
        <ChipFilter>Customer: any</ChipFilter>
        <ChipFilter>Status: any</ChipFilter>
      </TableToolbar>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Ref</th>
              <th>Customer</th>
              <th>Date</th>
              <th>Method</th>
              <th class="num">Amount</th>
              <th class="num">Matched</th>
              <th>Against invoice</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in PAYMENTS" :key="p.id" class="row-link" @click="drawer = p">
              <td><code>{{ p.ref }}</code></td>
              <td>{{ p.customer }}</td>
              <td>{{ fmtDate(p.date) }}</td>
              <td>
                <Badge :status="p.method === 'M_PESA' ? 'm-pesa' : 'bank-transfer'" :dot="false">{{ p.method }}</Badge>
              </td>
              <td class="num mono">{{ p.currency }} {{ fmt(p.amount) }}</td>
              <td class="num mono">{{ fmt(p.matched) }}</td>
              <td>
                <code v-if="p.invoice">{{ p.invoice }}</code>
                <span v-else class="muted" style="font-style:italic">unmatched</span>
              </td>
              <td><Badge :status="p.status" :dot="false" /></td>
              <td @click.stop>
                <IconBtn icon="dots" @click="drawer = p" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="PAYMENTS.length" label="payments" />
    </div>

    <Modal
      :open="drawer !== null"
      :title="drawer?.ref"
      :subtitle="drawer ? `${drawer.customer} · ${drawer.currency} ${fmt(drawer.amount)}` : ''"
      :width="820"
      @close="drawer = null"
    >
      <template v-if="drawer">
        <div class="kpi-grid" style="grid-template-columns:repeat(4,1fr)">
          <Kpi label="Date" :value="fmtDate(drawer.date)" />
          <Kpi label="Method" :value="drawer.method" />
          <Kpi label="Amount" :value="`${drawer.currency} ${fmt(drawer.amount)}`" />
          <Kpi label="Status" :value="drawer.status" />
        </div>

        <div class="card">
          <div class="card-head">Matched invoices</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>Invoice</th>
                <th>Customer</th>
                <th class="num">Matched amount</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="drawer.invoice">
                <td><code>{{ drawer.invoice }}</code></td>
                <td>{{ drawer.customer }}</td>
                <td class="num mono">{{ fmt(drawer.matched) }}</td>
              </tr>
              <tr v-else>
                <td colspan="3" class="muted" style="font-style:italic">No invoices matched</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="card">
          <div class="card-head">Posting on approval</div>
          <table class="tbl">
            <thead>
              <tr>
                <th>Account</th>
                <th>Description</th>
                <th class="num">Debit</th>
                <th class="num">Credit</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>1-1100</code> Cash &amp; Bank — KES</td>
                <td>Payment received</td>
                <td class="num mono">{{ fmt(drawer.amount) }}</td>
                <td class="num mono">—</td>
              </tr>
              <tr>
                <td><code>1-1200</code> Accounts Receivable</td>
                <td>Clear receivable</td>
                <td class="num mono">—</td>
                <td class="num mono">{{ fmt(drawer.amount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>

      <template #footer>
        <template v-if="drawer?.status === 'PENDING_APPROVAL'">
          <Button variant="primary" icon="approve">Approve</Button>
          <Button variant="ghost" icon="link">Match</Button>
        </template>
        <template v-else-if="drawer?.status === 'APPROVED'">
          <Button variant="primary" icon="approve">Post payment</Button>
          <Button variant="ghost" icon="rotate">Reverse</Button>
        </template>
        <template v-else-if="drawer?.status === 'DRAFT'">
          <Button variant="primary" icon="approve">Submit</Button>
          <Button variant="ghost" icon="link">Match</Button>
        </template>
        <template v-else>
          <Button variant="ghost" icon="receipt">View receipt</Button>
          <Button variant="ghost" icon="rotate">Reverse</Button>
        </template>
      </template>
    </Modal>
  </div>
</template>

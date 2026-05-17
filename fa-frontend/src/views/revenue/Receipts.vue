<script setup>
import { RECEIPTS } from '@/data/index.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
</script>

<template>
  <div class="page">
    <PageHeader
      title="Receipts"
      :meta="`${RECEIPTS.length} receipts`"
    />

    <div class="page-section stack">
      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Ref</th>
              <th>Linked payment</th>
              <th>Customer</th>
              <th>Date</th>
              <th class="num">Amount</th>
              <th>Issued?</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in RECEIPTS" :key="r.id">
              <td><code>{{ r.ref }}</code></td>
              <td><code>{{ r.payment }}</code></td>
              <td>{{ r.customer }}</td>
              <td>{{ fmtDate(r.date) }}</td>
              <td class="num mono">{{ r.currency }} {{ fmt(r.amount) }}</td>
              <td>
                <Badge :status="r.issued ? 'approved' : 'info'" :dot="false">
                  {{ r.issued ? 'Sent' : 'Generated' }}
                </Badge>
              </td>
              <td><Badge :status="r.status" :dot="false" /></td>
              <td>
                <div style="display:flex;gap:4px">
                  <Button variant="ghost" size="sm" icon="envelope">
                    {{ r.issued ? 'Resend' : 'Issue' }}
                  </Button>
                  <Button variant="ghost" size="sm" icon="download">PDF</Button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="RECEIPTS.length" label="receipts" />
    </div>
  </div>
</template>

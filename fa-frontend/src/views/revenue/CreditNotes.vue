<script setup>
import { ref, onMounted } from 'vue'
import { CREDIT_NOTES } from '@/data/index.js'
import { invoices as invoicesApi } from '@/api/index.js'
import { useToast } from '@/composables/useToast.js'
import { fmt, fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import IconBtn from '@/components/primitives/IconBtn.vue'
import Badge from '@/components/primitives/Badge.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const { toast } = useToast()

const list = ref([...CREDIT_NOTES])

onMounted(async () => {
  try {
    const data = await invoicesApi.creditNotes()
    if (data?.content) list.value = data.content
    else if (Array.isArray(data)) list.value = data
  } catch { /* stays on demo */ }
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="Credit Notes"
      :meta="`${list.length} credit notes`"
    >
      <Button variant="primary" icon="plus">New credit note</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Ref</th>
              <th>Against</th>
              <th>Customer</th>
              <th>Date</th>
              <th class="num">Amount</th>
              <th>Reason</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="cn in list" :key="cn.id">
              <td><code>{{ cn.ref ?? cn.invoiceNumber }}</code></td>
              <td><code>{{ cn.invoice ?? cn.originalInvoiceId ?? '—' }}</code></td>
              <td>{{ cn.customer ?? cn.customerName ?? '—' }}</td>
              <td>{{ fmtDate(cn.date ?? cn.issueDate) }}</td>
              <td class="num mono">{{ (cn.currency ?? cn.currencyCode ?? 'KES') }} {{ fmt(cn.amount ?? cn.totalAmount ?? 0) }}</td>
              <td>{{ cn.reason ?? '—' }}</td>
              <td><Badge :status="cn.status?.toLowerCase() ?? 'outline'" :dot="false">{{ cn.status ?? '—' }}</Badge></td>
              <td><IconBtn icon="dots" /></td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="list.length" label="credit notes" />
    </div>
  </div>
</template>

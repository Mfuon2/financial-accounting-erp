<script setup>
import { ref, onMounted } from 'vue'
import { approvals as approvalsApi } from '@/api/approvals.js'
import { fmt } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const items = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    items.value = await approvalsApi.list()
  } finally {
    loading.value = false
  }
})

async function approve(item) {
  await approvalsApi.approve(item.id, { type: item.type.toUpperCase().replace(/ /g, '_') })
  items.value = items.value.filter(a => a.id !== item.id)
}

async function reject(item) {
  await approvalsApi.reject(item.id, { type: item.type.toUpperCase().replace(/ /g, '_'), reason: 'Rejected' })
  items.value = items.value.filter(a => a.id !== item.id)
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Approvals"
      :meta="`${items.length} items pending your review`"
    >
      <Button variant="ghost" icon="filter">Filter</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Type</th>
              <th>Reference</th>
              <th>Title</th>
              <th class="num">Amount</th>
              <th>Submitted by</th>
              <th>Waiting for</th>
              <th>Submitted</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in items" :key="a.id">
              <td>
                <Badge
                  :status="a.type === 'Journal Entry' ? 'posted' : a.type === 'Payment' ? 'info' : 'draft'"
                  :dot="false"
                >{{ a.type }}</Badge>
              </td>
              <td><span class="code-cell link">{{ a.ref }}</span></td>
              <td>{{ a.title }}</td>
              <td class="num">{{ a.currency }} {{ fmt(a.amount) }}</td>
              <td><Badge status="outline" :dot="false">{{ a.submittedBy }}</Badge></td>
              <td>{{ a.waitingFor }}</td>
              <td>{{ a.submittedAt }}</td>
              <td>
                <div style="display:flex;gap:4px">
                  <Button variant="ghost" size="sm" icon="x" @click="reject(a)">Reject</Button>
                  <Button variant="primary" size="sm" icon="check" @click="approve(a)">Approve</Button>
                </div>
              </td>
            </tr>
            <tr v-if="!loading && items.length === 0">
              <td colspan="8" style="text-align:center;padding:24px;color:var(--muted)">No pending approvals</td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="items.length" label="items" />
      </div>
    </div>
  </div>
</template>

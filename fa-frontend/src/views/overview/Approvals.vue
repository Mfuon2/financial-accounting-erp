<script setup>
import { ref, computed, onMounted } from 'vue'
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

const approvingId = ref(null)
const rejectingId = ref(null)

function typeLabel(type) {
  const map = { JOURNAL_ENTRY: 'Journal Entry', INVOICE: 'Invoice', BILL: 'Vendor Bill' }
  return map[type?.toUpperCase()] ?? type
}
function typeBadge(type) {
  const map = { JOURNAL_ENTRY: 'posted', INVOICE: 'info', BILL: 'submitted' }
  return map[type?.toUpperCase()] ?? 'draft'
}

async function approve(item) {
  approvingId.value = item.id
  try {
    await approvalsApi.approve(item.id, { type: item.type.toUpperCase().replace(/ /g, '_') })
    items.value = items.value.filter(a => a.id !== item.id)
  } finally { approvingId.value = null }
}

async function reject(item) {
  rejectingId.value = item.id
  try {
    await approvalsApi.reject(item.id, { type: item.type.toUpperCase().replace(/ /g, '_'), reason: 'Rejected' })
    items.value = items.value.filter(a => a.id !== item.id)
  } finally { rejectingId.value = null }
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
                <Badge :status="typeBadge(a.type)" :dot="false">{{ typeLabel(a.type) }}</Badge>
              </td>
              <td><code style="font-size:12px">{{ a.ref }}</code></td>
              <td>{{ a.title }}</td>
              <td class="num mono">{{ a.currency }} {{ fmt(a.amount) }}</td>
              <td><Badge status="outline" :dot="false">{{ a.submittedBy }}</Badge></td>
              <td style="font-size:12px;color:var(--muted)">{{ a.waitingFor }}</td>
              <td style="font-size:12px;color:var(--muted)">{{ a.submittedAt }}</td>
              <td>
                <div style="display:flex;gap:4px">
                  <Button variant="ghost" size="sm" icon="x"
                    :loading="rejectingId === a.id"
                    @click="reject(a)">Reject</Button>
                  <Button variant="primary" size="sm" icon="approve"
                    :loading="approvingId === a.id"
                    @click="approve(a)">Approve</Button>
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

<script setup>
import { computed } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import TableFooter from '@/components/tables/TableFooter.vue'

const tasks = [
  { id: 1,  name: "Unadjusted Trial Balance generated",        status: "DONE",             who: "system",    ts: "2026-02-28 06:00", link: "/trial-balance" },
  { id: 2,  name: "Transition period to ADJUSTING",            status: "DONE",             who: "w.njeri",   ts: "2026-02-28 09:00", link: "/periods" },
  { id: 3,  name: "Accrue utilities (Feb)",                    status: "PENDING",          who: "m.karanja", ts: null,               link: "/journals" },
  { id: 4,  name: "Prepaid expense amortization (batch)",      status: "PENDING_APPROVAL", who: "w.njeri",   ts: "2026-02-28 11:02", link: "/journals" },
  { id: 5,  name: "Recognize unearned revenue (IFRS 15)",      status: "DONE",             who: "system",    ts: "2026-02-28 12:30", link: "/journals" },
  { id: 6,  name: "Batch depreciation — 5 assets",            status: "DONE",             who: "system",    ts: "2026-02-28 23:55", link: "/depreciation" },
  { id: 7,  name: "FX revaluation (IAS 21)",                  status: "DONE",             who: "system",    ts: "2026-02-28 23:58", link: "/fx" },
  { id: 8,  name: "Adjusted Trial Balance regenerated",        status: "DONE",             who: "system",    ts: "2026-02-28 23:59", link: "/trial-balance" },
  { id: 9,  name: "Profit & Loss draft",                      status: "READY",            who: null,        ts: null,               link: "/pnl" },
  { id: 10, name: "Balance Sheet draft",                      status: "READY",            who: null,        ts: null,               link: "/balance-sheet" },
  { id: 11, name: "Cash Flow Statement (IAS 7)",              status: "READY",            who: null,        ts: null,               link: "/cash-flow" },
  { id: 12, name: "IAS 1 compliance check",                   status: "READY",            who: null,        ts: null,               link: "/ias1" },
  { id: 13, name: "Transition period to CLOSING",             status: "BLOCKED",          who: null,        ts: null,               link: "/close" },
  { id: 14, name: "Post closing entries (zero temp accts)",   status: "BLOCKED",          who: null,        ts: null,               link: "/close" },
  { id: 15, name: "Post-closing Trial Balance",               status: "BLOCKED",          who: null,        ts: null,               link: "/trial-balance" },
]

const doneCount = computed(() => tasks.filter(t => t.status === 'DONE').length)
</script>

<template>
  <div class="page">
    <PageHeader
      title="Period-End Workflow"
      :meta="`Period 2026-02 · ${doneCount}/${tasks.length} tasks complete`"
    >
      <Button variant="ghost" icon="branch">Validate cycle</Button>
      <Button variant="primary" icon="play">Run 9-step cycle</Button>
    </PageHeader>

    <div class="page-section stack">
      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th style="width:40px">#</th>
              <th>Task</th>
              <th>Status</th>
              <th>By</th>
              <th>At</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="t in tasks" :key="t.id">
              <td class="muted">{{ t.id }}</td>
              <td>{{ t.name }}</td>
              <td>
                <Badge :status="t.status" :dot="false" />
              </td>
              <td>
                <Badge v-if="t.who" status="outline" :dot="false">{{ t.who }}</Badge>
                <span v-else class="muted">—</span>
              </td>
              <td class="muted" style="font-size:11px">{{ t.ts || '—' }}</td>
              <td>
                <a :href="t.link" style="font-size:11px;color:var(--accent)">Open</a>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <TableFooter :total="tasks.length" label="tasks" />
    </div>
  </div>
</template>

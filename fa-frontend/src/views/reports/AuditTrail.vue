<script setup>
import { ref, computed, onMounted } from 'vue'
import { AUDIT } from '@/data/index.js'
import { audit } from '@/api/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { useToast } from '@/composables/useToast.js'
import PageHeader   from '@/components/PageHeader.vue'
import Button       from '@/components/primitives/Button.vue'
import Badge        from '@/components/primitives/Badge.vue'
import TableFooter  from '@/components/tables/TableFooter.vue'

const { toast } = useToast()

const events        = ref([])
const page          = ref(0)
const totalElements = ref(0)
const loading       = ref(false)
const filterAction  = ref('')

const PAGE_SIZE = 50

const ACTIONS = ['CREATE', 'UPDATE', 'DELETE', 'POST', 'REVERSE', 'APPROVE', 'REJECT', 'CLOSE', 'REOPEN', 'EXPORT', 'TAX_ADJUSTMENT']

// ── Load ───────────────────────────────────────────────────────────────────────
async function load(p = 0) {
  loading.value = true
  if (isDemo.value) {
    const demo = filterAction.value
      ? AUDIT.filter(e => (e.action || '').toUpperCase() === filterAction.value)
      : AUDIT
    events.value        = demo
    totalElements.value = demo.length
    page.value          = 0
    loading.value       = false
    return
  }
  try {
    const params = { page: p, size: PAGE_SIZE }
    if (filterAction.value) params.action = filterAction.value
    const res = await audit.list(params)
    if (res?.content !== undefined) {
      events.value        = res.content
      totalElements.value = res.totalElements ?? res.content.length
    } else if (Array.isArray(res)) {
      events.value        = res
      totalElements.value = res.length
    }
    page.value = p
  } catch {
    toast.error('Failed to load audit logs.')
  } finally {
    loading.value = false
  }
}
onMounted(() => load(0))

function applyFilter(action) {
  filterAction.value = filterAction.value === action ? '' : action
  load(0)
}

function prevPage() { if (page.value > 0) load(page.value - 1) }
function nextPage() {
  if ((page.value + 1) * PAGE_SIZE < totalElements.value) load(page.value + 1)
}

// ── Helpers ────────────────────────────────────────────────────────────────────
function actionStatus(action) {
  if (!action) return 'draft'
  const a = String(action).toUpperCase()
  if (['POST', 'CREATE'].includes(a))    return 'posted'
  if (a === 'APPROVE')                   return 'approved'
  if (['REVERSE', 'DELETE', 'REJECT'].includes(a)) return 'void'
  if (['UPDATE', 'PATCH', 'CLOSE', 'REOPEN'].includes(a)) return 'pending'
  if (a === 'EXPORT')                    return 'info'
  return 'draft'
}

function formatTs(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  if (isNaN(d)) return String(ts)
  return d.toLocaleString('en-GB', {
    year: 'numeric', month: 'short', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
    hour12: false,
  })
}

function actorLabel(e) {
  return e.actor ?? e.userId ?? '—'
}

function targetLabel(e) {
  const rt = e.target ?? e.resourceType ?? ''
  const ri = e.resourceId ? ` ${String(e.resourceId).slice(0, 8)}…` : ''
  return rt + ri || '—'
}

function detailLabel(e) {
  if (e.detail) return e.detail
  if (e.payloadAfter) {
    try {
      const parsed = typeof e.payloadAfter === 'string' ? JSON.parse(e.payloadAfter) : e.payloadAfter
      const keys = Object.keys(parsed).slice(0, 3)
      return keys.map(k => `${k}: ${parsed[k]}`).join(' · ')
    } catch { return String(e.payloadAfter).slice(0, 120) }
  }
  return '—'
}

const totalPages = computed(() => Math.max(1, Math.ceil(totalElements.value / PAGE_SIZE)))
</script>

<template>
  <div class="page">
    <PageHeader
      title="Audit Trail"
      :meta="`${totalElements.toLocaleString()} events · immutable log`"
    >
      <Button variant="ghost" icon="download">Export CSV</Button>
    </PageHeader>

    <div class="page-section stack">
      <!-- Action filter chips -->
      <div class="filter-row">
        <button
          class="chip-btn"
          :class="{ active: !filterAction }"
          @click="applyFilter('')"
        >All</button>
        <button
          v-for="a in ACTIONS" :key="a"
          class="chip-btn"
          :class="{ active: filterAction === a }"
          @click="applyFilter(a)"
        >{{ a }}</button>
      </div>

      <div class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th style="width:160px">Timestamp</th>
              <th style="width:180px">Actor (User ID)</th>
              <th style="width:120px">Action</th>
              <th style="width:200px">Target</th>
              <th>Detail</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="5" class="empty-cell">Loading…</td>
            </tr>
            <tr v-else-if="!events.length">
              <td colspan="5" class="empty-cell">
                <div style="font-size:14px;font-weight:600;margin-bottom:4px">No audit events found</div>
                <div class="muted" style="font-size:12px">Events are recorded as you create, update, and transition records.</div>
              </td>
            </tr>
            <tr v-for="(e, i) in events" :key="e.id ?? i">
              <td class="mono muted" style="font-size:11px;white-space:nowrap">
                {{ formatTs(e.ts ?? e.createdAt) }}
              </td>
              <td>
                <code class="actor-code">{{ actorLabel(e) }}</code>
              </td>
              <td>
                <Badge :status="actionStatus(e.action)" :dot="false">{{ e.action ?? '—' }}</Badge>
              </td>
              <td>
                <span class="resource-type">{{ e.resourceType ?? e.target ?? '—' }}</span>
                <div v-if="e.resourceId" class="muted mono" style="font-size:10px">
                  {{ String(e.resourceId).slice(0, 8) }}…
                </div>
              </td>
              <td class="detail-cell">{{ detailLabel(e) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div v-if="totalPages > 1" class="pagination-row">
        <Button variant="ghost" size="sm" :disabled="page === 0" @click="prevPage">
          &lt; &lt;
        </Button>
        <Button variant="ghost" size="sm" :disabled="page === 0" @click="prevPage">
          &lt;
        </Button>
        <span class="muted" style="font-size:12px;min-width:80px;text-align:center">
          {{ page + 1 }} / {{ totalPages }}
        </span>
        <Button variant="ghost" size="sm" :disabled="(page + 1) >= totalPages" @click="nextPage">
          &gt;
        </Button>
        <Button variant="ghost" size="sm" :disabled="(page + 1) >= totalPages" @click="nextPage">
          &gt; &gt;
        </Button>
      </div>

      <TableFooter :total="totalElements" label="events" :defaultSize="PAGE_SIZE" />
    </div>
  </div>
</template>

<style scoped>
.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 0 2px;
}
.chip-btn {
  padding: 3px 10px;
  border-radius: 99px;
  border: 1px solid var(--border, #e0e0e0);
  background: transparent;
  font-size: 11px;
  font-weight: 600;
  cursor: pointer;
  color: var(--muted);
  transition: background .12s, color .12s, border-color .12s;
}
.chip-btn:hover { background: var(--surface-raised, #f5f5f5); color: var(--text); }
.chip-btn.active {
  background: var(--accent, #3b5bdb);
  border-color: var(--accent, #3b5bdb);
  color: #fff;
}
.actor-code {
  font-family: monospace;
  font-size: 11px;
  background: var(--surface-raised, #f5f5f5);
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}
.resource-type {
  font-size: 12px;
  font-weight: 600;
}
.detail-cell {
  font-size: 12px;
  color: var(--muted);
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty-cell {
  text-align: center;
  padding: 48px 24px;
  color: var(--muted);
}
.pagination-row {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
  padding: 4px 0;
}
</style>

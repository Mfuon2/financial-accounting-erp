<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { periods as periodsApi } from '@/api/index.js'
import { useAuth } from '@/composables/useAuth.js'
import { useToast } from '@/composables/useToast.js'
import { fmtDate } from '@/utils/format.js'
import PageHeader from '@/components/PageHeader.vue'
import Button from '@/components/primitives/Button.vue'
import Badge from '@/components/primitives/Badge.vue'
import Modal from '@/components/overlays/Modal.vue'
import TableFooter from '@/components/tables/TableFooter.vue'
import Segmented from '@/components/primitives/Segmented.vue'

const { currentUser } = useAuth()
const { toast } = useToast()

const allPeriods   = ref([])
const loading      = ref(false)
const transitioning = ref(null) // period id being transitioned
const showGenFY    = ref(false)
const genFYYear    = ref(new Date().getFullYear())
const genFYLoading = ref(false)
const selectedFY   = ref('ALL') // fiscal-year filter for the table: 'ALL' or a year string

const entityId = computed(() => currentUser.value?.entityId ?? 'current')

// ── Status display helpers ────────────────────────────────────────────────────
const STATUS_NEXT = {
  FUTURE:    ['OPEN'],
  OPEN:      ['ADJUSTING'],
  ADJUSTING: ['CLOSING', 'OPEN'],
  CLOSING:   ['CLOSED', 'ADJUSTING'],
  CLOSED:    ['REOPENED'],
  REOPENED:  ['ADJUSTING', 'CLOSING', 'CLOSED'],
}

const STATUS_LABEL = {
  FUTURE:    'Activate',
  OPEN:      'Begin Adjusting',
  ADJUSTING: 'Begin Closing',
  CLOSING:   'Close Period',
  CLOSED:    'Reopen',
  REOPENED:  'To Adjusting',
}

const STATUS_VARIANT = {
  FUTURE:    'ghost',
  OPEN:      'ghost',
  ADJUSTING: 'ghost',
  CLOSING:   'primary',
  CLOSED:    'ghost',
  REOPENED:  'ghost',
}

// ── Data load ─────────────────────────────────────────────────────────────────
async function load() {
  loading.value = true
  try {
    allPeriods.value = await periodsApi.list({ entityId: entityId.value })
  } catch (e) {
    toast.error('Failed to load accounting periods')
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ── Computed ──────────────────────────────────────────────────────────────────
const adjustingPeriod = computed(() => allPeriods.value.find(p => p.status === 'ADJUSTING'))
// Intentionally NOT scoped to the fiscal-year filter below — the active period must
// stay visible regardless of which fiscal year the table is currently filtered to.
const openPeriod      = computed(() => allPeriods.value.find(p => p.status === 'OPEN'))

// ── Fiscal-year filter/switcher (BUG-34) ───────────────────────────────────────
// Derived entirely from allPeriods at runtime — never a hardcoded year list.
function yearOf(p) {
  if (p.startDate) return Number(String(p.startDate).slice(0, 4))
  if (p.periodName) {
    const parts = p.periodName.trim().split(/\s+/)
    const y = Number(parts[parts.length - 1])
    if (!Number.isNaN(y)) return y
  }
  return null
}

const fiscalYears = computed(() => {
  const years = new Set()
  for (const p of allPeriods.value) {
    const y = yearOf(p)
    if (y) years.add(y)
  }
  return [...years].sort((a, b) => a - b)
})

const fyFilterOptions = computed(() => [
  { value: 'ALL', label: 'All years' },
  ...fiscalYears.value.map(y => ({ value: String(y), label: String(y) })),
])

const filteredPeriods = computed(() => {
  if (selectedFY.value === 'ALL') return allPeriods.value
  return allPeriods.value.filter(p => yearOf(p) === Number(selectedFY.value))
})

// ── Data-driven default for "Generate fiscal year" (BUG-24) ───────────────────
// Default to (highest existing fiscal year) + 1 once periods have loaded; fall
// back to the current calendar year only when no periods exist yet. Recomputed
// reactively (not just once at setup) so it stays correct after load()/regeneration.
const suggestedFYYear = computed(() => {
  if (fiscalYears.value.length > 0) return Math.max(...fiscalYears.value) + 1
  return new Date().getFullYear()
})

watch(suggestedFYYear, (year) => {
  // Don't clobber a value the user is actively editing in the open modal.
  if (!showGenFY.value) genFYYear.value = year
}, { immediate: true })

function openGenFYModal() {
  genFYYear.value = suggestedFYYear.value
  showGenFY.value = true
}

// ── Period name display ───────────────────────────────────────────────────────
function displayName(p) {
  // API returns e.g. "JANUARY 2026" — prettify to "Jan 2026"
  if (p.periodName) {
    const parts = p.periodName.split(' ')
    if (parts.length === 2) {
      return parts[0].charAt(0) + parts[0].slice(1).toLowerCase() + ' ' + parts[1]
    }
    return p.periodName
  }
  // fallback for demo: derive from startDate
  if (p.startDate) {
    const d = new Date(p.startDate + 'T00:00:00')
    return d.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
  }
  return p.id
}

function periodCode(p) {
  if (p.startDate) return p.startDate.substring(0, 7)
  return p.id
}

// ── Transition ────────────────────────────────────────────────────────────────
async function doTransition(period, nextStatus) {
  transitioning.value = period.id
  try {
    await periodsApi.transition(period.id, nextStatus)
    toast.success(`Period transitioned to ${nextStatus}`)
    await load()
  } catch (e) {
    toast.error(e?.message || 'Transition failed')
  } finally {
    transitioning.value = null
  }
}

// ── Generate fiscal year ──────────────────────────────────────────────────────
async function generateFY() {
  genFYLoading.value = true
  try {
    await periodsApi.generateFiscalYear({ entityId: entityId.value, fiscalYear: Number(genFYYear.value) })
    toast.success(`FY ${genFYYear.value} generated — 12 periods created`)
    showGenFY.value = false
    await load()
  } catch (e) {
    toast.error(e?.message || 'Failed to generate fiscal year')
  } finally {
    genFYLoading.value = false
  }
}
</script>

<template>
  <div class="page">
    <PageHeader
      title="Accounting Periods"
      :meta="`${allPeriods.length} periods`"
    >
      <Button variant="primary" icon="plus" @click="openGenFYModal">Generate fiscal year</Button>
    </PageHeader>

    <div class="page-section stack">
      <!-- Active-period banner: always visible, independent of the fiscal-year filter below -->
      <div v-if="openPeriod" class="alert-banner alert-active">
        <span class="alert-icon">●</span>
        Active period: <strong>{{ displayName(openPeriod) }}</strong>
        ({{ fmtDate(openPeriod.startDate) }} – {{ fmtDate(openPeriod.endDate) }})
      </div>
      <div v-else-if="!loading" class="alert-banner alert-muted">
        No period is currently <strong>OPEN</strong>. Activate one from the periods below.
      </div>

      <!-- Adjusting period alert -->
      <div v-if="adjustingPeriod" class="alert-banner alert-warn">
        <span class="alert-icon">⚠</span>
        Period <strong>{{ periodCode(adjustingPeriod) }}</strong> is in <strong>ADJUSTING</strong> status.
        Complete all adjusting entries before closing.
      </div>

      <!-- Fiscal-year filter/switcher (BUG-34) — options derived from actual period data -->
      <div v-if="fiscalYears.length > 0" class="fy-filter-row">
        <span class="fy-filter-label">Fiscal year</span>
        <Segmented v-model="selectedFY" :options="fyFilterOptions" />
      </div>

      <!-- Loading skeleton -->
      <div v-if="loading" class="card" style="padding:24px;text-align:center;color:var(--muted)">
        Loading periods…
      </div>

      <div v-else class="card">
        <table class="tbl">
          <thead>
            <tr>
              <th>Period</th>
              <th>Name</th>
              <th>Range</th>
              <th>Status</th>
              <th>Closed by</th>
              <th>Closed at</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in filteredPeriods" :key="p.id">
              <td><span class="code-cell">{{ periodCode(p) }}</span></td>
              <td>{{ displayName(p) }}</td>
              <td class="muted">{{ fmtDate(p.startDate) }} – {{ fmtDate(p.endDate) }}</td>
              <td>
                <Badge :status="p.status" :dot="false" />
              </td>
              <td>
                <Badge v-if="p._closedBy" status="outline" :dot="false">{{ p._closedBy }}</Badge>
                <span v-else class="muted">—</span>
              </td>
              <td>{{ fmtDate(p._closedAt) }}</td>
              <td>
                <div class="action-row">
                  <template v-for="next in (STATUS_NEXT[p.status] || [])" :key="next">
                    <Button
                      :variant="STATUS_VARIANT[p.status] || 'ghost'"
                      size="sm"
                      :loading="transitioning === p.id"
                      @click="doTransition(p, next)"
                    >
                      {{ next === 'ADJUSTING' && p.status === 'REOPENED' ? 'To Adjusting' :
                         next === 'CLOSING'   && p.status === 'REOPENED' ? 'To Closing' :
                         next === 'CLOSED'    && p.status === 'REOPENED' ? 'Close' :
                         next === 'OPEN'      && p.status === 'ADJUSTING' ? '← Reopen' :
                         next === 'ADJUSTING' && p.status === 'CLOSING' ? '← Back' :
                         STATUS_LABEL[p.status] || next }}
                    </Button>
                  </template>
                </div>
              </td>
            </tr>
            <tr v-if="filteredPeriods.length === 0">
              <td colspan="7" class="text-center muted" style="padding: 24px;">
                No periods found{{ selectedFY !== 'ALL' ? ` for fiscal year ${selectedFY}` : '' }}.
              </td>
            </tr>
          </tbody>
        </table>
        <TableFooter :total="filteredPeriods.length" label="periods" />
      </div>
    </div>

    <!-- Generate fiscal year modal -->
    <Modal :open="showGenFY" title="Generate Fiscal Year" subtitle="Creates 12 accounting periods for the year" @close="showGenFY = false">
      <div class="form-grid cols-1">
        <div class="field">
          <label>Fiscal year</label>
          <input v-model.number="genFYYear" class="input" type="number" min="2020" max="2100" placeholder="e.g. 2027" />
        </div>
        <p class="field-hint">
          Generates 12 monthly periods (Jan–Dec). <strong>All 12 start as FUTURE</strong> — none is
          auto-opened, including January. After generating, use the <strong>Activate</strong> action
          on the period you want to start using. Returns an error if the year already exists.
        </p>
      </div>
      <template #footer>
        <Button variant="primary" icon="plus" :loading="genFYLoading" @click="generateFY">Generate</Button>
        <Button variant="ghost" @click="showGenFY = false">Cancel</Button>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.alert-banner {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.5;
}
.alert-warn {
  background: color-mix(in oklab, oklch(0.75 0.18 85) 12%, var(--surface));
  border: 1px solid color-mix(in oklab, oklch(0.75 0.18 85) 35%, transparent);
  color: var(--fg);
}
.alert-active {
  background: color-mix(in oklab, var(--pos) 12%, var(--surface));
  border: 1px solid color-mix(in oklab, var(--pos) 35%, transparent);
  color: var(--fg);
  align-items: center;
}
.alert-active .alert-icon { color: var(--pos); }
.alert-muted {
  background: var(--surface);
  border: 1px solid var(--border);
  color: var(--muted);
  align-items: center;
}
.alert-icon {
  font-size: 15px;
  flex-shrink: 0;
  margin-top: 1px;
}
.action-row {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}
.field-hint {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.5;
  margin: 0;
}
.fy-filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.fy-filter-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
</style>

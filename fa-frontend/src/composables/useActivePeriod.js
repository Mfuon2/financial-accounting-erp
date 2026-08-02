import { ref, computed } from 'vue'
import { periods as periodsApi } from '@/api/index.js'
import { useAuth } from '@/composables/useAuth.js'

// Only these statuses represent a period the entity is *currently* working in.
// FUTURE (not yet begun), REOPENED (a past, already-closed period reopened for a
// one-off correction), and CLOSED (finalized/historical) are deliberately excluded —
// none of them should be surfaced as "the active period" on the dashboard/reports.
const STATUS_PRIORITY = ['ADJUSTING', 'CLOSING', 'OPEN']

function priorityOf(status) {
  const i = STATUS_PRIORITY.indexOf(status)
  return i === -1 ? Infinity : i
}

// Module-level singleton — shared across all components
const _periods      = ref([])
const _loading      = ref(false)
const _loaded       = ref(false)
const _selectedFY   = ref(null) // null means "auto" (pick the most active period)

const _fiscalYears = computed(() => {
  const years = new Set()
  for (const p of _periods.value) {
    const y = (p.startDate ?? '').slice(0, 4)
    if (y) years.add(y)
  }
  return [...years].sort()
})

const _activePeriod = computed(() => {
  if (!_periods.value.length) return null
  const pool = _selectedFY.value
    ? _periods.value.filter(p => (p.startDate ?? '').startsWith(_selectedFY.value))
    : _periods.value
  const scoped = pool.length ? pool : _periods.value
  // Only OPEN/ADJUSTING/CLOSING periods qualify as "active" — a freshly generated
  // fiscal year (all FUTURE, per BUG-27's fix) or a year that's fully CLOSED/only
  // has a REOPENED correction in progress must yield null, not an arbitrary pick.
  const candidates = scoped.filter(p => STATUS_PRIORITY.includes(p.status))
  if (!candidates.length) return null
  const sorted = [...candidates].sort((a, b) => priorityOf(a.status) - priorityOf(b.status))
  const p = sorted[0]
  // Normalize a `code` field (YYYY-MM) from startDate for AppTopbar compatibility
  return { ...p, code: (p.startDate ?? '').slice(0, 7) }
})

export function useActivePeriod() {
  const { currentUser } = useAuth()

  async function load(force = false) {
    if (_loaded.value && !force) return
    const entityId = currentUser.value?.entityId
    if (!entityId) return
    _loading.value = true
    try {
      const data = await periodsApi.list({ entityId, size: 200 })
      _periods.value = data?.content ?? (Array.isArray(data) ? data : [])
      _loaded.value  = true
    } catch { } finally { _loading.value = false }
  }

  function setFiscalYear(year) {
    _selectedFY.value = year ?? null
  }

  return {
    activePeriod: _activePeriod,
    allPeriods:   _periods,
    fiscalYears:  _fiscalYears,
    selectedFY:   _selectedFY,
    loading:      _loading,
    load,
    setFiscalYear,
  }
}

import { ref, computed } from 'vue'
import { periods as periodsApi } from '@/api/index.js'
import { useAuth } from '@/composables/useAuth.js'

const STATUS_PRIORITY = ['ADJUSTING', 'CLOSING', 'OPEN', 'REOPENED', 'CLOSED']

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
  const candidates = pool.length ? pool : _periods.value
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

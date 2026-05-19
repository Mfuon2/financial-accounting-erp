import { ref, computed } from 'vue'
import { accounts as accountsApi } from '@/api/index.js'

// Module-level singleton — survives across component mounts
const _accounts         = ref([])
const _entityId         = ref(null)
const _loaded           = ref(false)
const _loading          = ref(false)
// Derived: only leaf accounts that can receive direct postings
const _postableAccounts = computed(() => _accounts.value.filter(a => !a.isHeader))

export function useAccountCache() {
  async function load(entityId) {
    if (_loaded.value && _entityId.value === entityId) return
    if (_loading.value) return
    _loading.value = true
    try {
      const data = await accountsApi.list({ entityId })
      const arr  = Array.isArray(data) ? data : (data?.content ?? [])
      _accounts.value  = arr
      _entityId.value  = entityId
      _loaded.value    = true
    } catch { /* keep stale data */ } finally {
      _loading.value = false
    }
  }

  // Call after creating/importing accounts so next consumer re-fetches
  function invalidate() {
    _loaded.value   = false
    _entityId.value = null
  }

  return {
    accounts:         _accounts,
    postableAccounts: _postableAccounts,
    loading:          _loading,
    load,
    invalidate,
  }
}

import { ref } from 'vue'
import { organization as orgApi } from '@/api/organization.js'

// Module-level singleton
const _org     = ref(null)
const _loading = ref(false)
const _loaded  = ref(false)

export function useOrganization() {
  async function load(force = false) {
    if (_loaded.value && !force) return
    _loading.value = true
    try {
      _org.value    = await orgApi.get()
      _loaded.value = true
    } catch { } finally { _loading.value = false }
  }

  return { org: _org, loading: _loading, load }
}

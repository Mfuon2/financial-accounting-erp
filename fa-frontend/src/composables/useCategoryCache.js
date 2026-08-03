import { ref, computed } from 'vue'
import { categories as categoriesApi } from '@/api/index.js'

// Module-level singleton — survives across component mounts, one cache per categoryType,
// mirroring the existing useAccountCache.js pattern. Keeps the 5 rewired views (Suppliers,
// Customers, Bills, Payments, Invoices) from each independently re-fetching the same handful
// of rows on every render.
const _byType = {}   // type -> { list: Ref<[]>, entityId: Ref, loaded: Ref, loading: Ref }

function bucket(type) {
  if (!_byType[type]) {
    _byType[type] = {
      list: ref([]),
      entityId: ref(null),
      loaded: ref(false),
      loading: ref(false),
    }
  }
  return _byType[type]
}

export function useCategoryCache(type) {
  const b = bucket(type)

  async function load(entityId) {
    if (b.loaded.value && b.entityId.value === entityId) return
    if (b.loading.value) return
    b.loading.value = true
    try {
      const data = await categoriesApi.list(entityId, type, true)
      const arr = Array.isArray(data) ? data : (data?.content ?? [])
      b.list.value = arr
      b.entityId.value = entityId
      b.loaded.value = true
    } catch { /* keep stale data */ } finally {
      b.loading.value = false
    }
  }

  // Call after creating/deactivating a category from the admin screen so the next consumer
  // (e.g. a dropdown on Bills.vue) re-fetches instead of showing stale options.
  function invalidate() {
    b.loaded.value = false
    b.entityId.value = null
  }

  const options = computed(() => b.list.value.map(c => ({ value: c.code, label: c.label })))

  return {
    list: b.list,
    options,
    loading: b.loading,
    load,
    invalidate,
  }
}

/** Invalidates every known category-type cache — used by the admin Categories.vue screen,
 *  which manages more than one type in the same view. */
export function invalidateAllCategoryCaches() {
  Object.values(_byType).forEach(b => { b.loaded.value = false; b.entityId.value = null })
}

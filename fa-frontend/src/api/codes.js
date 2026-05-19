import { isDemo } from '@/composables/useAppMode.js'
import { get } from './client.js'

// Known prefixes and their demo-mode format for local fallback
const YEAR_SCOPED = new Set(['INV', 'BILL', 'JE', 'SD', 'PAY', 'RCT', 'CN', 'DN', 'PO', 'QT'])

function demoCode(prefix) {
  const p = prefix.toUpperCase()
  const seq = String(Math.floor(Math.random() * 9) + 1).padStart(4, '0')
  return YEAR_SCOPED.has(p)
    ? `${p}-${new Date().getFullYear()}-${seq}`
    : `${p}${seq}`
}

export const codes = {
  /**
   * Peek at the next code (read-only, no sequence consumed). Use for form pre-fill.
   * Pass moduleKey (e.g. 'FIXED_ASSET') to apply the org's configured prefix and format.
   */
  next: (entityId, prefix, moduleKey = null) => {
    if (isDemo.value) return Promise.resolve({ prefix: prefix.toUpperCase(), code: demoCode(prefix) })
    const params = { entityId, prefix }
    if (moduleKey) params.moduleKey = moduleKey
    return get(`/api/v1/codes/next?${new URLSearchParams(params)}`)
  },

  /** Consume and return the next code. Use only when needed outside a create flow. */
  generate: (entityId, prefix) => isDemo.value
    ? Promise.resolve({ prefix: prefix.toUpperCase(), code: demoCode(prefix) })
    : get(`/api/v1/codes/generate?${new URLSearchParams({ entityId, prefix })}`),

  /** Peek next code for every registered prefix — useful for dashboard pre-population. */
  all: (entityId) => isDemo.value
    ? Promise.resolve({})
    : get(`/api/v1/codes/all?${new URLSearchParams({ entityId })}`),
}

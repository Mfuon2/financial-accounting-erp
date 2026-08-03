import { get, post, put } from './client.js'
import { isDemo } from '@/composables/useAppMode.js'

/**
 * Mirrors `CategoryType.defaultSeed()` on the backend (shared/categories module) — used only
 * as the demo-mode fallback so dropdowns (payment terms, payment methods, ...) keep working
 * with no backend attached. Production/real mode always reads the actual per-entity table via
 * GET /api/v1/categories.
 */
const DEFAULT_SEED = {
  PAYMENT_TERM: [
    { code: 'DUE_ON_RECEIPT', label: 'Due On Receipt' },
    { code: 'NET_15', label: 'Net 15' },
    { code: 'NET_30', label: 'Net 30' },
    { code: 'NET_45', label: 'Net 45' },
    { code: 'NET_60', label: 'Net 60' },
    { code: 'NET_90', label: 'Net 90' },
  ],
  PAYMENT_METHOD: [
    { code: 'BANK_TRANSFER', label: 'Bank Transfer' },
    { code: 'MPESA', label: 'M-Pesa' },
    { code: 'CASH', label: 'Cash' },
    { code: 'CHEQUE', label: 'Cheque' },
    { code: 'CREDIT_CARD', label: 'Credit Card' },
  ],
}

function demoList(type) {
  return (DEFAULT_SEED[type] ?? []).map((c, i) => ({
    id: `demo-${type}-${c.code}`,
    entityId: 'demo',
    categoryType: type,
    code: c.code,
    label: c.label,
    sortOrder: i,
    isActive: true,
  }))
}

export const categories = {
  /** List category values for a type. activeOnly defaults to true (dropdown consumers). */
  list: (entityId, type, activeOnly = true) => {
    if (isDemo.value) return Promise.resolve(demoList(type))
    return get(`/api/v1/categories?entityId=${entityId}&type=${type}&activeOnly=${activeOnly}`)
  },
  create: (entityId, categoryType, code, label, sortOrder = null) => {
    if (isDemo.value) {
      return Promise.resolve({
        id: `demo-${categoryType}-${Date.now()}`,
        entityId, categoryType, code: code.trim().toUpperCase().replace(/\s+/g, '_'),
        label, sortOrder: sortOrder ?? 0, isActive: true,
      })
    }
    return post('/api/v1/categories', { entityId, categoryType, code, label, sortOrder })
  },
  update: (id, { label, sortOrder } = {}) => {
    if (isDemo.value) return Promise.resolve({ id, label, sortOrder })
    return put(`/api/v1/categories/${id}`, { label, sortOrder })
  },
  deactivate: (id, reason) => {
    if (isDemo.value) return Promise.resolve({ id, isActive: false, deactivationReason: reason })
    return post(`/api/v1/categories/${id}/deactivate`, { reason })
  },
  activate: (id) => {
    if (isDemo.value) return Promise.resolve({ id, isActive: true })
    return post(`/api/v1/categories/${id}/activate`, {})
  },
}

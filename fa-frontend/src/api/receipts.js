import { RECEIPTS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, silentGet } from './client.js'

export const receipts = {
  // GET /api/v1/receipts?entityId=<uuid>&page=0&size=50
  list: (params) =>
    isDemo.value
      ? Promise.resolve({ content: RECEIPTS, totalElements: RECEIPTS.length })
      : get(`/api/v1/receipts?${new URLSearchParams(params)}`),

  // GET /api/v1/receipts/:id
  get: (id) =>
    isDemo.value
      ? Promise.resolve(RECEIPTS.find(r => r.id === id) ?? null)
      : get(`/api/v1/receipts/${id}`),

  // POST /api/v1/receipts/generate  — { paymentId, entityId, periodId?, deliveryEmail?, deliveryPhone?, notes? }
  generate: (body) =>
    isDemo.value
      ? Promise.resolve({ ...body, id: String(Date.now()), status: 'POSTED', receiptNumber: `RCT-DEMO-${Date.now()}` })
      : post('/api/v1/receipts/generate', body),

  // POST /api/v1/receipts/:id/issue
  issue: (id) =>
    isDemo.value
      ? Promise.resolve({ id, status: 'ISSUED' })
      : post(`/api/v1/receipts/${id}/issue`),

  // POST /api/v1/receipts/:id/void  — { reason }
  void: (id, reason) =>
    isDemo.value
      ? Promise.resolve({ id, status: 'VOID' })
      : post(`/api/v1/receipts/${id}/void`, { reason }),

  // GET /api/v1/receipts/by-payment/:paymentId — returns null (no toast) if not found
  byPayment: (paymentId) =>
    isDemo.value
      ? Promise.resolve(RECEIPTS.find(r => r.paymentId === paymentId) ?? null)
      : silentGet(`/api/v1/receipts/by-payment/${paymentId}`),
}

import { isDemo } from '@/composables/useAppMode.js'
import { post } from './client.js'

export const adjustments = {
  recordAccrual: (body) =>
    isDemo.value
      ? Promise.resolve({ id: String(Date.now()), status: 'DRAFT', sourceType: 'ACCRUAL' })
      : post('/api/v1/adjustments/accruals', body),

  recordDeferral: (body) =>
    isDemo.value
      ? Promise.resolve({ id: String(Date.now()), status: 'DRAFT', sourceType: 'DEFERRAL' })
      : post('/api/v1/adjustments/deferrals', body),

  amortizePrepayments: (entityId, periodId) =>
    isDemo.value
      ? Promise.resolve({})
      : post(`/api/v1/adjustments/prepayments/amortize?entityId=${entityId}&periodId=${periodId}`),

  recognizeUnearnedRevenue: (entityId, periodId) =>
    isDemo.value
      ? Promise.resolve({})
      : post(`/api/v1/adjustments/unearned/recognize?entityId=${entityId}&periodId=${periodId}`),
}

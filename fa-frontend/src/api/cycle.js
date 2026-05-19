import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

export const cycle = {
  validateStep: (entityId, periodId, targetStatus) =>
    isDemo.value
      ? Promise.resolve({})
      : get(`/api/v1/accounting-cycle/validate-step?entityId=${entityId}&periodId=${periodId}&targetStatus=${targetStatus}`),

  run: (body) =>
    isDemo.value
      ? Promise.resolve({ message: 'Demo: 9-step cycle complete' })
      : post('/api/v1/accounting-cycle/run', body),

  transition: (body) =>
    isDemo.value
      ? Promise.resolve({})
      : post('/api/v1/accounting-cycle/transition', body),
}

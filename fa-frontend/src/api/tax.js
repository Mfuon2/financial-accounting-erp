import { TAX_CODES } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const tax = {
  list:      (entityId)    => isDemo.value ? Promise.resolve(TAX_CODES)                                     : get(`/api/v1/tax/codes?${new URLSearchParams({ entityId })}`),
  get:       (id)          => isDemo.value ? Promise.resolve(TAX_CODES.find(t => t.id===id||t.code===id))   : get(`/api/v1/tax/codes/${id}`),
  create:    (body)        => isDemo.value ? Promise.resolve(body)                                           : post('/api/v1/tax/codes', body),
  update:    (id, body)    => isDemo.value ? Promise.resolve(body)                                           : put(`/api/v1/tax/codes/${id}`, body),
  calculate: (body)        => isDemo.value ? Promise.resolve({ taxAmount: 0, totalAmount: body.baseAmount }) : post('/api/v1/tax/calculate', body),
  listRates: (taxCodeId)   => isDemo.value ? Promise.resolve([])                                             : get(`/api/v1/tax/rates?${new URLSearchParams({ taxCodeId })}`),
  createRate:(body)        => isDemo.value ? Promise.resolve(body)                                           : post('/api/v1/tax/rates', body),
}

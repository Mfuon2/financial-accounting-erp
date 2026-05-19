import { CUSTOMERS, AR_AGEING } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const customers = {
  list:       (params)     => isDemo.value ? Promise.resolve({ content: CUSTOMERS, totalElements: CUSTOMERS.length }) : get(`/api/v1/customers?${new URLSearchParams(params)}`),
  get:        (id)         => isDemo.value ? Promise.resolve(CUSTOMERS.find(c => c.id === id))                        : get(`/api/v1/customers/${id}`),
  create:     (body)       => isDemo.value ? Promise.resolve({ ...body, id: String(Date.now()), isActive: true })     : post('/api/v1/customers', body),
  update:     (id, body)   => isDemo.value ? Promise.resolve({ ...body, id })                                         : put(`/api/v1/customers/${id}`, body),
  deactivate: (id, body)   => isDemo.value ? Promise.resolve({ id, isActive: false })                                 : post(`/api/v1/customers/${id}/deactivate`, body),
  // AR_AGEING is now the structured object matching backend ArAgeingResponse
  arAgeing:   (params)     => isDemo.value ? Promise.resolve(AR_AGEING)                                               : get(`/api/v1/customers/ar-ageing?${new URLSearchParams(params ?? {})}`),
}

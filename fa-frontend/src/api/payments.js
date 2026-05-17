import { PAYMENTS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

export const payments = {
  list:    ()      => isDemo.value ? Promise.resolve(PAYMENTS)                       : get('/api/v1/payments'),
  get:     (id)    => isDemo.value ? Promise.resolve(PAYMENTS.find(p => p.id===id)) : get(`/api/v1/payments/${id}`),
  create:  (body)  => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })   : post('/api/v1/payments', body),
  confirm: (id)    => isDemo.value ? Promise.resolve({ id, status: 'CONFIRMED' })   : post(`/api/v1/payments/${id}/confirm`),
  void:    (id)    => isDemo.value ? Promise.resolve({ id, status: 'VOID' })        : post(`/api/v1/payments/${id}/void`),
}

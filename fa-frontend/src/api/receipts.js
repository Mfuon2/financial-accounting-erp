import { RECEIPTS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

export const receipts = {
  list:   ()      => isDemo.value ? Promise.resolve(RECEIPTS)                       : get('/api/v1/receipts'),
  get:    (id)    => isDemo.value ? Promise.resolve(RECEIPTS.find(r => r.id===id)) : get(`/api/v1/receipts/${id}`),
  create: (body)  => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })   : post('/api/v1/receipts', body),
  allocate:(id, body) => isDemo.value ? Promise.resolve({ id })                    : post(`/api/v1/receipts/${id}/allocate`, body),
  void:   (id)    => isDemo.value ? Promise.resolve({ id, status: 'VOID' })        : post(`/api/v1/receipts/${id}/void`),
}

import { SUPPLIERS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const suppliers = {
  list:       (params)     => isDemo.value ? Promise.resolve({ content: SUPPLIERS, totalElements: SUPPLIERS.length }) : get(`/api/v1/suppliers?${new URLSearchParams(params)}`),
  get:        (id)         => isDemo.value ? Promise.resolve(SUPPLIERS.find(s => s.id === id))                        : get(`/api/v1/suppliers/${id}`),
  create:     (body)       => isDemo.value ? Promise.resolve({ ...body, id: String(Date.now()), isActive: true })     : post('/api/v1/suppliers', body),
  update:     (id, body)   => isDemo.value ? Promise.resolve({ ...body, id })                                         : put(`/api/v1/suppliers/${id}`, body),
  deactivate: (id, body)   => isDemo.value ? Promise.resolve({ id, isActive: false })                                 : post(`/api/v1/suppliers/${id}/deactivate`, body),
  statement:  (id)         => isDemo.value ? Promise.resolve([])                                                      : get(`/api/v1/suppliers/${id}/statement`),
}

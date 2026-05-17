import { ASSETS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const assets = {
  list:             (entityId)    => isDemo.value ? Promise.resolve(ASSETS)                            : get(`/api/v1/assets?entityId=${entityId}&size=500`),
  get:              (id)          => isDemo.value ? Promise.resolve(ASSETS.find(a => a.id === id))     : get(`/api/v1/assets/${id}`),
  create:           (body)        => isDemo.value ? Promise.resolve({ ...body, id: crypto.randomUUID() }) : post('/api/v1/assets', body),
  update:           (id, body)    => isDemo.value ? Promise.resolve({ ...body, id })                   : put(`/api/v1/assets/${id}`, body),
  dispose:          (id, body)    => isDemo.value ? Promise.resolve({ id })                            : post(`/api/v1/assets/${id}/dispose`, body),
  schedule:         (id, months)  => isDemo.value ? Promise.resolve([])                                : get(`/api/v1/assets/${id}/depreciation-schedule${months ? `?months=${months}` : ''}`),
  batchDepreciate:  (body)        => isDemo.value ? Promise.resolve({})                                : post('/api/v1/assets/batch-depreciate', body),
}

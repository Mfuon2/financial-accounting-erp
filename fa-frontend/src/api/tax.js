import { TAX_CODES } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const tax = {
  list:    ()         => isDemo.value ? Promise.resolve(TAX_CODES)                       : get('/api/v1/tax/codes'),
  get:     (code)     => isDemo.value ? Promise.resolve(TAX_CODES.find(t => t.code===code)) : get(`/api/v1/tax/codes/${code}`),
  create:  (body)     => isDemo.value ? Promise.resolve(body)                            : post('/api/v1/tax/codes', body),
  update:  (code, body)=> isDemo.value ? Promise.resolve(body)                           : put(`/api/v1/tax/codes/${code}`, body),
  toggle:  (code)     => isDemo.value ? Promise.resolve({})                             : post(`/api/v1/tax/codes/${code}/toggle`),
}

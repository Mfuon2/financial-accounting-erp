import { APPROVALS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

export const approvals = {
  list:    ()         => isDemo.value ? Promise.resolve(APPROVALS)                       : get('/api/v1/approvals'),
  get:     (id)       => isDemo.value ? Promise.resolve(APPROVALS.find(a => a.id===id))  : get(`/api/v1/approvals/${id}`),
  approve: (id, body) => isDemo.value ? Promise.resolve({ id, status: 'APPROVED' })      : post(`/api/v1/approvals/${id}/approve`, body),
  reject:  (id, body) => isDemo.value ? Promise.resolve({ id, status: 'REJECTED' })      : post(`/api/v1/approvals/${id}/reject`, body),
}

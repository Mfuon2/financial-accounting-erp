import { USERS, API_KEYS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const users = {
  list:          (params)        => isDemo.value ? Promise.resolve(USERS)                        : get(`/api/v1/users?${new URLSearchParams(params)}`),
  get:           (id)            => isDemo.value ? Promise.resolve(USERS.find(u => u.id===id))   : get(`/api/v1/users/${id}`),
  create:        (body)          => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })  : post('/api/v1/users', body),
  updateProfile: (id, fullName)  => isDemo.value ? Promise.resolve()                             : put(`/api/v1/users/${id}/profile`, { fullName }),
  updateRole:    (id, role)      => isDemo.value ? Promise.resolve()                             : put(`/api/v1/users/${id}/role`, { role }),
  deactivate:    (id, reason)    => isDemo.value ? Promise.resolve({ id, active: false })        : post(`/api/v1/users/${id}/deactivate`, { reason }),
  reactivate:    (id)            => isDemo.value ? Promise.resolve({ id, active: true })         : post(`/api/v1/users/${id}/reactivate`),
  resetPassword: (id)            => isDemo.value ? Promise.resolve()                             : post(`/api/v1/users/${id}/reset-password`),
  apiKeys:       ()              => isDemo.value ? Promise.resolve(API_KEYS)                     : get('/api/v1/api-keys'),
  createApiKey:  (body)          => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })  : post('/api/v1/api-keys', body),
  revokeApiKey:  (id)            => isDemo.value ? Promise.resolve()                             : post(`/api/v1/api-keys/${id}/revoke`),
}

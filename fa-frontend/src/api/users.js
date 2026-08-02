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
  apiKeys:       (params)        => isDemo.value ? Promise.resolve(API_KEYS)                    : get(`/api/v1/integration/keys?${new URLSearchParams(params)}`),
  createApiKey:  (body)          => isDemo.value ? Promise.resolve({ ...body, id: Date.now() }) : post('/api/v1/integration/keys', body),
  revokeApiKey:  (id, reason)    => isDemo.value ? Promise.resolve()                            : post(`/api/v1/integration/keys/${id}/revoke`, { reason }),
  rotateApiKey:  (id)            => isDemo.value ? Promise.resolve()                            : post(`/api/v1/integration/keys/${id}/rotate`),
}

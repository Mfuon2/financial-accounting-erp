import { SESSIONS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, del } from './client.js'

export const auth = {
  login:           (body)    => isDemo.value ? Promise.resolve({ token: 'demo-token', user: {} })  : post('/api/v1/auth/login', body),
  logout:          ()        => isDemo.value ? Promise.resolve()                                    : post('/api/v1/auth/logout'),
  refresh:         (body)    => isDemo.value ? Promise.resolve({ token: 'demo-token' })             : post('/api/v1/auth/refresh', body),
  sessions:        ()        => isDemo.value ? Promise.resolve(SESSIONS)                            : get('/api/v1/auth/sessions'),
  revokeSession:   (id)      => isDemo.value ? Promise.resolve()                                    : del(`/api/v1/auth/sessions/${id}`),
  revokeAllOthers: ()        => isDemo.value ? Promise.resolve()                                    : post('/api/v1/auth/sessions/revoke-all-others', {}),
  changePassword:  (body)    => isDemo.value ? Promise.resolve()  : post('/api/v1/auth/change-password', body),
  resetPassword:   (body)    => isDemo.value ? Promise.resolve()  : post('/api/v1/auth/reset-password', body),
  forgotPassword:  (body)    => isDemo.value ? Promise.resolve()  : post('/api/v1/auth/forgot-password', body),
  verifyEmail:     (userId)  => isDemo.value ? Promise.resolve()  : post(`/api/v1/auth/verify-email/${userId}`),
}

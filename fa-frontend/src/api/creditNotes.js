import { CREDIT_NOTES } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

export const creditNotes = {
  list:   ()      => isDemo.value ? Promise.resolve(CREDIT_NOTES)                    : get('/api/v1/credit-notes'),
  get:    (id)    => isDemo.value ? Promise.resolve(CREDIT_NOTES.find(c => c.id===id)) : get(`/api/v1/credit-notes/${id}`),
  create: (body)  => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })     : post('/api/v1/credit-notes', body),
  apply:  (id)    => isDemo.value ? Promise.resolve({ id, status: 'APPLIED' })       : post(`/api/v1/credit-notes/${id}/apply`),
  void:   (id)    => isDemo.value ? Promise.resolve({ id, status: 'VOID' })          : post(`/api/v1/credit-notes/${id}/void`),
}

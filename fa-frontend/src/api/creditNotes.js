import { CREDIT_NOTES } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

export const creditNotes = {
  // GET /api/v1/credit-notes?entityId=&[page=&size=] — standalone credit-notes listing
  // (Invoice rows with status=CREDIT_NOTE), backed by InvoiceService#findCreditNotesByEntity.
  list:   (params) => isDemo.value ? Promise.resolve({ content: CREDIT_NOTES, totalElements: CREDIT_NOTES.length }) : get(`/api/v1/credit-notes?${new URLSearchParams(params ?? {})}`),
  get:    (id)    => isDemo.value ? Promise.resolve(CREDIT_NOTES.find(c => c.id===id)) : get(`/api/v1/credit-notes/${id}`),
  create: (body)  => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })     : post('/api/v1/credit-notes', body),
  apply:  (id)    => isDemo.value ? Promise.resolve({ id, status: 'APPLIED' })       : post(`/api/v1/credit-notes/${id}/apply`),
  void:   (id)    => isDemo.value ? Promise.resolve({ id, status: 'VOID' })          : post(`/api/v1/credit-notes/${id}/void`),
}

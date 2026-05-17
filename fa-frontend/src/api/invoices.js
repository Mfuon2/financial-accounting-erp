import { INVOICES, CREDIT_NOTES } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const invoices = {
  list:        (params)    => isDemo.value ? Promise.resolve(INVOICES)                       : get(`/api/v1/invoices?${new URLSearchParams(params ?? {})}`),
  creditNotes: ()          => isDemo.value ? Promise.resolve(CREDIT_NOTES)                  : get('/api/v1/invoices?status=CREDIT_NOTE'),
  get:         (id)        => isDemo.value ? Promise.resolve(INVOICES.find(i => i.id===id)) : get(`/api/v1/invoices/${id}`),
  create:      (body)      => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })   : post('/api/v1/invoices', body),
  update:      (id, body)  => isDemo.value ? Promise.resolve({ ...body, id })               : put(`/api/v1/invoices/${id}`, body),
  approve:     (id)        => isDemo.value ? Promise.resolve({ id, status: 'APPROVED' })    : post(`/api/v1/invoices/${id}/approve`),
  void:        (id)        => isDemo.value ? Promise.resolve({ id, status: 'VOID' })        : post(`/api/v1/invoices/${id}/void`),
  createCreditNote: (id, body) => isDemo.value ? Promise.resolve({ id })                    : post(`/api/v1/invoices/${id}/credit-note`, body),
  applyPayment:(id, body)  => isDemo.value ? Promise.resolve({ id })                        : post(`/api/v1/invoices/${id}/payment`, body),
  arAgeing:    ()          => isDemo.value ? Promise.resolve([])                            : get('/api/v1/invoices/ar-ageing'),
}

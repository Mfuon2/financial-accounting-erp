import { BILLS, AP_AGEING } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const bills = {
  list:          (params)  => isDemo.value ? Promise.resolve({ content: BILLS, totalElements: BILLS.length }) : get(`/api/v1/bills?${new URLSearchParams(params)}`),
  get:           (id)      => isDemo.value ? Promise.resolve(BILLS.find(b => b.id === id))                   : get(`/api/v1/bills/${id}`),
  create:        (body)    => isDemo.value ? Promise.resolve({ bill: { ...body, id: String(Date.now()), status: 'DRAFT', billNumber: `BILL-${new Date().getFullYear()}-00099` }, warnings: [] }) : post('/api/v1/bills', body),
  update:        (id, body)=> isDemo.value ? Promise.resolve({ ...BILLS.find(b => b.id === id), ...body })   : put(`/api/v1/bills/${id}`, body),
  approve:       (id)      => isDemo.value ? Promise.resolve({ id, status: 'APPROVED' })                     : post(`/api/v1/bills/${id}/approve`),
  void:          (id, r)   => isDemo.value ? Promise.resolve({ id, status: 'VOID' })                        : post(`/api/v1/bills/${id}/void${r ? `?reason=${encodeURIComponent(r)}` : ''}`),
  debitNote:     (id, body)=> isDemo.value ? Promise.resolve({ id: String(Date.now()), originalBillId: id, isDebitNote: true, status: 'DEBIT_NOTE', ...body }) : post(`/api/v1/bills/${id}/debit-note`, body),
  payments:      (id)      => isDemo.value ? Promise.resolve([])                                             : get(`/api/v1/bills/${id}/payments`),
  recordPayment: (id, body)=> isDemo.value ? Promise.resolve({ id: String(Date.now()), billId: id, ...body }) : post(`/api/v1/bills/${id}/payments`, body),
  paymentRun:    (body)    => isDemo.value ? Promise.resolve({ id: String(Date.now()), ...body, billCount: body.billIds?.length ?? 0 }) : post('/api/v1/bills/payment-run', body),
  paymentRuns:   (params)  => isDemo.value ? Promise.resolve([])                                             : get(`/api/v1/bills/payment-runs?${new URLSearchParams(params)}`),
  ageing:        (params)  => isDemo.value ? Promise.resolve(AP_AGEING)                                     : get(`/api/v1/bills/ageing?${new URLSearchParams(params)}`),
}

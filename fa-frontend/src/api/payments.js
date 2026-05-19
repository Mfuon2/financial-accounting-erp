import { PAYMENTS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, apiFetch } from './client.js'

// Helper: POST with extra headers (used for Idempotency-Key on create)
function postH(path, body, headers = {}) {
  return apiFetch(path, {
    method: 'POST',
    body: JSON.stringify(body),
    headers,
  })
}

// Demo helpers — normalise PAYMENTS demo data to match PaymentResponse shape
function demoList() {
  return {
    content: PAYMENTS.map(_demoNorm),
    totalElements: PAYMENTS.length,
    totalPages: 1,
    number: 0,
    size: PAYMENTS.length,
  }
}
function demoGet(id) {
  const p = PAYMENTS.find(p => p.id === id)
  return p ? _demoNorm(p) : null
}
function _demoNorm(p) {
  // Map demo shape → PaymentResponse shape
  return {
    id:                   p.id,
    entityId:             'demo',
    periodId:             null,
    paymentNumber:        p.ref ?? p.id,
    invoiceId:            p.invoice ?? null,
    customerId:           p.customer ?? null,
    paymentMethod:        p.method ?? 'BANK_TRANSFER',
    paymentAmount:        p.amount ?? 0,
    currencyCode:         p.currency ?? 'KES',
    exchangeRate:         1,
    functionalAmount:     p.amount ?? 0,
    status:               p.status,
    transactionReference: null,
    journalEntryId:       null,
    mpesaResultCode:      null,
    mpesaReceiptNumber:   null,
    paymentDate:          p.date ?? null,
    notes:                null,
    version:              0,
    // keep legacy demo fields for display
    customer:             p.customer,
    matched:              p.matched,
  }
}

export const payments = {
  /**
   * List payments for an entity (paginated).
   * params: { entityId, status?, customerId?, page?, size? }
   */
  list: (params = {}) =>
    isDemo.value
      ? Promise.resolve(demoList())
      : get(`/api/v1/payments?${new URLSearchParams(params)}`),

  /** Get a single payment by UUID. */
  get: (id) =>
    isDemo.value
      ? Promise.resolve(demoGet(id))
      : get(`/api/v1/payments/${id}`),

  /**
   * Create a new payment (PENDING status).
   * Requires Idempotency-Key header — pass a client-generated UUID.
   * body: CreatePaymentCommand
   */
  create: (body, idempotencyKey) =>
    isDemo.value
      ? Promise.resolve({
          ..._demoNorm({ ...body, id: String(Date.now()), ref: `PAY-${Date.now()}`, status: 'PENDING', matched: 0 }),
        })
      : postH('/api/v1/payments', body, { 'Idempotency-Key': idempotencyKey }),

  /**
   * Match a PENDING payment to an invoice.
   * body: { invoiceId: UUID, matchedAmount: number }
   */
  match: (id, body) =>
    isDemo.value
      ? Promise.resolve({ id, status: 'MATCHED', ...body })
      : post(`/api/v1/payments/${id}/match`, body),

  /**
   * Approve a MATCHED payment.
   */
  approve: (id) =>
    isDemo.value
      ? Promise.resolve({ id, status: 'APPROVED' })
      : post(`/api/v1/payments/${id}/approve`),

  /**
   * Post an APPROVED payment to the general ledger (DR Cash / CR AR).
   */
  post: (id) =>
    isDemo.value
      ? Promise.resolve({ id, status: 'POSTED' })
      : post(`/api/v1/payments/${id}/post`),

  /**
   * Reverse a POSTED payment.
   * body: { reason: string }
   */
  reverse: (id, body) =>
    isDemo.value
      ? Promise.resolve({ id, status: 'REVERSED', ...body })
      : post(`/api/v1/payments/${id}/reverse`, body),
}

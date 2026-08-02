import { INVOICES, AR_AGEING } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

// Demo helper: normalise static fixture into API-shape
function demoInvoices() {
  return INVOICES.map(i => ({
    id: i.id,
    entityId: 'demo',
    periodId: null,
    invoiceNumber: i.ref,
    customerId: i.customer,
    customerName: i.customerName,
    issueDate: i.date,
    dueDate: i.due,
    currencyCode: i.currency,
    exchangeRate: 1,
    subtotal: i.subtotal,
    taxAmount: i.tax,
    discountAmount: 0,
    totalAmount: i.total,
    paidAmount: i.paid ?? 0,
    outstandingAmount: i.balance,
    status: i.status === 'POSTED' ? 'SENT' : i.status,  // map demo "POSTED" → backend "SENT"
    notes: null,
    journalEntryId: null,
    lines: (i.lines ?? []).map((l, idx) => ({
      id: `${i.id}-L${idx + 1}`,
      lineNumber: idx + 1,
      accountId: null,
      description: l.desc,
      quantity: l.qty,
      unitPrice: l.unit,
      lineSubtotal: l.qty * l.unit,
      lineTax: l.qty * l.unit * 0.16,
      lineTotal: l.qty * l.unit * 1.16,
      recognitionType: i.recognition ?? 'POINT_IN_TIME',
    })),
  }))
}

function demoArAgeing() {
  // AR_AGEING is now a structured object matching backend ArAgeingResponse shape
  return {
    entityId:         'demo',
    asOfDate:         AR_AGEING.asOfDate,
    current:          { invoiceCount: AR_AGEING.current.invoiceCount,          totalAmount: AR_AGEING.current.totalAmount,          invoices: AR_AGEING.current.invoices },
    thirtyOneToSixty: { invoiceCount: AR_AGEING.thirtyOneToSixty.invoiceCount, totalAmount: AR_AGEING.thirtyOneToSixty.totalAmount, invoices: AR_AGEING.thirtyOneToSixty.invoices },
    sixtyOneToNinety: { invoiceCount: AR_AGEING.sixtyOneToNinety.invoiceCount, totalAmount: AR_AGEING.sixtyOneToNinety.totalAmount, invoices: AR_AGEING.sixtyOneToNinety.invoices },
    ninetyPlus:       { invoiceCount: AR_AGEING.ninetyPlus.invoiceCount,       totalAmount: AR_AGEING.ninetyPlus.totalAmount,       invoices: AR_AGEING.ninetyPlus.invoices },
    totalOutstanding: AR_AGEING.totalOutstanding,
  }
}

export const invoices = {
  // GET /api/v1/invoices?entityId=&[customerId=&status=&fromDate=&toDate=&page=&size=]
  list: (params) =>
    isDemo.value
      ? Promise.resolve({ content: demoInvoices(), totalElements: INVOICES.length })
      : get(`/api/v1/invoices?${new URLSearchParams(params ?? {})}`),

  // GET /api/v1/invoices/{id}
  get: (id) =>
    isDemo.value
      ? Promise.resolve(demoInvoices().find(i => i.id === id))
      : get(`/api/v1/invoices/${id}`),

  // POST /api/v1/invoices  body: CreateInvoiceCommand
  create: (body) =>
    isDemo.value
      ? Promise.resolve({ ...demoInvoices()[0], ...body, id: String(Date.now()), invoiceNumber: `INV-${new Date().getFullYear()}-DEMO`, status: 'DRAFT' })
      : post('/api/v1/invoices', body),

  // POST /api/v1/invoices/{id}/approve
  approve: (id) =>
    isDemo.value
      ? Promise.resolve({ ...demoInvoices().find(i => i.id === id), status: 'APPROVED' })
      : post(`/api/v1/invoices/${id}/approve`),

  // POST /api/v1/invoices/{id}/void  body: VoidInvoiceCommand { reason }
  void: (id, reason) =>
    isDemo.value
      ? Promise.resolve({ ...demoInvoices().find(i => i.id === id), status: 'VOID' })
      : post(`/api/v1/invoices/${id}/void`, { reason }),

  // POST /api/v1/invoices/{id}/credit-note  body: CreateCreditNoteCommand { creditNoteAmount, reason }
  createCreditNote: (id, body) =>
    isDemo.value
      ? Promise.resolve({ ...demoInvoices().find(i => i.id === id), id: `CN-${Date.now()}`, status: 'CREDIT_NOTE', ...body })
      : post(`/api/v1/invoices/${id}/credit-note`, body),

  // POST /api/v1/invoices/{id}/payment  body: ApplyInvoicePaymentCommand { paymentAmount }
  applyPayment: (id, paymentAmount) =>
    isDemo.value
      ? Promise.resolve('PARTIALLY_PAID')
      : post(`/api/v1/invoices/${id}/payment`, { paymentAmount }),

  // GET /api/v1/invoices/ar-ageing?entityId=&asOfDate=
  arAgeing: (params) =>
    isDemo.value
      ? Promise.resolve(demoArAgeing())
      : get(`/api/v1/invoices/ar-ageing?${new URLSearchParams(params ?? {})}`),
}

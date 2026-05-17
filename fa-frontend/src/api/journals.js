import { JOURNALS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

function demoJournals() {
  return JOURNALS.map(j => ({
    id: j.id,
    entityId: 'demo',
    periodId: 'P-2026-02',
    transDate: j.date,
    description: j.description,
    status: j.status,
    sourceType: j.source,
    sourceId: null,
    createdAt: j.date + 'T00:00:00Z',
    createdBy: j.submittedBy || null,
    modifiedAt: j.date + 'T00:00:00Z',
    isActive: true,
    lines: (j.lines || []).map((l, i) => ({
      id: `${j.id}-L${i}`,
      accountId: l.account,
      description: l.memo,
      debitAmount: Number(l.debit || 0),
      creditAmount: Number(l.credit || 0),
      currencyCode: 'KES',
      exchangeRate: 1,
      functionalDebit: Number(l.debit || 0),
      functionalCredit: Number(l.credit || 0),
      taxCode: null,
      taxAmount: null,
      _accountCode: l.account,
      _accountName: l.name,
    })),
    _ref: j.ref,
    _postedAt: j.postedAt,
    _postedBy: j.postedBy,
    _submittedBy: j.submittedBy,
  }))
}

export const journals = {
  list:       (params)     => isDemo.value ? Promise.resolve(demoJournals())                                       : get(`/api/v1/journal-entries?${new URLSearchParams(params)}`),
  get:        (id)         => isDemo.value ? Promise.resolve(demoJournals().find(j => j.id === id))               : get(`/api/v1/journal-entries/${id}`),
  create:     (body)       => isDemo.value ? Promise.resolve({ ...body, id: String(Date.now()), status: 'DRAFT' }) : post('/api/v1/journal-entries', body),
  update:     (id, body)   => isDemo.value ? Promise.resolve({ id, ...body })                                     : put(`/api/v1/journal-entries/${id}`, body),
  submit:     (id)         => isDemo.value ? Promise.resolve()                                                    : post(`/api/v1/journal-entries/${id}/submit`),
  approve:    (id)         => isDemo.value ? Promise.resolve()                                                    : post(`/api/v1/journal-entries/${id}/approve`),
  reject:     (id, reason) => isDemo.value ? Promise.resolve()                                                   : post(`/api/v1/journal-entries/${id}/reject?reason=${encodeURIComponent(reason)}`),
  reverse:    (id)         => isDemo.value ? Promise.resolve()                                                    : post(`/api/v1/journal-entries/${id}/reverse`),
  auditTrail: (id)         => isDemo.value ? Promise.resolve([])                                                  : get(`/api/v1/journal-entries/${id}/audit-trail`),
}

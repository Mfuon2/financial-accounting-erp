import { COA } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put, uploadFile } from './client.js'

// Map demo COA shape → API Account shape
function demoAccounts() {
  return COA.map(a => ({
    id: a.code,
    accountCode: a.code,
    accountName: a.name,
    accountType: a.class,
    accountSubtype: a.subtype ?? 'OPERATING_EXPENSES',
    normalBalance: a.normal === 'DR' ? 'DEBIT' : 'CREDIT',
    ifrsCategory: a.ifrs ?? 'OPERATING_EXPENSES',
    parentAccountId: a.parent ?? null,
    isActive: true,
    isTemporary: a.class === 'REVENUE' || a.class === 'EXPENSE',
  }))
}

export const accounts = {
  list:         (params)    => isDemo.value ? Promise.resolve(demoAccounts())            : get(`/api/v1/coa/accounts?${new URLSearchParams(params)}`),
  get:          (id)        => isDemo.value ? Promise.resolve(demoAccounts().find(a => a.id === id)) : get(`/api/v1/coa/accounts/${id}`),
  create:       (body)      => isDemo.value ? Promise.resolve({ ...body, id: String(Date.now()) })   : post('/api/v1/coa/accounts', body),
  update:       (id, body)  => isDemo.value ? Promise.resolve({ id, ...body })           : put(`/api/v1/coa/accounts/${id}`, body),
  deactivate:   (id)        => isDemo.value ? Promise.resolve()                          : post(`/api/v1/coa/accounts/${id}/deactivate`),
  validateCode: (params)    => isDemo.value ? Promise.resolve(true)                      : get(`/api/v1/coa/accounts/validate-code?${new URLSearchParams(params)}`),
  importJson:   (entityId, rows) => isDemo.value ? Promise.resolve()                    : post(`/api/v1/coa/import?entityId=${entityId}`, rows),
  hierarchy:    (id)        => isDemo.value ? Promise.resolve([])                        : get(`/api/v1/coa/accounts/${id}/hierarchy`),
}

import { COA } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put, uploadFile } from './client.js'

// Per-class fallback subtype for the rare HEADER row with no explicit `subtype` in the fixture —
// headers are always excluded from subtype-sensitive pickers via isHeader anyway, so this only
// needs to be "a valid enum value of the right class", not individually correct per header.
const FALLBACK_SUBTYPE_BY_CLASS = {
  ASSET: 'NON_CURRENT_OTHER',
  LIABILITY: 'CURRENT_ACCRUED',
  EQUITY: 'OTHER_COMPREHENSIVE_INCOME',
  REVENUE: 'OTHER_INCOME',
  EXPENSE: 'OPERATING_EXPENSES',
}

// Map demo COA shape → API Account shape
function demoAccounts() {
  return COA.map(a => ({
    id: a.code,
    accountCode: a.code,
    accountName: a.name,
    accountType: a.class,
    // Every real (POST) row in the fixture carries an explicit `subtype` matching the backend's
    // AccountSubtype enum (see data/index.js's COA comment) — found and fixed alongside the Cash
    // & Bank Management module, which needs to filter demo accounts down to CASH_AND_EQUIVALENTS
    // the same way BankStatementService.validateBankAccount does in production. Before this fix,
    // every demo account silently fell back to 'OPERATING_EXPENSES' regardless of what it really
    // was — the same class of demo-mode-only gap as the isHeader fix from the Budgeting module.
    accountSubtype: a.subtype ?? FALLBACK_SUBTYPE_BY_CLASS[a.class] ?? 'OPERATING_EXPENSES',
    normalBalance: a.normal === 'DR' ? 'DEBIT' : 'CREDIT',
    ifrsCategory: a.ifrs ?? 'OPERATING_EXPENSES',
    parentAccountId: a.parent ?? null,
    isActive: true,
    isHeader: a.type === 'HEADER',
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

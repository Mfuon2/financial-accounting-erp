import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

export const ledger = {
  // GET /api/v1/ledger/accounts/{accountId}/entries?startDate=&endDate=&page=&size=
  accountEntries: (accountId, params = {}) =>
    isDemo.value
      ? Promise.resolve({ content: [], totalElements: 0 })
      : get(`/api/v1/ledger/accounts/${accountId}/entries?${new URLSearchParams(params)}`),

  // GET /api/v1/ledger/accounts/{accountId}/t-account?startDate=&endDate=
  tAccount: (accountId, params = {}) =>
    isDemo.value
      ? Promise.resolve({ debitLines: [], creditLines: [], openingBalance: 0, closingBalance: 0 })
      : get(`/api/v1/ledger/accounts/${accountId}/t-account?${new URLSearchParams(params)}`),

  // GET /api/v1/ledger/entries/{id}
  entry: (id) =>
    isDemo.value
      ? Promise.resolve(null)
      : get(`/api/v1/ledger/entries/${id}`),

  // GET /api/v1/ledger/subsidiary/customers/{customerId}
  customerSubsidiary: (customerId, params = {}) =>
    isDemo.value
      ? Promise.resolve({ entries: [], openingBalance: 0, closingBalance: 0 })
      : get(`/api/v1/ledger/subsidiary/customers/${customerId}?${new URLSearchParams(params)}`),

  // GET /api/v1/ledger/subsidiary/suppliers/{supplierId}
  supplierSubsidiary: (supplierId, params = {}) =>
    isDemo.value
      ? Promise.resolve({ entries: [], openingBalance: 0, closingBalance: 0 })
      : get(`/api/v1/ledger/subsidiary/suppliers/${supplierId}?${new URLSearchParams(params)}`),

  // GET /api/v1/ledger/assets/{assetId}/depreciation-schedule
  assetDepreciationSchedule: (assetId) =>
    isDemo.value
      ? Promise.resolve([])
      : get(`/api/v1/ledger/assets/${assetId}/depreciation-schedule`),

  // POST /api/v1/ledger/assets/{assetId}/depreciate — { entityId, periodId }
  depreciateAsset: (assetId, body) =>
    isDemo.value
      ? Promise.resolve({ message: 'Demo: depreciation posted' })
      : post(`/api/v1/ledger/assets/${assetId}/depreciate`, body),
}

import { CURRENCIES, FX_RATES } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put, silentGet } from './client.js'

export const fx = {
  currencies:     (entityId)    => isDemo.value ? Promise.resolve(CURRENCIES) : get(`/api/v1/fx/currencies${entityId ? '?entityId=' + entityId : ''}`),
  getCurrency:    (code)        => isDemo.value ? Promise.resolve(CURRENCIES.find(c => c.code === code)) : get(`/api/v1/fx/currencies/${code}`),
  createCurrency: (body)        => isDemo.value ? Promise.resolve(body) : post('/api/v1/fx/currencies', body),
  updateCurrency: (id, body)    => isDemo.value ? Promise.resolve(body) : put(`/api/v1/fx/currencies/${id}`, body),

  // GET /api/v1/fx/exchange-rates?entityId=&fromCurrency=&toCurrency=&date=&rateType=
  // Returns null (no toast) when no rate exists for that date — use silentGet
  getRate: (params) =>
    isDemo.value
      ? Promise.resolve(FX_RATES.find(r => r.from === params.fromCurrency && r.to === params.toCurrency)?.rate ?? null)
      : silentGet(`/api/v1/fx/exchange-rates?${new URLSearchParams(params)}`),

  listRates:  (entityId) => isDemo.value ? Promise.resolve(FX_RATES) : get(`/api/v1/fx/exchange-rates/all?entityId=${entityId}`),
  createRate: (body)     => isDemo.value ? Promise.resolve({ ...body, id: Date.now() }) : post('/api/v1/fx/exchange-rates', body),
  updateRate: (id, body) => isDemo.value ? Promise.resolve({ ...body, id })             : put(`/api/v1/fx/exchange-rates/${id}`, body),
  revalue:    (body)     => isDemo.value ? Promise.resolve({})                          : post('/api/v1/fx/revaluation', body),
  previewRevaluation: (entityId, date) =>
    isDemo.value
      ? Promise.resolve(null)
      : get(`/api/v1/fx/revaluation/preview?${new URLSearchParams({ entityId, date })}`),
}

import { CURRENCIES, FX_RATES } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

export const fx = {
  currencies:       ()          => isDemo.value ? Promise.resolve(CURRENCIES)                      : get('/api/v1/fx/currencies'),
  getCurrency:      (code)      => isDemo.value ? Promise.resolve(CURRENCIES.find(c => c.code===code)) : get(`/api/v1/fx/currencies/${code}`),
  createCurrency:   (body)      => isDemo.value ? Promise.resolve(body)                            : post('/api/v1/fx/currencies', body),
  updateCurrency:   (code, body)=> isDemo.value ? Promise.resolve(body)                            : put(`/api/v1/fx/currencies/${code}`, body),
  rates:            ()          => isDemo.value ? Promise.resolve(FX_RATES)                        : get('/api/v1/fx/exchange-rates'),
  createRate:       (body)      => isDemo.value ? Promise.resolve({ ...body, id: Date.now() })     : post('/api/v1/fx/exchange-rates', body),
  updateRate:       (id, body)  => isDemo.value ? Promise.resolve({ ...body, id })                 : put(`/api/v1/fx/exchange-rates/${id}`, body),
  revalue:          (body)      => isDemo.value ? Promise.resolve({})                              : post('/api/v1/fx/revalue', body),
}

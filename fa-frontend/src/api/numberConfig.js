import { get, put } from './client.js'
import { isDemo } from '@/composables/useAppMode.js'
import { NumberingModule } from '@/data/numberingModules.js'

export const numberConfig = {
  getAll: (entityId) => {
    if (isDemo.value) return Promise.resolve(NumberingModule.defaults())
    return get(`/api/v1/number-config?entityId=${entityId}`)
  },
  update: (entityId, moduleKey, prefix, customFormat = null) => {
    if (isDemo.value) return Promise.resolve({ moduleKey, prefix, customFormat })
    return put(`/api/v1/number-config/${moduleKey}?entityId=${entityId}`, { prefix, customFormat })
  },
}

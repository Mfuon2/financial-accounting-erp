import { AUDIT } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get } from './client.js'

export const audit = {
  list:   (params)  => isDemo.value ? Promise.resolve(AUDIT)                       : get(`/api/v1/audit-logs?${new URLSearchParams(params ?? {})}`),
  get:    (id)      => isDemo.value ? Promise.resolve(AUDIT.find(e => e.id===id)) : get(`/api/v1/audit-logs/${id}`),
  export: (params)  => isDemo.value ? Promise.resolve(null)                       : get(`/api/v1/audit-logs/export?${new URLSearchParams(params ?? {})}`),
}

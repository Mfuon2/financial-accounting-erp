import { ORG } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, put } from './client.js'

export const organization = {
  get:    ()      => isDemo.value ? Promise.resolve(ORG)    : get('/api/v1/organizations/me'),
  update: (body)  => isDemo.value ? Promise.resolve(body)   : put('/api/v1/organizations/me', body),
  apiKeys: ()     => isDemo.value ? Promise.resolve([])     : get('/api/v1/organizations/me/api-keys'),
}

import { IAS1_CHECKS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get } from './client.js'

export const compliance = {
  ias1Checks: () => isDemo.value ? Promise.resolve(IAS1_CHECKS) : get('/api/v1/compliance/ias1'),
}

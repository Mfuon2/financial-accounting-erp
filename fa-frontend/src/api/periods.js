import { PERIODS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

function demoPeriods() {
  return PERIODS.map(p => ({
    id: p.id,
    entityId: 'demo',
    periodId: null,
    periodName: `${p.month.toUpperCase()} ${p.year}`,
    startDate: p.start,
    endDate: p.end,
    status: p.status,
    isActive: true,
    createdAt: null,
    _closedBy: p.closedBy,
    _closedAt: p.closedAt,
  }))
}

export const periods = {
  list:              (params)          => isDemo.value ? Promise.resolve(demoPeriods())                         : get(`/api/v1/periods?${new URLSearchParams(params)}`),
  get:               (id)             => isDemo.value ? Promise.resolve(demoPeriods().find(p => p.id === id)) : get(`/api/v1/periods/${id}`),
  generateFiscalYear:(body)           => isDemo.value ? Promise.resolve('Fiscal year generated')              : post('/api/v1/periods/generate-fiscal-year', body),
  transition:        (id, nextStatus) => isDemo.value ? Promise.resolve({ id, status: nextStatus })          : post(`/api/v1/periods/${id}/transition?nextStatus=${nextStatus}`),
}

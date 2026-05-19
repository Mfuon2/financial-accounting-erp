import { SPARK_REV, SPARK_EXP, SPARK_AR, SPARK_CASH, APPROVALS, AUDIT, AR_AGEING } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get } from './client.js'

function last12MonthLabels() {
  const labels = []
  const now = new Date()
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    labels.push(d.toLocaleString('en', { month: 'short' }).toUpperCase().slice(0, 3))
  }
  return labels
}

const DEMO_SUMMARY = {
  cashAndEquivalents: 2713620,
  accountsReceivable: AR_AGEING.totalOutstanding,
  mtdRevenue:  2170200,
  mtdExpenses:  946800,
  sparkRev:    SPARK_REV,
  sparkExp:    SPARK_EXP,
  sparkAr:     SPARK_AR,
  sparkCash:   SPARK_CASH,
  sparkLabels: last12MonthLabels(),
  pendingApprovals: APPROVALS.length,
  recentAudit: AUDIT.slice(0, 10).map(e => ({ ts: e.ts, detail: e.detail, actor: e.actor })),
}

export const dashboard = {
  summary:    () => isDemo.value ? Promise.resolve(DEMO_SUMMARY)                           : get('/api/v1/analytics/dashboard'),
  sparklines: () => isDemo.value ? Promise.resolve({
    revenue:  SPARK_REV,
    expenses: SPARK_EXP,
    ar:       SPARK_AR,
    cash:     SPARK_CASH,
    labels:   last12MonthLabels(),
  })                                                                                        : get('/api/v1/analytics/dashboard/sparklines'),
  tbSummary:  () => isDemo.value ? Promise.resolve(null)                                   : get('/api/v1/analytics/dashboard/tb-summary'),
}

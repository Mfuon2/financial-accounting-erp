import { SPARK_REV, SPARK_EXP, SPARK_AR, SPARK_CASH, APPROVALS, AUDIT, AR_AGEING } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get } from './client.js'

const DEMO_SUMMARY = {
  cashAndEquivalents: 2713620,
  accountsReceivable: AR_AGEING.reduce((s, r) => s + r.total, 0),
  mtdRevenue:  2170200,
  mtdExpenses:  946800,
  sparkRev:    SPARK_REV,
  sparkExp:    SPARK_EXP,
  sparkAr:     SPARK_AR,
  sparkCash:   SPARK_CASH,
  sparkLabels: ['M03','M04','M05','M06','M07','M08','M09','M10','M11','M12','JAN','FEB'],
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
    labels:   DEMO_SUMMARY.sparkLabels,
  })                                                                                        : get('/api/v1/analytics/dashboard/sparklines'),
}

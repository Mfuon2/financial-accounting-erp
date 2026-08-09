import { BUDGETS } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

// Demo helper: normalise static fixture into API-shape (mirrors invoices.js's demoInvoices())
function demoBudgets() {
  return BUDGETS.map(b => ({
    id: b.id,
    entityId: 'demo',
    name: b.name,
    status: b.status,
    notes: b.notes,
    version: 0,
    totalAmount: b.lines.reduce((sum, l) => sum + l.amount, 0),
    lines: b.lines.map(l => ({
      id: l.id,
      accountId: l.accountCode,
      accountCode: l.accountCode,
      accountName: l.accountName,
      periodId: l.periodId,
      periodName: l.periodName,
      amount: l.amount,
    })),
  }))
}

function demoVariance(id) {
  const budget = BUDGETS.find(b => b.id === id)
  if (!budget) return null
  const lines = budget.lines.map(l => ({
    accountId: l.accountCode,
    accountCode: l.accountCode,
    accountName: l.accountName,
    periodId: l.periodId,
    periodName: l.periodName,
    budgetedAmount: l.amount,
    actualAmount: l.actual,
    variance: l.actual - l.amount,
    variancePercent: l.amount !== 0 ? ((l.actual - l.amount) / Math.abs(l.amount)) * 100 : null,
  }))
  return {
    budgetId: budget.id,
    budgetName: budget.name,
    lines,
    totalBudgeted: lines.reduce((s, l) => s + l.budgetedAmount, 0),
    totalActual: lines.reduce((s, l) => s + l.actualAmount, 0),
    totalVariance: lines.reduce((s, l) => s + l.variance, 0),
  }
}

export const budgets = {
  // GET /api/v1/budgets?entityId=&[page=&size=]
  list: (params) =>
    isDemo.value
      ? Promise.resolve({ content: demoBudgets(), totalElements: BUDGETS.length })
      : get(`/api/v1/budgets?${new URLSearchParams(params ?? {})}`),

  // GET /api/v1/budgets/{id}
  get: (id) =>
    isDemo.value
      ? Promise.resolve(demoBudgets().find(b => b.id === id))
      : get(`/api/v1/budgets/${id}`),

  // POST /api/v1/budgets  body: CreateBudgetCommand { entityId, name, notes, lines }
  create: (body) =>
    isDemo.value
      ? Promise.resolve({
          id: `BUD-${Date.now()}`, entityId: body.entityId, name: body.name, notes: body.notes ?? null,
          status: 'DRAFT', version: 0,
          totalAmount: (body.lines ?? []).reduce((s, l) => s + Number(l.amount), 0),
          lines: (body.lines ?? []).map((l, i) => ({ id: `demo-line-${i}`, ...l, accountCode: l.accountId, accountName: l.accountId, periodName: l.periodId })),
        })
      : post('/api/v1/budgets', body),

  // PUT /api/v1/budgets/{id}  body: UpdateBudgetCommand { name, notes, lines }
  update: (id, body) =>
    isDemo.value
      ? Promise.resolve({ ...demoBudgets().find(b => b.id === id), ...body })
      : put(`/api/v1/budgets/${id}`, body),

  // POST /api/v1/budgets/{id}/approve
  approve: (id) =>
    isDemo.value
      ? Promise.resolve({ ...demoBudgets().find(b => b.id === id), status: 'APPROVED' })
      : post(`/api/v1/budgets/${id}/approve`),

  // POST /api/v1/budgets/{id}/void  body: VoidBudgetCommand { reason }
  void: (id, reason) =>
    isDemo.value
      ? Promise.resolve({ ...demoBudgets().find(b => b.id === id), status: 'VOID' })
      : post(`/api/v1/budgets/${id}/void`, { reason }),

  // GET /api/v1/budgets/{id}/variance
  variance: (id) =>
    isDemo.value
      ? Promise.resolve(demoVariance(id))
      : get(`/api/v1/budgets/${id}/variance`),
}

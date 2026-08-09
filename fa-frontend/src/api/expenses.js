import { EXPENSE_CLAIMS, USERS, COA } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, put } from './client.js'

// Demo helper: normalise static fixture into API-shape (mirrors budgets.js's demoBudgets())
function demoAccount(accountCode) {
  const a = COA.find(c => c.code === accountCode)
  return { accountCode, accountName: a?.name ?? accountCode }
}

function demoEmployeeName(employeeId) {
  return USERS.find(u => u.id === employeeId)?.fullName ?? 'Unknown'
}

function demoClaims() {
  return EXPENSE_CLAIMS.map(c => ({
    id: c.id,
    entityId: 'demo',
    employeeId: c.employeeId,
    employeeName: demoEmployeeName(c.employeeId),
    claimDate: c.claimDate,
    status: c.status,
    notes: c.notes,
    journalEntryId: c.journalEntryId ?? null,
    rejectionReason: c.rejectionReason ?? null,
    version: 0,
    totalAmount: c.lines.reduce((sum, l) => sum + l.amount, 0),
    lines: c.lines.map(l => ({
      id: l.id,
      accountId: l.accountCode,
      accountCode: l.accountCode,
      accountName: demoAccount(l.accountCode).accountName,
      description: l.description,
      amount: l.amount,
      dateIncurred: l.dateIncurred,
      receiptReference: l.receiptReference ?? null,
    })),
  }))
}

export const expenseClaims = {
  // GET /api/v1/expense-claims?entityId=&status=&employeeId=&[page=&size=]
  list: (params) =>
    isDemo.value
      ? Promise.resolve({
          content: demoClaims().filter(c =>
            (!params?.status || c.status === params.status) &&
            (!params?.employeeId || c.employeeId === params.employeeId)
          ),
          totalElements: EXPENSE_CLAIMS.length,
        })
      : get(`/api/v1/expense-claims?${new URLSearchParams(params ?? {})}`),

  // GET /api/v1/expense-claims/{id}
  get: (id) =>
    isDemo.value
      ? Promise.resolve(demoClaims().find(c => c.id === id))
      : get(`/api/v1/expense-claims/${id}`),

  // POST /api/v1/expense-claims  body: CreateExpenseClaimCommand { entityId, employeeId, claimDate, notes, lines }
  create: (body) =>
    isDemo.value
      ? Promise.resolve({
          id: `EXP-${Date.now()}`, entityId: body.entityId, employeeId: body.employeeId,
          employeeName: demoEmployeeName(body.employeeId), claimDate: body.claimDate, notes: body.notes ?? null,
          status: 'DRAFT', version: 0, journalEntryId: null, rejectionReason: null,
          totalAmount: (body.lines ?? []).reduce((s, l) => s + Number(l.amount), 0),
          lines: (body.lines ?? []).map((l, i) => ({
            id: `demo-line-${i}`, ...l, accountCode: l.accountId, accountName: demoAccount(l.accountId).accountName,
          })),
        })
      : post('/api/v1/expense-claims', body),

  // PUT /api/v1/expense-claims/{id}  body: UpdateExpenseClaimCommand { claimDate, notes, lines }
  update: (id, body) =>
    isDemo.value
      ? Promise.resolve({ ...demoClaims().find(c => c.id === id), ...body })
      : put(`/api/v1/expense-claims/${id}`, body),

  // POST /api/v1/expense-claims/{id}/submit
  submit: (id) =>
    isDemo.value
      ? Promise.resolve({ ...demoClaims().find(c => c.id === id), status: 'SUBMITTED' })
      : post(`/api/v1/expense-claims/${id}/submit`),

  // POST /api/v1/expense-claims/{id}/approve
  approve: (id) =>
    isDemo.value
      ? Promise.resolve({ ...demoClaims().find(c => c.id === id), status: 'REIMBURSED', journalEntryId: `JE-DEMO-${id}` })
      : post(`/api/v1/expense-claims/${id}/approve`),

  // POST /api/v1/expense-claims/{id}/reject  body: RejectExpenseClaimCommand { reason }
  reject: (id, reason) =>
    isDemo.value
      ? Promise.resolve({ ...demoClaims().find(c => c.id === id), status: 'REJECTED', rejectionReason: reason })
      : post(`/api/v1/expense-claims/${id}/reject`, { reason }),

  // POST /api/v1/expense-claims/{id}/reopen
  reopen: (id) =>
    isDemo.value
      ? Promise.resolve({ ...demoClaims().find(c => c.id === id), status: 'DRAFT', rejectionReason: null })
      : post(`/api/v1/expense-claims/${id}/reopen`),
}

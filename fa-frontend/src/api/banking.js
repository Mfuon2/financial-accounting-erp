import { BANK_STATEMENTS, COA } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post } from './client.js'

// ── Demo helpers ────────────────────────────────────────────────────────────────
// Mirrors BankStatementService's reconciliation identity exactly:
//   adjustedBookBalance = glBalance + bankOutstandingTotal
//   adjustedBankBalance = closingBalance + glOutstandingTotal
// glBalance/ledgerEntries are given directly by the fixture (see data/index.js's comment) rather
// than derived from a full demo ledger-entries dataset, matching budgets.js's demoVariance()
// precedent of using pre-computed fixture values instead of re-deriving from a live ledger.

function accountFor(code) {
  const a = COA.find(c => c.code === code)
  return { accountId: code, accountCode: code, accountName: a?.name ?? code }
}

function signedLedgerAmount(entry) {
  return (entry.debit ?? 0) - (entry.credit ?? 0)
}

function demoImports() {
  return BANK_STATEMENTS.map(s => demoStatementResponse(s))
}

function demoStatementResponse(s) {
  const acct = accountFor(s.accountCode)
  return {
    id: s.id,
    entityId: 'demo',
    accountId: acct.accountId,
    accountCode: acct.accountCode,
    accountName: acct.accountName,
    statementDate: s.statementDate,
    openingBalance: s.openingBalance,
    closingBalance: s.closingBalance,
    notes: s.notes ?? null,
    version: 0,
    lines: s.lines.map(l => demoLineResponse(s, l)),
  }
}

function demoLineResponse(statement, line) {
  const matchedEntry = line.matchedLedgerEntryId
    ? statement.ledgerEntries.find(e => e.id === line.matchedLedgerEntryId)
    : null
  return {
    id: line.id,
    transDate: line.transDate,
    description: line.description,
    amount: line.amount,
    reference: line.reference ?? null,
    status: line.status,
    ignoreReason: line.ignoreReason ?? null,
    matches: matchedEntry
      ? [{
          id: `${line.id}-match`,
          ledgerEntryId: matchedEntry.id,
          matchType: line.matchType ?? 'MANUAL',
          matchedAt: `${statement.statementDate}T00:00:00Z`,
          ledgerEntryTransDate: matchedEntry.transDate,
          ledgerEntryDebit: matchedEntry.debit ?? 0,
          ledgerEntryCredit: matchedEntry.credit ?? 0,
        }]
      : [],
  }
}

function findStatement(id) {
  return BANK_STATEMENTS.find(s => s.id === id)
}

function findLine(lineId) {
  for (const s of BANK_STATEMENTS) {
    const line = s.lines.find(l => l.id === lineId)
    if (line) return { statement: s, line }
  }
  return null
}

function demoReconciliation(id) {
  const s = findStatement(id)
  if (!s) return null
  const acct = accountFor(s.accountCode)

  const matchedLedgerEntryIds = new Set(s.lines.filter(l => l.matchedLedgerEntryId).map(l => l.matchedLedgerEntryId))
  const outstandingGl = s.ledgerEntries.filter(e => !matchedLedgerEntryIds.has(e.id))
  const glOutstandingTotal = outstandingGl.reduce((sum, e) => sum + signedLedgerAmount(e), 0)

  const outstandingBankLines = s.lines.filter(l => l.status !== 'MATCHED')
  const bankOutstandingTotal = outstandingBankLines.reduce((sum, l) => sum + l.amount, 0)

  const adjustedBookBalance = s.glBalance + bankOutstandingTotal
  const adjustedBankBalance = s.closingBalance + glOutstandingTotal
  const difference = adjustedBookBalance - adjustedBankBalance

  const statementLinesTotal = s.lines.reduce((sum, l) => sum + l.amount, 0)
  const statementLinesTieToClosingBalance = Math.abs((s.openingBalance + statementLinesTotal) - s.closingBalance) < 0.005

  return {
    importId: s.id,
    accountId: acct.accountId,
    accountCode: acct.accountCode,
    accountName: acct.accountName,
    statementDate: s.statementDate,
    openingBalance: s.openingBalance,
    closingBalance: s.closingBalance,
    glBalance: s.glBalance,
    matchedCount: s.lines.filter(l => l.status === 'MATCHED').length,
    unmatchedCount: s.lines.filter(l => l.status === 'UNMATCHED').length,
    ignoredCount: s.lines.filter(l => l.status === 'IGNORED').length,
    statementLinesTotal,
    statementLinesTieToClosingBalance,
    outstandingLedgerEntries: outstandingGl.map(e => ({
      ledgerEntryId: e.id, transDate: e.transDate, functionalDebit: e.debit ?? 0, functionalCredit: e.credit ?? 0,
      signedAmount: signedLedgerAmount(e),
    })),
    glOutstandingTotal,
    bankOutstandingTotal,
    adjustedBookBalance,
    adjustedBankBalance,
    difference,
    tiesOut: Math.abs(difference) < 0.005,
    lines: s.lines.map(l => demoLineResponse(s, l)),
  }
}

function demoSuggestions(lineId) {
  const found = findLine(lineId)
  if (!found) return []
  const { statement, line } = found
  if (line.status !== 'UNMATCHED') return []
  const matchedIds = new Set(statement.lines.filter(l => l.matchedLedgerEntryId).map(l => l.matchedLedgerEntryId))
  const lineDate = new Date(line.transDate)
  return statement.ledgerEntries
    .filter(e => !matchedIds.has(e.id))
    .filter(e => signedLedgerAmount(e) === line.amount)
    .filter(e => Math.abs((new Date(e.transDate) - lineDate) / 86400000) <= 3)
    .map(e => ({
      ledgerEntryId: e.id, transDate: e.transDate, functionalDebit: e.debit ?? 0, functionalCredit: e.credit ?? 0,
      signedAmount: signedLedgerAmount(e),
    }))
}

// ── API ───────────────────────────────────────────────────────────────────────

export const bankStatements = {
  // GET /api/v1/bank-statements?entityId=&page=&size=
  list: (params) =>
    isDemo.value
      ? Promise.resolve({ content: demoImports(), totalElements: BANK_STATEMENTS.length })
      : get(`/api/v1/bank-statements?${new URLSearchParams(params ?? {})}`),

  // GET /api/v1/bank-statements/{id}
  get: (id) =>
    isDemo.value
      ? Promise.resolve(demoStatementResponse(findStatement(id)))
      : get(`/api/v1/bank-statements/${id}`),

  // POST /api/v1/bank-statements  body: CreateBankStatementImportCommand
  import: (body) =>
    isDemo.value
      ? Promise.resolve({
          id: `BSTMT-${Date.now()}`, entityId: body.entityId, accountId: body.accountId,
          accountCode: body.accountId, accountName: body.accountId,
          statementDate: body.statementDate, openingBalance: Number(body.openingBalance),
          closingBalance: Number(body.closingBalance), notes: body.notes ?? null, version: 0,
          lines: (body.lines ?? []).map((l, i) => ({
            id: `demo-line-${i}`, transDate: l.transactionDate, description: l.description,
            amount: Number(l.amount), reference: l.reference ?? null, status: 'UNMATCHED',
            ignoreReason: null, matches: [],
          })),
        })
      : post('/api/v1/bank-statements', body),

  // GET /api/v1/bank-statements/{id}/reconciliation
  reconciliation: (id) =>
    isDemo.value ? Promise.resolve(demoReconciliation(id)) : get(`/api/v1/bank-statements/${id}/reconciliation`),

  // GET /api/v1/bank-statements/lines/{lineId}/suggestions
  suggestions: (lineId) =>
    isDemo.value ? Promise.resolve(demoSuggestions(lineId)) : get(`/api/v1/bank-statements/lines/${lineId}/suggestions`),

  // POST /api/v1/bank-statements/lines/{lineId}/match  body: { ledgerEntryIds }
  match: (lineId, ledgerEntryIds) => {
    if (isDemo.value) {
      const found = findLine(lineId)
      if (found) {
        found.line.status = 'MATCHED'
        found.line.matchedLedgerEntryId = ledgerEntryIds[0]
        found.line.matchType = 'MANUAL'
      }
      return Promise.resolve(found ? demoStatementResponse(found.statement) : null)
    }
    return post(`/api/v1/bank-statements/lines/${lineId}/match`, { ledgerEntryIds })
  },

  // POST /api/v1/bank-statements/lines/{lineId}/auto-match
  autoMatch: (lineId) => {
    if (isDemo.value) {
      const candidates = demoSuggestions(lineId)
      const found = findLine(lineId)
      if (candidates.length === 1 && found) {
        found.line.status = 'MATCHED'
        found.line.matchedLedgerEntryId = candidates[0].ledgerEntryId
        found.line.matchType = 'AUTO'
        return Promise.resolve(demoStatementResponse(found.statement))
      }
      return Promise.reject(new Error(
        candidates.length === 0 ? 'No auto-match candidate found within the date window.' : 'Multiple candidates match — resolve manually.'
      ))
    }
    return post(`/api/v1/bank-statements/lines/${lineId}/auto-match`)
  },

  // POST /api/v1/bank-statements/lines/{lineId}/unmatch
  unmatch: (lineId) => {
    if (isDemo.value) {
      const found = findLine(lineId)
      if (found) {
        found.line.status = 'UNMATCHED'
        found.line.matchedLedgerEntryId = null
        found.line.matchType = null
      }
      return Promise.resolve(found ? demoStatementResponse(found.statement) : null)
    }
    return post(`/api/v1/bank-statements/lines/${lineId}/unmatch`)
  },

  // POST /api/v1/bank-statements/lines/{lineId}/ignore  body: { reason }
  ignore: (lineId, reason) => {
    if (isDemo.value) {
      const found = findLine(lineId)
      if (found) {
        found.line.status = 'IGNORED'
        found.line.ignoreReason = reason
      }
      return Promise.resolve(found ? demoStatementResponse(found.statement) : null)
    }
    return post(`/api/v1/bank-statements/lines/${lineId}/ignore`, { reason })
  },

  // POST /api/v1/bank-statements/lines/{lineId}/unignore
  unignore: (lineId) => {
    if (isDemo.value) {
      const found = findLine(lineId)
      if (found) {
        found.line.status = 'UNMATCHED'
        found.line.ignoreReason = null
      }
      return Promise.resolve(found ? demoStatementResponse(found.statement) : null)
    }
    return post(`/api/v1/bank-statements/lines/${lineId}/unignore`)
  },
}

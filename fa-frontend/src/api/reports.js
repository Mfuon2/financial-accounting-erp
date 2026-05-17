import { PnL, BS, CASHFLOW, TRIAL_BALANCE } from '@/data/index.js'
import { isDemo } from '@/composables/useAppMode.js'
import { get, post, downloadFile } from './client.js'

const DEMO_COMPARATIVE = {
  rows: TRIAL_BALANCE.map(r => ({
    accountCode: r.code, accountName: r.name,
    currentDebit: r.dr || 0, currentCredit: r.cr || 0,
    priorDebit: (r.dr || 0) * 0.92, priorCredit: (r.cr || 0) * 0.92,
    movementDebit: (r.dr || 0) * 0.08, movementCredit: (r.cr || 0) * 0.08,
  })),
  currentTotalDebits: TRIAL_BALANCE.reduce((s, r) => s + (r.dr || 0), 0),
  currentTotalCredits: TRIAL_BALANCE.reduce((s, r) => s + (r.cr || 0), 0),
}

const DEMO_CLOSING_PREVIEW = {
  periodCode: '2026-02',
  totalRevenue: 2184700,
  totalExpenses: 1298120,
  netIncome: 886580,
  revenueLines: [
    { accountCode: '4-1000', accountName: 'Service Revenue',           debit: 1944700, credit: null },
    { accountCode: '4-2000', accountName: 'Subscription Revenue',      debit: 240000,  credit: null },
  ],
  expenseLines: [
    { accountCode: '5-1000', accountName: 'Cost of Sales',             debit: null, credit: 320400  },
    { accountCode: '5-2000', accountName: 'Salaries & Wages',          debit: null, credit: 624000  },
    { accountCode: '5-3000', accountName: 'Operating Expenses',        debit: null, credit: 198400  },
    { accountCode: '5-3100', accountName: 'Depreciation Expense',      debit: null, credit: 124400  },
    { accountCode: '5-9000', accountName: 'Gain/Loss on Disposal & FX', debit: null, credit: 30920  },
  ],
  dividendLines: [],
}

export const reports = {
  pnl:              (params)         => isDemo.value ? Promise.resolve(PnL)              : get(`/api/v1/statements/profit-loss?${new URLSearchParams(params)}`),
  balanceSheet:     (params)         => isDemo.value ? Promise.resolve(BS)               : get(`/api/v1/statements/balance-sheet?${new URLSearchParams(params)}`),
  cashFlow:         (params)         => isDemo.value ? Promise.resolve(CASHFLOW)         : get(`/api/v1/statements/cash-flow?${new URLSearchParams(params)}`),
  trialBalance:     (params)         => isDemo.value ? Promise.resolve({
    entityId: 'demo', asOfDate: '2026-02-28',
    rows: TRIAL_BALANCE.map(r => ({ accountCode: r.code, accountName: r.name, debitBalance: r.dr || 0, creditBalance: r.cr || 0 })),
    totalDebits:  TRIAL_BALANCE.reduce((s, r) => s + (r.dr  || 0), 0),
    totalCredits: TRIAL_BALANCE.reduce((s, r) => s + (r.cr  || 0), 0),
  }) : get(`/api/v1/trial-balance?${new URLSearchParams(params)}`),
  comparative:      (params)         => isDemo.value ? Promise.resolve(DEMO_COMPARATIVE) : get(`/api/v1/trial-balance/comparative?${new URLSearchParams(params)}`),
  closingPreview:   (params)         => isDemo.value ? Promise.resolve(DEMO_CLOSING_PREVIEW) : get(`/api/v1/closing/preview?${new URLSearchParams(params)}`),
  runClosing:       (params)         => isDemo.value ? Promise.resolve({})               : post(`/api/v1/closing/run?${new URLSearchParams(params)}`, {}),
  reopenPeriod:     (params)         => isDemo.value ? Promise.resolve({})               : post(`/api/v1/closing/reopen?${new URLSearchParams(params)}`, {}),
  generalLedger:    (params)         => isDemo.value ? Promise.resolve([])               : get(`/api/v1/reports/general-ledger?${new URLSearchParams(params)}`),
  pdfBalanceSheet:  (params, fname)  => isDemo.value ? Promise.resolve()                 : downloadFile(`/api/v1/statements/balance-sheet/pdf?${new URLSearchParams(params)}`, fname ?? 'balance-sheet.pdf'),
  pdfPnl:           (params, fname)  => isDemo.value ? Promise.resolve()                 : downloadFile(`/api/v1/statements/profit-loss/pdf?${new URLSearchParams(params)}`, fname ?? 'profit-loss.pdf'),
  pdfCashFlow:      (params, fname)  => isDemo.value ? Promise.resolve()                 : downloadFile(`/api/v1/statements/cash-flow/pdf?${new URLSearchParams(params)}`, fname ?? 'cash-flow.pdf'),
}

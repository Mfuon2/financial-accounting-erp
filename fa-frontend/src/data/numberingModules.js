// Static module registry — mirrors NumberingModule.kt on the backend.
// Only the prefix is configurable; yearScoped and allowedPrefixes are fixed.

export const NUMBERING_MODULES = [
  { moduleKey: 'CUSTOMER',         label: 'Customer',         defaultPrefix: 'CU',   allowedPrefixes: ['CU', 'CUST', 'CLIENT'],   yearScoped: false },
  { moduleKey: 'SUPPLIER',         label: 'Supplier',         defaultPrefix: 'SUPP', allowedPrefixes: ['SUPP', 'VEND', 'VENDOR'],  yearScoped: false },
  { moduleKey: 'FIXED_ASSET',      label: 'Fixed Asset',      defaultPrefix: 'FA',   allowedPrefixes: ['FA', 'AST', 'ASSET'],      yearScoped: false },
  { moduleKey: 'SALES_INVOICE',    label: 'Sales Invoice',    defaultPrefix: 'INV',  allowedPrefixes: ['INV', 'SINV', 'SI'],       yearScoped: true  },
  { moduleKey: 'PURCHASE_BILL',    label: 'Purchase Bill',    defaultPrefix: 'BILL', allowedPrefixes: ['BILL', 'PINV', 'PB'],      yearScoped: true  },
  { moduleKey: 'JOURNAL_ENTRY',    label: 'Journal Entry',    defaultPrefix: 'JE',   allowedPrefixes: ['JE', 'JNL', 'JV'],         yearScoped: true  },
  { moduleKey: 'SOURCE_DOCUMENT',  label: 'Source Document',  defaultPrefix: 'SD',   allowedPrefixes: ['SD', 'DOC', 'REF'],        yearScoped: true  },
  { moduleKey: 'PAYMENT',          label: 'Payment',          defaultPrefix: 'PAY',  allowedPrefixes: ['PAY', 'PMT'],              yearScoped: true  },
  { moduleKey: 'RECEIPT',          label: 'Receipt',          defaultPrefix: 'RCT',  allowedPrefixes: ['RCT', 'RCPT'],             yearScoped: true  },
  { moduleKey: 'CREDIT_NOTE',      label: 'Credit Note',      defaultPrefix: 'CN',   allowedPrefixes: ['CN', 'CRNOTE'],            yearScoped: true  },
  { moduleKey: 'DEBIT_NOTE',       label: 'Debit Note',       defaultPrefix: 'DN',   allowedPrefixes: ['DN', 'DRNOTE'],            yearScoped: true  },
  { moduleKey: 'PURCHASE_ORDER',   label: 'Purchase Order',   defaultPrefix: 'PO',   allowedPrefixes: ['PO', 'PORD'],              yearScoped: true  },
  { moduleKey: 'QUOTATION',        label: 'Quotation',        defaultPrefix: 'QT',   allowedPrefixes: ['QT', 'QUOT'],              yearScoped: true  },
]

function fmt(prefix, yearScoped) {
  const year = new Date().getFullYear()
  return yearScoped ? `${prefix}-${year}-0001` : `${prefix}0001`
}

export const NumberingModule = {
  defaults: () => NUMBERING_MODULES.map(m => ({
    moduleKey:       m.moduleKey,
    label:           m.label,
    prefix:          m.defaultPrefix,
    allowedPrefixes: m.allowedPrefixes,
    yearScoped:      m.yearScoped,
    format:          m.yearScoped ? `${m.defaultPrefix}-{YYYY}-{0000}` : `${m.defaultPrefix}{0000}`,
    example:         fmt(m.defaultPrefix, m.yearScoped),
    resets:          m.yearScoped ? 'Yearly' : 'Never',
  })),
}

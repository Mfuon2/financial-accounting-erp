/* QeSuite — Mock data for every entity referenced by the Postman collection.
   All data is fake; references are stable so navigation links work cleanly. */

const ORG = {
  id: "ORG-1A3F",
  name: "Apollo Enterprises Ltd",
  legalName: "Apollo Enterprises Limited",
  registrationNumber: "PVT-20240001",
  taxIdentificationNumber: "A001234567A",
  functionalCurrency: "KES",
  reportingCurrency: "KES",
  countryCode: "KE",
  timezone: "Africa/Nairobi",
  fiscalYearStartMonth: 1,
  addressLine1: "12 Westlands Road",
  city: "Nairobi",
  postalCode: "00100",
  phone: "+254 700 000 000",
  email: "finance@apollo.co.ke",
  website: "apollo.co.ke",
};

const CURRENCIES = [
  { code: "KES", name: "Kenyan Shilling", symbol: "KSh", functional: true, decimals: 2 },
  { code: "USD", name: "US Dollar",       symbol: "$",   functional: false, decimals: 2 },
  { code: "EUR", name: "Euro",            symbol: "€",   functional: false, decimals: 2 },
  { code: "GBP", name: "Pound Sterling",  symbol: "£",   functional: false, decimals: 2 },
];

const FX_RATES = [
  { id: "FX-001", from: "USD", to: "KES", rate: 129.4500, asOf: "2026-02-28", source: "CBK" },
  { id: "FX-002", from: "EUR", to: "KES", rate: 140.2210, asOf: "2026-02-28", source: "CBK" },
  { id: "FX-003", from: "GBP", to: "KES", rate: 164.8800, asOf: "2026-02-28", source: "CBK" },
  { id: "FX-004", from: "USD", to: "KES", rate: 130.0200, asOf: "2026-01-31", source: "CBK" },
  { id: "FX-005", from: "EUR", to: "KES", rate: 141.3800, asOf: "2026-01-31", source: "CBK" },
];

const TAX_CODES = [
  { code: "VAT-16", name: "VAT Standard 16%", type: "OUTPUT", rate: 0.16, account: "2-2100", active: true },
  { code: "VAT-0",  name: "VAT Zero-Rated",   type: "OUTPUT", rate: 0.00, account: "2-2100", active: true },
  { code: "VAT-EX", name: "VAT Exempt",       type: "EXEMPT", rate: 0.00, account: null,     active: true },
  { code: "WHT-5",  name: "Withholding 5%",   type: "WHT",    rate: 0.05, account: "2-2110", active: true },
];

/* ---------- Chart of Accounts (tree) ---------- */
const COA = [
  // Assets (1)
  { code: "1-0000", name: "ASSETS", type: "HEADER", class: "ASSET", parent: null, normal: "DR", balance: 8_412_500, ifrs: "IAS 1" },
  { code: "1-1000", name: "Current Assets", type: "HEADER", class: "ASSET", parent: "1-0000", normal: "DR", balance: 4_980_320 },
  { code: "1-1100", name: "Cash & Bank — KES Operating", type: "POST", class: "ASSET", parent: "1-1000", normal: "DR", balance: 2_148_900, currency: "KES" },
  { code: "1-1105", name: "Cash & Bank — USD Settlement", type: "POST", class: "ASSET", parent: "1-1000", normal: "DR", balance: 540_220, currency: "USD" },
  { code: "1-1110", name: "Petty Cash", type: "POST", class: "ASSET", parent: "1-1000", normal: "DR", balance: 24_500, currency: "KES" },
  { code: "1-1200", name: "Accounts Receivable", type: "POST", class: "ASSET", parent: "1-1000", normal: "DR", balance: 1_842_700, currency: "KES", sub: true },
  { code: "1-1300", name: "Prepaid Expenses", type: "POST", class: "ASSET", parent: "1-1000", normal: "DR", balance: 124_000, currency: "KES" },
  { code: "1-1400", name: "Inventory", type: "POST", class: "ASSET", parent: "1-1000", normal: "DR", balance: 300_000, currency: "KES" },
  { code: "1-2000", name: "Non-Current Assets", type: "HEADER", class: "ASSET", parent: "1-0000", normal: "DR", balance: 3_432_180 },
  { code: "1-2100", name: "Property, Plant & Equipment", type: "POST", class: "ASSET", parent: "1-2000", normal: "DR", balance: 3_800_000, currency: "KES" },
  { code: "1-2110", name: "Accumulated Depreciation", type: "POST", class: "ASSET", parent: "1-2000", normal: "CR", balance: -367_820, currency: "KES", contra: true },

  // Liabilities (2)
  { code: "2-0000", name: "LIABILITIES", type: "HEADER", class: "LIABILITY", parent: null, normal: "CR", balance: 1_984_140 },
  { code: "2-1000", name: "Current Liabilities", type: "HEADER", class: "LIABILITY", parent: "2-0000", normal: "CR", balance: 1_658_140 },
  { code: "2-1100", name: "Accounts Payable", type: "POST", class: "LIABILITY", parent: "2-1000", normal: "CR", balance: 824_300, currency: "KES", sub: true },
  { code: "2-1200", name: "Deferred Revenue (IFRS 15)", type: "POST", class: "LIABILITY", parent: "2-1000", normal: "CR", balance: 450_000, currency: "KES" },
  { code: "2-2100", name: "VAT Payable", type: "POST", class: "LIABILITY", parent: "2-1000", normal: "CR", balance: 312_840, currency: "KES" },
  { code: "2-2110", name: "Withholding Tax Payable", type: "POST", class: "LIABILITY", parent: "2-1000", normal: "CR", balance: 71_000, currency: "KES" },
  { code: "2-3000", name: "Non-Current Liabilities", type: "HEADER", class: "LIABILITY", parent: "2-0000", normal: "CR", balance: 326_000 },
  { code: "2-3100", name: "Long-Term Loan", type: "POST", class: "LIABILITY", parent: "2-3000", normal: "CR", balance: 326_000, currency: "KES" },

  // Equity (3)
  { code: "3-0000", name: "EQUITY", type: "HEADER", class: "EQUITY", parent: null, normal: "CR", balance: 6_428_360 },
  { code: "3-1000", name: "Share Capital", type: "POST", class: "EQUITY", parent: "3-0000", normal: "CR", balance: 5_000_000, currency: "KES" },
  { code: "3-2000", name: "Retained Earnings", type: "POST", class: "EQUITY", parent: "3-0000", normal: "CR", balance: 1_428_360, currency: "KES" },

  // Revenue (4)
  { code: "4-0000", name: "REVENUE", type: "HEADER", class: "REVENUE", parent: null, normal: "CR", balance: 2_184_700 },
  { code: "4-1000", name: "Service Revenue", type: "POST", class: "REVENUE", parent: "4-0000", normal: "CR", balance: 1_944_700, currency: "KES" },
  { code: "4-2000", name: "Subscription Revenue (OVER_TIME)", type: "POST", class: "REVENUE", parent: "4-0000", normal: "CR", balance: 240_000, currency: "KES" },

  // Expense (5)
  { code: "5-0000", name: "EXPENSES", type: "HEADER", class: "EXPENSE", parent: null, normal: "DR", balance: 1_298_120 },
  { code: "5-1000", name: "Cost of Sales", type: "POST", class: "EXPENSE", parent: "5-0000", normal: "DR", balance: 320_400, currency: "KES" },
  { code: "5-2000", name: "Salaries & Wages", type: "POST", class: "EXPENSE", parent: "5-0000", normal: "DR", balance: 624_000, currency: "KES" },
  { code: "5-3000", name: "Operating Expenses", type: "POST", class: "EXPENSE", parent: "5-0000", normal: "DR", balance: 198_400, currency: "KES" },
  { code: "5-3100", name: "Depreciation Expense", type: "POST", class: "EXPENSE", parent: "5-0000", normal: "DR", balance: 124_400, currency: "KES" },
  { code: "5-9000", name: "Gain/Loss on Disposal", type: "POST", class: "EXPENSE", parent: "5-0000", normal: "DR", balance: 30_920, currency: "KES" },
];

/* ---------- Periods (FY 2026) ---------- */
const PERIODS = [
  { id: "P-2026-01", code: "2026-01", year: 2026, month: "January",  start: "2026-01-01", end: "2026-01-31", status: "CLOSED",    closedBy: "j.muriuki@apollo", closedAt: "2026-02-04" },
  { id: "P-2026-02", code: "2026-02", year: 2026, month: "February", start: "2026-02-01", end: "2026-02-28", status: "ADJUSTING", closedBy: null, closedAt: null },
  { id: "P-2026-03", code: "2026-03", year: 2026, month: "March",    start: "2026-03-01", end: "2026-03-31", status: "OPEN",      closedBy: null, closedAt: null },
  { id: "P-2026-04", code: "2026-04", year: 2026, month: "April",    start: "2026-04-01", end: "2026-04-30", status: "OPEN",      closedBy: null, closedAt: null },
  { id: "P-2026-05", code: "2026-05", year: 2026, month: "May",      start: "2026-05-01", end: "2026-05-31", status: "OPEN",      closedBy: null, closedAt: null },
  { id: "P-2026-06", code: "2026-06", year: 2026, month: "June",     start: "2026-06-01", end: "2026-06-30", status: "OPEN",      closedBy: null, closedAt: null },
  { id: "P-2026-07", code: "2026-07", year: 2026, month: "July",     start: "2026-07-01", end: "2026-07-31", status: "OPEN" },
  { id: "P-2026-08", code: "2026-08", year: 2026, month: "August",   start: "2026-08-01", end: "2026-08-31", status: "OPEN" },
  { id: "P-2026-09", code: "2026-09", year: 2026, month: "September",start: "2026-09-01", end: "2026-09-30", status: "OPEN" },
  { id: "P-2026-10", code: "2026-10", year: 2026, month: "October",  start: "2026-10-01", end: "2026-10-31", status: "OPEN" },
  { id: "P-2026-11", code: "2026-11", year: 2026, month: "November", start: "2026-11-01", end: "2026-11-30", status: "OPEN" },
  { id: "P-2026-12", code: "2026-12", year: 2026, month: "December", start: "2026-12-01", end: "2026-12-31", status: "OPEN" },
];

/* ---------- Customers / Suppliers ---------- */
const CUSTOMERS = [
  { id: "CUS-1001", code: "ACM-001", name: "Acme Corp", contact: "Daniel K.", email: "ap@acme.co.ke", phone: "+254 711 100 000", currency: "KES", creditLimit: 2_000_000, balance: 482_700, terms: "NET-30", active: true, lastInvoice: "2026-02-21" },
  { id: "CUS-1002", code: "NMB-001", name: "Nimbus Logistics", contact: "Anita O.", email: "finance@nimbus.africa", phone: "+254 722 220 000", currency: "USD", creditLimit: 18_000, balance: 6_420, terms: "NET-45", active: true, lastInvoice: "2026-02-19" },
  { id: "CUS-1003", code: "KAR-001", name: "Karibu Hotels Group", contact: "Peter M.", email: "ar@karibu.ke", phone: "+254 733 330 000", currency: "KES", creditLimit: 1_200_000, balance: 318_400, terms: "NET-30", active: true, lastInvoice: "2026-02-15" },
  { id: "CUS-1004", code: "SVN-001", name: "Sevana Pharma", contact: "Lillian W.", email: "lillian@sevana.co", phone: "+254 700 440 000", currency: "KES", creditLimit: 800_000, balance: 0, terms: "NET-15", active: true, lastInvoice: "2026-01-30" },
  { id: "CUS-1005", code: "GRV-001", name: "Greenvale Agritech", contact: "Brian R.", email: "brian@greenvale.ag", phone: "+254 711 550 000", currency: "KES", creditLimit: 500_000, balance: 162_800, terms: "NET-30", active: true, lastInvoice: "2026-02-22" },
  { id: "CUS-1006", code: "ZNT-001", name: "Zenith Capital",  contact: "Joyce A.", email: "joyce@zenith.fi", phone: "+254 700 660 000", currency: "USD", creditLimit: 25_000, balance: 8_900, terms: "NET-60", active: false, lastInvoice: "2025-12-04" },
  { id: "CUS-1007", code: "OLY-001", name: "Olympus Media", contact: "Tom N.",   email: "tom@olympus.media", phone: "+254 711 770 000", currency: "KES", creditLimit: 600_000, balance: 222_300, terms: "NET-30", active: true, lastInvoice: "2026-02-26" },
  { id: "CUS-1008", code: "PRT-001", name: "Portside Marine", contact: "Hassan B.", email: "ops@portside.ke", phone: "+254 722 880 000", currency: "KES", creditLimit: 450_000, balance: 91_200, terms: "NET-15", active: true, lastInvoice: "2026-02-18" },
];

const SUPPLIERS = [
  { id: "SUP-2001", code: "OFS-001", name: "Office Supplies Ltd", contact: "Mary K.", email: "sales@officesupplies.co.ke", phone: "+254 700 010 010", currency: "KES", balance: 184_300, terms: "NET-30", active: true, lastBill: "2026-02-20" },
  { id: "SUP-2002", code: "CLD-001", name: "Cloudbase Hosting",   contact: "Auto-billed", email: "billing@cloudbase.io", phone: null,             currency: "USD", balance: 2_140,   terms: "NET-15", active: true, lastBill: "2026-02-01" },
  { id: "SUP-2003", code: "ELP-001", name: "EL Power Co.",        contact: "Acct Mgmt", email: "ar@elpower.ke", phone: "+254 711 040 040", currency: "KES", balance: 64_200,  terms: "NET-30", active: true, lastBill: "2026-02-12" },
  { id: "SUP-2004", code: "ALR-001", name: "AllRoads Logistics",  contact: "George N.", email: "george@allroads.co.ke", phone: "+254 722 050 050", currency: "KES", balance: 38_900,  terms: "NET-45", active: true, lastBill: "2026-02-22" },
  { id: "SUP-2005", code: "JKM-001", name: "JK Maintenance",      contact: "John K.", email: "info@jkmaint.ke", phone: "+254 700 060 060", currency: "KES", balance: 12_300,  terms: "NET-15", active: false, lastBill: "2025-11-18" },
  { id: "SUP-2006", code: "PRM-001", name: "Premier Telecom",     contact: "Auto-billed", email: "biz@premier.tel", phone: null,           currency: "KES", balance: 22_800,  terms: "NET-30", active: true, lastBill: "2026-02-01" },
];

/* ---------- Fixed Assets ---------- */
const ASSETS = [
  { id: "FA-3001", tag: "FA-LAPTOP-001", name: "MacBook Pro 14 — Dev", category: "IT Equipment",  acquired: "2025-08-01", cost: 320_000, salvage: 30_000, life: 36, method: "STRAIGHT_LINE", monthlyDep: 8_055.56, accum: 56_388.89, netBook: 263_611.11, status: "IN_USE", assignedTo: "Mwangi Karanja" },
  { id: "FA-3002", tag: "FA-LAPTOP-002", name: "MacBook Pro 14 — Finance", category: "IT Equipment", acquired: "2025-06-15", cost: 320_000, salvage: 30_000, life: 36, method: "STRAIGHT_LINE", monthlyDep: 8_055.56, accum: 72_500.00, netBook: 247_500.00, status: "IN_USE", assignedTo: "Wambui Njeri" },
  { id: "FA-3003", tag: "FA-VEH-001",    name: "Toyota Hilux DC", category: "Vehicles",        acquired: "2024-11-01", cost: 4_200_000, salvage: 400_000, life: 60, method: "STRAIGHT_LINE", monthlyDep: 63_333.33, accum: 1_013_333, netBook: 3_186_667, status: "IN_USE", assignedTo: "Operations Pool" },
  { id: "FA-3004", tag: "FA-FUR-001",    name: "Open-plan Workstations (x12)", category: "Furniture", acquired: "2025-03-20", cost: 720_000, salvage: 60_000, life: 84, method: "STRAIGHT_LINE", monthlyDep: 7_857.14, accum: 86_428.54, netBook: 633_571.46, status: "IN_USE", assignedTo: "Office HQ" },
  { id: "FA-3005", tag: "FA-AC-001",     name: "VRF HVAC System", category: "Building",       acquired: "2024-07-01", cost: 1_800_000, salvage: 150_000, life: 120, method: "STRAIGHT_LINE", monthlyDep: 13_750, accum: 261_250, netBook: 1_538_750, status: "IN_USE", assignedTo: "Office HQ" },
  { id: "FA-3006", tag: "FA-SVR-001",    name: "Rack Server — Edge Node", category: "IT Equipment", acquired: "2023-02-10", cost: 880_000, salvage: 60_000, life: 48, method: "STRAIGHT_LINE", monthlyDep: 17_083.33, accum: 632_083, netBook: 247_917, status: "DISPOSED", assignedTo: "Decommissioned" },
];

/* ---------- Source Documents ---------- */
const SOURCE_DOCS = [
  { id: "SD-4001", ref: "SD-0001", type: "Receipt", supplier: "Office Supplies Ltd", amount: 18_400, currency: "KES", date: "2026-02-04", status: "ARCHIVED", attachments: 2, classifiedAs: "Operating Expense — Office Supplies (IAS 1)" },
  { id: "SD-4002", ref: "SD-0002", type: "Bank Statement", supplier: "Equity Bank", amount: 2_148_900, currency: "KES", date: "2026-02-28", status: "REVIEWED", attachments: 1, classifiedAs: null },
  { id: "SD-4003", ref: "SD-0003", type: "Invoice (Inbound)", supplier: "Cloudbase Hosting", amount: 2_140, currency: "USD", date: "2026-02-01", status: "APPROVED", attachments: 1, classifiedAs: "IT Hosting — Operating Expense" },
  { id: "SD-4004", ref: "SD-0004", type: "Receipt", supplier: "AllRoads Logistics", amount: 38_900, currency: "KES", date: "2026-02-22", status: "SUBMITTED", attachments: 1, classifiedAs: null },
  { id: "SD-4005", ref: "SD-0005", type: "Receipt", supplier: "Premier Telecom", amount: 22_800, currency: "KES", date: "2026-02-15", status: "DRAFT", attachments: 0, classifiedAs: null },
  { id: "SD-4006", ref: "SD-0006", type: "Contract", supplier: "Nimbus Logistics", amount: null, currency: null, date: "2026-02-10", status: "APPROVED", attachments: 3, classifiedAs: "Revenue Contract (IFRS 15 OVER_TIME)" },
  { id: "SD-4007", ref: "SD-0007", type: "Receipt", supplier: "JK Maintenance", amount: 12_300, currency: "KES", date: "2025-11-18", status: "VOID", attachments: 0, classifiedAs: null },
];

/* ---------- Journal Entries ---------- */
const JOURNALS = [
  {
    id: "JE-5001", ref: "JE-2026-0042", date: "2026-02-25", period: "2026-02",
    description: "Salary Payment — February 2026", source: "MANUAL",
    status: "POSTED", postedAt: "2026-02-25 16:08", postedBy: "j.muriuki",
    submittedBy: "w.njeri", approvedBy: "j.muriuki",
    lines: [
      { account: "5-2000", name: "Salaries & Wages",       debit: 624_000, credit: 0,       memo: "Feb payroll gross" },
      { account: "1-1100", name: "Cash & Bank — KES",      debit: 0,        credit: 553_000, memo: "Net pay disbursed" },
      { account: "2-2110", name: "Withholding Tax Payable",debit: 0,        credit: 71_000,  memo: "PAYE accrual" },
    ],
  },
  {
    id: "JE-5002", ref: "JE-2026-0043", date: "2026-02-28", period: "2026-02",
    description: "Monthly Depreciation — Feb 2026 (Batch)", source: "AUTO_DEPRECIATION",
    status: "POSTED", postedAt: "2026-02-28 23:55", postedBy: "system",
    lines: [
      { account: "5-3100", name: "Depreciation Expense",   debit: 118_134.92, credit: 0, memo: "5 assets" },
      { account: "1-2110", name: "Accumulated Depreciation", debit: 0, credit: 118_134.92, memo: "Contra asset" },
    ],
  },
  {
    id: "JE-5003", ref: "JE-2026-0044", date: "2026-02-28", period: "2026-02",
    description: "FX Revaluation — USD AR & Cash (IAS 21)", source: "FX_REVAL",
    status: "POSTED", postedAt: "2026-02-28 23:58", postedBy: "system",
    lines: [
      { account: "1-1200", name: "Accounts Receivable",      debit: 0, credit: 8_420,  memo: "USD AR reval" },
      { account: "1-1105", name: "Cash & Bank — USD",        debit: 0, credit: 3_240,  memo: "USD cash reval" },
      { account: "5-9000", name: "FX Loss",                  debit: 11_660, credit: 0, memo: "Period FX loss" },
    ],
  },
  {
    id: "JE-5004", ref: "JE-2026-0045", date: "2026-02-27", period: "2026-02",
    description: "Prepaid Insurance Amortization", source: "AUTO_AMORT",
    status: "PENDING_APPROVAL", submittedBy: "w.njeri",
    lines: [
      { account: "5-3000", name: "Operating Expenses",   debit: 21_000, credit: 0, memo: "1/6 of annual policy" },
      { account: "1-1300", name: "Prepaid Expenses",     debit: 0, credit: 21_000, memo: "Amortized portion" },
    ],
  },
  {
    id: "JE-5005", ref: "JE-2026-0046", date: "2026-02-26", period: "2026-02",
    description: "Accrued Utilities — Feb", source: "MANUAL",
    status: "DRAFT", submittedBy: null,
    lines: [
      { account: "5-3000", name: "Operating Expenses",   debit: 16_800, credit: 0, memo: "Estimated power" },
      { account: "2-1100", name: "Accounts Payable",     debit: 0, credit: 16_800, memo: "Accrual — EL Power" },
    ],
  },
  {
    id: "JE-5006", ref: "JE-2026-0041", date: "2026-02-15", period: "2026-02",
    description: "Reversal of JE-2026-0030 (duplicate posting)", source: "REVERSAL",
    status: "POSTED", postedAt: "2026-02-15 11:20", postedBy: "j.muriuki",
    lines: [
      { account: "5-3000", name: "Operating Expenses",   debit: 0, credit: 4_500, memo: "Reversing dup" },
      { account: "1-1100", name: "Cash & Bank — KES",    debit: 4_500, credit: 0, memo: "Reversing dup" },
    ],
  },
  {
    id: "JE-5007", ref: "JE-2026-0040", date: "2026-02-12", period: "2026-02",
    description: "Sales Invoice INV-2026-0017 Posted", source: "INVOICE",
    status: "POSTED", postedAt: "2026-02-12 14:30", postedBy: "system",
    lines: [
      { account: "1-1200", name: "Accounts Receivable",  debit: 290_000, credit: 0, memo: "Acme Corp" },
      { account: "4-1000", name: "Service Revenue",      debit: 0, credit: 250_000, memo: "Consulting" },
      { account: "2-2100", name: "VAT Payable",          debit: 0, credit: 40_000, memo: "VAT 16%" },
    ],
  },
];

/* ---------- Invoices ---------- */
const INVOICES = [
  { id: "INV-6001", ref: "INV-2026-0017", customer: "CUS-1001", customerName: "Acme Corp", date: "2026-02-12", due: "2026-03-14", currency: "KES", subtotal: 250_000, tax: 40_000, total: 290_000, paid: 0, balance: 290_000, status: "POSTED", recognition: "POINT_IN_TIME", aging: 12, lines: [{ desc: "Implementation consulting (Q1)", qty: 1, unit: 250_000, tax: "VAT-16" }] },
  { id: "INV-6002", ref: "INV-2026-0018", customer: "CUS-1002", customerName: "Nimbus Logistics", date: "2026-02-19", due: "2026-04-05", currency: "USD", subtotal: 5_500, tax: 880, total: 6_380, paid: 0, balance: 6_380, status: "POSTED", recognition: "POINT_IN_TIME", aging: 5, lines: [{ desc: "Cross-border freight audit", qty: 1, unit: 5_500, tax: "VAT-16" }] },
  { id: "INV-6003", ref: "INV-2026-0019", customer: "CUS-1003", customerName: "Karibu Hotels Group", date: "2026-02-15", due: "2026-03-17", currency: "KES", subtotal: 274_500, tax: 43_920, total: 318_420, paid: 0, balance: 318_420, status: "POSTED", recognition: "POINT_IN_TIME", aging: 9, lines: [{ desc: "Hospitality CRM rollout", qty: 1, unit: 274_500, tax: "VAT-16" }] },
  { id: "INV-6004", ref: "INV-2026-0020", customer: "CUS-1005", customerName: "Greenvale Agritech", date: "2026-02-22", due: "2026-03-24", currency: "KES", subtotal: 140_345, tax: 22_455, total: 162_800, paid: 0, balance: 162_800, status: "POSTED", recognition: "OVER_TIME", aging: 2, lines: [{ desc: "Annual SaaS subscription", qty: 12, unit: 11_695, tax: "VAT-16" }] },
  { id: "INV-6005", ref: "INV-2026-0021", customer: "CUS-1007", customerName: "Olympus Media", date: "2026-02-26", due: "2026-03-28", currency: "KES", subtotal: 191_810, tax: 30_690, total: 222_500, paid: 0, balance: 222_500, status: "POSTED", recognition: "POINT_IN_TIME", aging: -2, lines: [{ desc: "Brand campaign analytics", qty: 1, unit: 191_810, tax: "VAT-16" }] },
  { id: "INV-6006", ref: "INV-2026-0022", customer: "CUS-1008", customerName: "Portside Marine", date: "2026-02-18", due: "2026-03-05", currency: "KES", subtotal: 78_620, tax: 12_580, total: 91_200, paid: 0, balance: 91_200, status: "POSTED", recognition: "POINT_IN_TIME", aging: 6, discount: 5, lines: [{ desc: "Marine ops audit (5% bulk discount)", qty: 1, unit: 82_758, tax: "VAT-16" }] },
  { id: "INV-6007", ref: "INV-2026-0023", customer: "CUS-1001", customerName: "Acme Corp", date: "2026-02-21", due: "2026-03-23", currency: "KES", subtotal: 165_517, tax: 26_483, total: 192_000, paid: 0, balance: 192_000, status: "DRAFT", recognition: "POINT_IN_TIME", aging: null, lines: [{ desc: "Migration retainer — Feb", qty: 1, unit: 165_517, tax: "VAT-16" }] },
  { id: "INV-6008", ref: "INV-2026-0015", customer: "CUS-1004", customerName: "Sevana Pharma", date: "2026-01-30", due: "2026-02-14", currency: "KES", subtotal: 320_000, tax: 51_200, total: 371_200, paid: 371_200, balance: 0, status: "PAID", recognition: "POINT_IN_TIME", aging: -14, lines: [{ desc: "Compliance package", qty: 1, unit: 320_000, tax: "VAT-16" }] },
];

/* ---------- Credit Notes ---------- */
const CREDIT_NOTES = [
  { id: "CN-7001", ref: "CN-2026-0001", invoice: "INV-2026-0019", customer: "Karibu Hotels Group", date: "2026-02-26", amount: 28_000, currency: "KES", reason: "Service credit — rollout delay", status: "POSTED" },
  { id: "CN-7002", ref: "CN-2026-0002", invoice: "INV-2026-0017", customer: "Acme Corp",          date: "2026-02-28", amount: 14_500, currency: "KES", reason: "Goodwill adjustment",          status: "DRAFT" },
];

/* ---------- Payments ---------- */
const PAYMENTS = [
  { id: "PAY-8001", ref: "PAY-2026-0011", customer: "Sevana Pharma", date: "2026-02-12", currency: "KES", amount: 371_200, matched: 371_200, method: "BANK_TRANSFER", invoice: "INV-2026-0015", status: "POSTED", postedAt: "2026-02-12 09:30" },
  { id: "PAY-8002", ref: "PAY-2026-0012", customer: "Karibu Hotels Group", date: "2026-02-22", currency: "KES", amount: 100_000, matched: 100_000, method: "M_PESA", invoice: "INV-2026-0019", status: "POSTED", postedAt: "2026-02-22 13:11" },
  { id: "PAY-8003", ref: "PAY-2026-0013", customer: "Acme Corp", date: "2026-02-25", currency: "KES", amount: 50_000, matched: 50_000, method: "BANK_TRANSFER", invoice: null, status: "APPROVED" },
  { id: "PAY-8004", ref: "PAY-2026-0014", customer: "Olympus Media", date: "2026-02-27", currency: "KES", amount: 222_500, matched: 0, method: "BANK_TRANSFER", invoice: null, status: "PENDING_APPROVAL" },
  { id: "PAY-8005", ref: "PAY-2026-0015", customer: "Nimbus Logistics", date: "2026-02-27", currency: "USD", amount: 6_380, matched: 0, method: "BANK_TRANSFER", invoice: null, status: "DRAFT" },
];

/* ---------- Receipts ---------- */
const RECEIPTS = [
  { id: "RCT-9001", ref: "RCT-2026-0011", payment: "PAY-2026-0011", customer: "Sevana Pharma", date: "2026-02-12", amount: 371_200, currency: "KES", issued: true, status: "ISSUED" },
  { id: "RCT-9002", ref: "RCT-2026-0012", payment: "PAY-2026-0012", customer: "Karibu Hotels Group", date: "2026-02-22", amount: 100_000, currency: "KES", issued: true, status: "ISSUED" },
  { id: "RCT-9003", ref: "RCT-2026-0013", payment: "PAY-2026-0013", customer: "Acme Corp", date: "2026-02-25", amount: 50_000, currency: "KES", issued: false, status: "GENERATED" },
];

/* ---------- AR Ageing ---------- */
const AR_AGEING = [
  { customer: "Acme Corp",          current: 192_000, b1_30: 290_000, b31_60: 0,      b61_90: 0,      b90:  0,      total: 482_000 },
  { customer: "Nimbus Logistics",   current: 0,        b1_30: 825_000, b31_60: 0,      b61_90: 0,      b90:  0,      total: 825_000 },
  { customer: "Karibu Hotels Group",current: 28_000,   b1_30: 290_420, b31_60: 0,      b61_90: 0,      b90:  0,      total: 318_420 },
  { customer: "Greenvale Agritech", current: 162_800,  b1_30: 0,        b31_60: 0,      b61_90: 0,      b90:  0,      total: 162_800 },
  { customer: "Olympus Media",      current: 222_500,  b1_30: 0,        b31_60: 0,      b61_90: 0,      b90:  0,      total: 222_500 },
  { customer: "Portside Marine",    current: 91_200,   b1_30: 0,        b31_60: 0,      b61_90: 0,      b90:  0,      total: 91_200 },
  { customer: "Zenith Capital",     current: 0,        b1_30: 0,        b31_60: 0,      b61_90: 0,      b90:  1_152_220, total: 1_152_220 },
];

/* ---------- Users / API Keys / Sessions ---------- */
const USERS = [
  { id: "U-1001", username: "j.muriuki",  fullName: "Jane Muriuki",  email: "j.muriuki@apollo.co.ke",  role: "ADMIN",            active: true, lastLogin: "2026-02-28 09:14", mfa: true },
  { id: "U-1002", username: "w.njeri",    fullName: "Wambui Njeri",  email: "w.njeri@apollo.co.ke",    role: "SENIOR_ACCOUNTANT",active: true, lastLogin: "2026-02-28 08:42", mfa: true },
  { id: "U-1003", username: "m.karanja",  fullName: "Mwangi Karanja",email: "m.karanja@apollo.co.ke",  role: "ACCOUNTANT",       active: true, lastLogin: "2026-02-27 17:30", mfa: false },
  { id: "U-1004", username: "h.bakari",   fullName: "Hassan Bakari", email: "h.bakari@apollo.co.ke",   role: "AUDITOR",          active: true, lastLogin: "2026-02-25 11:02", mfa: true },
  { id: "U-1005", username: "l.wairimu",  fullName: "Lillian Wairimu",email: "l.wairimu@apollo.co.ke", role: "ACCOUNTANT",       active: false, lastLogin: "2025-11-12 14:50", mfa: false },
];

const API_KEYS = [
  { id: "AK-001", label: "Mobile App — Production", prefix: "qek_live_8a3F",       created: "2025-09-12", lastUsed: "2026-02-28 06:00", scope: "read:invoices, write:payments", active: true,  expires: null },
  { id: "AK-002", label: "M-Pesa Gateway",          prefix: "qek_live_2Bx9",       created: "2025-06-04", lastUsed: "2026-02-28 14:55", scope: "callback:payments",              active: true,  expires: "2027-06-04" },
  { id: "AK-003", label: "ETL — Data Warehouse",    prefix: "qek_live_Lp02",       created: "2025-04-01", lastUsed: "2026-02-28 23:00", scope: "read:*",                         active: true,  expires: null },
  { id: "AK-004", label: "Sandbox testing",         prefix: "qek_test_R7tZ",       created: "2024-11-20", lastUsed: "2025-12-18 09:00", scope: "read:*, write:*",                active: false, expires: "2025-12-31" },
];

const SESSIONS = [
  { id: "S-001", device: "MacBook Pro · Chrome 132", ip: "102.215.99.4", location: "Nairobi, KE", started: "2026-02-28 08:42", current: true },
  { id: "S-002", device: "iPhone 15 · iOS 17",        ip: "102.215.99.4", location: "Nairobi, KE", started: "2026-02-27 18:11", current: false },
  { id: "S-003", device: "Windows 11 · Edge 122",     ip: "41.90.66.10",  location: "Mombasa, KE",  started: "2026-02-24 10:02", current: false },
];

/* ---------- Audit log ---------- */
const AUDIT = [
  { ts: "2026-02-28 23:58", actor: "system",      action: "POST",     target: "JE-2026-0044", detail: "FX revaluation posted — KES 11,660 loss" },
  { ts: "2026-02-28 23:55", actor: "system",      action: "POST",     target: "JE-2026-0043", detail: "Batch depreciation — 5 assets, KES 118,134.92" },
  { ts: "2026-02-28 16:14", actor: "j.muriuki",   action: "APPROVE",  target: "PAY-2026-0013", detail: "Approved KES 50,000 payment from Acme Corp" },
  { ts: "2026-02-28 14:55", actor: "M-Pesa API",  action: "CALLBACK", target: "PAY-2026-0012", detail: "STK push confirmed — KES 100,000" },
  { ts: "2026-02-28 11:02", actor: "w.njeri",     action: "SUBMIT",   target: "JE-2026-0045", detail: "Prepaid amortization submitted for approval" },
  { ts: "2026-02-28 09:14", actor: "j.muriuki",   action: "LOGIN",    target: "session S-001", detail: "Web login via password+MFA" },
  { ts: "2026-02-28 09:00", actor: "w.njeri",     action: "TRANSITION", target: "Period 2026-02", detail: "OPEN → ADJUSTING" },
  { ts: "2026-02-27 17:30", actor: "m.karanja",   action: "CREATE",   target: "INV-2026-0023", detail: "Draft invoice — Acme Corp, KES 192,000" },
  { ts: "2026-02-26 14:00", actor: "j.muriuki",   action: "REVERSE",  target: "JE-2026-0030", detail: "Reversal posted as JE-2026-0041" },
];

/* ---------- Approvals queue ---------- */
const APPROVALS = [
  { id: "APR-001", type: "Journal Entry",   ref: "JE-2026-0045", title: "Prepaid Insurance Amortization", amount: 21_000, currency: "KES", submittedBy: "w.njeri",  submittedAt: "2026-02-28 11:02", waitingFor: "j.muriuki" },
  { id: "APR-002", type: "Payment",          ref: "PAY-2026-0014", title: "Olympus Media — KES 222,500", amount: 222_500, currency: "KES", submittedBy: "m.karanja", submittedAt: "2026-02-28 10:14", waitingFor: "j.muriuki" },
  { id: "APR-003", type: "Source Document", ref: "SD-0004", title: "AllRoads Logistics receipt", amount: 38_900, currency: "KES", submittedBy: "m.karanja", submittedAt: "2026-02-28 08:55", waitingFor: "w.njeri" },
  { id: "APR-004", type: "Invoice",          ref: "INV-2026-0023", title: "Acme Corp migration retainer", amount: 192_000, currency: "KES", submittedBy: "m.karanja", submittedAt: "2026-02-27 17:35", waitingFor: "w.njeri" },
];

/* ---------- Trial balance (unadjusted vs adjusted) ---------- */
const TRIAL_BALANCE = [
  { code: "1-1100", name: "Cash & Bank — KES Operating",  dr: 2_148_900, cr: 0,        adj_dr: 2_148_900, adj_cr: 0 },
  { code: "1-1105", name: "Cash & Bank — USD Settlement", dr: 543_460,    cr: 0,        adj_dr: 540_220,    adj_cr: 0 },
  { code: "1-1110", name: "Petty Cash",                   dr: 24_500,     cr: 0,        adj_dr: 24_500,     adj_cr: 0 },
  { code: "1-1200", name: "Accounts Receivable",          dr: 1_851_120,  cr: 0,        adj_dr: 1_842_700,  adj_cr: 0 },
  { code: "1-1300", name: "Prepaid Expenses",             dr: 145_000,    cr: 0,        adj_dr: 124_000,    adj_cr: 0 },
  { code: "1-1400", name: "Inventory",                    dr: 300_000,    cr: 0,        adj_dr: 300_000,    adj_cr: 0 },
  { code: "1-2100", name: "PP&E",                         dr: 3_800_000,  cr: 0,        adj_dr: 3_800_000,  adj_cr: 0 },
  { code: "1-2110", name: "Accum. Depreciation",          dr: 0,          cr: 249_685,  adj_dr: 0,          adj_cr: 367_820 },
  { code: "2-1100", name: "Accounts Payable",             dr: 0,          cr: 807_500,  adj_dr: 0,          adj_cr: 824_300 },
  { code: "2-1200", name: "Deferred Revenue",             dr: 0,          cr: 580_000,  adj_dr: 0,          adj_cr: 450_000 },
  { code: "2-2100", name: "VAT Payable",                  dr: 0,          cr: 312_840,  adj_dr: 0,          adj_cr: 312_840 },
  { code: "2-2110", name: "WHT Payable",                  dr: 0,          cr: 71_000,   adj_dr: 0,          adj_cr: 71_000 },
  { code: "2-3100", name: "Long-Term Loan",               dr: 0,          cr: 326_000,  adj_dr: 0,          adj_cr: 326_000 },
  { code: "3-1000", name: "Share Capital",                dr: 0,          cr: 5_000_000,adj_dr: 0,          adj_cr: 5_000_000 },
  { code: "3-2000", name: "Retained Earnings",            dr: 0,          cr: 1_428_360,adj_dr: 0,          adj_cr: 1_428_360 },
  { code: "4-1000", name: "Service Revenue",              dr: 0,          cr: 2_074_700,adj_dr: 0,          adj_cr: 1_944_700 },
  { code: "4-2000", name: "Subscription Revenue",         dr: 0,          cr: 110_000,  adj_dr: 0,          adj_cr: 240_000 },
  { code: "5-1000", name: "Cost of Sales",                dr: 320_400,    cr: 0,        adj_dr: 320_400,    adj_cr: 0 },
  { code: "5-2000", name: "Salaries & Wages",             dr: 624_000,    cr: 0,        adj_dr: 624_000,    adj_cr: 0 },
  { code: "5-3000", name: "Operating Expenses",           dr: 177_400,    cr: 0,        adj_dr: 198_400,    adj_cr: 0 },
  { code: "5-3100", name: "Depreciation Expense",         dr: 6_265.08,   cr: 0,        adj_dr: 124_400,    adj_cr: 0 },
  { code: "5-9000", name: "Gain/Loss on Disposal & FX",   dr: 19_240,     cr: 0,        adj_dr: 30_920,     adj_cr: 0 },
];

/* ---------- IAS 1 Compliance items ---------- */
const IAS1_CHECKS = [
  { id: "IAS1-1", name: "Statement of financial position present", status: "PASS", detail: "Balance Sheet generated for 2026-02-28" },
  { id: "IAS1-2", name: "Statement of profit or loss & OCI",       status: "PASS", detail: "P&L generated; no OCI items in period" },
  { id: "IAS1-3", name: "Statement of cash flows (IAS 7)",         status: "PASS", detail: "Indirect method, all sections present" },
  { id: "IAS1-4", name: "Statement of changes in equity",          status: "WARN", detail: "Pending owner contribution disclosure note" },
  { id: "IAS1-5", name: "Notes — accounting policies",             status: "PASS", detail: "Functional currency, depreciation methods disclosed" },
  { id: "IAS1-6", name: "Comparative period presented",            status: "PASS", detail: "Prior period (2025) comparatives attached" },
  { id: "IAS1-7", name: "Going concern assessment",                status: "PASS", detail: "Cash runway ≥ 18 months" },
  { id: "IAS1-8", name: "Materiality threshold defined",           status: "WARN", detail: "Threshold set at KES 50,000 — consider 1% of revenue" },
  { id: "IAS1-9", name: "Offsetting prohibition",                  status: "PASS", detail: "No asset/liability offsets detected" },
  { id: "IAS1-10", name: "Disclosure: functional currency",        status: "PASS", detail: "KES disclosed in notes" },
];

/* ---------- Income Statement / Balance Sheet / Cash Flow ---------- */
const PnL = {
  period: "1 Jan 2026 – 28 Feb 2026",
  sections: [
    { type: "section", label: "Revenue" },
    { type: "line", label: "Service revenue", indent: 1, current: 1_944_700, prior: 1_802_000 },
    { type: "line", label: "Subscription revenue (OVER_TIME, IFRS 15)", indent: 1, current: 240_000, prior: 180_000 },
    { type: "line", label: "Less: Sales discounts", indent: 1, current: -14_500, prior: -8_200 },
    { type: "subtotal", label: "Total revenue", current: 2_170_200, prior: 1_973_800 },
    { type: "section", label: "Cost of Sales" },
    { type: "line", label: "Cost of services delivered", indent: 1, current: -320_400, prior: -302_100 },
    { type: "subtotal", label: "Gross profit", current: 1_849_800, prior: 1_671_700 },
    { type: "section", label: "Operating Expenses" },
    { type: "line", label: "Salaries & wages", indent: 1, current: -624_000, prior: -610_000 },
    { type: "line", label: "Operating expenses", indent: 1, current: -198_400, prior: -174_300 },
    { type: "line", label: "Depreciation", indent: 1, current: -124_400, prior: -112_800 },
    { type: "subtotal", label: "Operating profit", current: 903_000, prior: 774_600 },
    { type: "section", label: "Other Items" },
    { type: "line", label: "FX revaluation loss (IAS 21)", indent: 1, current: -11_660, prior: 6_400 },
    { type: "line", label: "Gain on disposal", indent: 1, current: -19_260, prior: 0 },
    { type: "total", label: "Profit for the period", current: 872_080, prior: 781_000 },
  ],
};

const BS = {
  asOf: "28 Feb 2026",
  sections: [
    { type: "section", label: "Assets" },
    { type: "line", label: "Cash & cash equivalents", indent: 1, current: 2_713_620, prior: 2_180_400 },
    { type: "line", label: "Trade receivables (net)", indent: 1, current: 1_842_700, prior: 1_510_300 },
    { type: "line", label: "Prepayments", indent: 1, current: 124_000, prior: 142_000 },
    { type: "line", label: "Inventory", indent: 1, current: 300_000, prior: 280_500 },
    { type: "subtotal", label: "Current assets", current: 4_980_320, prior: 4_113_200 },
    { type: "line", label: "Property, plant & equipment (net)", indent: 1, current: 3_432_180, prior: 3_556_580 },
    { type: "subtotal", label: "Non-current assets", current: 3_432_180, prior: 3_556_580 },
    { type: "total", label: "Total assets", current: 8_412_500, prior: 7_669_780 },
    { type: "section", label: "Liabilities" },
    { type: "line", label: "Trade payables", indent: 1, current: 824_300, prior: 692_400 },
    { type: "line", label: "Deferred revenue (IFRS 15)", indent: 1, current: 450_000, prior: 380_000 },
    { type: "line", label: "Tax payable (VAT + WHT)", indent: 1, current: 383_840, prior: 318_780 },
    { type: "subtotal", label: "Current liabilities", current: 1_658_140, prior: 1_391_180 },
    { type: "line", label: "Long-term loan", indent: 1, current: 326_000, prior: 342_000 },
    { type: "subtotal", label: "Non-current liabilities", current: 326_000, prior: 342_000 },
    { type: "section", label: "Equity" },
    { type: "line", label: "Share capital", indent: 1, current: 5_000_000, prior: 5_000_000 },
    { type: "line", label: "Retained earnings", indent: 1, current: 1_428_360, prior: 936_600 },
    { type: "subtotal", label: "Total equity", current: 6_428_360, prior: 5_936_600 },
    { type: "total", label: "Total equity & liabilities", current: 8_412_500, prior: 7_669_780 },
  ],
};

const CASHFLOW = {
  period: "1 Jan 2026 – 28 Feb 2026",
  sections: [
    { type: "section", label: "Operating activities (indirect)" },
    { type: "line", label: "Profit for the period", indent: 1, current: 872_080, prior: 781_000 },
    { type: "line", label: "Depreciation", indent: 1, current: 124_400, prior: 112_800 },
    { type: "line", label: "FX revaluation loss", indent: 1, current: 11_660, prior: -6_400 },
    { type: "line", label: "Increase in trade receivables", indent: 1, current: -332_400, prior: -210_300 },
    { type: "line", label: "Increase in trade payables", indent: 1, current: 131_900, prior: 64_800 },
    { type: "line", label: "Increase in deferred revenue", indent: 1, current: 70_000, prior: 38_000 },
    { type: "subtotal", label: "Net cash from operating", current: 877_640, prior: 779_900 },
    { type: "section", label: "Investing activities" },
    { type: "line", label: "Purchase of PP&E", indent: 1, current: 0, prior: -240_000 },
    { type: "line", label: "Proceeds from disposal", indent: 1, current: 18_000, prior: 0 },
    { type: "subtotal", label: "Net cash from investing", current: 18_000, prior: -240_000 },
    { type: "section", label: "Financing activities" },
    { type: "line", label: "Loan repayments", indent: 1, current: -16_000, prior: -16_000 },
    { type: "subtotal", label: "Net cash from financing", current: -16_000, prior: -16_000 },
    { type: "total", label: "Net change in cash", current: 879_640, prior: 523_900 },
    { type: "line", label: "Cash at beginning of period", indent: 0, current: 1_833_980, prior: 1_310_080 },
    { type: "total", label: "Cash at end of period", current: 2_713_620, prior: 1_833_980 },
  ],
};

/* ---------- Sparkline data + dashboard time series ---------- */
const SPARK_CASH = [1.51, 1.62, 1.55, 1.74, 1.92, 2.10, 2.18, 2.34, 2.42, 2.55, 2.61, 2.71];
const SPARK_AR   = [1.20, 1.32, 1.40, 1.38, 1.52, 1.61, 1.55, 1.68, 1.74, 1.80, 1.79, 1.84];
const SPARK_REV  = [0.32, 0.41, 0.55, 0.49, 0.62, 0.71, 0.78, 0.84, 0.92, 1.05, 1.12, 1.18];
const SPARK_EXP  = [0.18, 0.22, 0.30, 0.34, 0.41, 0.45, 0.49, 0.53, 0.61, 0.68, 0.72, 0.76];

window.QSDATA = {
  ORG, CURRENCIES, FX_RATES, TAX_CODES, COA, PERIODS,
  CUSTOMERS, SUPPLIERS, ASSETS, SOURCE_DOCS, JOURNALS, INVOICES, CREDIT_NOTES,
  PAYMENTS, RECEIPTS, AR_AGEING, USERS, API_KEYS, SESSIONS, AUDIT, APPROVALS,
  TRIAL_BALANCE, IAS1_CHECKS, PnL, BS, CASHFLOW,
  SPARK_CASH, SPARK_AR, SPARK_REV, SPARK_EXP,
};

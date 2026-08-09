-- §2 (CLAUDE.md "Configuration-driven, not hard-coded") — third CategoryType kind, closing the
-- last of the three hard-coded-category violations logged in MEMORY.md: the DOC_TYPES array
-- hard-coded in fa-frontend/src/views/ledger/SourceDocs.vue.
--
-- Backfills every *existing* entity's DOCUMENT_TYPE rows with exactly the codes/order/labels the
-- hard-coded array used, so no existing SourceDocument.type reference goes stale. Entities created
-- after this migration are lazily seeded on first read by CategoryService.listByType, same as
-- PAYMENT_TERM/PAYMENT_METHOD (see V41).
INSERT INTO categories (id, entity_id, category_type, code, label, sort_order, is_active, created_at, modified_at)
SELECT gen_random_uuid(), o.id, v.category_type, v.code, v.label, v.sort_order, TRUE, now(), now()
FROM organizations o
CROSS JOIN (VALUES
    ('DOCUMENT_TYPE', 'SALES_INVOICE',     'Sales Invoice',     0),
    ('DOCUMENT_TYPE', 'PURCHASE_INVOICE',  'Purchase Invoice',  1),
    ('DOCUMENT_TYPE', 'CASH_RECEIPT',      'Cash Receipt',      2),
    ('DOCUMENT_TYPE', 'PAYMENT_VOUCHER',   'Payment Voucher',   3),
    ('DOCUMENT_TYPE', 'BANK_STATEMENT',    'Bank Statement',    4),
    ('DOCUMENT_TYPE', 'CREDIT_NOTE',       'Credit Note',       5),
    ('DOCUMENT_TYPE', 'DEBIT_NOTE',        'Debit Note',        6),
    ('DOCUMENT_TYPE', 'PAYROLL_RECORD',    'Payroll Record',    7),
    ('DOCUMENT_TYPE', 'TAX_DECLARATION',   'Tax Declaration',   8),
    ('DOCUMENT_TYPE', 'JOURNAL_VOUCHER',   'Journal Voucher',   9)
) AS v(category_type, code, label, sort_order)
ON CONFLICT (entity_id, category_type, code) DO NOTHING;

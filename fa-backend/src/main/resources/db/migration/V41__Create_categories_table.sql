-- §2 (CLAUDE.md "Configuration-driven, not hard-coded") — generic, per-entity,
-- database-backed store for business-meaningful category values (payment terms, payment
-- methods, and future kinds), following the same shape as the pre-existing configurable
-- document numbering system (`entity_number_configs`, V31).
--
-- Replaces the hard-coded PAYMENT_TERMS arrays in Suppliers.vue/Customers.vue and the
-- hard-coded/inconsistent PAYMENT_METHODS arrays in Bills.vue/Payments.vue/Invoices.vue
-- (see MEMORY.md Known Issues).
CREATE TABLE categories (
    id UUID PRIMARY KEY NOT NULL,
    entity_id UUID NOT NULL,
    period_id UUID,
    category_type VARCHAR(30) NOT NULL,
    code VARCHAR(40) NOT NULL,
    label VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    modified_at TIMESTAMPTZ NOT NULL,
    modified_by UUID,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT,
    CONSTRAINT uq_categories_entity_type_code UNIQUE (entity_id, category_type, code)
);

CREATE INDEX idx_categories_entity_type ON categories(entity_id, category_type);
CREATE INDEX idx_categories_is_active ON categories(is_active);

-- Backfill: pre-populate every *existing* entity's PAYMENT_TERM and PAYMENT_METHOD rows with
-- exactly the codes/order the hard-coded frontend arrays used, so no existing
-- Customer.paymentTerms / Supplier.paymentTerms / Payment.paymentMethod /
-- BillPayment.paymentMethod reference goes stale. Entities created after this migration are
-- lazily seeded on first read by CategoryService.listByType — there is no "on entity create"
-- hook in this codebase today (COA templates are applied the same explicit, not automatic, way).
INSERT INTO categories (id, entity_id, category_type, code, label, sort_order, is_active, created_at, modified_at)
SELECT gen_random_uuid(), o.id, v.category_type, v.code, v.label, v.sort_order, TRUE, now(), now()
FROM organizations o
CROSS JOIN (VALUES
    ('PAYMENT_TERM', 'DUE_ON_RECEIPT', 'Due On Receipt', 0),
    ('PAYMENT_TERM', 'NET_15',         'Net 15',         1),
    ('PAYMENT_TERM', 'NET_30',         'Net 30',         2),
    ('PAYMENT_TERM', 'NET_45',         'Net 45',         3),
    ('PAYMENT_TERM', 'NET_60',         'Net 60',         4),
    ('PAYMENT_TERM', 'NET_90',         'Net 90',         5),
    ('PAYMENT_METHOD', 'BANK_TRANSFER', 'Bank Transfer', 0),
    ('PAYMENT_METHOD', 'MPESA',         'M-Pesa',        1),
    ('PAYMENT_METHOD', 'CASH',          'Cash',          2),
    ('PAYMENT_METHOD', 'CHEQUE',        'Cheque',        3),
    ('PAYMENT_METHOD', 'CREDIT_CARD',   'Credit Card',   4)
) AS v(category_type, code, label, sort_order);

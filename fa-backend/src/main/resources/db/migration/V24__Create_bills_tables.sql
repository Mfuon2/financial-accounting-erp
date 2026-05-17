-- Accounts Payable: Vendor Bills and Bill Payments

CREATE TABLE bills (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID        NOT NULL,
    period_id       UUID        REFERENCES accounting_periods(id),
    bill_number     VARCHAR(50) NOT NULL,
    supplier_id     UUID        REFERENCES suppliers(id),
    supplier_name   VARCHAR(255) NOT NULL,
    bill_date       DATE        NOT NULL,
    due_date        DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    subtotal        NUMERIC(19,6) NOT NULL DEFAULT 0,
    tax_amount      NUMERIC(19,6) NOT NULL DEFAULT 0,
    total_amount    NUMERIC(19,6) NOT NULL DEFAULT 0,
    paid_amount     NUMERIC(19,6) NOT NULL DEFAULT 0,
    currency_code   VARCHAR(3)  NOT NULL DEFAULT 'KES',
    description     TEXT,
    notes           TEXT,
    journal_entry_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    modified_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID,
    modified_by     UUID,
    is_active       BOOLEAN     NOT NULL DEFAULT true,
    CONSTRAINT bills_bill_number_entity_unique UNIQUE (entity_id, bill_number)
);

CREATE TABLE bill_items (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id         UUID        NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
    description     VARCHAR(500) NOT NULL,
    quantity        NUMERIC(19,6) NOT NULL DEFAULT 1,
    unit_price      NUMERIC(19,6) NOT NULL,
    tax_code        VARCHAR(20),
    tax_rate        NUMERIC(5,4) NOT NULL DEFAULT 0,
    line_total      NUMERIC(19,6) NOT NULL,
    account_code    VARCHAR(20)
);

CREATE TABLE bill_payments (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id       UUID        NOT NULL,
    bill_id         UUID        NOT NULL REFERENCES bills(id),
    payment_date    DATE        NOT NULL,
    amount          NUMERIC(19,6) NOT NULL,
    currency_code   VARCHAR(3)  NOT NULL DEFAULT 'KES',
    reference       VARCHAR(100),
    notes           TEXT,
    journal_entry_id UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      UUID
);

CREATE INDEX idx_bills_entity       ON bills(entity_id);
CREATE INDEX idx_bills_supplier     ON bills(supplier_id);
CREATE INDEX idx_bills_status       ON bills(status);
CREATE INDEX idx_bills_due_date     ON bills(due_date);
CREATE INDEX idx_bill_payments_bill ON bill_payments(bill_id);
CREATE INDEX idx_bill_payments_ent  ON bill_payments(entity_id);

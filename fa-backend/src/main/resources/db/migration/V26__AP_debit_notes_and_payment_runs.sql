-- AP: Debit notes, payment runs, source document link on bills

-- Debit note fields and source document reference on bills
ALTER TABLE bills
    ADD COLUMN is_debit_note      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN original_bill_id   UUID    REFERENCES bills(id),
    ADD COLUMN source_document_id UUID;

-- Payment runs — batch payment of multiple bills in one journal entry
CREATE TABLE payment_runs (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id        UUID          NOT NULL,
    payment_date     DATE          NOT NULL,
    payment_method   VARCHAR(20),
    cash_account_id  UUID,
    total_amount     NUMERIC(19,6) NOT NULL DEFAULT 0,
    bill_count       INTEGER       NOT NULL DEFAULT 0,
    journal_entry_id UUID,
    reference        VARCHAR(100),
    notes            TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by       UUID
);

ALTER TABLE bill_payments
    ADD COLUMN payment_run_id UUID REFERENCES payment_runs(id);

CREATE INDEX idx_bills_supplier_date ON bills(entity_id, supplier_id, bill_date);
CREATE INDEX idx_bills_debit_note    ON bills(entity_id, original_bill_id) WHERE is_debit_note = true;
CREATE INDEX idx_payment_runs_entity ON payment_runs(entity_id);
CREATE INDEX idx_bill_payments_run   ON bill_payments(payment_run_id);

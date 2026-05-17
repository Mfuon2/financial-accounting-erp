-- §14.1, §14.2 — Invoice master table.
CREATE TABLE invoices (
    id UUID PRIMARY KEY NOT NULL,
    entity_id UUID NOT NULL,
    period_id UUID NOT NULL,
    invoice_number VARCHAR(50) NOT NULL,
    customer_id UUID NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(20,6) NOT NULL,
    subtotal DECIMAL(20,6) NOT NULL,
    tax_amount DECIMAL(20,6) NOT NULL,
    discount_amount DECIMAL(20,6) NOT NULL,
    total_amount DECIMAL(20,6) NOT NULL,
    paid_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
    outstanding_amount DECIMAL(20,6) NOT NULL,
    status VARCHAR(50) NOT NULL,
    notes TEXT,
    journal_entry_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL,
    created_by UUID,
    modified_at TIMESTAMP NOT NULL,
    modified_by UUID,
    deactivated_at TIMESTAMP,
    deactivated_by UUID,
    deactivation_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    -- §3.2 — Per-entity invoice number uniqueness.
    CONSTRAINT uq_invoices_number UNIQUE (entity_id, invoice_number),
    -- §3.2 — FKs (Rule 03 — RESTRICT delete).
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_journal_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entries(id) ON DELETE RESTRICT,
    -- §14.2 — Sign rules: positive for invoices; negative for credit notes (the
    -- service applies `negate()` consistently across subtotal/tax/total/outstanding).
    CONSTRAINT chk_invoices_sign_consistency CHECK (
        (status = 'CREDIT_NOTE' AND total_amount <= 0)
        OR (status <> 'CREDIT_NOTE' AND subtotal >= 0 AND tax_amount >= 0
            AND discount_amount >= 0 AND total_amount >= 0 AND paid_amount >= 0)
    ),
    CONSTRAINT chk_invoices_paid_within_total CHECK (
        (status = 'CREDIT_NOTE') OR (paid_amount <= total_amount)
    ),
    CONSTRAINT chk_invoices_outstanding_equals CHECK (outstanding_amount = total_amount - paid_amount),
    CONSTRAINT chk_invoices_exchange_rate_positive CHECK (exchange_rate > 0),
    CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT', 'APPROVED', 'SENT', 'PARTIALLY_PAID', 'PAID', 'VOID', 'CREDIT_NOTE'))
);

-- §8.2 — Required indexes for §4.5 performance targets.
CREATE INDEX idx_invoices_entity_id ON invoices(entity_id);
CREATE INDEX idx_invoices_customer_id ON invoices(customer_id);
CREATE INDEX idx_invoices_status ON invoices(status);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);
CREATE INDEX idx_invoices_issue_date ON invoices(issue_date);
CREATE INDEX idx_invoices_period_id ON invoices(period_id);

-- §14.1 — Invoice line items (children of `invoices`; cascade delete with parent).
CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY NOT NULL,
    invoice_id UUID NOT NULL,
    line_number INT NOT NULL,
    account_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity DECIMAL(20,6) NOT NULL,
    unit_price DECIMAL(20,6) NOT NULL,
    tax_rate_id UUID,
    recognition_type VARCHAR(50),
    recognized_amount DECIMAL(20,6),
    line_subtotal DECIMAL(20,6) NOT NULL,
    line_tax DECIMAL(20,6) NOT NULL,
    line_total DECIMAL(20,6) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    modified_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
    CONSTRAINT fk_invoice_lines_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_lines_tax_rate FOREIGN KEY (tax_rate_id) REFERENCES tax_rates(id) ON DELETE RESTRICT,
    -- §14.2 — Line subtotal/tax/total are non-negative for invoices; credit-note lines
    -- carry negative values. Enforced via the parent invoice's status (no direct
    -- access here in CHECK; the service is the gate).
    CONSTRAINT chk_invoice_lines_total_equals CHECK (line_total = line_subtotal + line_tax),
    CONSTRAINT chk_invoice_lines_recognition_type CHECK (
        recognition_type IS NULL OR recognition_type IN ('POINT_IN_TIME', 'OVER_TIME')
    )
);

CREATE INDEX idx_invoice_lines_invoice_id ON invoice_lines(invoice_id);
CREATE INDEX idx_invoice_lines_account_id ON invoice_lines(account_id);
CREATE INDEX idx_invoice_lines_tax_rate_id ON invoice_lines(tax_rate_id);

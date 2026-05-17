-- §15 — Receipts master table.
-- A receipt is issued to a customer after a payment has been matched to a posted journal entry.
CREATE TABLE receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id UUID NOT NULL,
    period_id UUID,
    receipt_number VARCHAR(50) NOT NULL,
    payment_id UUID NOT NULL,
    invoice_id UUID REFERENCES invoices(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    receipt_date DATE NOT NULL,
    receipt_amount DECIMAL(20,6) NOT NULL CHECK (receipt_amount > 0),
    currency_code VARCHAR(3) NOT NULL,
    journal_entry_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    delivery_email VARCHAR(255),
    delivery_phone VARCHAR(20),
    issued_at TIMESTAMP WITH TIME ZONE,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by UUID,
    deactivation_reason TEXT,
    CONSTRAINT uq_receipts_number UNIQUE (entity_id, receipt_number),
    CONSTRAINT uq_receipts_payment UNIQUE (payment_id)
);

CREATE INDEX idx_receipts_entity_status ON receipts(entity_id, status);
CREATE INDEX idx_receipts_customer ON receipts(customer_id);
CREATE INDEX idx_receipts_invoice ON receipts(invoice_id);

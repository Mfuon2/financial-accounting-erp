-- Module 14: Payments — master table and indexes.
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id UUID NOT NULL,
    period_id UUID,
    payment_number VARCHAR(50) NOT NULL,
    invoice_id UUID REFERENCES invoices(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    payment_method VARCHAR(30) NOT NULL,
    payment_amount DECIMAL(20,6) NOT NULL CHECK (payment_amount > 0),
    currency_code VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(20,6) NOT NULL DEFAULT 1.000000,
    functional_amount DECIMAL(20,6) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    transaction_reference VARCHAR(100),
    journal_entry_id UUID,
    mpesa_result_code VARCHAR(10),
    mpesa_receipt_number VARCHAR(50),
    payment_date DATE NOT NULL,
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
    CONSTRAINT uq_payments_number UNIQUE (entity_id, payment_number),
    CONSTRAINT uq_payments_trans_ref UNIQUE (entity_id, transaction_reference)
);
CREATE INDEX idx_payments_entity_status ON payments(entity_id, status);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);
CREATE INDEX idx_payments_customer ON payments(customer_id);
CREATE INDEX idx_payments_date ON payments(entity_id, payment_date);

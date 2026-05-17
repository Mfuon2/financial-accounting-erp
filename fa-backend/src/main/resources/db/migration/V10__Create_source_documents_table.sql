-- §2.2 — Source Documents table
CREATE TABLE source_documents (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    period_id UUID,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    doc_date DATE NOT NULL,
    reference_number VARCHAR(100) NOT NULL,
    description TEXT,
    amount DECIMAL(20,6),
    currency_code VARCHAR(3),
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT
);

CREATE INDEX idx_source_docs_entity ON source_documents(entity_id);
CREATE INDEX idx_source_docs_status ON source_documents(status);
CREATE INDEX idx_source_docs_type ON source_documents(type);

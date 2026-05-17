-- §11 — Forensic Audit Log (INSERT-only)
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    period_id UUID,
    user_id UUID NOT NULL,
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id UUID NOT NULL,
    payload_before TEXT,
    payload_after TEXT,
    client_ip VARCHAR(45),
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT
);

CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX idx_audit_logs_entity ON audit_logs(entity_id);

-- §13 — Taxation Master Data
CREATE TABLE tax_codes (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    period_id UUID,
    code VARCHAR(20) NOT NULL,
    description TEXT,
    is_recoverable BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT,
    UNIQUE(entity_id, code)
);

CREATE TABLE tax_rates (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    period_id UUID,
    tax_code_id UUID NOT NULL REFERENCES tax_codes(id),
    rate DECIMAL(10,4) NOT NULL,
    effective_from DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT
);

CREATE INDEX idx_tax_rates_lookup ON tax_rates(tax_code_id, effective_from DESC);

-- Update journal_entry_lines with tax columns
ALTER TABLE journal_entry_lines 
    ADD COLUMN tax_code VARCHAR(20),
    ADD COLUMN tax_amount DECIMAL(20,6);

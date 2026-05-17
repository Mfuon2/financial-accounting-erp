-- V1__Create_accounts_table.sql
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    period_id UUID,
    account_code VARCHAR(20) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    account_subtype VARCHAR(50) NOT NULL,
    normal_balance VARCHAR(10) NOT NULL,
    is_temporary BOOLEAN NOT NULL,
    parent_account_id UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ifrs_classification VARCHAR(255),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_by UUID,
    
    -- §2.1 & §3.2 — Constraints
    CONSTRAINT uq_account_code UNIQUE (entity_id, account_code),
    CONSTRAINT uq_account_name UNIQUE (entity_id, account_name),
    CONSTRAINT chk_normal_balance CHECK (normal_balance IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX idx_accounts_entity ON accounts(entity_id);
CREATE INDEX idx_accounts_code ON accounts(account_code);

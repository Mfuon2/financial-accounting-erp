-- V4__Create_journal_and_ledger_tables.sql

-- Module 03: Journal Entry Engine
CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    period_id UUID NOT NULL REFERENCES accounting_periods(id),
    trans_date DATE NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL, -- DRAFT, PENDING_APPROVAL, POSTED, REVERSED
    source_type VARCHAR(50),
    source_id UUID,
    
    -- Audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by UUID,
    deactivation_reason TEXT
);

CREATE INDEX idx_journal_entries_entity_period ON journal_entries(entity_id, period_id);
CREATE INDEX idx_journal_entries_status ON journal_entries(status);

CREATE TABLE journal_entry_lines (
    id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id),
    description TEXT,
    
    -- Monetary columns §3.1
    debit_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
    
    -- Multi-currency §12
    currency_code VARCHAR(3) NOT NULL,
    exchange_rate DECIMAL(20,6) NOT NULL DEFAULT 1,
    functional_debit DECIMAL(20,6) NOT NULL DEFAULT 0,
    functional_credit DECIMAL(20,6) NOT NULL DEFAULT 0,

    -- Constraints §3.2
    CONSTRAINT chk_debit_credit_exclusive CHECK (
        (debit_amount = 0 AND credit_amount > 0) OR 
        (debit_amount > 0 AND credit_amount = 0)
    ),
    CONSTRAINT chk_functional_debit_credit_exclusive CHECK (
        (functional_debit = 0 AND functional_credit > 0) OR 
        (functional_debit > 0 AND functional_credit = 0)
    )
);

CREATE INDEX idx_jel_journal_entry ON journal_entry_lines(journal_entry_id);
CREATE INDEX idx_jel_account ON journal_entry_lines(account_id);

-- Module 04: General Ledger
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    journal_entry_line_id UUID NOT NULL REFERENCES journal_entry_lines(id),
    trans_date DATE NOT NULL,
    
    debit_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(20,6) NOT NULL DEFAULT 0,
    running_balance DECIMAL(20,6) NOT NULL,
    
    -- Audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by UUID,
    deactivation_reason TEXT
);

CREATE INDEX idx_ledger_entries_account_date ON ledger_entries(account_id, trans_date);
CREATE INDEX idx_ledger_entries_entity ON ledger_entries(entity_id);

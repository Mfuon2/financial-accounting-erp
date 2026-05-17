-- V8__Add_missing_period_id_to_ledger_entries.sql

ALTER TABLE ledger_entries ADD COLUMN period_id UUID;

-- Indexes for performance
CREATE INDEX idx_ledger_entries_period ON ledger_entries(period_id);

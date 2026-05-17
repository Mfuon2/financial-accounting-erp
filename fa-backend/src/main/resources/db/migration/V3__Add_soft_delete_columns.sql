-- V3__Add_soft_delete_columns.sql
-- Missing columns in accounts (is_active already exists in V1)
ALTER TABLE accounts ADD COLUMN deactivated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounts ADD COLUMN deactivated_by UUID;
ALTER TABLE accounts ADD COLUMN deactivation_reason TEXT;

-- Missing columns in accounting_periods
ALTER TABLE accounting_periods ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE accounting_periods ADD COLUMN deactivated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE accounting_periods ADD COLUMN deactivated_by UUID;
ALTER TABLE accounting_periods ADD COLUMN deactivation_reason TEXT;

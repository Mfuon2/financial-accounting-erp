-- V7__Add_missing_period_id_to_master_data.sql

ALTER TABLE currencies ADD COLUMN period_id UUID;
ALTER TABLE exchange_rates ADD COLUMN period_id UUID;
ALTER TABLE fixed_assets ADD COLUMN period_id UUID;

-- Indexes for performance
CREATE INDEX idx_currencies_period ON currencies(period_id);
CREATE INDEX idx_exchange_rates_period ON exchange_rates(period_id);
CREATE INDEX idx_fixed_assets_period ON fixed_assets(period_id);

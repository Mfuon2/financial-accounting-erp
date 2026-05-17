-- V6__Create_fx_and_asset_tables.sql

-- Module 12: Multi-Currency Engine
CREATE TABLE currencies (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    currency_name VARCHAR(50) NOT NULL,
    is_functional BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by UUID,
    deactivation_reason TEXT,

    CONSTRAINT uq_currency_code UNIQUE (entity_id, currency_code)
);

CREATE INDEX idx_currencies_entity ON currencies(entity_id);

CREATE TABLE exchange_rates (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    rate_date DATE NOT NULL,
    rate_value DECIMAL(20,6) NOT NULL,
    rate_type VARCHAR(20) NOT NULL, -- SPOT, CLOSING, AVERAGE
    
    -- Audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by UUID,
    deactivation_reason TEXT,

    CONSTRAINT uq_exchange_rate UNIQUE (entity_id, from_currency, to_currency, rate_date, rate_type)
);

CREATE INDEX idx_exchange_rates_lookup ON exchange_rates(entity_id, from_currency, to_currency, rate_date);

-- Module 16: Fixed Asset Management
CREATE TABLE fixed_assets (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    asset_code VARCHAR(50) NOT NULL,
    asset_name VARCHAR(200) NOT NULL,
    
    -- COA Mappings (§11.1)
    cost_account_id UUID NOT NULL REFERENCES accounts(id),
    accum_dep_account_id UUID NOT NULL REFERENCES accounts(id),
    dep_expense_account_id UUID NOT NULL REFERENCES accounts(id),
    
    acquisition_date DATE NOT NULL,
    acquisition_cost DECIMAL(20,6) NOT NULL,
    salvage_value DECIMAL(20,6) NOT NULL DEFAULT 0,
    useful_life_months INTEGER NOT NULL,
    depreciation_method VARCHAR(30) NOT NULL, -- STRAIGHT_LINE, DOUBLE_DECLINING
    
    status VARCHAR(30) NOT NULL, -- ACTIVE, DISPOSED, FULLY_DEPRECIATED
    
    -- Audit columns
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID NOT NULL,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_by UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMP WITH TIME ZONE,
    deactivated_by UUID,
    deactivation_reason TEXT,

    CONSTRAINT uq_asset_code UNIQUE (entity_id, asset_code)
);

CREATE INDEX idx_fixed_assets_entity ON fixed_assets(entity_id);

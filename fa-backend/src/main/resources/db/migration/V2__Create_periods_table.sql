-- V2__Create_periods_table.sql
CREATE TABLE accounting_periods (
    id UUID PRIMARY KEY,
    entity_id UUID NOT NULL,
    period_id UUID,
    period_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FUTURE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    modified_by UUID,
    
    CONSTRAINT uq_period_name UNIQUE (entity_id, period_name),
    CONSTRAINT uq_period_dates UNIQUE (entity_id, start_date, end_date),
    CONSTRAINT chk_period_dates CHECK (start_date <= end_date)
);

CREATE INDEX idx_periods_entity ON accounting_periods(entity_id);
CREATE INDEX idx_periods_dates ON accounting_periods(entity_id, start_date, end_date);

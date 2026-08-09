-- Project.md Domain 1 (Financial Operations) — Budgeting module, workplan.md Phase 1 item 1.
-- Budget (header) + BudgetLine (per-account, per-period amount) — mirrors the invoices/
-- invoice_lines header+lines shape. A budget never posts to the ledger; it is a planning
-- artifact compared against actual ledger activity at read time (BudgetService variance report).
CREATE TABLE budgets (
    id UUID PRIMARY KEY NOT NULL,
    entity_id UUID NOT NULL,
    period_id UUID,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(20,6) NOT NULL DEFAULT 0,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    modified_at TIMESTAMPTZ NOT NULL,
    modified_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT
);

CREATE INDEX idx_budgets_entity_id ON budgets(entity_id);
CREATE INDEX idx_budgets_entity_status ON budgets(entity_id, status);

CREATE TABLE budget_lines (
    id UUID PRIMARY KEY NOT NULL,
    budget_id UUID NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    period_id UUID NOT NULL,
    amount NUMERIC(20,6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_budget_lines_budget_account_period UNIQUE (budget_id, account_id, period_id)
);

CREATE INDEX idx_budget_lines_budget_id ON budget_lines(budget_id);
CREATE INDEX idx_budget_lines_account_period ON budget_lines(account_id, period_id);

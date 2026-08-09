-- Project.md Domain 1 (Financial Operations) — Expense Management (T&E), workplan.md Phase 1 item 3.
-- ExpenseClaim (header) + ExpenseClaimLine (per-expense-item line) — mirrors the invoices/
-- invoice_lines and budgets/budget_lines header+lines shape. Unlike Budgeting, an approved claim
-- DOES post a journal entry (reimbursement liability) — see ExpenseClaimService.approve.
CREATE TABLE expense_claims (
    id UUID PRIMARY KEY NOT NULL,
    entity_id UUID NOT NULL,
    period_id UUID,
    employee_id UUID NOT NULL,
    claim_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(20,6) NOT NULL DEFAULT 0,
    notes TEXT,
    journal_entry_id UUID,
    rejection_reason TEXT,
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

CREATE INDEX idx_expense_claims_entity_id ON expense_claims(entity_id);
CREATE INDEX idx_expense_claims_entity_status ON expense_claims(entity_id, status);
CREATE INDEX idx_expense_claims_entity_employee ON expense_claims(entity_id, employee_id);

CREATE TABLE expense_claim_lines (
    id UUID PRIMARY KEY NOT NULL,
    claim_id UUID NOT NULL REFERENCES expense_claims(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    amount NUMERIC(20,6) NOT NULL,
    date_incurred DATE NOT NULL,
    receipt_reference VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_expense_claim_lines_claim_id ON expense_claim_lines(claim_id);
CREATE INDEX idx_expense_claim_lines_account_id ON expense_claim_lines(account_id);

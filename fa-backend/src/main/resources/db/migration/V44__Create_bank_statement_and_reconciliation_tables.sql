-- Project.md Domain 1 (Financial Operations) — Cash & Bank Management module, workplan.md Phase 1
-- item 2. Bank statement import (header + lines, mirrors budgets/budget_lines) + a match table
-- linking a statement line to one or more existing ledger_entries rows for the same GL account.
-- Never posts a journal entry — reconciliation compares existing GL activity against an imported
-- statement at read time (BankStatementService.reconciliationSummary), same non-posting philosophy
-- as the Budgeting module.
CREATE TABLE bank_statement_imports (
    id UUID PRIMARY KEY NOT NULL,
    entity_id UUID NOT NULL,
    period_id UUID,
    account_id UUID NOT NULL,
    statement_date DATE NOT NULL,
    opening_balance NUMERIC(20,6) NOT NULL,
    closing_balance NUMERIC(20,6) NOT NULL,
    notes TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    created_by UUID,
    modified_at TIMESTAMPTZ NOT NULL,
    modified_by UUID,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMPTZ,
    deactivated_by UUID,
    deactivation_reason TEXT,
    -- Cheap defensive de-dupe: re-importing the exact same statement (same account/date/closing
    -- balance) is rejected rather than silently creating duplicate reconciliation lines. Not a
    -- substitute for real CSV-import de-duplication (out of scope — see module handover notes).
    CONSTRAINT uq_bank_stmt_import_dedupe UNIQUE (entity_id, account_id, statement_date, closing_balance)
);

CREATE INDEX idx_bank_stmt_imports_entity_id ON bank_statement_imports(entity_id);
CREATE INDEX idx_bank_stmt_imports_account_id ON bank_statement_imports(account_id);

CREATE TABLE bank_statement_lines (
    id UUID PRIMARY KEY NOT NULL,
    bank_statement_import_id UUID NOT NULL REFERENCES bank_statement_imports(id) ON DELETE CASCADE,
    trans_date DATE NOT NULL,
    description VARCHAR(500) NOT NULL,
    -- Signed convention: positive = deposit/credit to the bank (increases the bank balance),
    -- negative = withdrawal/debit from the bank (decreases the bank balance). This is the same
    -- sign a real bank statement uses in a single "amount" column, and it deliberately matches
    -- (functional_debit - functional_credit) on ledger_entries for a DEBIT-normal cash/bank
    -- account, so a bank line and its matched ledger entries are directly comparable without a
    -- sign-flip anywhere in BankStatementService.
    amount NUMERIC(20,6) NOT NULL,
    reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
    ignore_reason TEXT,
    ignored_at TIMESTAMPTZ,
    ignored_by UUID,
    created_at TIMESTAMPTZ NOT NULL,
    modified_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_bank_stmt_lines_import_id ON bank_statement_lines(bank_statement_import_id);
CREATE INDEX idx_bank_stmt_lines_status ON bank_statement_lines(status);

CREATE TABLE bank_line_matches (
    id UUID PRIMARY KEY NOT NULL,
    bank_statement_line_id UUID NOT NULL REFERENCES bank_statement_lines(id) ON DELETE CASCADE,
    ledger_entry_id UUID NOT NULL,
    match_type VARCHAR(10) NOT NULL,
    matched_at TIMESTAMPTZ NOT NULL,
    matched_by UUID,
    -- A given ledger entry may only ever be matched to one bank statement line at a time —
    -- enforced here (not just in BankStatementService) so a race between two concurrent match
    -- requests can't double-match the same GL entry.
    CONSTRAINT uq_bank_line_match_ledger_entry UNIQUE (ledger_entry_id)
);

CREATE INDEX idx_bank_line_matches_line_id ON bank_line_matches(bank_statement_line_id);
CREATE INDEX idx_bank_line_matches_ledger_entry_id ON bank_line_matches(ledger_entry_id);

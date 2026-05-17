-- §5.1 — Rename ledger entry columns for functional consistency
ALTER TABLE ledger_entries RENAME COLUMN debit_amount TO functional_debit;
ALTER TABLE ledger_entries RENAME COLUMN credit_amount TO functional_credit;

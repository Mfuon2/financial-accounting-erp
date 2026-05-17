-- §10.1 — Add IFRS Category to Accounts
ALTER TABLE accounts 
    ADD COLUMN ifrs_category VARCHAR(50) NOT NULL DEFAULT 'OPERATING_EXPENSES';

CREATE INDEX idx_accounts_ifrs_category ON accounts(ifrs_category);

-- Add is_header flag to accounts (IAS 1 §29 — header/summary accounts block direct posting)
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS is_header BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill: any account referenced as a parent_account_id is a header account
UPDATE accounts
SET    is_header = TRUE
WHERE  id IN (
    SELECT DISTINCT parent_account_id
    FROM   accounts
    WHERE  parent_account_id IS NOT NULL
);

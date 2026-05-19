-- BUG-17: Add CURRENT_DEFERRED_REVENUE account (2-1500) to the default COA.
-- This account is required for IFRS 15 OVER_TIME revenue recognition.
-- Inserts the account for every entity that already has the 2-1000 (Current Liabilities)
-- parent account but does not yet have a 2-1500 account.

INSERT INTO accounts (
    id,
    entity_id,
    account_code,
    account_name,
    account_type,
    account_subtype,
    normal_balance,
    ifrs_category,
    parent_account_id,
    is_header,
    is_temporary,
    is_active,
    currency_code,
    created_at,
    modified_at
)
SELECT
    gen_random_uuid(),
    parent.entity_id,
    '2-1500',
    'Deferred Revenue',
    'LIABILITY',
    'CURRENT_DEFERRED_REVENUE',
    'CREDIT',
    'CURRENT_LIABILITIES',
    parent.id,
    FALSE,
    FALSE,
    TRUE,
    COALESCE(parent.currency_code, 'KES'),
    now(),
    now()
FROM accounts parent
WHERE parent.account_code = '2-1000'
  AND NOT EXISTS (
      SELECT 1 FROM accounts child
      WHERE child.entity_id    = parent.entity_id
        AND child.account_code = '2-1500'
  );

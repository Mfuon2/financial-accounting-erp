-- BUG-13: Accumulated depreciation accounts were created with subtype NON_CURRENT_PPE
-- (ASSET, DEBIT-normal) instead of ACCUMULATED_DEPRECIATION (ASSET, CREDIT-normal).
-- This caused them to appear as investing cash outflows in the indirect cash flow statement.
-- Fix: set the correct subtype and normal balance for all accounts whose name indicates
-- accumulated depreciation or amortisation.

UPDATE accounts
SET account_subtype  = 'ACCUMULATED_DEPRECIATION',
    normal_balance   = 'CREDIT',
    modified_at      = now()
WHERE account_subtype = 'NON_CURRENT_PPE'
  AND (
      account_name ILIKE '%Accumulated Depreciation%'
   OR account_name ILIKE '%Accum. Dep%'
   OR account_name ILIKE '%Accumulated Amortisation%'
   OR account_name ILIKE '%Accum. Amort%'
  );

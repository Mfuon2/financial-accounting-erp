-- BUG-08: Account "Tax Receivables" (typically code 1-1220) was incorrectly typed as
-- LIABILITY with CREDIT normal balance. Tax receivables (e.g. VAT input credits refundable
-- from the tax authority) are current assets with DEBIT normal balance.

UPDATE accounts
SET account_type     = 'ASSET',
    account_subtype  = 'CURRENT_RECEIVABLE',
    normal_balance   = 'DEBIT',
    ifrs_category    = 'CURRENT_ASSETS',
    modified_at      = now()
WHERE account_type    = 'LIABILITY'
  AND (
      account_name ILIKE '%Tax Receivable%'
   OR account_name ILIKE '%VAT Receivable%'
   OR account_name ILIKE '%VAT Input%'
   OR account_name ILIKE '%Input Tax%'
  )
  AND ifrs_category IN ('CURRENT_ASSETS', 'NON_CURRENT_ASSETS');

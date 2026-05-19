ALTER TABLE tax_codes ADD COLUMN IF NOT EXISTS name         VARCHAR(100);
ALTER TABLE tax_codes ADD COLUMN IF NOT EXISTS tax_type     VARCHAR(20);
ALTER TABLE tax_codes ADD COLUMN IF NOT EXISTS account_code VARCHAR(20);

-- Backfill name from description where description is short (≤ 50 chars) and no name set
UPDATE tax_codes SET name = description WHERE name IS NULL AND description IS NOT NULL AND LENGTH(description) <= 50;

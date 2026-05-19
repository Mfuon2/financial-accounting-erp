-- Add symbol and decimals columns to currencies table
-- symbol: optional display symbol (e.g. KSh, $, €)
-- decimals: ISO 4217 minor unit decimal places (e.g. 2 for KES/USD, 0 for UGX, 3 for KWD)
ALTER TABLE currencies
    ADD COLUMN IF NOT EXISTS symbol    VARCHAR(10)  NULL,
    ADD COLUMN IF NOT EXISTS decimals  INTEGER      NOT NULL DEFAULT 2;

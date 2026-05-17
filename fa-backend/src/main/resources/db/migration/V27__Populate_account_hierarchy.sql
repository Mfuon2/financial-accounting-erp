-- Infer parent_account_id from account code structure for X-ABCD format accounts.
-- Algorithm: for suffix ABCD, try to find parent by progressively zeroing trailing digits.
-- Falls back to root (X-0000) if intermediate parents don't exist.

DO $$
DECLARE
  r       RECORD;
  prefix  TEXT;
  suffix  TEXT;
  sn      INT;
  d1 CHAR; d2 CHAR; d3 CHAR; d4 CHAR;
  pid     UUID;
  pc      TEXT;
BEGIN
  FOR r IN
    SELECT id, entity_id, account_code
    FROM accounts
    WHERE parent_account_id IS NULL
      AND account_code ~ '^\d-\d{4}$'
    ORDER BY entity_id, account_code
  LOOP
    prefix := split_part(r.account_code, '-', 1);
    suffix := split_part(r.account_code, '-', 2);
    sn     := suffix::INT;

    CONTINUE WHEN sn = 0; -- root account, no parent

    d1 := SUBSTRING(suffix, 1, 1);
    d2 := SUBSTRING(suffix, 2, 1);
    d3 := SUBSTRING(suffix, 3, 1);
    d4 := SUBSTRING(suffix, 4, 1);

    pid := NULL;

    IF d4 != '0' THEN
      -- Leaf ABCD → try ABC0, then AB00, then A000, then 0000
      pc := prefix || '-' || d1 || d2 || d3 || '0';
      SELECT id INTO pid FROM accounts WHERE entity_id = r.entity_id AND account_code = pc LIMIT 1;
    END IF;

    IF pid IS NULL AND (d4 != '0' OR d3 != '0') AND d3 != '0' THEN
      -- ABC0 or fallback → try AB00
      pc := prefix || '-' || d1 || d2 || '00';
      SELECT id INTO pid FROM accounts WHERE entity_id = r.entity_id AND account_code = pc LIMIT 1;
    END IF;

    IF pid IS NULL AND d2 != '0' THEN
      -- AB00 or fallback → try A000
      pc := prefix || '-' || d1 || '000';
      SELECT id INTO pid FROM accounts WHERE entity_id = r.entity_id AND account_code = pc LIMIT 1;
    END IF;

    IF pid IS NULL AND d1 != '0' THEN
      -- Final fallback → X-0000 root
      pc := prefix || '-0000';
      SELECT id INTO pid FROM accounts WHERE entity_id = r.entity_id AND account_code = pc LIMIT 1;
    END IF;

    IF pid IS NOT NULL THEN
      UPDATE accounts SET parent_account_id = pid WHERE id = r.id;
    END IF;

  END LOOP;
END $$;

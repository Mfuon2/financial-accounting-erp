-- Seed code_sequences from existing customer/supplier codes so peek() returns
-- the correct next value instead of always starting from 1.

INSERT INTO code_sequences (id, entity_id, prefix, year, last_seq)
SELECT
    gen_random_uuid(),
    entity_id,
    'CU',
    NULL,
    MAX(CAST(SUBSTRING(customer_code FROM 3) AS INTEGER))
FROM customers
WHERE customer_code ~ '^CU[0-9]+$'
GROUP BY entity_id
ON CONFLICT (entity_id, prefix, year) DO UPDATE
    SET last_seq = GREATEST(code_sequences.last_seq, EXCLUDED.last_seq);

INSERT INTO code_sequences (id, entity_id, prefix, year, last_seq)
SELECT
    gen_random_uuid(),
    entity_id,
    'SUPP',
    NULL,
    MAX(CAST(SUBSTRING(supplier_code FROM 5) AS INTEGER))
FROM suppliers
WHERE supplier_code ~ '^SUPP[0-9]+$'
GROUP BY entity_id
ON CONFLICT (entity_id, prefix, year) DO UPDATE
    SET last_seq = GREATEST(code_sequences.last_seq, EXCLUDED.last_seq);

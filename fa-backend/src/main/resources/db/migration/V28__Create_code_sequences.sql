-- §15 — Atomic sequence table for all auto-generated business codes.
-- Each (entity_id, prefix, year) triple has its own counter.
-- year is NULL for master-data codes that don't reset annually (customers, suppliers, assets).
-- year is set to the calendar year for transactional codes (invoices, JEs, bills, etc.).
CREATE TABLE code_sequences (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_id  UUID         NOT NULL,
    prefix     VARCHAR(20)  NOT NULL,
    year       INT,
    last_seq   INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_code_sequences UNIQUE (entity_id, prefix, year)
);

CREATE INDEX idx_code_sequences_lookup ON code_sequences (entity_id, prefix, year);

ALTER TABLE journal_entries ADD COLUMN IF NOT EXISTS reference VARCHAR(30);
CREATE INDEX IF NOT EXISTS idx_journal_entries_reference ON journal_entries (entity_id, reference);

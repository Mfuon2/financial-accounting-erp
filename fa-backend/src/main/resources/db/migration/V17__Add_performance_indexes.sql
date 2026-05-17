-- ============================================================
-- V17: Performance indexes for high-frequency query paths
-- ============================================================

-- Journal entries: most common query is by entity + status + date
CREATE INDEX IF NOT EXISTS idx_journal_entries_entity_status
    ON journal_entries(entity_id, status);
CREATE INDEX IF NOT EXISTS idx_journal_entries_entity_date
    ON journal_entries(entity_id, trans_date);
CREATE INDEX IF NOT EXISTS idx_journal_entries_source
    ON journal_entries(source_type, source_id)
    WHERE source_id IS NOT NULL;

-- Journal entry lines: frequently joined back to journal entries
CREATE INDEX IF NOT EXISTS idx_journal_entry_lines_journal_id
    ON journal_entry_lines(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_journal_entry_lines_account_id
    ON journal_entry_lines(account_id);

-- Ledger entries: primary query path is account + date range
CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_date
    ON ledger_entries(account_id, trans_date);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_entity_date
    ON ledger_entries(entity_id, trans_date);

-- Accounts: most lookups are by entity + account code
CREATE INDEX IF NOT EXISTS idx_accounts_entity_active
    ON accounts(entity_id, is_active);
CREATE INDEX IF NOT EXISTS idx_accounts_entity_type
    ON accounts(entity_id, account_type);
CREATE INDEX IF NOT EXISTS idx_accounts_entity_subtype
    ON accounts(entity_id, account_subtype);
CREATE INDEX IF NOT EXISTS idx_accounts_entity_ifrs_category
    ON accounts(entity_id, ifrs_category);
CREATE INDEX IF NOT EXISTS idx_accounts_parent
    ON accounts(parent_account_id)
    WHERE parent_account_id IS NOT NULL;

-- Periods: lookup by entity and date
CREATE INDEX IF NOT EXISTS idx_periods_entity_status
    ON accounting_periods(entity_id, status);
CREATE INDEX IF NOT EXISTS idx_periods_entity_dates
    ON accounting_periods(entity_id, start_date, end_date);

-- Source documents: entity + status + type
CREATE INDEX IF NOT EXISTS idx_source_docs_entity_status
    ON source_documents(entity_id, status);
CREATE INDEX IF NOT EXISTS idx_source_docs_entity_type
    ON source_documents(entity_id, type);

-- Customers: name search
CREATE INDEX IF NOT EXISTS idx_customers_entity_active
    ON customers(entity_id, is_active);
CREATE INDEX IF NOT EXISTS idx_customers_name
    ON customers(entity_id, name);

-- Suppliers: same pattern
CREATE INDEX IF NOT EXISTS idx_suppliers_entity_active
    ON suppliers(entity_id, is_active);

-- Invoices: key reporting queries
CREATE INDEX IF NOT EXISTS idx_invoices_entity_customer
    ON invoices(entity_id, customer_id);
CREATE INDEX IF NOT EXISTS idx_invoices_entity_status
    ON invoices(entity_id, status);
CREATE INDEX IF NOT EXISTS idx_invoices_entity_dates
    ON invoices(entity_id, issue_date, due_date);
CREATE INDEX IF NOT EXISTS idx_invoices_entity_overdue
    ON invoices(entity_id, due_date, status)
    WHERE status NOT IN ('PAID', 'VOID', 'CREDIT_NOTE');

-- Payments: by status, customer, date
-- Table: payments (confirmed in V15__Create_payments_table.sql)
CREATE INDEX IF NOT EXISTS idx_payments_entity_date
    ON payments(entity_id, payment_date);

-- Audit logs: forensic queries by resource
-- Note: column is user_id (not performed_by) per V9 schema
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource
    ON audit_logs(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_action
    ON audit_logs(entity_id, action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id
    ON audit_logs(user_id);

-- Exchange rates: lookup by currency pair and date
CREATE INDEX IF NOT EXISTS idx_exchange_rates_lookup
    ON exchange_rates(entity_id, from_currency, to_currency, rate_date);

-- Fixed assets: by entity and status
CREATE INDEX IF NOT EXISTS idx_fixed_assets_entity_status
    ON fixed_assets(entity_id, status);

-- Tax codes and rates
CREATE INDEX IF NOT EXISTS idx_tax_rates_code_date
    ON tax_rates(tax_code_id, effective_from);

-- Idempotency keys: TTL expiry cleanup
CREATE INDEX IF NOT EXISTS idx_idempotency_ttl
    ON idempotency_keys(ttl_expires_at);

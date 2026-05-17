-- V5__Add_account_balance_columns.sql
ALTER TABLE accounts ADD COLUMN total_debits DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN total_credits DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN current_balance DECIMAL(20,6) NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD COLUMN original_currency_balance DECIMAL(20,6) NOT NULL DEFAULT 0;

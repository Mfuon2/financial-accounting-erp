-- AP Module: FX support on bills, payment method and cash account on bill_payments

ALTER TABLE bills
    ADD COLUMN exchange_rate      NUMERIC(19,6) NOT NULL DEFAULT 1.000000,
    ADD COLUMN functional_amount  NUMERIC(19,6) NOT NULL DEFAULT 0;

ALTER TABLE bill_payments
    ADD COLUMN payment_method   VARCHAR(20),
    ADD COLUMN cash_account_id  UUID;

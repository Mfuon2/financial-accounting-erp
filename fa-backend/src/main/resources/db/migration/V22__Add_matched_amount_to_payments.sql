-- D9: Add matched_amount column to payments table.
-- Stores the amount matched/applied to the linked invoice during matchToInvoice().
-- Null until a payment is matched; used in postPayment() to correctly apply partial amounts.
ALTER TABLE payments ADD COLUMN IF NOT EXISTS matched_amount DECIMAL(20,6);

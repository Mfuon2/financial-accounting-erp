-- V40__Add_period_close_audit_fields.sql
-- BUG-30: Capture who closed a period and when, for full audit trail.

ALTER TABLE accounting_periods
    ADD COLUMN closed_by_user_id UUID,
    ADD COLUMN closed_at         TIMESTAMP WITH TIME ZONE;

COMMENT ON COLUMN accounting_periods.closed_by_user_id IS 'User ID of the person who transitioned this period to CLOSED status';
COMMENT ON COLUMN accounting_periods.closed_at         IS 'Timestamp when the period was transitioned to CLOSED status';

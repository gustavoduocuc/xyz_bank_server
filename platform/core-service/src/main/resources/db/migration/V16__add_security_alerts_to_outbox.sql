-- Security alerts belong to a customer, not to an account, and every outbox event can name its customer
ALTER TABLE outbox_events ALTER COLUMN account_id DROP NOT NULL;

ALTER TABLE outbox_events ADD COLUMN customer_id UUID;
ALTER TABLE outbox_events ADD COLUMN alert_type VARCHAR(32);
ALTER TABLE outbox_events ADD COLUMN occurred_at TIMESTAMPTZ;

ALTER TABLE outbox_events ADD COLUMN reason VARCHAR(512);

ALTER TABLE outbox_events DROP CONSTRAINT outbox_events_account_id_fkey;

ALTER TABLE outbox_events ALTER COLUMN amount DROP NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN currency DROP NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN interest_rate DROP NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN opening_balance DROP NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN closing_balance DROP NOT NULL;
ALTER TABLE outbox_events ALTER COLUMN occurred_on DROP NOT NULL;

ALTER TABLE outbox_events ALTER COLUMN period DROP NOT NULL;

ALTER TABLE outbox_events ADD COLUMN movement_type VARCHAR(32);

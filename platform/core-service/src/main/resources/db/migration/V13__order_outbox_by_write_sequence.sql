-- The relay must publish in the order events were written, not in the order of their date and
-- random UUID, so that the events of one account reach Kafka in the order they were confirmed.
-- Rows that already exist are numbered by PostgreSQL when the column is added.
ALTER TABLE outbox_events ADD COLUMN seq BIGINT GENERATED ALWAYS AS IDENTITY;

CREATE INDEX idx_outbox_events_unpublished_seq ON outbox_events (published, seq);

CREATE TABLE interest_calculations (
    event_id   VARCHAR(128)   PRIMARY KEY,
    account_id VARCHAR(64)    NOT NULL,
    period     INT            NOT NULL,
    amount     NUMERIC(19, 2) NOT NULL,
    currency   VARCHAR(3)     NOT NULL,
    status     VARCHAR(16)    NOT NULL CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED')),
    reason     VARCHAR(512)
);

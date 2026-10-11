CREATE TABLE customers (
    id UUID PRIMARY KEY,
    full_name VARCHAR(200) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30),
    address VARCHAR(300),
    version BIGINT NOT NULL DEFAULT 0,
    -- Idempotency-Key of the POST that created the row; repeating it returns this customer
    idempotency_key VARCHAR(100) UNIQUE
);

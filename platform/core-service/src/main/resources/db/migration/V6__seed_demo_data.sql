INSERT INTO customers (id, full_name, email)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Ana Perez',
    'ana.perez@xyzbank.cl'
);

INSERT INTO accounts (
    id,
    account_number,
    customer_id,
    balance,
    currency,
    version,
    daily_withdrawn_amount,
    daily_withdrawn_date
)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    '1000000001',
    '11111111-1111-1111-1111-111111111111',
    1500.00,
    'USD',
    0,
    0.00,
    NULL
);

INSERT INTO transactions (id, account_id, type, amount, currency, occurred_on, description, idempotency_key)
VALUES
    (
        '33333333-3333-3333-3333-333333333333',
        '22222222-2222-2222-2222-222222222222',
        'CREDIT',
        2000.00,
        'USD',
        '2026-01-02',
        'Payroll deposit',
        NULL
    ),
    (
        '44444444-4444-4444-4444-444444444444',
        '22222222-2222-2222-2222-222222222222',
        'DEBIT',
        350.00,
        'USD',
        '2026-01-05',
        'Card purchase',
        NULL
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        '22222222-2222-2222-2222-222222222222',
        'DEBIT',
        150.00,
        'USD',
        '2026-01-08',
        'ATM withdrawal',
        NULL
    );

INSERT INTO annual_interest_summaries (
    id,
    account_id,
    year,
    opening_balance,
    closing_balance,
    interest_rate,
    interest_amount,
    currency
)
VALUES (
    '66666666-6666-6666-6666-666666666666',
    '22222222-2222-2222-2222-222222222222',
    2025,
    1000.00,
    1035.00,
    0.0350,
    35.00,
    'USD'
);

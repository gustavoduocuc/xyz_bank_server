CREATE TABLE IF NOT EXISTS migration_executions (
    job_name VARCHAR(100) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    executed_at TIMESTAMP NOT NULL,
    write_count INT NULL,
    skip_count INT NULL
);

CREATE TABLE IF NOT EXISTS daily_transaction_reports (
    transaction_id VARCHAR(50) PRIMARY KEY,
    transaction_date DATE NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    anomalies VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS account_balances (
    account_id VARCHAR(50) PRIMARY KEY,
    account_name VARCHAR(120) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    age INT NOT NULL,
    previous_balance DECIMAL(14, 2) NOT NULL,
    interest_rate DECIMAL(8, 4) NOT NULL,
    final_balance DECIMAL(14, 2) NOT NULL
);

CREATE TABLE IF NOT EXISTS annual_audit_reports (
    account_id VARCHAR(50) PRIMARY KEY,
    total_deposits DECIMAL(14, 2) NOT NULL,
    total_withdrawals DECIMAL(14, 2) NOT NULL,
    net_balance DECIMAL(14, 2) NOT NULL,
    movement_count INT NOT NULL
);

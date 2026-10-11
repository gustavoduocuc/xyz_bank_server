-- Revert all business migration data and ledger entries.
-- Usage:
--   docker compose exec -T mysql mysql -umigration -pmigration xyz_bank_migration < scripts/revert-migration.sql

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE daily_transaction_lines;
TRUNCATE TABLE daily_transaction_reports;
TRUNCATE TABLE daily_transaction_summaries;
TRUNCATE TABLE account_balances;
TRUNCATE TABLE annual_movements;
TRUNCATE TABLE annual_audit_reports;
DELETE FROM migration_executions;

-- Optional: clear Spring Batch metadata so job instances can be relaunched cleanly.
DELETE FROM BATCH_STEP_EXECUTION_CONTEXT;
DELETE FROM BATCH_STEP_EXECUTION;
DELETE FROM BATCH_JOB_EXECUTION_CONTEXT;
DELETE FROM BATCH_JOB_EXECUTION_PARAMS;
DELETE FROM BATCH_JOB_EXECUTION;
DELETE FROM BATCH_JOB_INSTANCE;

SET FOREIGN_KEY_CHECKS = 1;

-- Per-job examples (commented):
-- DELETE FROM daily_transaction_lines;
-- DELETE FROM daily_transaction_reports;
-- DELETE FROM daily_transaction_summaries;
-- DELETE FROM migration_executions WHERE job_name = 'dailyTransactionsJob';
--
-- DELETE FROM account_balances;
-- DELETE FROM migration_executions WHERE job_name = 'monthlyInterestsJob';
--
-- DELETE FROM annual_movements;
-- DELETE FROM annual_audit_reports;
-- DELETE FROM migration_executions WHERE job_name = 'annualGenerationJob';

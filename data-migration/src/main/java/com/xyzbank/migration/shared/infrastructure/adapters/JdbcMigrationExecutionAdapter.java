package com.xyzbank.migration.shared.infrastructure.adapters;

import com.xyzbank.migration.shared.application.ports.MigrationExecutionPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

public class JdbcMigrationExecutionAdapter implements MigrationExecutionPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMigrationExecutionAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean hasSuccessfulExecution(String jobName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM migration_executions
                        WHERE job_name = ? AND status = 'SUCCESS'
                        """,
                Integer.class,
                jobName
        );
        return count != null && count > 0;
    }

    @Override
    public void markSuccess(String jobName, int writeCount, int skipCount) {
        Timestamp executedAt = Timestamp.from(Instant.now());
        if (exists(jobName)) {
            jdbcTemplate.update(
                    """
                            UPDATE migration_executions
                            SET status = 'SUCCESS', executed_at = ?, write_count = ?, skip_count = ?
                            WHERE job_name = ?
                            """,
                    executedAt,
                    writeCount,
                    skipCount,
                    jobName
            );
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO migration_executions (job_name, status, executed_at, write_count, skip_count)
                        VALUES (?, 'SUCCESS', ?, ?, ?)
                        """,
                jobName,
                executedAt,
                writeCount,
                skipCount
        );
    }

    @Override
    public void markFailed(String jobName) {
        Timestamp executedAt = Timestamp.from(Instant.now());
        if (exists(jobName)) {
            jdbcTemplate.update(
                    """
                            UPDATE migration_executions
                            SET status = 'FAILED', executed_at = ?, write_count = NULL, skip_count = NULL
                            WHERE job_name = ?
                            """,
                    executedAt,
                    jobName
            );
            return;
        }
        jdbcTemplate.update(
                """
                        INSERT INTO migration_executions (job_name, status, executed_at, write_count, skip_count)
                        VALUES (?, 'FAILED', ?, NULL, NULL)
                        """,
                jobName,
                executedAt
        );
    }

    private boolean exists(String jobName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM migration_executions WHERE job_name = ?",
                Integer.class,
                jobName
        );
        return count != null && count > 0;
    }
}

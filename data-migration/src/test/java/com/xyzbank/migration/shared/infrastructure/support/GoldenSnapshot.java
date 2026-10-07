package com.xyzbank.migration.shared.infrastructure.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a query result as CSV so migrated tables can be compared against
 * versioned golden files under src/test/resources/expected/.
 */
public final class GoldenSnapshot {

    public static final Path expectedRoot = Path.of("src/test/resources/expected");

    public static final String dailyTransactionReports = """
            SELECT transaction_id, transaction_date, amount, transaction_type, anomalies
            FROM daily_transaction_reports
            ORDER BY CAST(transaction_id AS UNSIGNED), transaction_id
            """;

    public static final String accountBalances = """
            SELECT account_id, account_name, account_type, age, previous_balance, interest_rate, final_balance
            FROM account_balances
            ORDER BY CAST(account_id AS UNSIGNED), account_id
            """;

    public static final String annualAuditReports = """
            SELECT account_id, total_deposits, total_withdrawals, net_balance, movement_count
            FROM annual_audit_reports
            ORDER BY CAST(account_id AS UNSIGNED), account_id
            """;

    public static final String dailyTransactionSummaries = """
            SELECT summary_date, total_debits, total_credits, transaction_count, anomaly_count
            FROM daily_transaction_summaries
            ORDER BY summary_date
            """;

    public static final String counts = """
            SELECT 'daily_transaction_reports' AS table_name, COUNT(*) AS row_count FROM daily_transaction_reports
            UNION ALL SELECT 'account_balances', COUNT(*) FROM account_balances
            UNION ALL SELECT 'annual_audit_reports', COUNT(*) FROM annual_audit_reports
            """;

    private GoldenSnapshot() {
    }

    public static String of(JdbcTemplate jdbcTemplate, String sql) {
        List<String> lines = new ArrayList<>();
        jdbcTemplate.query(sql, resultSet -> {
            ResultSetMetaData metadata = resultSet.getMetaData();
            int columns = metadata.getColumnCount();
            if (lines.isEmpty()) {
                List<String> header = new ArrayList<>();
                for (int column = 1; column <= columns; column++) {
                    header.add(metadata.getColumnLabel(column));
                }
                lines.add(String.join(",", header));
            }
            List<String> values = new ArrayList<>();
            for (int column = 1; column <= columns; column++) {
                values.add(render(resultSet.getObject(column)));
            }
            lines.add(String.join(",", values));
        });
        return String.join("\n", lines) + "\n";
    }

    public static String expected(String dataSet, String name) {
        try {
            return Files.readString(expectedRoot.resolve(dataSet).resolve(name + ".csv"), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static void write(String dataSet, String name, String content) {
        try {
            Path target = expectedRoot.resolve(dataSet).resolve(name + ".csv");
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String render(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}

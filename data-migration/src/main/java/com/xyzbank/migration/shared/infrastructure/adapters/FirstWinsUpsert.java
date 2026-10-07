package com.xyzbank.migration.shared.infrastructure.adapters;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds an idempotent MySQL upsert where, for the same key, the row read from the
 * lowest CSV line wins. Rewriting a row with the same source line changes nothing,
 * so reprocessing a chunk, a partition or a whole file converges to one result.
 */
public final class FirstWinsUpsert {

    private static final String sourceLine = "source_line";
    private static final String incoming = "incoming";

    private FirstWinsUpsert() {
    }

    /**
     * @param table          target table, which must have a {@code source_line} column
     * @param keyColumn      the primary key column
     * @param keyExpression  SQL for the key value, e.g. {@code ?} or {@code SHA2(?, 256)}
     * @param valueColumns   the remaining columns, bound in order after the key; {@code source_line} is bound last
     */
    public static String statement(String table, String keyColumn, String keyExpression, List<String> valueColumns) {
        List<String> columns = new ArrayList<>();
        columns.add(keyColumn);
        columns.addAll(valueColumns);
        columns.add(sourceLine);

        List<String> placeholders = new ArrayList<>();
        placeholders.add(keyExpression);
        valueColumns.forEach(column -> placeholders.add("?"));
        placeholders.add("?");

        return """
                INSERT INTO %s (%s)
                VALUES (%s) AS %s
                %s""".formatted(
                table,
                String.join(", ", columns),
                String.join(", ", placeholders),
                incoming,
                onDuplicateKeyUpdate(table, valueColumns));
    }

    /**
     * The {@code ON DUPLICATE KEY UPDATE} clause alone, for an {@code INSERT … SELECT}
     * whose derived table is aliased {@code incoming} and exposes {@code source_line}.
     */
    public static String onDuplicateKeyUpdate(String table, List<String> valueColumns) {
        String assignments = valueColumns.stream()
                .map(column -> "%1$s = IF(%2$s.%3$s < %4$s.%3$s, %2$s.%1$s, %4$s.%1$s)"
                        .formatted(column, incoming, sourceLine, table))
                .collect(Collectors.joining(",\n    "));
        // source_line goes last: MySQL applies the assignments left to right.
        return """
                ON DUPLICATE KEY UPDATE
                    %s,
                    %s = LEAST(%s.%s, %s.%s)
                """.formatted(assignments, sourceLine, table, sourceLine, incoming, sourceLine);
    }
}

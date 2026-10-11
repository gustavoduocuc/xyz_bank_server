package com.xyzbank.migration.shared.infrastructure.adapters;

import com.xyzbank.migration.shared.infrastructure.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "migration.run-all=false")
@DisplayName("The migration schema")
class TheMigrationSchemaIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Creates the staging and summary tables
     * 2. Can be applied again over an existing database without losing rows
     */

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("creates the staging and summary tables")
    void createsTheStagingAndSummaryTables() {
        for (String table : List.of("daily_transaction_lines", "daily_transaction_summaries", "annual_movements")) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class,
                    table);
            assertEquals(1, found, table);
        }
    }

    @Test
    @DisplayName("can be applied again over an existing database without losing rows")
    void canBeAppliedAgainOverAnExistingDatabaseWithoutLosingRows() {
        jdbcTemplate.update("DELETE FROM migration_executions WHERE job_name = 'schemaProbe'");
        jdbcTemplate.update(
                "INSERT INTO migration_executions (job_name, status, executed_at) VALUES ('schemaProbe', 'SUCCESS', NOW())");

        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM migration_executions WHERE job_name = 'schemaProbe'", Integer.class);
        assertEquals(1, rows);
        jdbcTemplate.update("DELETE FROM migration_executions WHERE job_name = 'schemaProbe'");
    }
}

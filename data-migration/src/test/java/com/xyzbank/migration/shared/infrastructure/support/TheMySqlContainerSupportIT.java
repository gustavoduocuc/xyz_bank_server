package com.xyzbank.migration.shared.infrastructure.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties = "migration.run-all=false")
@DisplayName("The shared MySQL container support")
class TheMySqlContainerSupportIT extends MySqlContainerSupport {

    /*
     * Cases:
     * 1. Connects to MySQL with the migration schema applied
     */

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("connects to MySQL with the migration schema applied")
    void connectsToMySqlWithTheMigrationSchemaApplied() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'migration_executions'",
                Integer.class);

        assertEquals(1, tables);
    }
}

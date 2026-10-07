package com.xyzbank.migration.shared.infrastructure.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

/**
 * Base class for integration tests that need a real MySQL 8.4.
 * One container is shared by every test class of the JVM; it is started lazily
 * so that {@code disabledWithoutDocker} can skip the class before any Docker call.
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class MySqlContainerSupport {

    public static final List<String> businessTables = List.of(
            "daily_transaction_lines",
            "daily_transaction_reports",
            "daily_transaction_summaries",
            "account_balances",
            "annual_movements",
            "annual_audit_reports",
            "migration_executions"
    );

    private static MySQLContainer<?> mysql;

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        MySQLContainer<?> container = startedContainer();
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:db/schema.sql");
    }

    public static synchronized MySQLContainer<?> startedContainer() {
        if (mysql == null) {
            mysql = new MySQLContainer<>("mysql:8.4").withReuse(true);
            mysql.start();
        }
        return mysql;
    }

    /**
     * A JdbcTemplate on the shared container, outside any Spring context, with the
     * migration schema applied and every business table empty.
     */
    public static JdbcTemplate freshJdbcTemplate() {
        MySQLContainer<?> container = startedContainer();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String table : businessTables) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
        return jdbcTemplate;
    }

    protected static void clearMigrationData(JdbcTemplate jdbcTemplate) {
        for (String table : businessTables) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of(
                "BATCH_STEP_EXECUTION_CONTEXT",
                "BATCH_STEP_EXECUTION",
                "BATCH_JOB_EXECUTION_CONTEXT",
                "BATCH_JOB_EXECUTION_PARAMS",
                "BATCH_JOB_EXECUTION",
                "BATCH_JOB_INSTANCE")) {
            jdbcTemplate.execute("DELETE FROM " + table);
        }
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }
}

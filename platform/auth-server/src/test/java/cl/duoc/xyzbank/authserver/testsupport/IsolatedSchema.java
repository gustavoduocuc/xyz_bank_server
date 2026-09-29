package cl.duoc.xyzbank.authserver.testsupport;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * A freshly migrated PostgreSQL schema of its own on the shared test container, for
 * repository integration tests that must not see rows written by the running application.
 */
public final class IsolatedSchema {

    private IsolatedSchema() {
    }

    public static JdbcTemplate freshlyMigrated(PostgreSQLContainer<?> postgres, String schema) {
        DataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl() + "&currentSchema=" + schema, postgres.getUsername(), postgres.getPassword());
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas(schema).cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        return new JdbcTemplate(dataSource);
    }
}

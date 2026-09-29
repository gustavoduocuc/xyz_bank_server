package cl.duoc.xyzbank.authserver.testsupport;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for every test that starts the auth-server application: one PostgreSQL container,
 * started once and shared by all Spring contexts in the run (the same pattern core-service's
 * tests use), so Flyway and the JDBC stores run against the real database engine.
 */
@ActiveProfiles("test")
public abstract class AbstractAuthServerIT {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_server")
            .withUsername("auth_server")
            .withPassword("auth_server");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Command-line arguments pointing an application context started by hand (not through
     * {@code @SpringBootTest}) at the shared container.
     */
    protected static String[] datasourceArguments(String... extraArguments) {
        String[] datasource = {
            "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
            "--spring.datasource.username=" + POSTGRES.getUsername(),
            "--spring.datasource.password=" + POSTGRES.getPassword()
        };
        String[] arguments = new String[datasource.length + extraArguments.length];
        System.arraycopy(datasource, 0, arguments, 0, datasource.length);
        System.arraycopy(extraArguments, 0, arguments, datasource.length, extraArguments.length);
        return arguments;
    }
}

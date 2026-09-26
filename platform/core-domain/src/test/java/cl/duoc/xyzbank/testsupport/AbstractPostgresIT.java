package cl.duoc.xyzbank.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // core-service's enforcement filter (service credential + scope + ownership) is
        // gated off by default in production so the change can roll out incrementally.
        // Every test built on this shared base runs with it forced on instead, so no test
        // suite silently exercises the disabled path and calls it coverage; the one
        // dedicated test for the disabled behavior overrides this back to false itself.
        registry.add("security.enforcement.enabled", () -> "true");
    }
}

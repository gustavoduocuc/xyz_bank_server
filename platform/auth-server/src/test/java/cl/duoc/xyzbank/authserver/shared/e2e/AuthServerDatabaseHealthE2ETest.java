package cl.duoc.xyzbank.authserver.shared.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Uses a database container of its own (not the shared one from AbstractAuthServerIT),
 * because the test stops it; the context is discarded afterwards.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext
@DisplayName("The auth-server health and its database")
class AuthServerDatabaseHealthE2ETest {

    /*
     * Cases:
     * 1. Health is UP while the database is reachable, and stops being UP once it is gone,
     *    without exposing component details in either state
     */

    private static final PostgreSQLContainer<?> OWN_POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        OWN_POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OWN_POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", OWN_POSTGRES::getUsername);
        registry.add("spring.datasource.password", OWN_POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "2000");
    }

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("stops reporting UP once its database is gone, without exposing details")
    void stopsReportingUpOnceItsDatabaseIsGone() {
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow(port);
        Response whileReachable = flow.request().get("/actuator/health");

        OWN_POSTGRES.stop();
        Response afterDatabaseLoss = flow.request().get("/actuator/health");

        assertEquals("UP", whileReachable.jsonPath().getString("status"));
        assertNotEquals("UP", afterDatabaseLoss.jsonPath().getString("status"), afterDatabaseLoss.asString());
        assertNull(afterDatabaseLoss.jsonPath().get("components"));
    }
}

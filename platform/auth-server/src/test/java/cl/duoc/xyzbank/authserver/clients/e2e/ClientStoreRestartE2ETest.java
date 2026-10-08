package cl.duoc.xyzbank.authserver.clients.e2e;

import cl.duoc.xyzbank.authserver.AuthServerApplication;
import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.stream.Stream;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_PASSWORD;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.DEMO_USERNAME;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.authorizationRequest;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.newCodeVerifier;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.queryParam;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restarts the application on a schema of its own (not the shared one other tests use),
 * because one case restarts it with a different redirect URI for bff-web.
 */
@DisplayName("The persistent client store across restarts")
class ClientStoreRestartE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Restarting with unchanged configuration keeps one registration per client
     * 2. Restarting with a different redirect URI for bff-web accepts authorization requests
     *    only with the new redirect URI
     */

    private static final String SCHEMA = "client_restart_e2e";
    private static final String MOVED_WEB_REDIRECT_URI = "https://localhost:18081/login/oauth2/code/oidc";

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void startFromAnEmptySchema() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                schemaJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }

    @Test
    @DisplayName("keeps one registration per client when restarted with the same configuration")
    void keepsOneRegistrationPerClientWhenRestartedWithTheSameConfiguration() {
        startAuthServerOnOwnSchema().close();

        startAuthServerOnOwnSchema().close();

        List<String> clientIds = jdbcTemplate.queryForList(
                "SELECT client_id FROM oauth2_registered_client ORDER BY client_id", String.class);
        assertEquals(List.of("accounts-admin", "bff-atm", "bff-mobile", "bff-web", "customers-admin", "interests-service",
                "payments-admin", "payments-service"), clientIds);
    }

    @Test
    @DisplayName("accepts only the new redirect URI after restarting with a changed one")
    void acceptsOnlyTheNewRedirectUriAfterRestartingWithAChangedOne() {
        startAuthServerOnOwnSchema().close();

        Response withOldRedirectUri;
        Response withNewRedirectUri;
        try (ConfigurableApplicationContext restarted =
                startAuthServerOnOwnSchema("--auth.clients.web.redirect-uri=" + MOVED_WEB_REDIRECT_URI)) {
            AuthorizationCodeFlow flow = new AuthorizationCodeFlow(portOf(restarted));
            withOldRedirectUri = flow.authorize(
                    authorizationRequest(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, newCodeVerifier()),
                    DEMO_USERNAME, DEMO_PASSWORD);
            withNewRedirectUri = flow.authorize(
                    authorizationRequest(WEB_CLIENT_ID, MOVED_WEB_REDIRECT_URI, WEB_SCOPES, newCodeVerifier()),
                    DEMO_USERNAME, DEMO_PASSWORD);
        }

        assertEquals(400, withOldRedirectUri.statusCode(), withOldRedirectUri.asString());
        assertNull(withOldRedirectUri.getHeader("Location"));
        String location = withNewRedirectUri.getHeader("Location");
        assertEquals(302, withNewRedirectUri.statusCode(), withNewRedirectUri.asString());
        assertTrue(location.startsWith(MOVED_WEB_REDIRECT_URI), location);
        assertNotNull(queryParam(location, "code"), location);
    }

    private static ConfigurableApplicationContext startAuthServerOnOwnSchema(String... extraArguments) {
        Stream<String> ownSchema = Stream.of(
                "--server.port=0",
                "--spring.datasource.url=" + schemaJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.flyway.schemas=" + SCHEMA);
        return new SpringApplicationBuilder(AuthServerApplication.class)
                .profiles("test")
                .run(Stream.concat(ownSchema, Stream.of(extraArguments)).toArray(String[]::new));
    }

    private static String schemaJdbcUrl() {
        String url = POSTGRES.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
    }
}

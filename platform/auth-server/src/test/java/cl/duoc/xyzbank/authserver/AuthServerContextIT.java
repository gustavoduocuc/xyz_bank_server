package cl.duoc.xyzbank.authserver;

import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyUnavailableException;
import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.ConnectException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The auth-server application context")
class AuthServerContextIT extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Starts with the provisioned signing keystore and exposes exactly that one key
     * 2. Refuses to start when no signing keystore is configured
     * 3. Creates its schema (V1 and V2) through Flyway on startup
     * 4. Refuses to start when its database is unreachable
     */

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("starts with the provisioned signing key as its only key")
    void startsWithTheProvisionedSigningKeyAsItsOnlyKey() throws Exception {
        int keys = jwkSource.get(new JWKSelector(new JWKMatcher.Builder().build()), null).size();

        assertEquals(1, keys);
    }

    @Test
    @DisplayName("refuses to start when no signing keystore is configured")
    void refusesToStartWhenNoSigningKeystoreIsConfigured() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(AuthServerApplication.class)
                .profiles("test");

        Exception exception = assertThrows(
                Exception.class, () -> application.run(datasourceArguments("--server.port=0", "--auth.signing.keystore-path=")));

        assertTrue(hasCause(exception, SigningKeyUnavailableException.class), "unexpected failure: " + exception);
    }

    @Test
    @DisplayName("creates its schema through Flyway on startup")
    void createsItsSchemaThroughFlywayOnStartup() {
        List<String> appliedVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);

        assertTrue(appliedVersions.containsAll(List.of("1", "2")), appliedVersions.toString());
    }

    @Test
    @DisplayName("refuses to start when its database is unreachable")
    void refusesToStartWhenItsDatabaseIsUnreachable() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(AuthServerApplication.class)
                .profiles("test");

        Exception exception = assertThrows(Exception.class, () -> application.run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/auth_server",
                "--spring.datasource.hikari.connection-timeout=2000"));

        assertTrue(hasCause(exception, ConnectException.class), "unexpected failure: " + exception);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}

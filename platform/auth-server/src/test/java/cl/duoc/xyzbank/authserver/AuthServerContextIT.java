package cl.duoc.xyzbank.authserver;

import cl.duoc.xyzbank.authserver.signing.infrastructure.adapters.SigningKeyUnavailableException;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("The auth-server application context")
class AuthServerContextIT {

    /*
     * Cases:
     * 1. Starts with the provisioned signing keystore and exposes exactly that one key
     * 2. Refuses to start when no signing keystore is configured
     */

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

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
                Exception.class, () -> application.run("--server.port=0", "--auth.signing.keystore-path="));

        assertTrue(hasCause(exception, SigningKeyUnavailableException.class), "unexpected failure: " + exception);
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

package cl.duoc.xyzbank.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for core-service tests that start the application: the shared PostgreSQL container
 * plus a JWKS endpoint publishing TestAccessTokens' key, so requests carry tokens verified
 * exactly as auth-server's are in the stack.
 */
public abstract class AbstractCoreServiceIT extends AbstractPostgresIT {

    @DynamicPropertySource
    static void registerTokenVerification(DynamicPropertyRegistry registry) {
        registry.add("auth.jwk-set-uri", TestJwksServer::jwkSetUri);
        registry.add("auth.issuer", () -> TestAccessTokens.ISSUER);
    }
}

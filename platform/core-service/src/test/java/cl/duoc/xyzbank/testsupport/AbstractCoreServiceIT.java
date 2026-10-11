package cl.duoc.xyzbank.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for core-service tests that start the application: the shared PostgreSQL container
 * plus a JWKS endpoint publishing TestAccessTokens' key, so requests carry tokens verified
 * exactly as auth-server's are in the stack, and a stub customers-service.
 */
public abstract class AbstractCoreServiceIT extends AbstractPostgresIT {

    @DynamicPropertySource
    static void registerTokenVerification(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", TestJwksServer::jwkSetUri);
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestAccessTokens.ISSUER);
        registry.add("customers-service.base-url", TestCustomersService::baseUrl);
    }
}

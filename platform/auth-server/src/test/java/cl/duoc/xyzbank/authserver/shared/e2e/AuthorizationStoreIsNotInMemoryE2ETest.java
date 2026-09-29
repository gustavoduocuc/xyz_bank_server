package cl.duoc.xyzbank.authserver.shared.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@DisplayName("The authorization server's state stores")
class AuthorizationStoreIsNotInMemoryE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Authorizations, consents and registered clients are all kept in the database, never in memory
     */

    @Autowired
    private OAuth2AuthorizationService authorizationService;

    @Autowired
    private OAuth2AuthorizationConsentService authorizationConsentService;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Test
    @DisplayName("keeps authorizations, consents and clients in the database")
    void keepsAuthorizationsConsentsAndClientsInTheDatabase() {
        assertInstanceOf(JdbcOAuth2AuthorizationService.class, authorizationService);
        assertInstanceOf(JdbcOAuth2AuthorizationConsentService.class, authorizationConsentService);
        assertInstanceOf(JdbcRegisteredClientRepository.class, registeredClientRepository);
    }
}

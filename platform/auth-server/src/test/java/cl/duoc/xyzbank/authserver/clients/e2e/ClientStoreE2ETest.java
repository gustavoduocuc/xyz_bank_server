package cl.duoc.xyzbank.authserver.clients.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_SECRET;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The persistent client store")
class ClientStoreE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Holds exactly the four clients after startup
     * 2. Stores bff-web's secret only as a one-way hash, which the configured secret still matches
     */

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("holds exactly the four clients after startup")
    void holdsExactlyTheFourClientsAfterStartup() {
        List<String> clientIds = jdbcTemplate.queryForList(
                "SELECT client_id FROM oauth2_registered_client ORDER BY client_id", String.class);

        assertEquals(List.of("bff-atm", "bff-mobile", "bff-web", "interests-service"), clientIds);
    }

    @Test
    @DisplayName("stores bff-web's secret only as a one-way hash that the configured secret still matches")
    void storesBffWebsSecretOnlyAsAOneWayHash() {
        String storedSecret = jdbcTemplate.queryForObject(
                "SELECT client_secret FROM oauth2_registered_client WHERE client_id = ?", String.class, WEB_CLIENT_ID);
        AuthorizationCodeFlow flow = new AuthorizationCodeFlow(port);
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);

        int exchangeStatus = flow.exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET).statusCode();

        assertTrue(storedSecret.startsWith("{bcrypt}"), storedSecret);
        assertFalse(storedSecret.contains(WEB_CLIENT_SECRET));
        assertEquals(200, exchangeStatus);
    }
}

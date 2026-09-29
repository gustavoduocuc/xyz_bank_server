package cl.duoc.xyzbank.authserver.shared.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_ID;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_CLIENT_SECRET;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_REDIRECT_URI;
import static cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow.WEB_SCOPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("The authorization state across restarts")
class AuthorizationPersistenceE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. A code issued before a restart is exchanged successfully after it
     * 2. A code exchanged before a restart is still rejected as used after it
     */

    @Test
    @DisplayName("exchanges after a restart a code issued before it")
    void exchangesAfterARestartACodeIssuedBeforeIt() {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code;
        try (ConfigurableApplicationContext firstRun = startAuthServer()) {
            code = new AuthorizationCodeFlow(portOf(firstRun))
                    .authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);
        }

        Response exchange;
        try (ConfigurableApplicationContext secondRun = startAuthServer()) {
            exchange = new AuthorizationCodeFlow(portOf(secondRun)).exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET);
        }

        assertEquals(200, exchange.statusCode(), exchange.asString());
        assertNotNull(exchange.jsonPath().getString("id_token"));
    }

    @Test
    @DisplayName("keeps rejecting after a restart a code already exchanged before it")
    void keepsRejectingAfterARestartACodeAlreadyExchangedBeforeIt() {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code;
        try (ConfigurableApplicationContext firstRun = startAuthServer()) {
            AuthorizationCodeFlow flow = new AuthorizationCodeFlow(portOf(firstRun));
            code = flow.authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);
            assertEquals(200, flow.exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET).statusCode());
        }

        Response retry;
        try (ConfigurableApplicationContext secondRun = startAuthServer()) {
            retry = new AuthorizationCodeFlow(portOf(secondRun)).exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET);
        }

        assertEquals(400, retry.statusCode(), retry.asString());
        assertEquals("invalid_grant", retry.jsonPath().getString("error"));
        assertNull(retry.jsonPath().getString("access_token"));
    }
}

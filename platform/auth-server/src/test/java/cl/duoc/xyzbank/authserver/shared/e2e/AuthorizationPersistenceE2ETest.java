package cl.duoc.xyzbank.authserver.shared.e2e;

import cl.duoc.xyzbank.authserver.AuthServerApplication;
import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
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
     */

    @Test
    @DisplayName("exchanges after a restart a code issued before it")
    void exchangesAfterARestartACodeIssuedBeforeIt() {
        String verifier = AuthorizationCodeFlow.newCodeVerifier();
        String code;
        try (ConfigurableApplicationContext firstRun = start()) {
            code = new AuthorizationCodeFlow(portOf(firstRun))
                    .authorizationCodeFor(WEB_CLIENT_ID, WEB_REDIRECT_URI, WEB_SCOPES, verifier);
        }

        Response exchange;
        try (ConfigurableApplicationContext secondRun = start()) {
            exchange = new AuthorizationCodeFlow(portOf(secondRun)).exchangeAsWebClient(code, verifier, WEB_CLIENT_SECRET);
        }

        assertEquals(200, exchange.statusCode(), exchange.asString());
        assertNotNull(exchange.jsonPath().getString("id_token"));
    }

    private static ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(AuthServerApplication.class)
                .profiles("test")
                .run(datasourceArguments("--server.port=0"));
    }

    private static int portOf(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }
}

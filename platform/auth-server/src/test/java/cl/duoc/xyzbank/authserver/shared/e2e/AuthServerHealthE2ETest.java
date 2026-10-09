package cl.duoc.xyzbank.authserver.shared.e2e;

import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.AuthorizationCodeFlow;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The auth-server health endpoint")
class AuthServerHealthE2ETest extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Reports UP without credentials and without component details
     * 2. No other actuator endpoint is served
     */

    @LocalServerPort
    private int port;

    private AuthorizationCodeFlow flow;

    @BeforeEach
    void setUp() {
        flow = new AuthorizationCodeFlow(port);
    }

    @Test
    @DisplayName("reports UP without credentials and without details")
    void reportsUpWithoutCredentialsAndWithoutDetails() {
        Response response = flow.request().get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertEquals("UP", response.jsonPath().getString("status"));
        assertNull(response.jsonPath().get("components"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator", "/actuator/prometheus"})
    @DisplayName("serves no other actuator endpoint")
    void servesNoOtherActuatorEndpoint(String path) {
        Response response = flow.request().get(path);

        assertNotEquals(200, response.statusCode(), path + " -> " + response.asString());
    }
}

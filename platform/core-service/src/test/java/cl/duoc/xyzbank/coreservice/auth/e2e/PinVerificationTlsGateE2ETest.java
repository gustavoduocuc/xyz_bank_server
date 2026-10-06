package cl.duoc.xyzbank.coreservice.auth.e2e;

import cl.duoc.xyzbank.testsupport.AbstractCoreServiceIT;
import cl.duoc.xyzbank.testsupport.TestAccessTokens;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The PIN verification TLS gate")
class PinVerificationTlsGateE2ETest extends AbstractCoreServiceIT {

    /*
     * Cases:
     * 1. A request over the plain-HTTP connector is rejected, even with a well-formed body
     * 2. The same request over the TLS connector is accepted (does not fail at the gate)
     */

    @LocalServerPort
    private int primaryPort;

    @Test
    @DisplayName("rejects a request over the plain-http connector, even with a well-formed body")
    void rejectsPlainHttpRequestEvenWithWellFormedBody() {
        RestAssured.port = primaryPort;

        given()
                .contentType("application/json")
                .body("{\"cardNumber\":\"any-card\",\"pin\":\"1234\"}")
                .when()
                .post("/internal/auth/atm/pin-verifications")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("does not reject the same request over the tls connector")
    void doesNotRejectSameRequestOverTlsConnector() {
        given()
                .relaxedHTTPSValidation()
                .baseUri("https://localhost:8453")
                .header("Authorization", "Bearer " + TestAccessTokens.atm())
                .contentType("application/json")
                .body("{\"cardNumber\":\"unknown-card\",\"pin\":\"1234\"}")
                .when()
                .post("/internal/auth/atm/pin-verifications")
                .then()
                .statusCode(not(400));
    }
}

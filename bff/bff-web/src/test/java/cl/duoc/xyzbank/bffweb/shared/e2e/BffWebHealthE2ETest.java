package cl.duoc.xyzbank.bffweb.shared.e2e;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The bff-web health endpoint")
class BffWebHealthE2ETest {

    /*
     * Cases:
     * 1. Reports healthy without identity headers
     * 2. Serves the OpenAPI document without identity headers
     * 3. The management port reports health and the state of every circuit breaker (bff-resilience
     *    spec, "Circuit-breaker state is observable through actuator without exposing it to
     *    customers")
     * 4. The circuit-breaker endpoint is not served on the customer-facing port
     * 5. An open breaker does not make the BFF report itself unhealthy
     */

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private CircuitBreakerRegistry circuitBreakers;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.useRelaxedHTTPSValidation();
        circuitBreakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    @Test
    @DisplayName("reports healthy without identity headers")
    void reportsHealthyWithoutIdentityHeaders() {
        given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("serves the OpenAPI document without identity headers")
    void servesTheOpenApiDocumentWithoutIdentityHeaders() {
        given()
                .when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body(containsString("openapi:"))
                .body(containsString("/customers/{customerId}/dashboard"));
    }

    @Test
    @DisplayName("reports health and every circuit breaker's state on the management port")
    void reportsHealthAndEveryCircuitBreakersStateOnTheManagementPort() {
        circuitBreakers.circuitBreaker("coreService");
        circuitBreakers.circuitBreaker("interestsService");
        circuitBreakers.circuitBreaker("authServer");

        given().baseUri("http://localhost").port(managementPort)
                .when().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
        given().baseUri("http://localhost").port(managementPort)
                .when().get("/actuator/circuitbreakers")
                .then()
                .statusCode(200)
                .body("circuitBreakers.coreService.state", equalTo("CLOSED"))
                .body("circuitBreakers.interestsService.state", equalTo("CLOSED"))
                .body("circuitBreakers.authServer.state", equalTo("CLOSED"));
    }

    @Test
    @DisplayName("does not serve circuit-breaker state on the customer-facing port")
    void doesNotServeCircuitBreakerStateOnTheCustomerFacingPort() {
        given()
                .when().get("/actuator/circuitbreakers")
                .then().statusCode(not(200));
    }

    @Test
    @DisplayName("stays healthy while a circuit breaker is open")
    void staysHealthyWhileACircuitBreakerIsOpen() {
        circuitBreakers.circuitBreaker("coreService").transitionToOpenState();

        given().baseUri("http://localhost").port(managementPort)
                .when().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
        given()
                .when().get("/actuator/health")
                .then().statusCode(200).body("status", equalTo("UP"));
    }
}

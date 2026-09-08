package cl.duoc.xyzbank.bffmobile.shared.e2e;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The bff-mobile health endpoint")
class BffMobileHealthE2ETest {

    /*
     * Cases:
     * 1. Reports healthy without identity headers
     * 2. Serves the OpenAPI document without identity headers
     */

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
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
                .body(containsString("/accounts/{accountId}/summary"));
    }
}

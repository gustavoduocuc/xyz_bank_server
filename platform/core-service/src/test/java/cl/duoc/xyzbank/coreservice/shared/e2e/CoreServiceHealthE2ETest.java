package cl.duoc.xyzbank.coreservice.shared.e2e;

import cl.duoc.xyzbank.testsupport.AbstractPostgresIT;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The core-service health endpoint")
class CoreServiceHealthE2ETest extends AbstractPostgresIT {

    /*
     * Cases:
     * 1. Reports UP when the process and database are available
     * 2. Echoes a generated correlation id when the header is omitted
     * 3. Serves the OpenAPI document describing internal endpoints
     */

    @LocalServerPort
    private int port;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("reports UP when the process and database are available")
    void reportsUpWhenTheProcessAndDatabaseAreAvailable() {
        given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("echoes a generated correlation id when the header is omitted")
    void echoesAGeneratedCorrelationIdWhenTheHeaderIsOmitted() {
        given()
                .when()
                .get("/actuator/health")
                .then()
                .statusCode(200)
                .header("X-Correlation-Id", not(emptyOrNullString()));
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
                .body(containsString("/internal/customers/{customerId}"));
    }
}

package cl.duoc.xyzbank.paymentsservice.payments.integration;

import cl.duoc.xyzbank.paymentsservice.testsupport.AbstractPostgresIT;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

// Spring Boot tests switch metrics export off unless asked
@AutoConfigureObservability
@DisplayName("The Prometheus endpoint")
class PrometheusEndpointIT extends AbstractPostgresIT {

    /*
     * Cases (observability spec, "Every deployable exposes Prometheus metrics on an internal endpoint"):
     * 1. /actuator/prometheus answers metrics in Prometheus text format without a token
     */

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("answers metrics in Prometheus format without a token")
    void answersMetricsInPrometheusFormatWithoutAToken() {
        given().get("/actuator/prometheus")
                .then().statusCode(200)
                .body(containsString("jvm_memory_used_bytes"));
    }
}

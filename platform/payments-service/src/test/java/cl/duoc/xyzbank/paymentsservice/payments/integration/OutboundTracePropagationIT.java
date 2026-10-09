package cl.duoc.xyzbank.paymentsservice.payments.integration;

import cl.duoc.xyzbank.paymentsservice.testsupport.AbstractPostgresIT;
import cl.duoc.xyzbank.paymentsservice.testsupport.TestCoreService;
import cl.duoc.xyzbank.paymentsservice.testsupport.TestTokens;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.restassured.RestAssured.given;

// Spring Boot tests switch tracing off unless asked
@AutoConfigureObservability
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
@DisplayName("payments-service's outbound trace propagation")
class OutboundTracePropagationIT extends AbstractPostgresIT {

    /*
     * Cases (observability spec, "One request is one trace across services"):
     * 1. The call to core-service carries the trace the inbound request arrived in
     */

    private static final String POSTINGS = "/internal/postings";

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("sends core-service the trace the inbound request arrived in")
    void sendsCoreServiceTheTraceTheInboundRequestArrivedIn() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        TestCoreService.server().stubFor(post(urlEqualTo(POSTINGS)).willReturn(aResponse().withStatus(201)));

        given().header("Authorization", "Bearer " + TestTokens.paymentsAdmin())
                .header("Idempotency-Key", "trace-" + UUID.randomUUID())
                .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
                .contentType("application/json")
                .body(Map.of("sourceAccountId", UUID.randomUUID().toString(),
                        "destinationAccountId", UUID.randomUUID().toString(), "amount", 10.00, "currency", "USD"))
                .post("/internal/transfers")
                .then().statusCode(201);

        TestCoreService.server().verify(postRequestedFor(urlEqualTo(POSTINGS))
                .withHeader("traceparent", matching("00-" + traceId + "-[0-9a-f]{16}-0[01]")));
    }
}

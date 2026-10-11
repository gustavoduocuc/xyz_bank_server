package cl.duoc.xyzbank.apigateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The routes come from the real config-repo/api-gateway.yml; the registry is replaced by a static
 * list of WireMock instances, so no Eureka is needed.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.config.import=optional:file:../../config-repo/api-gateway.yml",
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                "management.tracing.sampling.probability=1.0"
        })
// Spring Boot tests switch tracing off unless asked
@AutoConfigureObservability
@DisplayName("The api-gateway routes")
class ApiGatewayRoutesIT {

    /*
     * Cases (api-gateway spec):
     * 1. A request is routed to the service with the prefix removed and the response returned unchanged
     * 2. Authorization, X-Correlation-Id and Idempotency-Key reach the service, also on a repeated write
     * 3. With two instances of a service, both receive requests
     * 4. A service slower than its route timeout gets 504 and another route is unaffected
     * 5. A service with no instance gets 503
     * 6. A request carrying a trace context reaches the service inside the same trace
     */

    private static final WireMockServer CORE_ONE = start();
    private static final WireMockServer CORE_TWO = start();
    private static final WireMockServer INTERESTS = start();
    private static final WireMockServer CUSTOMERS = start();

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void registerInstances(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.core-service[0].uri", CORE_ONE::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.core-service[1].uri", CORE_TWO::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.interests-service[0].uri", INTERESTS::baseUrl);
        registry.add("spring.cloud.discovery.client.simple.instances.customers-service[0].uri", CUSTOMERS::baseUrl);
    }

    @BeforeEach
    void resetStubs() {
        for (WireMockServer server : new WireMockServer[] {CORE_ONE, CORE_TWO, INTERESTS, CUSTOMERS}) {
            server.resetAll();
        }
        client = client.mutate().responseTimeout(Duration.ofSeconds(10)).build();
    }

    @AfterAll
    static void stopServers() {
        CORE_ONE.stop();
        CORE_TWO.stop();
        INTERESTS.stop();
        CUSTOMERS.stop();
    }

    @Test
    @DisplayName("routes a request to the service without the service-id prefix and returns its response")
    void routesARequestToTheServiceWithoutTheServiceIdPrefixAndReturnsItsResponse() {
        CORE_ONE.stubFor(get(urlEqualTo("/internal/accounts/1/balance"))
                .willReturn(aResponse().withStatus(200).withBody("{\"balance\":10}")));
        CORE_TWO.stubFor(get(urlEqualTo("/internal/accounts/1/balance"))
                .willReturn(aResponse().withStatus(200).withBody("{\"balance\":10}")));

        client.get().uri("/core-service/internal/accounts/1/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("{\"balance\":10}");
    }

    @Test
    @DisplayName("forwards the identity, correlation and idempotency headers on a write and on its retry")
    void forwardsTheIdentityCorrelationAndIdempotencyHeadersOnAWriteAndOnItsRetry() {
        CUSTOMERS.stubFor(post(urlEqualTo("/internal/customers")).willReturn(aResponse().withStatus(201)));

        for (int attempt = 0; attempt < 2; attempt++) {
            client.post().uri("/customers-service/internal/customers")
                    .header("Authorization", "Bearer token-1")
                    .header("X-Correlation-Id", "corr-1")
                    .header("Idempotency-Key", "key-1")
                    .exchange()
                    .expectStatus().isCreated();
        }

        CUSTOMERS.verify(2, postRequestedFor(urlEqualTo("/internal/customers"))
                .withHeader("Authorization", equalTo("Bearer token-1"))
                .withHeader("X-Correlation-Id", equalTo("corr-1"))
                .withHeader("Idempotency-Key", equalTo("key-1")));
    }

    @Test
    @DisplayName("sends requests to both instances of a service")
    void sendsRequestsToBothInstancesOfAService() {
        CORE_ONE.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200)));
        CORE_TWO.stubFor(get(urlEqualTo("/ping")).willReturn(aResponse().withStatus(200)));

        for (int request = 0; request < 6; request++) {
            client.get().uri("/core-service/ping").exchange().expectStatus().isOk();
        }

        assertTrue(!CORE_ONE.findAll(getRequestedFor(urlEqualTo("/ping"))).isEmpty());
        assertTrue(!CORE_TWO.findAll(getRequestedFor(urlEqualTo("/ping"))).isEmpty());
    }

    @Test
    @DisplayName("answers 504 when a service is slower than its route timeout and leaves other routes working")
    void answers504WhenAServiceIsSlowerThanItsRouteTimeoutAndLeavesOtherRoutesWorking() {
        INTERESTS.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withFixedDelay(4000)));
        CUSTOMERS.stubFor(get(urlEqualTo("/fast")).willReturn(aResponse().withStatus(200)));

        client.get().uri("/interests-service/slow").exchange().expectStatus().isEqualTo(504);
        client.get().uri("/customers-service/fast").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("passes the caller's trace to the service as a child span of the same trace")
    void passesTheCallersTraceToTheServiceAsAChildSpanOfTheSameTrace() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        CUSTOMERS.stubFor(get(urlEqualTo("/traced")).willReturn(aResponse().withStatus(200)));

        client.get().uri("/customers-service/traced")
                .header("traceparent", "00-" + traceId + "-00f067aa0ba902b7-01")
                .exchange().expectStatus().isOk();

        CUSTOMERS.verify(getRequestedFor(urlEqualTo("/traced"))
                .withHeader("traceparent", com.github.tomakehurst.wiremock.client.WireMock.matching("00-" + traceId + "-[0-9a-f]{16}-0[01]")));
    }

    @Test
    @DisplayName("answers 503 when a service has no registered instance")
    void answers503WhenAServiceHasNoRegisteredInstance() {
        client.get().uri("/payments-service/payments").exchange().expectStatus().isEqualTo(503);
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }
}

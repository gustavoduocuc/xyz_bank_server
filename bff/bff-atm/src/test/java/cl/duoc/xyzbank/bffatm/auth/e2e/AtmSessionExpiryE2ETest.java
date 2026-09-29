package cl.duoc.xyzbank.bffatm.auth.e2e;

import cl.duoc.xyzbank.bffatm.testsupport.AuthServerStub;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.restassured.RestAssured;
import io.restassured.config.SSLConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("The ATM session's 120-second expiry")
class AtmSessionExpiryE2ETest {

    /*
     * Cases:
     * 1. A session just under 120 seconds old is still accepted
     * 2. A session older than 120 seconds is rejected, even though the mTLS certificate and
     *    pin were valid at issuance
     */

    private static final WireMockServer CORE_SERVICE = new WireMockServer(wireMockConfig()
            .dynamicPort()
            .dynamicHttpsPort()
            .keystorePath("tls/keystore.p12")
            .keystorePassword("xyzbank-dev")
            .keyManagerPassword("xyzbank-dev"));

    private static final AuthServerStub AUTH_SERVER = new AuthServerStub();

    static {
        CORE_SERVICE.start();
        AUTH_SERVER.start();
    }

    @DynamicPropertySource
    static void coreServicePinVerificationBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("core-service.pin-verification-base-url", () -> "https://localhost:" + CORE_SERVICE.httpsPort());
        AUTH_SERVER.register(registry);
    }

    @TestConfiguration
    static class MutableClockConfig {

        static final AtomicReference<Instant> CURRENT_INSTANT =
                new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

        @Bean
        @Primary
        Clock testClock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return CURRENT_INSTANT.get();
                }
            };
        }

        @Bean
        @Primary
        JwtCallerContextAdapter testJwtCallerContextAdapter(Clock testClock) {
            return new JwtCallerContextAdapter("dev-channel-auth-jwt-signing-secret-please-rotate-in-prod", testClock);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtCallerContextAdapter tokenAdapter;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "https://localhost";
        RestAssured.config = RestAssured.config()
                .sslConfig(SSLConfig.sslConfig()
                        .keyStore("tls/terminal-keystore.p12", "xyzbank-dev")
                        .and()
                        .relaxedHTTPSValidation());
        CORE_SERVICE.resetAll();
        MutableClockConfig.CURRENT_INSTANT.set(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @AfterAll
    static void stopCoreServiceStub() {
        CORE_SERVICE.stop();
        AUTH_SERVER.stop();
    }

    private String issueSession() {
        CORE_SERVICE.stubFor(post(urlEqualTo("/internal/auth/atm/pin-verifications"))
                .withRequestBody(equalToJson("{\"cardNumber\":\"card-1\",\"pin\":\"1234\"}"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"customerId\":\"customer-1\"}")));

        return given()
                .contentType("application/json")
                .body("{\"cardNumber\":\"card-1\",\"pin\":\"1234\"}")
                .when()
                .post("/pin-verifications")
                .then()
                .statusCode(200)
                .extract()
                .path("sessionToken");
    }

    @Test
    @DisplayName("accepts a session just under 120 seconds old")
    void acceptsASessionJustUnder120SecondsOld() {
        String sessionToken = issueSession();

        MutableClockConfig.CURRENT_INSTANT.updateAndGet(instant -> instant.plus(Duration.ofSeconds(119)));

        tokenAdapter.resolve(sessionToken);
    }

    @Test
    @DisplayName("rejects a session older than 120 seconds")
    void rejectsASessionOlderThan120Seconds() {
        String sessionToken = issueSession();

        MutableClockConfig.CURRENT_INSTANT.updateAndGet(instant -> instant.plus(Duration.ofSeconds(121)));

        assertThrows(CallerIdentityException.class, () -> tokenAdapter.resolve(sessionToken));
    }
}

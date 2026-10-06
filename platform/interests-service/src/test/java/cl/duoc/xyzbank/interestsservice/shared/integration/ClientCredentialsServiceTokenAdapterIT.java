package cl.duoc.xyzbank.interestsservice.shared.integration;

import cl.duoc.xyzbank.interestsservice.shared.infrastructure.adapters.ClientCredentialsServiceTokenAdapter;
import cl.duoc.xyzbank.interestsservice.testsupport.TestAuthServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The client_credentials service token adapter")
class ClientCredentialsServiceTokenAdapterIT {

    /*
     * Cases:
     * 1. Obtains interests-service's own token from auth-server's token endpoint
     * 2. Reuses the cached token while it is far from expiry
     * 3. Obtains a new token once the cached one is about to expire
     */

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-29T12:00:00Z"));
    private ClientCredentialsServiceTokenAdapter adapter;

    @BeforeEach
    void setUp() {
        TestAuthServer.reset();
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        adapter = new ClientCredentialsServiceTokenAdapter(
                RestClient.create(), TestAuthServer.tokenUri(), TestAuthServer.CLIENT_ID, TestAuthServer.CLIENT_SECRET, clock);
    }

    @Test
    @DisplayName("obtains interests-service's own token from auth-server")
    void obtainsInterestsServicesOwnTokenFromAuthServer() {
        String token = adapter.issueServiceToken();

        assertTrue(token.split("\\.").length == 3, token);
        assertEquals(1, TestAuthServer.tokenRequests());
    }

    @Test
    @DisplayName("reuses the cached token while it is far from expiry")
    void reusesTheCachedTokenWhileItIsFarFromExpiry() {
        String first = adapter.issueServiceToken();
        now.set(now.get().plus(Duration.ofMinutes(10)));

        String second = adapter.issueServiceToken();

        assertEquals(first, second);
        assertEquals(1, TestAuthServer.tokenRequests());
    }

    @Test
    @DisplayName("obtains a new token once the cached one is about to expire")
    void obtainsANewTokenOnceTheCachedOneIsAboutToExpire() {
        adapter.issueServiceToken();
        now.set(now.get().plus(Duration.ofMinutes(14).plusSeconds(30)));

        adapter.issueServiceToken();

        assertEquals(2, TestAuthServer.tokenRequests());
    }
}

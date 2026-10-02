package cl.duoc.xyzbank.interestsservice.shared.infrastructure.adapters;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import cl.duoc.xyzbank.interestsservice.shared.domain.ServiceTokenUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Obtains interests-service's own access token from auth-server with the client_credentials
 * grant (scope interests:write, audience core-service) and caches it until shortly before it
 * expires. interests-service only holds its client secret, which can ask auth-server for a
 * token but cannot sign one (adopt-oauth2-tokens-between-services design.md Decision 4).
 *
 * <p>Asking for a token has no side effect, so a failed request is retried ("authServerToken")
 * inside the authorization server's own breaker ("authServer"); once that gives up, the failure
 * is a {@link ServiceTokenUnavailableException}, which core-service's breaker and retries ignore
 * (add-resilience4j-to-bffs).
 */
public class ClientCredentialsServiceTokenAdapter implements ServiceTokenPort {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedTokenExpiry = Instant.MIN;

    public ClientCredentialsServiceTokenAdapter(
            RestClient restClient, String tokenUri, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    @Override
    @CircuitBreaker(name = "authServer")
    @Retry(name = "authServerToken", fallbackMethod = "tokenUnavailable")
    public synchronized String issueServiceToken() {
        Instant now = clock.instant();
        if (cachedToken == null || !now.isBefore(cachedTokenExpiry.minus(REFRESH_MARGIN))) {
            TokenResponse response = requestToken();
            cachedToken = response.accessToken();
            cachedTokenExpiry = now.plusSeconds(response.expiresIn());
        }
        return cachedToken;
    }

    /** Reached once the retry gives up, or at once for a refusal or an open circuit. */
    public String tokenUnavailable(Throwable failure) {
        throw new ServiceTokenUnavailableException("The authorization server could not issue a service token", failure);
    }

    private TokenResponse requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        return restClient.post()
                .uri(tokenUri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }
}

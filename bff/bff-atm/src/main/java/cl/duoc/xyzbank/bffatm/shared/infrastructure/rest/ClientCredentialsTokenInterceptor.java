package cl.duoc.xyzbank.bffatm.shared.infrastructure.rest;

import cl.duoc.xyzbank.bffatm.shared.application.DependencyUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Obtains bff-atm's own access token from the authorization server with the client_credentials
 * grant and sends it on every core-service call, cached until shortly before it expires.
 * bff-atm holds only its client secret, which can ask for a token but cannot sign one.
 *
 * <p>Asking for a token has no side effect, so a failed request is retried (bounded by the
 * "authServerToken" retry) inside the authorization server's own breaker. When no token can be
 * obtained the core-service call is never sent and the failure surfaces as
 * {@link DependencyUnavailableException}, which core-service's breaker and retries ignore
 * (bff-resilience spec, "An unavailable auth-server yields 503").
 */
public class ClientCredentialsTokenInterceptor implements ClientHttpRequestInterceptor {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final Supplier<TokenResponse> resilientTokenRequest;

    private String cachedToken;
    private Instant cachedTokenExpiry = Instant.MIN;

    public ClientCredentialsTokenInterceptor(
            RestClient restClient, String tokenUri, String clientId, String clientSecret, Clock clock,
            Retry retry, CircuitBreaker circuitBreaker) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
        this.resilientTokenRequest = Retry.decorateSupplier(
                retry, CircuitBreaker.decorateSupplier(circuitBreaker, this::requestToken));
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().setBearerAuth(currentToken());
        return execution.execute(request, body);
    }

    private synchronized String currentToken() {
        Instant now = clock.instant();
        if (cachedToken == null || !now.isBefore(cachedTokenExpiry.minus(REFRESH_MARGIN))) {
            TokenResponse response = obtainToken();
            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("The authorization server answered without an access token");
            }
            cachedToken = response.accessToken();
            cachedTokenExpiry = now.plusSeconds(response.expiresIn());
        }
        return cachedToken;
    }

    private TokenResponse obtainToken() {
        try {
            return resilientTokenRequest.get();
        } catch (RestClientException | CallNotPermittedException exception) {
            throw DependencyUnavailableException.of("Authorization server", exception);
        }
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

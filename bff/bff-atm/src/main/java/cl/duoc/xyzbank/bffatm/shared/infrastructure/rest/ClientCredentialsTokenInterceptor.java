package cl.duoc.xyzbank.bffatm.shared.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Obtains bff-atm's own access token from the authorization server with the client_credentials
 * grant and sends it on every core-service call, cached until shortly before it expires.
 * bff-atm holds only its client secret, which can ask for a token but cannot sign one.
 */
public class ClientCredentialsTokenInterceptor implements ClientHttpRequestInterceptor {

    private static final Duration REFRESH_MARGIN = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String cachedToken;
    private Instant cachedTokenExpiry = Instant.MIN;

    public ClientCredentialsTokenInterceptor(
            RestClient restClient, String tokenUri, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
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
            TokenResponse response = requestToken();
            if (response == null || response.accessToken() == null) {
                throw new IllegalStateException("The authorization server answered without an access token");
            }
            cachedToken = response.accessToken();
            cachedTokenExpiry = now.plusSeconds(response.expiresIn());
        }
        return cachedToken;
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

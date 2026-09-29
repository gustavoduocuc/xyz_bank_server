package cl.duoc.xyzbank.interestsservice.shared.infrastructure.adapters;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    public synchronized String issueServiceToken() {
        Instant now = clock.instant();
        if (cachedToken == null || !now.isBefore(cachedTokenExpiry.minus(REFRESH_MARGIN))) {
            TokenResponse response = requestToken();
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

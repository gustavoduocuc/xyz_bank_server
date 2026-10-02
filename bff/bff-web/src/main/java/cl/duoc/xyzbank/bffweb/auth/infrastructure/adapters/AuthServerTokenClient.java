package cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.shared.application.DependencyUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Calls the authorization server's token endpoint as bff-web's own confidential client
 * (client_secret_basic, as for the login code exchange). The authorization server rotates the
 * refresh token on every use and revokes the whole login when a rotated-out one is presented
 * again, so a refused refresh always means the customer must log in again. A refresh that could
 * not complete (unreachable, timed out, 5xx, open circuit) is not a refusal: it surfaces as
 * {@link DependencyUnavailableException} and leaves the session alone (bff-resilience spec).
 */
public class AuthServerTokenClient {

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;

    public AuthServerTokenClient(RestClient restClient, String tokenUri, String clientId, String clientSecret) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public IssuedTokens refresh(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        Map<?, ?> body;
        try {
            body = restClient.post()
                    .uri(tokenUri)
                    .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                throw new RefreshTokenRejectedException("The authorization server refused the refresh token", exception);
            }
            throw DependencyUnavailableException.of("Authorization server", exception);
        } catch (RestClientException | CallNotPermittedException exception) {
            // Unreachable, timed out or its circuit is open. Never retried: the server may have
            // rotated the token already, and presenting it again would count as reuse
            throw DependencyUnavailableException.of("Authorization server", exception);
        }
        if (body == null || body.get("access_token") == null || body.get("refresh_token") == null) {
            throw new IllegalStateException("The authorization server answered the refresh without tokens");
        }
        Number expiresIn = (Number) body.get("expires_in");
        return new IssuedTokens(
                body.get("access_token").toString(),
                Duration.ofSeconds(expiresIn == null ? 0 : expiresIn.longValue()),
                body.get("refresh_token").toString());
    }
}

package cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * Calls the authorization server as bff-mobile's confidential client. A mobile refresh must
 * name the device the login was bound to. Device revocation proves the customer with that
 * device's current access token and authenticates the client with its secret.
 */
public class AuthServerTokenClient {

    private final RestClient restClient;
    private final String tokenUri;
    private final String revocationBaseUri;
    private final String clientId;
    private final String clientSecret;

    public AuthServerTokenClient(
            RestClient restClient,
            String tokenUri,
            String revocationBaseUri,
            String clientId,
            String clientSecret) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.revocationBaseUri = revocationBaseUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public void revokeDevice(String deviceId, String accessToken) {
        URI uri = UriComponentsBuilder.fromUriString(revocationBaseUri)
                .path("/devices/{deviceId}/revocations")
                .queryParam("access_token", accessToken)
                .build(deviceId);
        restClient.post()
                .uri(uri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .retrieve()
                .toBodilessEntity();
    }

    public IssuedTokens refresh(String refreshToken, String deviceId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        form.add("device_id", deviceId);
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
            throw new RefreshTokenRejectedException("The authorization server refused the refresh token", exception);
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

package cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters;

import java.time.Duration;

/** The tokens the authorization server issued to bff-web for one customer's session. */
public record IssuedTokens(String accessToken, Duration accessTokenLifetime, String refreshToken) {
}

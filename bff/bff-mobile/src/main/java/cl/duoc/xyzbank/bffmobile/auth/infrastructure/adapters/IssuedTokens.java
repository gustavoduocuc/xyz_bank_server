package cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters;

import java.time.Duration;

/** The tokens the authorization server issued to bff-mobile for one device session. */
public record IssuedTokens(String accessToken, Duration accessTokenLifetime, String refreshToken) {
}

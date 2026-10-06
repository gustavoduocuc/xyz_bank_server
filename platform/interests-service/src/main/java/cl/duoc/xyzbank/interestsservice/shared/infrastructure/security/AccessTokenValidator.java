package cl.duoc.xyzbank.interestsservice.shared.infrastructure.security;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates access tokens issued by auth-server for interests-service: RS256 signature against
 * its JWKS, issuer, expiry, audience "interests-service", and a client ("azp") that matches the
 * token's channel. Only the scopes of that channel are granted. interests-service holds no key
 * that could produce such a token (adopt-oauth2-tokens-between-services design.md Decision 4).
 */
public class AccessTokenValidator {

    private final JwtDecoder decoder;
    private final Map<String, Channel> channelsByClientId;

    public AccessTokenValidator(JwtDecoder decoder, Map<String, Channel> channelsByClientId) {
        this.decoder = decoder;
        this.channelsByClientId = Map.copyOf(channelsByClientId);
    }

    public static JwtDecoder decoderFor(String jwkSetUri, String issuer, String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(issuer), audienceValidator(audience)));
        return decoder;
    }

    public ValidatedAccessToken validate(String accessToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAccessTokenException("Invalid access token", exception);
        }
        Channel channel = channelOf(jwt);
        String clientId = jwt.getClaimAsString("azp");
        if (clientId == null || channelsByClientId.get(clientId) != channel) {
            throw new InvalidAccessTokenException("The token's client does not match its channel");
        }
        Set<String> scopes = scopesOf(jwt.getClaim("scope")).stream()
                .filter(channel.scopes()::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new ValidatedAccessToken(clientId, channel, scopes, jwt.getSubject());
    }

    private static Channel channelOf(Jwt jwt) {
        try {
            return Channel.valueOf(jwt.getClaimAsString("channel"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidAccessTokenException("The token carries no known channel", exception);
        }
    }

    private static Collection<String> scopesOf(Object scopeClaim) {
        if (scopeClaim instanceof Collection<?> scopes) {
            return scopes.stream().map(Object::toString).toList();
        }
        if (scopeClaim instanceof String scopes) {
            return List.of(scopes.split(" "));
        }
        return List.of();
    }

    private static OAuth2TokenValidator<Jwt> audienceValidator(String audience) {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "The token is not meant for " + audience, null));
    }
}

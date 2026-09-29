package cl.duoc.xyzbank.coreservice.auth.infrastructure.adapters;

import cl.duoc.xyzbank.coreservice.auth.application.dto.VerifiedAccessToken;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AccessTokenVerifier;
import cl.duoc.xyzbank.coreservice.auth.application.ports.InvalidAccessTokenException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifies an access token issued by auth-server and turns it into a VerifiedAccessToken.
 * Beyond the decoder's signature/issuer/audience/expiry checks, the client the token was
 * issued to ("azp") must be the one registered for its "channel", and only the scopes of that
 * channel are granted -- so no token can satisfy another channel's scope requirement.
 */
public class JwtAccessTokenVerifier implements AccessTokenVerifier {

    private final JwtDecoder decoder;
    private final Map<String, Channel> channelsByClientId;

    public JwtAccessTokenVerifier(JwtDecoder decoder, Map<String, Channel> channelsByClientId) {
        this.decoder = decoder;
        this.channelsByClientId = Map.copyOf(channelsByClientId);
    }

    @Override
    public VerifiedAccessToken verify(String accessToken) {
        Jwt jwt = decode(accessToken);
        String clientId = jwt.getClaimAsString("azp");
        Channel channel = channelOf(jwt);
        if (clientId == null || channelsByClientId.get(clientId) != channel) {
            throw new InvalidAccessTokenException("The token's client does not match its channel");
        }
        return new VerifiedAccessToken(
                clientId,
                channel,
                grantedScopes(jwt, channel),
                jwt.getSubject(),
                Optional.ofNullable(jwt.getClaimAsString("device_id")));
    }

    private Jwt decode(String accessToken) {
        try {
            return decoder.decode(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidAccessTokenException("Invalid access token", exception);
        }
    }

    private static Channel channelOf(Jwt jwt) {
        try {
            return Channel.valueOf(jwt.getClaimAsString("channel"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidAccessTokenException("The token carries no known channel", exception);
        }
    }

    private static Set<String> grantedScopes(Jwt jwt, Channel channel) {
        return scopesOf(jwt.getClaim("scope")).stream()
                .filter(channel.scopes()::contains)
                .collect(Collectors.toUnmodifiableSet());
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
}

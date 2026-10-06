package cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters;

import cl.duoc.xyzbank.bffmobile.shared.application.CallerContextResolver;
import cl.duoc.xyzbank.bffmobile.shared.application.DependencyUnavailableException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestOperations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the caller from the bearer token, which holds the access token the platform
 * authorization server issued to bff-mobile at login. The token must verify against the
 * authorization server's JWK set, come from its issuer and be unexpired. Its {@code device_id}
 * is the device the login was bound to. A token issued to any other client is refused as
 * another channel's credential.
 */
public class AccessTokenCallerContextAdapter implements CallerContextResolver {

    private final JwtDecoder decoder;
    private final String mobileClientId;

    public AccessTokenCallerContextAdapter(JwtDecoder decoder, String mobileClientId) {
        this.decoder = decoder;
        this.mobileClientId = mobileClientId;
    }

    /** restOperations carries the auth-server timeouts and breaker for the JWK set fetch. */
    public static JwtDecoder decoderFor(String jwkSetUri, String issuer, RestOperations restOperations) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).restOperations(restOperations).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(issuer)));
        return decoder;
    }

    @Override
    public CallerContext resolve(String accessToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(accessToken);
        } catch (JwtException | IllegalArgumentException exception) {
            if (AuthServerFailures.isUnavailable(exception)) {
                // The keys could not be fetched: nothing was judged about the token itself
                throw DependencyUnavailableException.of("Authorization server", exception);
            }
            throw CallerIdentityException.invalid("Invalid access token");
        }
        if (!mobileClientId.equals(jwt.getClaimAsString("azp"))) {
            throw CallerIdentityException.forbidden("This endpoint requires the mobile channel");
        }
        if (!Channel.MOBILE.name().equals(jwt.getClaimAsString("channel")) || jwt.getSubject() == null) {
            throw CallerIdentityException.invalid("The access token's client does not match its channel");
        }
        Set<String> scopes = scopesOf(jwt.getClaim("scope")).stream()
                .filter(Channel.MOBILE.scopes()::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new MobileCaller(jwt.getSubject(), scopes, jwt.getClaimAsString("device_id"));
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

    private record MobileCaller(String customerId, Set<String> scopes, String deviceId) implements CallerContext {

        @Override
        public Channel channel() {
            return Channel.MOBILE;
        }

        @Override
        public Optional<String> terminalId() {
            return Optional.ofNullable(deviceId);
        }
    }
}

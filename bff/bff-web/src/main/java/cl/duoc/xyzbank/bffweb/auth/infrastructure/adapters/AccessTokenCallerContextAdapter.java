package cl.duoc.xyzbank.bffweb.auth.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.shared.application.CallerContextResolver;
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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the caller from the session cookie, which holds the access token the platform
 * authorization server issued to bff-web at login (adopt-oauth2-tokens-between-services
 * design.md Decision 1). The token must verify against the authorization server's JWK set,
 * come from its issuer and be unexpired. A token issued to bff-web must carry the web channel;
 * a trustworthy token issued to any other client is refused as another channel's credential.
 * bff-web holds no key that could produce such a token.
 */
public class AccessTokenCallerContextAdapter implements CallerContextResolver {

    private final JwtDecoder decoder;
    private final String webClientId;

    public AccessTokenCallerContextAdapter(JwtDecoder decoder, String webClientId) {
        this.decoder = decoder;
        this.webClientId = webClientId;
    }

    public static JwtDecoder decoderFor(String jwkSetUri, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), new JwtIssuerValidator(issuer)));
        return decoder;
    }

    @Override
    public CallerContext resolve(String sessionToken) {
        Jwt jwt;
        try {
            jwt = decoder.decode(sessionToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw CallerIdentityException.invalid("Invalid session token");
        }
        if (!webClientId.equals(jwt.getClaimAsString("azp"))) {
            throw CallerIdentityException.forbidden("This endpoint requires the web channel");
        }
        if (!Channel.WEB.name().equals(jwt.getClaimAsString("channel")) || jwt.getSubject() == null) {
            throw CallerIdentityException.invalid("The session token's client does not match its channel");
        }
        Set<String> scopes = scopesOf(jwt.getClaim("scope")).stream()
                .filter(Channel.WEB.scopes()::contains)
                .collect(Collectors.toUnmodifiableSet());
        return new WebCaller(jwt.getSubject(), scopes);
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

    private record WebCaller(String customerId, Set<String> scopes) implements CallerContext {

        @Override
        public Channel channel() {
            return Channel.WEB;
        }

        @Override
        public Optional<String> terminalId() {
            return Optional.empty();
        }
    }
}

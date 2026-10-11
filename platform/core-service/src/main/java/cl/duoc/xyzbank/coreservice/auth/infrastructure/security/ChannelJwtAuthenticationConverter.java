package cl.duoc.xyzbank.coreservice.auth.infrastructure.security;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Grants a token only the scopes that belong to its channel (SCOPE_x) plus its channel itself
 * (CHANNEL_X), so no token can satisfy another channel's requirement.
 */
public class ChannelJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Channel channel = channelOf(jwt);
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("CHANNEL_" + channel.name()));
        scopesOf(jwt.getClaim("scope")).stream()
                .filter(channel.scopes()::contains)
                .forEach(scope -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope)));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    static Channel channelOf(Jwt jwt) {
        try {
            return Channel.valueOf(jwt.getClaimAsString("channel"));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
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
}

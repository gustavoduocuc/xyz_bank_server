package cl.duoc.xyzbank.interestsservice.shared.infrastructure.security;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

/** The client a token was issued to ("azp") must be the one registered for the token's channel. */
public class ChannelClientValidator implements OAuth2TokenValidator<Jwt> {

    private final Map<Channel, String> clientsByChannel;

    public ChannelClientValidator(Map<Channel, String> clientsByChannel) {
        this.clientsByChannel = Map.copyOf(clientsByChannel);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Channel channel = ChannelJwtAuthenticationConverter.channelOf(jwt);
        String clientId = jwt.getClaimAsString("azp");
        if (channel != null && clientId != null && clientId.equals(clientsByChannel.get(channel))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "The token's client does not match its channel", null));
    }
}

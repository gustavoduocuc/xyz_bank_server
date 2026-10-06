package cl.duoc.xyzbank.authserver.devices.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides who is revoking a device: the caller must authenticate as the mobile channel client
 * (HTTP Basic with its secret, 401 otherwise), and must present that device's current access
 * token, issued by this server to the mobile client for exactly that device (403 otherwise).
 * The token's subject is the customer on whose behalf the device is revoked.
 */
public class DeviceRevocationAuthenticator {

    private static final String BASIC_PREFIX = "Basic ";

    private final RegisteredClientRepository registeredClients;
    private final ChannelClientRepository channelClients;
    private final PasswordEncoder passwordEncoder;
    private final JwtDecoder jwtDecoder;

    public DeviceRevocationAuthenticator(
            RegisteredClientRepository registeredClients,
            ChannelClientRepository channelClients,
            PasswordEncoder passwordEncoder,
            JwtDecoder jwtDecoder) {
        this.registeredClients = registeredClients;
        this.channelClients = channelClients;
        this.passwordEncoder = passwordEncoder;
        this.jwtDecoder = jwtDecoder;
    }

    public CustomerId customerRevoking(String deviceId, String authorizationHeader, String accessToken) {
        String mobileClientId = authenticatedMobileClient(authorizationHeader)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bff-mobile client authentication required"));
        Jwt token = decode(accessToken);
        boolean issuedToMobileClient = mobileClientId.equals(token.getClaimAsString("azp"));
        boolean boundToDevice = Objects.equals(deviceId, token.getClaimAsString("device_id"));
        if (!issuedToMobileClient || !boundToDevice) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The token does not belong to this device");
        }
        return CustomerId.create(token.getSubject());
    }

    private Optional<String> authenticatedMobileClient(String authorizationHeader) {
        return basicCredentials(authorizationHeader).filter(credentials -> {
            RegisteredClient client = registeredClients.findByClientId(credentials[0]);
            boolean mobile = channelClients.findByClientId(credentials[0])
                    .map(channelClient -> channelClient.channel() == Channel.MOBILE)
                    .orElse(false);
            return mobile && client != null && passwordEncoder.matches(credentials[1], client.getClientSecret());
        }).map(credentials -> credentials[0]);
    }

    private static Optional<String[]> basicCredentials(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BASIC_PREFIX)) {
            return Optional.empty();
        }
        try {
            String decoded = new String(
                    Base64.getDecoder().decode(authorizationHeader.substring(BASIC_PREFIX.length())), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            return separator < 0
                    ? Optional.empty()
                    : Optional.of(new String[] {decoded.substring(0, separator), decoded.substring(separator + 1)});
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private Jwt decode(String accessToken) {
        try {
            return jwtDecoder.decode(accessToken);
        } catch (JwtException | IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid device access token is required");
        }
    }
}

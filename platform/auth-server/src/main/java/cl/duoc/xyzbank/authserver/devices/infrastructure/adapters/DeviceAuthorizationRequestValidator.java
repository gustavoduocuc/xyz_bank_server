package cl.duoc.xyzbank.authserver.devices.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.devices.application.usecases.AssertDeviceActiveUseCase;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;

import java.util.function.Consumer;

/**
 * Requires every mobile authorization request to name its device ("device_id") and refuses a
 * revoked device, before any code is issued. It runs after the redirect URI was validated, so
 * the refusal is reported to bff-mobile's registered redirect URI.
 */
public class DeviceAuthorizationRequestValidator implements Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> {

    public static final String DEVICE_ID_PARAMETER = "device_id";

    private final ChannelClientRepository channelClients;
    private final AssertDeviceActiveUseCase assertDeviceActive;

    public DeviceAuthorizationRequestValidator(
            ChannelClientRepository channelClients, AssertDeviceActiveUseCase assertDeviceActive) {
        this.channelClients = channelClients;
        this.assertDeviceActive = assertDeviceActive;
    }

    @Override
    public void accept(OAuth2AuthorizationCodeRequestAuthenticationContext context) {
        OAuth2AuthorizationCodeRequestAuthenticationToken request = context.getAuthentication();
        boolean mobileLogin = channelClients.findByClientId(request.getClientId())
                .map(client -> client.channel() == Channel.MOBILE)
                .orElse(false);
        if (!mobileLogin) {
            return;
        }
        Object deviceId = request.getAdditionalParameters().get(DEVICE_ID_PARAMETER);
        if (deviceId == null || deviceId.toString().isBlank()) {
            throw refusal(OAuth2ErrorCodes.INVALID_REQUEST, "A mobile login must name its device", request);
        }
        try {
            assertDeviceActive.execute(deviceId.toString());
        } catch (DomainException revoked) {
            throw refusal(OAuth2ErrorCodes.ACCESS_DENIED, "This device has been revoked", request);
        }
    }

    private static OAuth2AuthorizationCodeRequestAuthenticationException refusal(
            String errorCode, String description, OAuth2AuthorizationCodeRequestAuthenticationToken request) {
        return new OAuth2AuthorizationCodeRequestAuthenticationException(
                new OAuth2Error(errorCode, description, null), request);
    }
}

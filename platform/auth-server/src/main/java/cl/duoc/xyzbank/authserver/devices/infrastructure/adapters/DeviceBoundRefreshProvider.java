package cl.duoc.xyzbank.authserver.devices.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.devices.application.usecases.AssertDeviceActiveUseCase;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;

import java.util.Objects;
import java.util.Optional;

/**
 * A refresh of a device-bound login must name that same device ("device_id") and the device
 * must still be active; otherwise it is refused with invalid_grant before anything rotates,
 * so the rightful device's refresh token keeps working (the "belongs to a different device"
 * rule core-service enforced).
 */
public class DeviceBoundRefreshProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizations;
    private final AssertDeviceActiveUseCase assertDeviceActive;

    public DeviceBoundRefreshProvider(
            AuthenticationProvider delegate,
            OAuth2AuthorizationService authorizations,
            AssertDeviceActiveUseCase assertDeviceActive) {
        this.delegate = delegate;
        this.authorizations = authorizations;
        this.assertDeviceActive = assertDeviceActive;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2RefreshTokenAuthenticationToken refresh = (OAuth2RefreshTokenAuthenticationToken) authentication;
        Optional.ofNullable(authorizations.findByToken(refresh.getRefreshToken(), OAuth2TokenType.REFRESH_TOKEN))
                .flatMap(DeviceRegisteringCodeExchangeProvider::deviceIdOf)
                .ifPresent(boundDevice -> refuseUnlessSameActiveDevice(boundDevice, refresh));
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private void refuseUnlessSameActiveDevice(String boundDevice, OAuth2RefreshTokenAuthenticationToken refresh) {
        Object presentedDevice = refresh.getAdditionalParameters().get(DeviceAuthorizationRequestValidator.DEVICE_ID_PARAMETER);
        if (!Objects.equals(boundDevice, Objects.toString(presentedDevice, null))) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
        try {
            assertDeviceActive.execute(boundDevice);
        } catch (DomainException revoked) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
    }
}

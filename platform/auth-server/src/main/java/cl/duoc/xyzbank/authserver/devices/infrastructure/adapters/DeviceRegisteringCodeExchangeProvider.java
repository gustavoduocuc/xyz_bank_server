package cl.duoc.xyzbank.authserver.devices.infrastructure.adapters;

import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.application.usecases.AssertDeviceActiveUseCase;
import cl.duoc.xyzbank.authserver.devices.application.usecases.RegisterDeviceForLoginUseCase;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;

import java.util.Optional;

/**
 * Wraps the authorization-code exchange: a mobile login's device must still be active when
 * its code is exchanged, and once the exchange succeeds the device is registered for the
 * customer who logged in (first login from it) -- adopt-oauth2-tokens-between-services
 * design.md Decision 3.
 */
public class DeviceRegisteringCodeExchangeProvider implements AuthenticationProvider {

    private static final OAuth2TokenType AUTHORIZATION_CODE = new OAuth2TokenType("code");

    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizations;
    private final CustomerLoginRepository customerLogins;
    private final AssertDeviceActiveUseCase assertDeviceActive;
    private final RegisterDeviceForLoginUseCase registerDevice;

    public DeviceRegisteringCodeExchangeProvider(
            AuthenticationProvider delegate,
            OAuth2AuthorizationService authorizations,
            CustomerLoginRepository customerLogins,
            AssertDeviceActiveUseCase assertDeviceActive,
            RegisterDeviceForLoginUseCase registerDevice) {
        this.delegate = delegate;
        this.authorizations = authorizations;
        this.customerLogins = customerLogins;
        this.assertDeviceActive = assertDeviceActive;
        this.registerDevice = registerDevice;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String code = ((OAuth2AuthorizationCodeAuthenticationToken) authentication).getCode();
        Optional<OAuth2Authorization> login = Optional.ofNullable(authorizations.findByToken(code, AUTHORIZATION_CODE));
        Optional<String> deviceId = login.flatMap(DeviceRegisteringCodeExchangeProvider::deviceIdOf);
        deviceId.ifPresent(this::refuseRevokedDevice);

        Authentication result = delegate.authenticate(authentication);

        deviceId.ifPresent(device -> registerDevice.execute(device, customerIdOf(login.orElseThrow())));
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }

    private void refuseRevokedDevice(String deviceId) {
        try {
            assertDeviceActive.execute(deviceId);
        } catch (DomainException revoked) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }
    }

    private CustomerId customerIdOf(OAuth2Authorization login) {
        return customerLogins.findByUsername(login.getPrincipalName())
                .orElseThrow(() -> new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT))
                .customerId();
    }

    static Optional<String> deviceIdOf(OAuth2Authorization login) {
        return Optional.ofNullable(login.<OAuth2AuthorizationRequest>getAttribute(OAuth2AuthorizationRequest.class.getName()))
                .map(request -> request.getAdditionalParameters().get(DeviceAuthorizationRequestValidator.DEVICE_ID_PARAMETER))
                .map(Object::toString);
    }
}

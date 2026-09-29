package cl.duoc.xyzbank.bffmobile.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/**
 * Captures the native-app client's supplied device identifier (a query parameter on the
 * login-initiation request, e.g. GET /oauth2/authorization/oidc?deviceId=...) into the HTTP
 * session, so OidcLoginSuccessHandler can read it back once the OIDC round trip completes and
 * bind the resulting session JWT to that device (design.md Decision 4).
 */
public class DeviceCapturingAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    static final String DEVICE_ID_PARAMETER = "deviceId";
    public static final String DEVICE_ID_SESSION_ATTRIBUTE = "oidc.deviceId";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public DeviceCapturingAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository, String authorizationRequestBaseUri) {
        this.delegate =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, authorizationRequestBaseUri);
        this.delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        captureDeviceId(request);
        return withDeviceId(delegate.resolve(request), request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        captureDeviceId(request);
        return withDeviceId(delegate.resolve(request, clientRegistrationId), request);
    }

    private OAuth2AuthorizationRequest withDeviceId(OAuth2AuthorizationRequest authorization, HttpServletRequest request) {
        if (authorization == null) {
            return null;
        }
        String deviceId = request.getParameter(DEVICE_ID_PARAMETER);
        if (deviceId == null || deviceId.isBlank()) {
            return authorization;
        }
        return OAuth2AuthorizationRequest.from(authorization)
                .additionalParameters(parameters -> parameters.put("device_id", deviceId))
                .build();
    }

    private void captureDeviceId(HttpServletRequest request) {
        String deviceId = request.getParameter(DEVICE_ID_PARAMETER);
        if (deviceId != null && !deviceId.isBlank()) {
            request.getSession().setAttribute(DEVICE_ID_SESSION_ATTRIBUTE, deviceId);
        }
    }
}

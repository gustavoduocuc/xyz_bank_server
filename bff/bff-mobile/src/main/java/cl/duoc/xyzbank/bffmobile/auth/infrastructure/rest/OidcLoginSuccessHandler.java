package cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.auth.config.DeviceCapturingAuthorizationRequestResolver;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.MobileSessionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * On a successful OIDC login, returns the tokens the authorization server just issued for this
 * device: the access token is the session token and its refresh token travels with it. bff-mobile
 * mints nothing and calls no other service (bff-mobile-auth spec). The device identifier was
 * sent as {@code device_id} on the authorization request and is echoed in the access token.
 */
@Component
public class OidcLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(180);

    private final OAuth2AuthorizedClientRepository authorizedClients;
    private final ObjectMapper objectMapper;

    public OidcLoginSuccessHandler(
            OAuth2AuthorizedClientRepository authorizedClients, ObjectMapper objectMapper) {
        this.authorizedClients = authorizedClients;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (request.getSession().getAttribute(DeviceCapturingAuthorizationRequestResolver.DEVICE_ID_SESSION_ATTRIBUTE)
                == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        OAuth2AuthorizedClient client = authorizedClient(authentication, request);
        if (client == null || client.getRefreshToken() == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                new MobileSessionResponse(
                        client.getAccessToken().getTokenValue(),
                        client.getRefreshToken().getTokenValue(),
                        Instant.now().plus(REFRESH_TOKEN_LIFETIME)));
    }

    private OAuth2AuthorizedClient authorizedClient(Authentication authentication, HttpServletRequest request) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return null;
        }
        return authorizedClients.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), authentication, request);
    }
}

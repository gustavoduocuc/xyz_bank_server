package cl.duoc.xyzbank.bffweb.auth.infrastructure.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Keeps the tokens of a just-completed login only for the callback request, so
 * OidcLoginSuccessHandler can move them into the session cookies. Spring's default repository
 * would keep every customer's tokens in bff-web's memory; the cookies are the only place they
 * live afterward.
 */
public class RequestScopedAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    private static final String ATTRIBUTE_PREFIX = RequestScopedAuthorizedClientRepository.class.getName() + ".";

    @Override
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return (T) request.getAttribute(ATTRIBUTE_PREFIX + clientRegistrationId);
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient, Authentication principal, HttpServletRequest request,
            HttpServletResponse response) {
        request.setAttribute(
                ATTRIBUTE_PREFIX + authorizedClient.getClientRegistration().getRegistrationId(), authorizedClient);
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId, Authentication principal, HttpServletRequest request,
            HttpServletResponse response) {
        request.removeAttribute(ATTRIBUTE_PREFIX + clientRegistrationId);
    }
}

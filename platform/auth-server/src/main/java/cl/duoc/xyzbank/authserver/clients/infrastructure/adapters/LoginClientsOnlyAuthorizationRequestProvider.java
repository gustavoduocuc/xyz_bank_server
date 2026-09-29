package cl.duoc.xyzbank.authserver.clients.infrastructure.adapters;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Refuses an authorization request from a client that has no authorization_code grant (the
 * service clients) before Spring Authorization Server looks at its redirect URI. Without it,
 * Spring Authorization Server answers such a request by redirecting "unauthorized_client" to
 * whatever redirect_uri the request supplied -- an open redirect, since service clients have
 * no registered redirect URI to validate it against. Refused here, the endpoint answers 400
 * with no redirect, exactly as for an unknown client.
 */
public class LoginClientsOnlyAuthorizationRequestProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final RegisteredClientRepository registeredClients;

    public LoginClientsOnlyAuthorizationRequestProvider(
            AuthenticationProvider delegate, RegisteredClientRepository registeredClients) {
        this.delegate = delegate;
        this.registeredClients = registeredClients;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2AuthorizationCodeRequestAuthenticationToken request =
                (OAuth2AuthorizationCodeRequestAuthenticationToken) authentication;
        RegisteredClient client = registeredClients.findByClientId(request.getClientId());
        if (client != null && !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.UNAUTHORIZED_CLIENT, "This client cannot start a customer login", null));
        }
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}

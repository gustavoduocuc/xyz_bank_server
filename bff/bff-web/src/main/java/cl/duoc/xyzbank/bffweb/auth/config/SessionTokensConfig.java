package cl.duoc.xyzbank.bffweb.auth.config;

import cl.duoc.xyzbank.bffweb.auth.infrastructure.rest.RequestScopedAuthorizedClientRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * The tokens of a just-completed login must be readable by {@code OidcLoginSuccessHandler}
 * and then live only in the session cookies. Spring's default repository would keep every
 * customer's tokens in bff-web's memory.
 */
@Configuration
public class SessionTokensConfig {

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new RequestScopedAuthorizedClientRepository();
    }
}

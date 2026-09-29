package cl.duoc.xyzbank.bffmobile.auth.config;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.RequestScopedAuthorizedClientRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

@Configuration
public class SessionTokensConfig {

    @Bean
    public OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new RequestScopedAuthorizedClientRepository();
    }
}

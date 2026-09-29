package cl.duoc.xyzbank.authserver.tokens.config;

import cl.duoc.xyzbank.authserver.clients.domain.repositories.ChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.domain.repositories.ServiceClientRepository;
import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;
import cl.duoc.xyzbank.authserver.tokens.application.usecases.IssueTokenClaimsUseCase;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.ChannelClaimsTokenCustomizer;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.ClientProfileLookupAdapter;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.CustomerLoginLookupAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration
public class TokensConfig {

    @Bean
    public IssueTokenClaimsUseCase issueTokenClaimsUseCase(
            ChannelClientRepository channelClientRepository,
            ServiceClientRepository serviceClientRepository,
            CustomerLoginRepository customerLoginRepository) {
        return new IssueTokenClaimsUseCase(
                new ClientProfileLookupAdapter(channelClientRepository, serviceClientRepository),
                new CustomerLoginLookupAdapter(customerLoginRepository));
    }

    /**
     * Picked up by Spring Authorization Server's JwtGenerator for every JWT it issues.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> channelClaimsTokenCustomizer(
            IssueTokenClaimsUseCase issueTokenClaimsUseCase) {
        return new ChannelClaimsTokenCustomizer(issueTokenClaimsUseCase);
    }
}

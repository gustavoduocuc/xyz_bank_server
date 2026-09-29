package cl.duoc.xyzbank.interestsservice.shared.config;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.adapters.ClientCredentialsServiceTokenAdapter;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.AccessTokenValidator;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AccessTokenConfig {

    @Bean
    public AccessTokenValidator accessTokenValidator(AuthProperties properties) {
        Map<String, Channel> channelsByClientId = properties.channelClients().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        return new AccessTokenValidator(
                AccessTokenValidator.decoderFor(properties.jwkSetUri(), properties.issuer(), properties.audience()),
                channelsByClientId);
    }

    /**
     * interests-service's own token for its balance and credit calls to core-service. A plain
     * RestClient on purpose: auth-server is not registered in Eureka.
     */
    @Bean
    public ServiceTokenPort serviceTokenPort(AuthProperties properties) {
        return new ClientCredentialsServiceTokenAdapter(
                RestClient.create(), properties.tokenUri(), properties.clientId(), properties.clientSecret(),
                Clock.systemUTC());
    }
}

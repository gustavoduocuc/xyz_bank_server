package cl.duoc.xyzbank.interestsservice.shared.config;

import cl.duoc.xyzbank.interestsservice.interests.application.ports.ServiceTokenPort;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.adapters.ClientCredentialsServiceTokenAdapter;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.AccessTokenValidator;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    public ServiceTokenPort serviceTokenPort(
            AuthProperties properties,
            @Value("${auth.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${auth.read-timeout-ms:3000}") int readTimeoutMs) {
        // add-resilience4j-to-bffs design.md Decision 2: token issuance signs and writes to
        // Postgres, 3 s bounds it; a connect to a live container takes ms
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new ClientCredentialsServiceTokenAdapter(
                RestClient.builder().requestFactory(requestFactory).build(),
                properties.tokenUri(), properties.clientId(), properties.clientSecret(), Clock.systemUTC());
    }
}

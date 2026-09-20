package cl.duoc.xyzbank.interestsservice.shared.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreServiceClientConfig {

    @Bean
    public RestClient coreServiceClient(
            @Value("${core-service.base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${core-service.read-timeout-ms:3000}") int readTimeout,
            @Value("${core-service.service-credential}") String serviceCredential) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Service-Credential", serviceCredential)
                .build();
    }
}

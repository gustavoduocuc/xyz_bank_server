package cl.duoc.xyzbank.bffatm.shared.config;

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
            @Value("${core-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${core-service.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}

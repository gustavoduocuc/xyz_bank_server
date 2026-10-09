package cl.duoc.xyzbank.bffatm.shared.config;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.AtmSessionClientInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.ClientCredentialsTokenInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.CorrelationIdClientInterceptor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class CoreServiceClientConfig {

    @Bean
    public ClientCredentialsTokenInterceptor clientCredentialsTokenInterceptor(
            @Value("${auth-server.token-uri}") String tokenUri,
            @Value("${auth-server.client-id}") String clientId,
            @Value("${auth-server.client-secret}") String clientSecret,
            @Value("${auth-server.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${auth-server.read-timeout-ms}") int readTimeoutMs,
            RetryRegistry retries,
            CircuitBreakerRegistry circuitBreakers) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return new ClientCredentialsTokenInterceptor(
                RestClient.builder().requestFactory(requestFactory).build(),
                tokenUri, clientId, clientSecret, Clock.systemUTC(),
                retries.retry("authServerToken"), circuitBreakers.circuitBreaker("authServer"));
    }

    @Bean
    @Primary
    public RestClient coreServiceClient(
            ObservationRegistry observationRegistry,
            @Value("${core-service.base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${core-service.read-timeout-ms}") int readTimeoutMs,
            CorrelationIdClientInterceptor correlationIdClientInterceptor,
            BearerTokenClientInterceptor bearerTokenClientInterceptor,
            AtmSessionClientInterceptor atmSessionClientInterceptor,
            ClientCredentialsTokenInterceptor clientCredentialsTokenInterceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return RestClient.builder()
                .observationRegistry(observationRegistry)
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(correlationIdClientInterceptor)
                .requestInterceptor(bearerTokenClientInterceptor)
                .requestInterceptor(atmSessionClientInterceptor)
                .requestInterceptor(clientCredentialsTokenInterceptor)
                .build();
    }

    /**
     * Used only for {@code POST /internal/auth/atm/pin-verifications}, core-service's
     * TLS-only PIN-verification connector (design.md Decision 8). Trusts the same shared
     * dev CA {@code bff-atm} already trusts for its own mTLS connector, since core-service's
     * PIN-verification certificate is signed by that same CA.
     */
    @Bean
    public RestClient corePinVerificationClient(
            ObservationRegistry observationRegistry,
            @Value("${core-service.pin-verification-base-url}") String baseUrl,
            @Value("${core-service.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${core-service.read-timeout-ms}") int readTimeoutMs,
            @Value("${server.ssl.trust-store}") String trustStorePath,
            @Value("${server.ssl.trust-store-password}") String trustStorePassword,
            ResourceLoader resourceLoader,
            CorrelationIdClientInterceptor correlationIdClientInterceptor,
            ClientCredentialsTokenInterceptor clientCredentialsTokenInterceptor)
            throws GeneralSecurityException, IOException {
        SSLContext sslContext = trustingSslContext(resourceLoader, trustStorePath, trustStorePassword);
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder()
                .observationRegistry(observationRegistry)
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(correlationIdClientInterceptor)
                .requestInterceptor(clientCredentialsTokenInterceptor)
                .build();
    }

    private static SSLContext trustingSslContext(ResourceLoader resourceLoader, String trustStorePath, String trustStorePassword)
            throws GeneralSecurityException, IOException {
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = resourceLoader.getResource(trustStorePath).getInputStream()) {
            trustStore.load(in, trustStorePassword.toCharArray());
        }
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }
}

package cl.duoc.xyzbank.bffatm.shared.config;

import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.AtmSessionClientInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.BearerTokenClientInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.ClientCredentialsTokenInterceptor;
import cl.duoc.xyzbank.bffatm.shared.infrastructure.rest.CorrelationIdClientInterceptor;
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
            @Value("${auth-server.client-secret}") String clientSecret) {
        return new ClientCredentialsTokenInterceptor(
                RestClient.create(), tokenUri, clientId, clientSecret, Clock.systemUTC());
    }

    @Bean
    @Primary
    public RestClient coreServiceClient(
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

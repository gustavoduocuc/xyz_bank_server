package cl.duoc.xyzbank.bffweb.auth.config;

import cl.duoc.xyzbank.bffweb.shared.infrastructure.rest.CircuitBreakerClientInterceptor;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

/**
 * Every client bff-web talks to the authorization server with: the same connect/read timeouts
 * and the same "authServer" circuit breaker, so login, refresh and the JWK set fetch all fail
 * fast together and none of them is ever retried (add-resilience4j-to-bffs design.md Decision 7).
 * Deliberately not RestClient beans themselves: bff-web resolves its other RestClients by name.
 */
public class AuthServerHttpClients {

    private final ClientHttpRequestFactory requestFactory;
    private final CircuitBreakerClientInterceptor circuitBreaker;

    public AuthServerHttpClients(
            ClientHttpRequestFactory requestFactory, CircuitBreakerClientInterceptor circuitBreaker) {
        this.requestFactory = requestFactory;
        this.circuitBreaker = circuitBreaker;
    }

    /** For bff-web's own token endpoint calls (refresh). */
    public RestClient restClient() {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(circuitBreaker)
                .build();
    }

    /** For Nimbus' JWK set fetch. */
    public RestTemplate restOperations() {
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getInterceptors().add(circuitBreaker);
        return restTemplate;
    }

    /**
     * Spring Security's login code exchange, with its OAuth 2.0 converters and error handler. An
     * open breaker is reported as a failed token response: Spring Security only translates
     * RestClientExceptions, and anything else would escape the login as a 500.
     */
    public OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> codeExchangeClient() {
        RestClientAuthorizationCodeTokenResponseClient client = restClientCodeExchange();
        return grantRequest -> {
            try {
                return client.getTokenResponse(grantRequest);
            } catch (CallNotPermittedException exception) {
                throw new OAuth2AuthorizationException(
                        new OAuth2Error("invalid_token_response", "The authorization server is unavailable", null),
                        exception);
            }
        };
    }

    private RestClientAuthorizationCodeTokenResponseClient restClientCodeExchange() {
        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(circuitBreaker)
                .messageConverters(messageConverters -> {
                    messageConverters.clear();
                    messageConverters.add(new FormHttpMessageConverter());
                    messageConverters.add(new OAuth2AccessTokenResponseHttpMessageConverter());
                })
                .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
                .build();
        RestClientAuthorizationCodeTokenResponseClient client = new RestClientAuthorizationCodeTokenResponseClient();
        client.setRestClient(restClient);
        return client;
    }
}

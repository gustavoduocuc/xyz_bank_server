package cl.duoc.xyzbank.bffmobile.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Wires spring-boot-starter-oauth2-client for the "OAuth2 for native apps" authorization-code
 * + PKCE handshake only (design.md Decision 3 and 4). Every request is otherwise permitted
 * through unauthenticated; the rest of bff-mobile keeps resolving identity from the device-
 * bound JWT the mobile client presents as a bearer token, never from Spring Security's
 * SecurityContext. The handshake itself needs a session (see DeviceCapturingAuthorizationRequestResolver
 * and Spring's own state/PKCE round trip); that session is created on demand for exactly that
 * round trip and never consulted again afterward.
 */
@Configuration
public class OidcLoginSecurityConfig {

    private static final String AUTHORIZATION_REQUEST_BASE_URI = "/oauth2/authorization";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ClientRegistrationRepository clientRegistrationRepository,
            AuthenticationSuccessHandler oidcLoginSuccessHandler,
            AuthenticationFailureHandler oidcLoginFailureHandler)
            throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .oauth2Login(oauth2Login -> oauth2Login
                        .authorizationEndpoint(authorization -> authorization.authorizationRequestResolver(
                                new DeviceCapturingAuthorizationRequestResolver(
                                        clientRegistrationRepository, AUTHORIZATION_REQUEST_BASE_URI)))
                        .successHandler(oidcLoginSuccessHandler)
                        .failureHandler(oidcLoginFailureHandler));
        return http.build();
    }

    /**
     * The provider is configured with explicit endpoints rather than an issuer-uri (the user
     * agent and this container reach it under different host names), and in that mode Spring
     * does not check the ID token's "iss". This adds that check back against the configured
     * public issuer; oauth2Login picks the bean up by type.
     */
    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory(
            @Value("${oidc.expected-issuer}") String expectedIssuer) {
        OidcIdTokenDecoderFactory decoderFactory = new OidcIdTokenDecoderFactory();
        decoderFactory.setJwtValidatorFactory(clientRegistration -> new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new OidcIdTokenValidator(clientRegistration),
                new JwtIssuerValidator(expectedIssuer)));
        return decoderFactory;
    }
}

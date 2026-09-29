package cl.duoc.xyzbank.authserver.shared.config;

import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.LoginClientsOnlyAuthorizationRequestProvider;
import cl.duoc.xyzbank.authserver.customers.domain.repositories.CustomerLoginRepository;
import cl.duoc.xyzbank.authserver.devices.application.usecases.AssertDeviceActiveUseCase;
import cl.duoc.xyzbank.authserver.devices.application.usecases.RegisterDeviceForLoginUseCase;
import cl.duoc.xyzbank.authserver.devices.infrastructure.adapters.DeviceAuthorizationRequestValidator;
import cl.duoc.xyzbank.authserver.devices.infrastructure.adapters.DeviceBoundRefreshProvider;
import cl.duoc.xyzbank.authserver.devices.infrastructure.adapters.DeviceRegisteringCodeExchangeProvider;
import cl.duoc.xyzbank.authserver.sessions.application.usecases.DetectRefreshTokenReuseUseCase;
import cl.duoc.xyzbank.authserver.sessions.infrastructure.adapters.RefreshTokenGrantGuard;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Two filter chains: the OAuth2/OIDC protocol endpoints, and the customer-facing form login
 * (plus the unauthenticated health endpoint). The issuer is pinned so every token and the
 * discovery document carry the public URL whether a request came from a browser
 * ("localhost") or a BFF container ("auth-server") -- design.md Decision 1.
 */
@Configuration
public class AuthorizationServerSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            RegisteredClientRepository registeredClientRepository,
            DeviceAuthorizationRequestValidator deviceAuthorizationRequestValidator,
            OAuth2AuthorizationService authorizationService,
            CustomerLoginRepository customerLoginRepository,
            AssertDeviceActiveUseCase assertDeviceActive,
            RegisterDeviceForLoginUseCase registerDevice,
            DetectRefreshTokenReuseUseCase detectRefreshTokenReuse) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server
                        .oidc(Customizer.withDefaults())
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers ->
                                providers.replaceAll(provider -> authorizationRequestProvider(
                                        provider, registeredClientRepository, deviceAuthorizationRequestValidator))))
                        .tokenEndpoint(endpoint -> endpoint.authenticationProviders(providers ->
                                providers.replaceAll(provider -> tokenProvider(provider, authorizationService,
                                        customerLoginRepository, assertDeviceActive, registerDevice,
                                        detectRefreshTokenReuse)))))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    /**
     * Token requests: an authorization-code exchange registers the login's device; a refresh of
     * a device-bound login must come from that active device, and is refused unless its token is
     * a login's current one (a replayed token revokes its login).
     */
    private static AuthenticationProvider tokenProvider(
            AuthenticationProvider provider,
            OAuth2AuthorizationService authorizationService,
            CustomerLoginRepository customerLoginRepository,
            AssertDeviceActiveUseCase assertDeviceActive,
            RegisterDeviceForLoginUseCase registerDevice,
            DetectRefreshTokenReuseUseCase detectRefreshTokenReuse) {
        if (provider instanceof OAuth2AuthorizationCodeAuthenticationProvider) {
            return new DeviceRegisteringCodeExchangeProvider(
                    provider, authorizationService, customerLoginRepository, assertDeviceActive, registerDevice);
        }
        if (provider instanceof OAuth2RefreshTokenAuthenticationProvider) {
            return new DeviceBoundRefreshProvider(
                    new RefreshTokenGrantGuard(provider, detectRefreshTokenReuse), authorizationService, assertDeviceActive);
        }
        return provider;
    }

    /**
     * Authorization requests: service clients are refused before any redirect, and mobile
     * logins must name an active device (validated after the redirect URI and scopes).
     */
    private static AuthenticationProvider authorizationRequestProvider(
            AuthenticationProvider provider,
            RegisteredClientRepository registeredClientRepository,
            DeviceAuthorizationRequestValidator deviceAuthorizationRequestValidator) {
        if (!(provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider requestProvider)) {
            return provider;
        }
        requestProvider.setAuthenticationValidator(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
                .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR)
                .andThen(deviceAuthorizationRequestValidator));
        return new LoginClientsOnlyAuthorizationRequestProvider(requestProvider, registeredClientRepository);
    }

    @Bean
    @Order(2)
    public SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(@Value("${auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}

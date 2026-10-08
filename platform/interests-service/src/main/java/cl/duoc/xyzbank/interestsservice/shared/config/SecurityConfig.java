package cl.duoc.xyzbank.interestsservice.shared.config;

import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.ChannelClientValidator;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.ChannelJwtAuthenticationConverter;
import cl.duoc.xyzbank.interestsservice.shared.infrastructure.security.ProblemDetailSecurityHandlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

/**
 * interests-service is an OAuth2 resource server: tokens are verified against auth-server's keys
 * (issuer, audience and keys in application.yml), a token gets only its channel's scopes and each
 * endpoint needs its scope.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public OAuth2TokenValidator<Jwt> channelClientValidator(AuthProperties properties) {
        return new ChannelClientValidator(properties.channelClients());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        ProblemDetailSecurityHandlers problems = new ProblemDetailSecurityHandlers(objectMapper);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/accounts/*/interest-summary")
                        .hasAuthority("SCOPE_web:interests:read")
                        .requestMatchers(HttpMethod.POST, "/accounts/*/interest-applications")
                        .hasAuthority("SCOPE_interests:write")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ChannelJwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .build();
    }
}

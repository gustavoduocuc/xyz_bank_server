package cl.duoc.xyzbank.coreservice.auth.config;

import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.security.ChannelClientValidator;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.security.ChannelJwtAuthenticationConverter;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.security.OwnershipAuthorizationManager;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.security.OwnershipAuthorizationManager.IdentifierType;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.security.ProblemDetailSecurityHandlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Arrays;

/**
 * core-service is an OAuth2 resource server: tokens are verified against auth-server's keys
 * (issuer, audience and keys in application.yml), a token gets only its channel's scopes, every
 * domain endpoint needs one of its scopes and, for customer resources, ownership.
 */
@Configuration
@EnableConfigurationProperties(ChannelClientProperties.class)
public class SecurityConfig {

    @Bean
    public OAuth2TokenValidator<Jwt> channelClientValidator(ChannelClientProperties properties) {
        return new ChannelClientValidator(properties.channelClients());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            AtmSessionLookup atmSessions,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository)
            throws Exception {
        ProblemDetailSecurityHandlers problems = new ProblemDetailSecurityHandlers(objectMapper);
        OwnershipFactory owner = (type, variable) ->
                new OwnershipAuthorizationManager(type, variable, atmSessions, accountRepository, transactionRepository);
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/internal/auth/atm/**").hasAuthority("CHANNEL_ATM")
                        .requestMatchers(HttpMethod.GET, "/internal/customers/{customerId}/accounts")
                        .access(scopedAndOwned(
                                owner.of(IdentifierType.CUSTOMER_ID, "customerId"), "web:accounts:read"))
                        .requestMatchers(HttpMethod.GET, "/internal/accounts/{accountId}/balance")
                        .access(scopedAndOwned(
                                owner.of(IdentifierType.ACCOUNT_ID, "accountId"),
                                "web:accounts:read", "mobile:accounts:read", "atm:read-balance", "interests:write"))
                        .requestMatchers(HttpMethod.GET, "/internal/accounts/{accountId}/transactions")
                        .access(scopedAndOwned(
                                owner.of(IdentifierType.ACCOUNT_ID, "accountId"),
                                "web:transactions:read", "mobile:transactions:read"))
                        .requestMatchers(HttpMethod.GET, "/internal/accounts/{accountId}/interest-summary")
                        .access(scopedAndOwned(
                                owner.of(IdentifierType.ACCOUNT_ID, "accountId"), "web:interests:read"))
                        .requestMatchers(HttpMethod.GET, "/internal/transactions/{transactionId}")
                        .access(scopedAndOwned(
                                owner.of(IdentifierType.TRANSACTION_ID, "transactionId"),
                                "web:transactions:read", "mobile:transactions:read"))
                        .requestMatchers(HttpMethod.POST, "/internal/accounts/{accountId}/withdrawals")
                        .access(scopedAndOwned(
                                owner.of(IdentifierType.ACCOUNT_ID, "accountId"), "atm:withdraw"))
                        .requestMatchers(HttpMethod.POST, "/internal/accounts/*/interest-credits")
                        .hasAuthority("SCOPE_interests:write")
                        .requestMatchers(HttpMethod.POST, "/internal/accounts").hasAuthority("SCOPE_accounts:write")
                        .requestMatchers(HttpMethod.PATCH, "/internal/accounts/*")
                        .hasAuthority("SCOPE_accounts:write")
                        .requestMatchers(HttpMethod.POST, "/internal/accounts/*/closure")
                        .hasAuthority("SCOPE_accounts:write")
                        .requestMatchers(HttpMethod.POST, "/internal/postings")
                        .hasAuthority("SCOPE_postings:write")
                        .requestMatchers("/internal/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ChannelJwtAuthenticationConverter()))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .build();
    }

    /** The scope check first, so a wrong scope is 403 whoever owns the resource; then ownership. */
    private static AuthorizationManager<RequestAuthorizationContext> scopedAndOwned(
            AuthorizationManager<RequestAuthorizationContext> ownership, String... scopes) {
        AuthorizationManager<RequestAuthorizationContext> scopeCheck = AuthorityAuthorizationManager.hasAnyAuthority(
                Arrays.stream(scopes).map(scope -> "SCOPE_" + scope).toArray(String[]::new));
        return (authentication, context) -> {
            AuthorizationDecision decision = scopeCheck.check(authentication, context);
            return decision != null && decision.isGranted() ? ownership.check(authentication, context) : decision;
        };
    }

    @FunctionalInterface
    private interface OwnershipFactory {
        AuthorizationManager<RequestAuthorizationContext> of(IdentifierType type, String variableName);
    }
}

package cl.duoc.xyzbank.authserver.tokens.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientMapper;
import cl.duoc.xyzbank.authserver.clients.unit.InMemoryChannelClientRepository;
import cl.duoc.xyzbank.authserver.clients.unit.InMemoryServiceClientRepository;
import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.customers.unit.InMemoryCustomerLoginRepository;
import cl.duoc.xyzbank.authserver.tokens.application.usecases.IssueTokenClaimsUseCase;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.ChannelClaimsTokenCustomizer;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.ClientProfileLookupAdapter;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.CustomerLoginLookupAdapter;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("The ChannelClaimsTokenCustomizer")
class ChannelClaimsTokenCustomizerTest {

    /*
     * Cases:
     * 1. Stamps the customer id as "sub" and WEB as "channel" on tokens issued to bff-web
     * 2. Stamps MOBILE as "channel" on tokens issued to bff-mobile
     * 3. Overwrites any "channel" value already present, so only the client decides it
     * 4. Leaves tokens that carry no customer identity (e.g. refresh tokens) untouched
     * 5. Stamps azp and the client's audiences on access tokens, leaving the ID token's audience
     * 6. Binds a mobile login's access token to the device named in its authorization request
     * 7. Stamps a service token with the service itself as subject and azp, its channel and audience
     * 8. Overwrites any "azp" value already present
     */

    private static final String SEED_CUSTOMER = "11111111-1111-1111-1111-111111111111";

    private final InMemoryChannelClientRepository channelClients;
    private final InMemoryServiceClientRepository serviceClients;
    private final ChannelRegisteredClientMapper registeredClientMapper;
    private final ChannelClaimsTokenCustomizer customizer;

    ChannelClaimsTokenCustomizerTest() {
        channelClients = new InMemoryChannelClientRepository(
                ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL,
                        "https://localhost:8081/login/oauth2/code/oidc"),
                ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.CONFIDENTIAL,
                        "https://localhost:8082/login/oauth2/code/oidc"));
        serviceClients = new InMemoryServiceClientRepository(ServiceClient.create("interests-service", Channel.INTERESTS));
        InMemoryCustomerLoginRepository customerLogins = new InMemoryCustomerLoginRepository(
                CustomerLogin.create("demo", "{noop}demo-password", CustomerId.create(SEED_CUSTOMER)));
        registeredClientMapper = new ChannelRegisteredClientMapper(Map.of(
                "bff-web", "{noop}secret", "bff-mobile", "{noop}secret", "interests-service", "{noop}secret"));
        customizer = new ChannelClaimsTokenCustomizer(new IssueTokenClaimsUseCase(
                new ClientProfileLookupAdapter(channelClients, serviceClients),
                new CustomerLoginLookupAdapter(customerLogins)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", OidcParameterNames.ID_TOKEN})
    @DisplayName("stamps the customer id and the WEB channel on tokens issued to bff-web")
    void stampsTheCustomerIdAndTheWebChannelOnTokensIssuedToBffWeb(String tokenType) {
        JwtClaimsSet claims = customizeLogin("bff-web", tokenType, JwtClaimsSet.builder().subject("demo"), null);

        assertEquals(SEED_CUSTOMER, claims.getSubject());
        assertEquals("WEB", claims.getClaimAsString("channel"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", OidcParameterNames.ID_TOKEN})
    @DisplayName("stamps the MOBILE channel on tokens issued to bff-mobile")
    void stampsTheMobileChannelOnTokensIssuedToBffMobile(String tokenType) {
        JwtClaimsSet claims = customizeLogin("bff-mobile", tokenType, JwtClaimsSet.builder().subject("demo"), "D1");

        assertEquals(SEED_CUSTOMER, claims.getSubject());
        assertEquals("MOBILE", claims.getClaimAsString("channel"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", OidcParameterNames.ID_TOKEN})
    @DisplayName("lets only the client decide the channel, overwriting any value already present")
    void letsOnlyTheClientDecideTheChannel(String tokenType) {
        JwtClaimsSet.Builder preset = JwtClaimsSet.builder().subject("demo").claim("channel", "MOBILE");

        JwtClaimsSet claims = customizeLogin("bff-web", tokenType, preset, null);

        assertEquals("WEB", claims.getClaimAsString("channel"));
    }

    @Test
    @DisplayName("leaves tokens that carry no customer identity untouched")
    void leavesTokensThatCarryNoCustomerIdentityUntouched() {
        JwtClaimsSet.Builder refreshTokenClaims = JwtClaimsSet.builder().subject("demo");

        JwtClaimsSet claims =
                customizeLogin("bff-web", OAuth2TokenType.REFRESH_TOKEN.getValue(), refreshTokenClaims, null);

        assertEquals("demo", claims.getSubject());
        assertNull(claims.getClaim("channel"));
    }

    @Test
    @DisplayName("stamps azp and the client's audiences on access tokens, leaving the ID token's audience")
    void stampsAzpAndTheClientsAudiencesOnAccessTokens() {
        JwtClaimsSet accessToken =
                customizeLogin("bff-web", "access_token", JwtClaimsSet.builder().subject("demo"), null);
        JwtClaimsSet idToken = customizeLogin(
                "bff-web", OidcParameterNames.ID_TOKEN, JwtClaimsSet.builder().subject("demo").audience(List.of("bff-web")), null);

        assertEquals("bff-web", accessToken.getClaimAsString("azp"));
        assertEquals(Set.of("core-service", "interests-service", "customers-service"), Set.copyOf(accessToken.getAudience()));
        assertEquals(List.of("bff-web"), idToken.getAudience());
        assertNull(idToken.getClaim("azp"));
    }

    @Test
    @DisplayName("binds a mobile login's access token to the device named in its authorization request")
    void bindsAMobileLoginsAccessTokenToItsDevice() {
        JwtClaimsSet claims = customizeLogin("bff-mobile", "access_token", JwtClaimsSet.builder().subject("demo"), "D1");

        assertEquals("D1", claims.getClaimAsString("device_id"));
        assertEquals(Set.of("core-service"), Set.copyOf(claims.getAudience()));
    }

    @Test
    @DisplayName("stamps a service token with the service itself as subject and azp")
    void stampsAServiceTokenWithTheServiceItselfAsSubjectAndAzp() {
        RegisteredClient registeredClient =
                registeredClientMapper.toRegisteredClient(serviceClients.findByClientId("interests-service").orElseThrow());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().subject("interests-service");
        JwtEncodingContext context = JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
                .registeredClient(registeredClient)
                .principal(new OAuth2ClientAuthenticationToken(
                        registeredClient, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, null))
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();

        customizer.customize(context);

        JwtClaimsSet built = claims.build();
        assertEquals("interests-service", built.getSubject());
        assertEquals("interests-service", built.getClaimAsString("azp"));
        assertEquals("INTERESTS", built.getClaimAsString("channel"));
        assertEquals(Set.of("core-service", "interests-service"), Set.copyOf(built.getAudience()));
        assertEquals(Set.of("interests:write"), Set.copyOf(built.getClaimAsStringList("scope")));
        assertNull(built.getClaim("device_id"));
    }

    @Test
    @DisplayName("overwrites any azp value already present")
    void overwritesAnyAzpValueAlreadyPresent() {
        JwtClaimsSet.Builder preset = JwtClaimsSet.builder().subject("demo").claim("azp", "bff-mobile");

        JwtClaimsSet claims = customizeLogin("bff-web", "access_token", preset, null);

        assertEquals("bff-web", claims.getClaimAsString("azp"));
    }

    private JwtClaimsSet customizeLogin(
            String clientId, String tokenType, JwtClaimsSet.Builder claims, String deviceId) {
        RegisteredClient registeredClient =
                registeredClientMapper.toRegisteredClient(channelClients.findByClientId(clientId).orElseThrow());
        OAuth2AuthorizationRequest authorizationRequest = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://localhost:9000/oauth2/authorize")
                .clientId(clientId)
                .additionalParameters(deviceId == null ? Map.of() : Map.of("device_id", deviceId))
                .build();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName("demo")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(), authorizationRequest)
                .build();
        JwtEncodingContext context = JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
                .registeredClient(registeredClient)
                .principal(UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of()))
                .authorization(authorization)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .tokenType(new OAuth2TokenType(tokenType))
                .build();

        customizer.customize(context);

        return claims.build();
    }
}

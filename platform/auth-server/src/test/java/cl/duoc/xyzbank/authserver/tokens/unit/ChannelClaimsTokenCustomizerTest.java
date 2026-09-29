package cl.duoc.xyzbank.authserver.tokens.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ChannelClient;
import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ClientType;
import cl.duoc.xyzbank.authserver.clients.infrastructure.adapters.ChannelRegisteredClientRepository;
import cl.duoc.xyzbank.authserver.clients.unit.InMemoryChannelClientRepository;
import cl.duoc.xyzbank.authserver.customers.domain.entities.CustomerLogin;
import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.customers.unit.InMemoryCustomerLoginRepository;
import cl.duoc.xyzbank.authserver.tokens.application.usecases.IssueTokenClaimsUseCase;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.ChannelClaimsTokenCustomizer;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.ChannelClientLookupAdapter;
import cl.duoc.xyzbank.authserver.tokens.infrastructure.adapters.CustomerLoginLookupAdapter;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The ChannelClaimsTokenCustomizer")
class ChannelClaimsTokenCustomizerTest {

    /*
     * Cases:
     * 1. Stamps the customer id as "sub" and WEB as "channel" on tokens issued to bff-web
     * 2. Stamps MOBILE as "channel" on tokens issued to bff-mobile
     * 3. Overwrites any "channel" value already present, so only the client decides it
     */

    private static final String SEED_CUSTOMER = "11111111-1111-1111-1111-111111111111";

    private final ChannelRegisteredClientRepository registeredClients;
    private final ChannelClaimsTokenCustomizer customizer;

    ChannelClaimsTokenCustomizerTest() {
        InMemoryChannelClientRepository channelClients = new InMemoryChannelClientRepository(
                ChannelClient.create("bff-web", Channel.WEB, ClientType.CONFIDENTIAL,
                        "https://localhost:8081/login/oauth2/code/oidc"),
                ChannelClient.create("bff-mobile", Channel.MOBILE, ClientType.PUBLIC,
                        "https://localhost:8082/login/oauth2/code/oidc"));
        InMemoryCustomerLoginRepository customerLogins = new InMemoryCustomerLoginRepository(
                CustomerLogin.create("demo", "{noop}demo-password", CustomerId.create(SEED_CUSTOMER)));
        registeredClients = new ChannelRegisteredClientRepository(channelClients, Map.of("bff-web", "{noop}secret"));
        customizer = new ChannelClaimsTokenCustomizer(new IssueTokenClaimsUseCase(
                new ChannelClientLookupAdapter(channelClients), new CustomerLoginLookupAdapter(customerLogins)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", OidcParameterNames.ID_TOKEN})
    @DisplayName("stamps the customer id and the WEB channel on tokens issued to bff-web")
    void stampsTheCustomerIdAndTheWebChannelOnTokensIssuedToBffWeb(String tokenType) {
        JwtClaimsSet claims = customize("bff-web", tokenType, JwtClaimsSet.builder().subject("demo"));

        assertEquals(SEED_CUSTOMER, claims.getSubject());
        assertEquals("WEB", claims.getClaimAsString("channel"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", OidcParameterNames.ID_TOKEN})
    @DisplayName("stamps the MOBILE channel on tokens issued to bff-mobile")
    void stampsTheMobileChannelOnTokensIssuedToBffMobile(String tokenType) {
        JwtClaimsSet claims = customize("bff-mobile", tokenType, JwtClaimsSet.builder().subject("demo"));

        assertEquals(SEED_CUSTOMER, claims.getSubject());
        assertEquals("MOBILE", claims.getClaimAsString("channel"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", OidcParameterNames.ID_TOKEN})
    @DisplayName("lets only the client decide the channel, overwriting any value already present")
    void letsOnlyTheClientDecideTheChannel(String tokenType) {
        JwtClaimsSet.Builder preset = JwtClaimsSet.builder().subject("demo").claim("channel", "MOBILE");

        JwtClaimsSet claims = customize("bff-web", tokenType, preset);

        assertEquals("WEB", claims.getClaimAsString("channel"));
    }

    private JwtClaimsSet customize(String clientId, String tokenType, JwtClaimsSet.Builder claims) {
        RegisteredClient registeredClient = registeredClients.findByClientId(clientId);
        JwtEncodingContext context = JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), claims)
                .registeredClient(registeredClient)
                .principal(UsernamePasswordAuthenticationToken.authenticated("demo", null, List.of()))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .tokenType(new OAuth2TokenType(tokenType))
                .build();

        customizer.customize(context);

        return claims.build();
    }
}

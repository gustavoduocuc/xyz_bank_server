package cl.duoc.xyzbank.authserver.tokens.unit;

import cl.duoc.xyzbank.authserver.tokens.application.dto.TokenClaims;
import cl.duoc.xyzbank.authserver.tokens.application.ports.ClientChannelLookup;
import cl.duoc.xyzbank.authserver.tokens.application.ports.CustomerIdLookup;
import cl.duoc.xyzbank.authserver.tokens.application.usecases.IssueTokenClaimsUseCase;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("The IssueTokenClaimsUseCase")
class IssueTokenClaimsUseCaseTest {

    /*
     * Cases:
     * 1. A demo login through bff-web yields the seed customer and the WEB channel
     */

    private static final String SEED_CUSTOMER = "11111111-1111-1111-1111-111111111111";

    private IssueTokenClaimsUseCase useCase;

    @BeforeEach
    void setUp() {
        Map<String, Channel> channelsByClient = Map.of("bff-web", Channel.WEB, "bff-mobile", Channel.MOBILE);
        Map<String, String> customersByUsername = Map.of("demo", SEED_CUSTOMER);
        ClientChannelLookup clientChannelLookup = clientId -> Optional.ofNullable(channelsByClient.get(clientId));
        CustomerIdLookup customerIdLookup = username -> Optional.ofNullable(customersByUsername.get(username));
        useCase = new IssueTokenClaimsUseCase(clientChannelLookup, customerIdLookup);
    }

    @Test
    @DisplayName("identifies the seed customer on the web channel for a demo login through bff-web")
    void identifiesTheSeedCustomerOnTheWebChannelForADemoLoginThroughBffWeb() {
        TokenClaims claims = useCase.execute("bff-web", "demo");

        assertEquals(new TokenClaims(SEED_CUSTOMER, Channel.WEB), claims);
    }
}

package cl.duoc.xyzbank.authserver.tokens.unit;

import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.authserver.tokens.application.dto.ClientProfile;
import cl.duoc.xyzbank.authserver.tokens.application.dto.TokenClaims;
import cl.duoc.xyzbank.authserver.tokens.application.ports.ClientProfileLookup;
import cl.duoc.xyzbank.authserver.tokens.application.ports.CustomerIdLookup;
import cl.duoc.xyzbank.authserver.tokens.application.usecases.IssueTokenClaimsUseCase;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The IssueTokenClaimsUseCase")
class IssueTokenClaimsUseCaseTest {

    /*
     * Cases:
     * 1. A demo login through bff-web yields the seed customer, the WEB channel, azp bff-web and
     *    the web audiences
     * 2. A demo login through bff-mobile yields the seed customer and the MOBILE channel, bound
     *    to the login's device
     * 3. An unknown client is refused rather than given a default channel
     * 4. An unknown username is refused
     * 5. A service token identifies the service itself: subject and azp are the client id, with
     *    its channel and audience and no device
     * 6. A service token is refused for a client that is not a service client
     */

    private static final String SEED_CUSTOMER = "11111111-1111-1111-1111-111111111111";
    private static final Set<String> WEB_AUDIENCES = Set.of("core-service", "interests-service");
    private static final Set<String> CORE_ONLY = Set.of("core-service");

    private IssueTokenClaimsUseCase useCase;

    @BeforeEach
    void setUp() {
        Map<String, ClientProfile> profiles = Map.of(
                "bff-web", new ClientProfile(Channel.WEB, WEB_AUDIENCES),
                "bff-mobile", new ClientProfile(Channel.MOBILE, CORE_ONLY),
                "interests-service", new ClientProfile(Channel.INTERESTS, CORE_ONLY));
        Map<String, String> customersByUsername = Map.of("demo", SEED_CUSTOMER);
        ClientProfileLookup clientProfileLookup = clientId -> Optional.ofNullable(profiles.get(clientId));
        CustomerIdLookup customerIdLookup = username -> Optional.ofNullable(customersByUsername.get(username));
        useCase = new IssueTokenClaimsUseCase(clientProfileLookup, customerIdLookup);
    }

    @Test
    @DisplayName("identifies the seed customer on the web channel for a demo login through bff-web")
    void identifiesTheSeedCustomerOnTheWebChannelForADemoLoginThroughBffWeb() {
        TokenClaims claims = useCase.forCustomer("bff-web", "demo", Optional.empty());

        assertEquals(
                new TokenClaims(SEED_CUSTOMER, Channel.WEB, "bff-web", WEB_AUDIENCES, Optional.empty()), claims);
    }

    @Test
    @DisplayName("identifies the seed customer on the mobile channel, bound to the login's device")
    void identifiesTheSeedCustomerOnTheMobileChannelBoundToTheLoginsDevice() {
        TokenClaims claims = useCase.forCustomer("bff-mobile", "demo", Optional.of("D1"));

        assertEquals(
                new TokenClaims(SEED_CUSTOMER, Channel.MOBILE, "bff-mobile", CORE_ONLY, Optional.of("D1")), claims);
    }

    @Test
    @DisplayName("refuses an unknown client instead of assuming a channel")
    void refusesAnUnknownClientInsteadOfAssumingAChannel() {
        DomainException exception =
                assertThrows(DomainException.class, () -> useCase.forCustomer("bff-atm", "demo", Optional.empty()));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
        assertTrue(exception.getMessage().contains("bff-atm"));
    }

    @Test
    @DisplayName("refuses an unknown username")
    void refusesAnUnknownUsername() {
        DomainException exception =
                assertThrows(DomainException.class, () -> useCase.forCustomer("bff-web", "mallory", Optional.empty()));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
        assertTrue(exception.getMessage().contains("mallory"));
    }

    @Test
    @DisplayName("identifies the service itself in a service token")
    void identifiesTheServiceItselfInAServiceToken() {
        TokenClaims claims = useCase.forService("interests-service");

        assertEquals(new TokenClaims(
                "interests-service", Channel.INTERESTS, "interests-service", CORE_ONLY, Optional.empty()), claims);
    }

    @Test
    @DisplayName("refuses a service token to a client that is not a service client")
    void refusesAServiceTokenToAClientThatIsNotAServiceClient() {
        DomainException exception = assertThrows(DomainException.class, () -> useCase.forService("bff-web"));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}

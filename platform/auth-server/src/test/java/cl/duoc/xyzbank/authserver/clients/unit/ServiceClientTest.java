package cl.duoc.xyzbank.authserver.clients.unit;

import cl.duoc.xyzbank.authserver.clients.domain.valueobjects.ServiceClient;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("The ServiceClient")
class ServiceClientTest {

    /*
     * Cases:
     * 1. The ATM service client allows exactly the ATM channel's scopes
     * 2. The interests service client allows exactly interests:write
     * 3. bff-atm's tokens are meant for core-service; interests-service's also for itself
     * 4. Rejects a service client for the web or mobile channel (those log customers in)
     * 5. Rejects a blank client id
     */

    @Test
    @DisplayName("allows the ATM service client exactly the ATM channel's scopes")
    void allowsTheAtmServiceClientExactlyTheAtmChannelsScopes() {
        ServiceClient client = ServiceClient.create("bff-atm", Channel.ATM);

        assertEquals(Set.of("atm:read-balance", "atm:withdraw"), client.allowedScopes());
    }

    @Test
    @DisplayName("allows the interests service client exactly interests:write")
    void allowsTheInterestsServiceClientExactlyInterestsWrite() {
        ServiceClient client = ServiceClient.create("interests-service", Channel.INTERESTS);

        assertEquals(Set.of("interests:write"), client.allowedScopes());
    }

    @Test
    @DisplayName("aims bff-atm's tokens at core-service and interests-service's also at itself")
    void aimsServiceTokensAtTheirAudiences() {
        ServiceClient atm = ServiceClient.create("bff-atm", Channel.ATM);
        ServiceClient interests = ServiceClient.create("interests-service", Channel.INTERESTS);

        assertEquals(Set.of("core-service"), atm.audiences());
        assertEquals(Set.of("core-service", "interests-service"), interests.audiences());
    }

    @ParameterizedTest
    @EnumSource(value = Channel.class, names = {"WEB", "MOBILE"})
    @DisplayName("rejects a service client for a channel whose customers log in")
    void rejectsAServiceClientForAChannelWhoseCustomersLogIn(Channel channel) {
        DomainException exception = assertThrows(DomainException.class, () -> ServiceClient.create("bff-x", channel));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }

    @Test
    @DisplayName("rejects a blank client id")
    void rejectsABlankClientId() {
        DomainException exception = assertThrows(DomainException.class, () -> ServiceClient.create(" ", Channel.ATM));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}

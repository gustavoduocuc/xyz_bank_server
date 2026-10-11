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
     * 3. The customers-admin client allows exactly customers:read and customers:write
     * 4. The accounts-admin client allows exactly accounts:write and customers:read
     * 5. The payments-admin and payments-service clients allow exactly their channels' scopes
     * 6. bff-atm's tokens are meant for core-service; interests-service's also for itself;
     *    customers-admin's for customers-service; accounts-admin's for core-service and customers-service;
     *    payments-admin's for payments-service; payments-service's for core-service
     * 7. Rejects a service client for the web or mobile channel (those log customers in)
     * 8. Rejects a blank client id
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
    @DisplayName("allows the customers-admin client exactly customers:read and customers:write")
    void allowsTheCustomersAdminClientExactlyCustomersReadAndWrite() {
        ServiceClient client = ServiceClient.create("customers-admin", Channel.CUSTOMERS_ADMIN);

        assertEquals(Set.of("customers:read", "customers:write"), client.allowedScopes());
    }

    @Test
    @DisplayName("allows the accounts-admin client exactly accounts:write and customers:read")
    void allowsTheAccountsAdminClientExactlyAccountsWriteAndCustomersRead() {
        ServiceClient client = ServiceClient.create("accounts-admin", Channel.ACCOUNTS_ADMIN);

        assertEquals(Set.of("accounts:write", "customers:read"), client.allowedScopes());
    }

    @Test
    @DisplayName("allows the payments clients exactly their channels' scopes")
    void allowsThePaymentsClientsExactlyTheirChannelsScopes() {
        ServiceClient paymentsAdmin = ServiceClient.create("payments-admin", Channel.PAYMENTS_ADMIN);
        ServiceClient paymentsService = ServiceClient.create("payments-service", Channel.PAYMENTS);

        assertEquals(Set.of("payments:write", "payments:read"), paymentsAdmin.allowedScopes());
        assertEquals(Set.of("postings:write"), paymentsService.allowedScopes());
    }

    @Test
    @DisplayName("aims each service client's tokens at its audiences")
    void aimsServiceTokensAtTheirAudiences() {
        ServiceClient atm = ServiceClient.create("bff-atm", Channel.ATM);
        ServiceClient interests = ServiceClient.create("interests-service", Channel.INTERESTS);
        ServiceClient customersAdmin = ServiceClient.create("customers-admin", Channel.CUSTOMERS_ADMIN);
        ServiceClient accountsAdmin = ServiceClient.create("accounts-admin", Channel.ACCOUNTS_ADMIN);
        ServiceClient paymentsAdmin = ServiceClient.create("payments-admin", Channel.PAYMENTS_ADMIN);
        ServiceClient paymentsService = ServiceClient.create("payments-service", Channel.PAYMENTS);

        assertEquals(Set.of("core-service"), atm.audiences());
        assertEquals(Set.of("core-service", "interests-service"), interests.audiences());
        assertEquals(Set.of("customers-service"), customersAdmin.audiences());
        assertEquals(Set.of("core-service", "customers-service"), accountsAdmin.audiences());
        assertEquals(Set.of("payments-service"), paymentsAdmin.audiences());
        assertEquals(Set.of("core-service"), paymentsService.audiences());
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

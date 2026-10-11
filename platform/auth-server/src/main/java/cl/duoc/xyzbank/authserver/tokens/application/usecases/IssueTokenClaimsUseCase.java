package cl.duoc.xyzbank.authserver.tokens.application.usecases;

import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.authserver.tokens.application.dto.ClientProfile;
import cl.duoc.xyzbank.authserver.tokens.application.dto.TokenClaims;
import cl.duoc.xyzbank.authserver.tokens.application.ports.ClientProfileLookup;
import cl.duoc.xyzbank.authserver.tokens.application.ports.CustomerIdLookup;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

import java.util.Optional;
import java.util.Set;

/**
 * Decides the identity claims of an access token. The channel, audiences and authorized
 * party always come from the client the token is issued to; the subject is the logged-in
 * customer for a login, or the service itself for a service token.
 */
public class IssueTokenClaimsUseCase {

    private static final Set<Channel> SERVICE_CHANNELS =
            Set.of(Channel.ATM, Channel.INTERESTS, Channel.CUSTOMERS_ADMIN, Channel.ACCOUNTS_ADMIN,
                    Channel.PAYMENTS_ADMIN, Channel.PAYMENTS);

    private final ClientProfileLookup clientProfileLookup;
    private final CustomerIdLookup customerIdLookup;

    public IssueTokenClaimsUseCase(ClientProfileLookup clientProfileLookup, CustomerIdLookup customerIdLookup) {
        this.clientProfileLookup = clientProfileLookup;
        this.customerIdLookup = customerIdLookup;
    }

    public TokenClaims forCustomer(String clientId, String username, Optional<String> deviceId) {
        ClientProfile client = profileOf(clientId);
        String customerId = customerIdLookup
                .customerIdOf(username)
                .orElseThrow(() -> DomainException.notFound("No customer is linked to username " + username));
        return new TokenClaims(customerId, client.channel(), clientId, client.audiences(), deviceId);
    }

    public TokenClaims forService(String clientId) {
        ClientProfile client = profileOf(clientId);
        if (!SERVICE_CHANNELS.contains(client.channel())) {
            throw DomainException.validation("Client " + clientId + " cannot obtain service tokens");
        }
        return new TokenClaims(clientId, client.channel(), clientId, client.audiences(), Optional.empty());
    }

    private ClientProfile profileOf(String clientId) {
        return clientProfileLookup
                .profileOf(clientId)
                .orElseThrow(() -> DomainException.notFound("No channel is registered for client " + clientId));
    }
}

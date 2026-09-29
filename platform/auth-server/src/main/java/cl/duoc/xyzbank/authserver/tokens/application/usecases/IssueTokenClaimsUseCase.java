package cl.duoc.xyzbank.authserver.tokens.application.usecases;

import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import cl.duoc.xyzbank.authserver.tokens.application.dto.TokenClaims;
import cl.duoc.xyzbank.authserver.tokens.application.ports.ClientChannelLookup;
import cl.duoc.xyzbank.authserver.tokens.application.ports.CustomerIdLookup;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;

/**
 * Decides the identity claims of a token issued to a client for a logged-in username: the
 * customer comes from the login, the channel only from the client the token is issued to.
 */
public class IssueTokenClaimsUseCase {

    private final ClientChannelLookup clientChannelLookup;
    private final CustomerIdLookup customerIdLookup;

    public IssueTokenClaimsUseCase(ClientChannelLookup clientChannelLookup, CustomerIdLookup customerIdLookup) {
        this.clientChannelLookup = clientChannelLookup;
        this.customerIdLookup = customerIdLookup;
    }

    public TokenClaims execute(String clientId, String username) {
        Channel channel = clientChannelLookup
                .channelOf(clientId)
                .orElseThrow(() -> DomainException.notFound("No channel is registered for client " + clientId));
        String customerId = customerIdLookup
                .customerIdOf(username)
                .orElseThrow(() -> DomainException.notFound("No customer is linked to username " + username));
        return new TokenClaims(customerId, channel);
    }
}

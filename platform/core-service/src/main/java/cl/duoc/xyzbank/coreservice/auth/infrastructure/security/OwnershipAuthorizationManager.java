package cl.duoc.xyzbank.coreservice.auth.infrastructure.security;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Decides that the customer a request acts for owns the customer, account or transaction named
 * in the URL: the token's subject for web and mobile, the customer of the referenced ATM session
 * for ATM, nobody for service channels. A resource that is not the caller's, or does not exist,
 * is rejected as not found.
 */
public class OwnershipAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    public enum IdentifierType {
        CUSTOMER_ID,
        ACCOUNT_ID,
        TRANSACTION_ID
    }

    private static final String ATM_SESSION_HEADER = "X-Atm-Session";
    private static final Set<Channel> SERVICE_CHANNELS =
            Set.of(Channel.INTERESTS, Channel.ACCOUNTS_ADMIN, Channel.PAYMENTS);

    private final IdentifierType type;
    private final String variableName;
    private final AtmSessionLookup atmSessions;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public OwnershipAuthorizationManager(
            IdentifierType type,
            String variableName,
            AtmSessionLookup atmSessions,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.type = type;
        this.variableName = variableName;
        this.atmSessions = atmSessions;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Jwt jwt = ((JwtAuthenticationToken) authentication.get()).getToken();
        Channel channel = Channel.valueOf(jwt.getClaimAsString("channel"));
        if (SERVICE_CHANNELS.contains(channel)) {
            return new AuthorizationDecision(true);
        }
        String customerId = actingCustomer(channel, jwt, context)
                .orElseThrow(() -> new InsufficientAuthenticationException("No customer is identified for this request"));
        Id identifier;
        try {
            identifier = Id.create(context.getVariables().get(variableName));
        } catch (DomainException exception) {
            // A malformed identifier is a format concern for the controller (422), not ownership
            return new AuthorizationDecision(true);
        }
        if (!ownerOf(identifier).filter(customerId::equals).isPresent()) {
            throw new ResourceNotOwnedException();
        }
        return new AuthorizationDecision(true);
    }

    private Optional<String> actingCustomer(Channel channel, Jwt jwt, RequestAuthorizationContext context) {
        if (channel == Channel.ATM) {
            return Optional.ofNullable(context.getRequest().getHeader(ATM_SESSION_HEADER))
                    .flatMap(atmSessions::activeCustomerOf);
        }
        return Optional.ofNullable(jwt.getSubject());
    }

    private Optional<String> ownerOf(Id identifier) {
        return switch (type) {
            case CUSTOMER_ID -> Optional.of(identifier.getValue());
            case ACCOUNT_ID -> accountRepository.findById(identifier).map(Account::getCustomerId).map(Id::getValue);
            case TRANSACTION_ID -> transactionRepository.findById(identifier)
                    .map(Transaction::getAccountId)
                    .flatMap(accountRepository::findById)
                    .map(Account::getCustomerId)
                    .map(Id::getValue);
        };
    }
}

package cl.duoc.xyzbank.coreservice.auth.infrastructure.rest;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.auth.application.dto.VerifiedAccessToken;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AccessTokenVerifier;
import cl.duoc.xyzbank.coreservice.auth.application.ports.AtmSessionLookup;
import cl.duoc.xyzbank.coreservice.auth.application.ports.InvalidAccessTokenException;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.DomainEndpointOwnership.IdentifierType;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.DomainEndpointOwnership.OwnershipCheck;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Enforces channel-auth on core-service's internal API. Every request must carry an access token
 * issued by auth-server for core-service (verified against its JWKS; no static service
 * credential). PIN verification (pre-auth) accepts only the ATM service token; every domain
 * endpoint needs one of its required scopes and, for customer resources, ownership by the
 * customer the request acts for: the token's subject for web and mobile, the customer of the
 * referenced core-service ATM session for ATM, none for the interests service.
 *
 * <p>Gated by security.enforcement.enabled: while disabled, every request passes through
 * unchanged.
 */
public class EnforcementFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ATM_SESSION_HEADER = "X-Atm-Session";
    private static final String ATM_PRE_AUTH_PREFIX = "/internal/auth/atm/";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean enabled;
    private final AccessTokenVerifier verifier;
    private final AtmSessionLookup atmSessions;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public EnforcementFilter(
            boolean enabled,
            AccessTokenVerifier verifier,
            AtmSessionLookup atmSessions,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.enabled = enabled;
        this.verifier = verifier;
        this.atmSessions = atmSessions;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<VerifiedAccessToken> token = verifiedToken(request);
        if (token.isEmpty()) {
            reject(response, HttpStatus.UNAUTHORIZED, "A valid access token is required");
            return;
        }
        if (request.getRequestURI().startsWith(ATM_PRE_AUTH_PREFIX)) {
            if (token.get().channel() != Channel.ATM) {
                reject(response, HttpStatus.FORBIDDEN, "Only the ATM channel may verify PINs");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }
        Optional<Set<String>> requiredScopes =
                DomainEndpointScopes.requiredScopesFor(request.getMethod(), request.getRequestURI());
        if (requiredScopes.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (Collections.disjoint(token.get().scopes(), requiredScopes.get())) {
            reject(response, HttpStatus.FORBIDDEN, "The token's scope does not permit this operation");
            return;
        }
        // Service channels act for no customer, so there is no ownership to check
        if (token.get().channel() == Channel.INTERESTS || token.get().channel() == Channel.ACCOUNTS_ADMIN) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<String> customerId = actingCustomer(request, token.get());
        if (customerId.isEmpty()) {
            reject(response, HttpStatus.UNAUTHORIZED, "No customer is identified for this request");
            return;
        }
        if (!ownsRequestedResource(request, customerId.get(), response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Optional<VerifiedAccessToken> verifiedToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(verifier.verify(header.substring(BEARER_PREFIX.length())));
        } catch (InvalidAccessTokenException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> actingCustomer(HttpServletRequest request, VerifiedAccessToken token) {
        if (token.channel() == Channel.ATM) {
            return Optional.ofNullable(request.getHeader(ATM_SESSION_HEADER)).flatMap(atmSessions::activeCustomerOf);
        }
        return Optional.ofNullable(token.subject());
    }

    private boolean ownsRequestedResource(HttpServletRequest request, String customerId, HttpServletResponse response)
            throws IOException {
        Optional<OwnershipCheck> ownershipCheck =
                DomainEndpointOwnership.ownershipCheckFor(request.getMethod(), request.getRequestURI());
        if (ownershipCheck.isEmpty()) {
            return true;
        }
        Id identifier;
        try {
            // request.getRequestURI() is raw (percent-encoded); decode before treating the
            // extracted segment as an identifier, matching what @PathVariable binding sees.
            String decoded = UriUtils.decode(ownershipCheck.get().identifierValue(), StandardCharsets.UTF_8);
            identifier = Id.create(decoded);
        } catch (DomainException exception) {
            // A malformed identifier is a format-validation concern for the controller
            // (422), not an ownership concern.
            return true;
        }
        Optional<String> ownerCustomerId = resolveOwnerCustomerId(ownershipCheck.get().type(), identifier);
        // A well-formed but unknown identifier is indistinguishable, from the caller's
        // perspective, from one it doesn't own: both are rejected as not-found here.
        if (ownerCustomerId.isEmpty() || !ownerCustomerId.get().equals(customerId)) {
            rejectNotFound(request, response);
            return false;
        }
        return true;
    }

    private Optional<String> resolveOwnerCustomerId(IdentifierType type, Id identifier) {
        return switch (type) {
            case IdentifierType.CUSTOMER_ID -> Optional.of(identifier.getValue());
            case IdentifierType.ACCOUNT_ID ->
                accountRepository.findById(identifier).map(Account::getCustomerId).map(Id::getValue);
            case IdentifierType.TRANSACTION_ID -> transactionRepository.findById(identifier)
                    .map(Transaction::getAccountId)
                    .flatMap(accountRepository::findById)
                    .map(Account::getCustomerId)
                    .map(Id::getValue);
        };
    }

    private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"detail\":\"" + detail + "\"}");
    }

    private void rejectNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(HttpStatus.NOT_FOUND.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        OBJECT_MAPPER.writeValue(response.getWriter(), problem);
    }
}

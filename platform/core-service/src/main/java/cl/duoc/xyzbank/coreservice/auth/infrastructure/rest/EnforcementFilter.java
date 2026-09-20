package cl.duoc.xyzbank.coreservice.auth.infrastructure.rest;

import cl.duoc.xyzbank.coredomain.accounts.domain.entities.Account;
import cl.duoc.xyzbank.coredomain.accounts.domain.repositories.AccountRepository;
import cl.duoc.xyzbank.coredomain.shared.domain.DomainException;
import cl.duoc.xyzbank.coredomain.shared.domain.Id;
import cl.duoc.xyzbank.coredomain.transactions.domain.entities.Transaction;
import cl.duoc.xyzbank.coredomain.transactions.domain.repositories.TransactionRepository;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.DomainEndpointOwnership.IdentifierType;
import cl.duoc.xyzbank.coreservice.auth.infrastructure.rest.DomainEndpointOwnership.OwnershipCheck;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Authenticates the calling service and, for domain endpoints, checks the caller's scope
 * and resource ownership. Gated by security.enforcement.enabled: while disabled, every
 * request passes through unchanged, matching core-service's pre-channel-auth behavior, so
 * this change can roll out without an intermediate state where core-service rejects a BFF
 * that hasn't been updated yet.
 */
public class EnforcementFilter extends OncePerRequestFilter {

    private static final String SERVICE_CREDENTIAL_HEADER = "X-Service-Credential";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean enabled;
    private final Map<String, String> serviceCredentials;
    private final JwtCallerContextAdapter tokenAdapter;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public EnforcementFilter(
            boolean enabled,
            Map<String, String> serviceCredentials,
            JwtCallerContextAdapter tokenAdapter,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository) {
        this.enabled = enabled;
        this.serviceCredentials = serviceCredentials;
        this.tokenAdapter = tokenAdapter;
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
        if (!isKnownServiceCredential(request.getHeader(SERVICE_CREDENTIAL_HEADER))) {
            reject(response, HttpStatus.UNAUTHORIZED, "A valid service credential is required");
            return;
        }
        Optional<Set<String>> requiredScopes =
                DomainEndpointScopes.requiredScopesFor(request.getMethod(), request.getRequestURI());
        if (requiredScopes.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<CallerContext> callerContext = resolveCallerContext(request, response);
        if (callerContext.isEmpty()) {
            return;
        }
        if (Collections.disjoint(callerContext.get().scopes(), requiredScopes.get())) {
            reject(response, HttpStatus.FORBIDDEN, "The token's scope does not permit this operation");
            return;
        }
        if (!ownsRequestedResource(request, callerContext.get(), response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Optional<CallerContext> resolveCallerContext(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String bearerToken = extractBearerToken(request);
        if (bearerToken == null) {
            reject(response, HttpStatus.UNAUTHORIZED, "A valid user token is required");
            return Optional.empty();
        }
        try {
            return Optional.of(tokenAdapter.resolve(bearerToken));
        } catch (CallerIdentityException exception) {
            reject(response, HttpStatus.UNAUTHORIZED, "A valid user token is required");
            return Optional.empty();
        }
    }

    private boolean ownsRequestedResource(
            HttpServletRequest request, CallerContext callerContext, HttpServletResponse response)
            throws IOException {
        if (callerContext.channel() == Channel.INTERESTS) {
            return true;
        }
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
        if (ownerCustomerId.isEmpty() || !ownerCustomerId.get().equals(callerContext.customerId())) {
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

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return header.substring(BEARER_PREFIX.length());
    }

    private boolean isKnownServiceCredential(String presented) {
        return presented != null && serviceCredentials.containsValue(presented);
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

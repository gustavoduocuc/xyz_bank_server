package cl.duoc.xyzbank.coreservice.auth.infrastructure.rest;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The path-variable to ownership-resolution table from design.md's Decision 5, matching
 * channel-auth's "Ownership-checked identifier" column exactly. A plain, explicit table,
 * mirroring {@link DomainEndpointScopes}, rather than annotations or AOP.
 */
public final class DomainEndpointOwnership {

    public enum IdentifierType {
        CUSTOMER_ID,
        ACCOUNT_ID,
        TRANSACTION_ID
    }

    public record OwnershipCheck(IdentifierType type, String identifierValue) {
    }

    private record Route(HttpMethod method, String pattern, String variableName, IdentifierType type) {
    }

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<Route> ROUTES = List.of(
            new Route(
                    HttpMethod.GET,
                    "/internal/customers/{customerId}/accounts",
                    "customerId",
                    IdentifierType.CUSTOMER_ID),
            new Route(
                    HttpMethod.GET, "/internal/accounts/{accountId}/balance", "accountId", IdentifierType.ACCOUNT_ID),
            new Route(
                    HttpMethod.GET,
                    "/internal/accounts/{accountId}/transactions",
                    "accountId",
                    IdentifierType.ACCOUNT_ID),
            new Route(
                    HttpMethod.GET,
                    "/internal/accounts/{accountId}/interest-summary",
                    "accountId",
                    IdentifierType.ACCOUNT_ID),
            new Route(
                    HttpMethod.GET,
                    "/internal/transactions/{transactionId}",
                    "transactionId",
                    IdentifierType.TRANSACTION_ID),
            new Route(
                    HttpMethod.POST,
                    "/internal/accounts/{accountId}/withdrawals",
                    "accountId",
                    IdentifierType.ACCOUNT_ID));

    private DomainEndpointOwnership() {
    }

    public static Optional<OwnershipCheck> ownershipCheckFor(String method, String path) {
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        return ROUTES.stream()
                .filter(route -> route.method() == httpMethod && PATH_MATCHER.match(route.pattern(), path))
                .findFirst()
                .map(route -> {
                    Map<String, String> variables = PATH_MATCHER.extractUriTemplateVariables(route.pattern(), path);
                    return new OwnershipCheck(route.type(), variables.get(route.variableName()));
                });
    }
}

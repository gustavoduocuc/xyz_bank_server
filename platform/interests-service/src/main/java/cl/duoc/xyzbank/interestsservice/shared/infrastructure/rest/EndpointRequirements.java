package cl.duoc.xyzbank.interestsservice.shared.infrastructure.rest;

import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What each interests-service endpoint requires of the caller's token, as an explicit table
 * auditable against the interests spec.
 */
final class EndpointRequirements {

    record Requirement(Set<Channel> channels, String scope) {
    }

    private record Route(HttpMethod method, String pattern, Requirement requirement) {
    }

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final List<Route> ROUTES = List.of(
            new Route(HttpMethod.GET, "/accounts/*/interest-summary",
                    new Requirement(Set.of(Channel.WEB), "web:interests:read")),
            new Route(HttpMethod.POST, "/accounts/*/interest-applications",
                    new Requirement(Set.of(Channel.INTERESTS), "interests:write")));

    private EndpointRequirements() {
    }

    static Optional<Requirement> requirementFor(String method, String path) {
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        return ROUTES.stream()
                .filter(route -> route.method() == httpMethod && PATH_MATCHER.match(route.pattern(), path))
                .map(Route::requirement)
                .findFirst();
    }
}

package cl.duoc.xyzbank.bffweb.auth.testsupport;

import java.time.Instant;
import java.util.List;

/**
 * Session cookie values for E2E arrangement: access tokens as the authorization server would
 * issue them, signed with {@link MockOidcProvider}'s key. A test that presents one must have a
 * {@link MockOidcProvider} running, since bff-web fetches its JWK set to verify the cookie.
 */
public final class TestSessions {

    private TestSessions() {
    }

    public static String webSessionFor(String customerId) {
        return MockOidcProvider.webAccessTokenFor(customerId);
    }

    public static String mobileSessionFor(String customerId) {
        return MockOidcProvider.accessTokenIssuedTo(
                "bff-mobile", "MOBILE", customerId, List.of("mobile:accounts:read", "mobile:transactions:read"));
    }

    public static String expiredWebSessionFor(String customerId) {
        return MockOidcProvider.expiredWebAccessTokenFor(customerId, Instant.parse("2020-01-01T00:00:00Z"));
    }

    public static String foreignSignedWebSessionFor(String customerId) {
        return MockOidcProvider.foreignSignedWebAccessTokenFor(customerId);
    }
}

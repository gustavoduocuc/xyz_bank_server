package cl.duoc.xyzbank.bffmobile.auth.testsupport;

import java.time.Instant;
import java.util.List;

/**
 * Session tokens for E2E arrangement: access tokens as the authorization server would issue
 * them, signed with {@link MockOidcProvider}'s key. A test that presents one must have a
 * {@link MockOidcProvider} running, since bff-mobile fetches its JWK set to verify the token.
 */
public final class TestSessions {

    private TestSessions() {
    }

    public static String mobileSessionFor(String customerId, String deviceId) {
        return MockOidcProvider.mobileAccessTokenFor(customerId, deviceId);
    }

    public static String webSessionFor(String customerId) {
        return MockOidcProvider.accessTokenIssuedTo(
                "bff-web", "WEB", customerId, List.of(
                        "web:accounts:read", "web:customers:read", "web:transactions:read", "web:interests:read"));
    }

    public static String expiredMobileSessionFor(String customerId, String deviceId) {
        return MockOidcProvider.expiredMobileAccessTokenFor(
                customerId, deviceId, Instant.parse("2020-01-01T00:00:00Z"));
    }

    public static String foreignSignedMobileSessionFor(String customerId, String deviceId) {
        return MockOidcProvider.foreignSignedMobileAccessTokenFor(customerId, deviceId);
    }
}

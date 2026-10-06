package cl.duoc.xyzbank.sharedsecurity.callercontext.unit;

import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerContext;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import cl.duoc.xyzbank.sharedsecurity.callercontext.Channel;
import cl.duoc.xyzbank.sharedsecurity.callercontext.JwtCallerContextAdapter;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("The JwtCallerContextAdapter")
class JwtCallerContextAdapterTest {

    /*
     * Cases:
     * 1. Issues a token whose claims carry the customer id, channel, and the channel's exact scope set
     * 2. Issues a token with no terminal id claim when none is supplied
     * 3. Issues a token with a terminal id claim when one is supplied
     * 4. Caps an ATM token's lifetime at 120 seconds after issuance
     * 5. Issues a web/mobile token with a short-lived default expiry longer than 120 seconds
     * 6. Resolves a web token into a CallerContext with the web channel's exact scopes
     * 7. Resolves a mobile token into a CallerContext with the mobile channel's exact scopes
     * 8. Resolves an ATM token into a CallerContext including its terminal id
     * 9. Resolves a web token into a CallerContext with no terminal id
     * 10. Rejects a missing token
     * 11. Rejects a malformed token
     * 12. Rejects a token with an invalid signature
     * 13. Rejects an expired token
     * 14. Rejects a token whose channel claim was tampered from atm to web
     */

    private static final String SECRET = "unit-test-signing-secret-unit-test-signing-secret";
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final JwtCallerContextAdapter adapter = new JwtCallerContextAdapter(SECRET);

    @Test
    @DisplayName("issues a token carrying the customer id, channel, and the channel's exact scope set")
    void issuesTokenWithCustomerIdChannelAndScopes() {
        String token = adapter.issue("customer-1", Channel.WEB, null);

        Claims claims = parseClaims(token);

        assertEquals("customer-1", claims.getSubject());
        assertEquals("WEB", claims.get("channel", String.class));
        assertEquals(Channel.WEB.scopes(), scopesOf(claims));
    }

    @Test
    @DisplayName("issues a token with no terminal id claim when none is supplied")
    void issuesTokenWithNoTerminalIdWhenNoneSupplied() {
        String token = adapter.issue("customer-1", Channel.WEB, null);

        Claims claims = parseClaims(token);

        assertNull(claims.get("terminalId"));
    }

    @Test
    @DisplayName("issues a token with a terminal id claim when one is supplied")
    void issuesTokenWithTerminalIdWhenSupplied() {
        String token = adapter.issue("customer-3", Channel.ATM, "terminal-9");

        Claims claims = parseClaims(token);

        assertEquals("terminal-9", claims.get("terminalId", String.class));
        assertNull(claims.get("atmSessionId"));
    }

    @Test
    @DisplayName("issues an atm session id claim when one is supplied")
    void issuesAnAtmSessionIdClaimWhenOneIsSupplied() {
        String token = adapter.issue("customer-3", Channel.ATM, "terminal-9", "atm-session-1");

        assertEquals("atm-session-1", parseClaims(token).get("atmSessionId", String.class));
        assertEquals(java.util.Optional.of("atm-session-1"), adapter.atmSessionIdOf(token));
    }

    @Test
    @DisplayName("caps an atm token's lifetime at 120 seconds after issuance")
    void capsAtmTokenLifetimeAt120Seconds() {
        JwtCallerContextAdapter fixedClockAdapter = new JwtCallerContextAdapter(
                SECRET, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        String token = fixedClockAdapter.issue("customer-3", Channel.ATM, "terminal-9");
        Claims claims = parseClaims(token, FIXED_NOW);

        assertEquals(FIXED_NOW.plus(Duration.ofSeconds(120)), claims.getExpiration().toInstant());
    }

    @Test
    @DisplayName("issues a web token with a short-lived default expiry longer than 120 seconds")
    void issuesWebTokenWithShortLivedDefaultExpiry() {
        JwtCallerContextAdapter fixedClockAdapter = new JwtCallerContextAdapter(
                SECRET, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

        String token = fixedClockAdapter.issue("customer-1", Channel.WEB, null);
        Claims claims = parseClaims(token, FIXED_NOW);

        Duration lifetime = Duration.between(FIXED_NOW, claims.getExpiration().toInstant());
        assertTrue(lifetime.compareTo(Duration.ofSeconds(120)) > 0);
    }

    @Test
    @DisplayName("resolves a web token into a caller context with the web channel's exact scopes")
    void resolvesWebTokenIntoCallerContext() {
        String token = adapter.issue("customer-1", Channel.WEB, null);

        CallerContext callerContext = adapter.resolve(token);

        assertEquals("customer-1", callerContext.customerId());
        assertEquals(Channel.WEB, callerContext.channel());
        assertEquals(Channel.WEB.scopes(), callerContext.scopes());
    }

    @Test
    @DisplayName("resolves a mobile token into a caller context with the mobile channel's exact scopes")
    void resolvesMobileTokenIntoCallerContext() {
        String token = adapter.issue("customer-2", Channel.MOBILE, null);

        CallerContext callerContext = adapter.resolve(token);

        assertEquals("customer-2", callerContext.customerId());
        assertEquals(Channel.MOBILE, callerContext.channel());
        assertEquals(Channel.MOBILE.scopes(), callerContext.scopes());
    }

    @Test
    @DisplayName("resolves an atm token into a caller context including its terminal id")
    void resolvesAtmTokenIncludingTerminalId() {
        String token = adapter.issue("customer-3", Channel.ATM, "terminal-9");

        CallerContext callerContext = adapter.resolve(token);

        assertEquals(Channel.ATM, callerContext.channel());
        assertEquals(Channel.ATM.scopes(), callerContext.scopes());
        assertEquals(java.util.Optional.of("terminal-9"), callerContext.terminalId());
    }

    @Test
    @DisplayName("resolves a web token into a caller context with no terminal id")
    void resolvesWebTokenWithNoTerminalId() {
        String token = adapter.issue("customer-1", Channel.WEB, null);

        CallerContext callerContext = adapter.resolve(token);

        assertEquals(java.util.Optional.empty(), callerContext.terminalId());
    }

    @Test
    @DisplayName("rejects a missing token")
    void rejectsMissingToken() {
        assertThrows(CallerIdentityException.class, () -> adapter.resolve(null));
        assertThrows(CallerIdentityException.class, () -> adapter.resolve(""));
    }

    @Test
    @DisplayName("rejects a malformed token")
    void rejectsMalformedToken() {
        assertThrows(CallerIdentityException.class, () -> adapter.resolve("not-a-jwt"));
    }

    @Test
    @DisplayName("rejects a token with an invalid signature")
    void rejectsTokenWithInvalidSignature() {
        JwtCallerContextAdapter otherAdapter = new JwtCallerContextAdapter("a-completely-different-signing-secret");
        String token = otherAdapter.issue("customer-1", Channel.WEB, null);

        assertThrows(CallerIdentityException.class, () -> adapter.resolve(token));
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpiredToken() {
        JwtCallerContextAdapter fixedClockAdapter = new JwtCallerContextAdapter(
                SECRET, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        String token = fixedClockAdapter.issue("customer-1", Channel.WEB, null);

        assertThrows(CallerIdentityException.class, () -> adapter.resolve(token));
    }

    @Test
    @DisplayName("rejects a token whose channel claim was tampered from atm to web")
    void rejectsTokenWithTamperedChannelClaim() {
        String token = adapter.issue("customer-3", Channel.ATM, "terminal-9");
        String tamperedToken = tamperChannelClaim(token, "ATM", "WEB");

        assertThrows(CallerIdentityException.class, () -> adapter.resolve(tamperedToken));
    }

    private String tamperChannelClaim(String token, String from, String to) {
        String[] parts = token.split("\\.");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"channel\":\"" + from + "\"", "\"channel\":\"" + to + "\"");
        String tamperedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + tamperedPayload + "." + parts[2];
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    private Claims parseClaims(String token, Instant asOf) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .clock(() -> Date.from(asOf))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Set<String> scopesOf(Claims claims) {
        String scope = claims.get("scope", String.class);
        return Stream.of(scope.split(" ")).collect(Collectors.toSet());
    }
}

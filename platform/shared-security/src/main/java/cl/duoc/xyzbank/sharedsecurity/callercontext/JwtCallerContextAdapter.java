package cl.duoc.xyzbank.sharedsecurity.callercontext;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

public final class JwtCallerContextAdapter {

    private static final Duration ATM_SESSION_TTL = Duration.ofSeconds(120);
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(15);

    private final SecretKey key;
    private final Clock clock;

    public JwtCallerContextAdapter(String secret) {
        this(secret, Clock.systemUTC());
    }

    public JwtCallerContextAdapter(String secret, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String issue(String customerId, Channel channel, String terminalId) {
        return issue(customerId, channel, terminalId, null);
    }

    public String issue(String customerId, Channel channel, String terminalId, String atmSessionId) {
        Instant now = clock.instant();
        JwtBuilder builder = Jwts.builder()
                .subject(customerId)
                .claim("channel", channel.name())
                .claim("scope", String.join(" ", channel.scopes()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiryFor(channel))))
                .signWith(key);
        if (terminalId != null) {
            builder.claim("terminalId", terminalId);
        }
        if (atmSessionId != null) {
            builder.claim("atmSessionId", atmSessionId);
        }
        return builder.compact();
    }

    public Optional<String> atmSessionIdOf(String token) {
        return Optional.ofNullable(claimsOf(token).get("atmSessionId", String.class));
    }

    public CallerContext resolve(String token) {
        Claims claims = claimsOf(token);
        Channel channel;
        try {
            channel = Channel.valueOf(claims.get("channel", String.class));
        } catch (IllegalArgumentException exception) {
            throw CallerIdentityException.invalid("Unrecognized channel in token");
        }
        String customerId = claims.getSubject();
        String terminalId = claims.get("terminalId", String.class);
        return new ResolvedCallerContext(customerId, channel, channel.scopes(), terminalId);
    }

    private Claims claimsOf(String token) {
        if (token == null || token.isBlank()) {
            throw CallerIdentityException.invalid("Token is required");
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw CallerIdentityException.invalid("Invalid or expired token");
        }
    }

    private Duration expiryFor(Channel channel) {
        return channel == Channel.ATM ? ATM_SESSION_TTL : DEFAULT_SESSION_TTL;
    }

    private record ResolvedCallerContext(String customerId, Channel channel, Set<String> scopes, String rawTerminalId)
            implements CallerContext {

        @Override
        public Optional<String> terminalId() {
            return Optional.ofNullable(rawTerminalId);
        }
    }
}

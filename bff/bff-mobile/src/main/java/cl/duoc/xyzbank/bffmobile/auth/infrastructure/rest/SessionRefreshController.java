package cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.AuthServerTokenClient;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.IssuedTokens;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.RefreshTokenRejectedException;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.MobileRefreshRequest;
import cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest.dto.MobileSessionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * Rotates a device's refresh token at the authorization server and returns the new access
 * token in the same body login uses. A refused grant — a different device, a revoked device,
 * or a reused token — is a 401, because the authorization server has ended that login.
 */
@RestController
public class SessionRefreshController {

    private static final Duration REFRESH_TOKEN_LIFETIME = Duration.ofDays(180);

    private final AuthServerTokenClient tokenClient;

    public SessionRefreshController(AuthServerTokenClient tokenClient) {
        this.tokenClient = tokenClient;
    }

    @PostMapping("/session/refresh")
    public ResponseEntity<MobileSessionResponse> refresh(@RequestBody MobileRefreshRequest request) {
        IssuedTokens tokens;
        try {
            tokens = tokenClient.refresh(request.refreshToken(), request.deviceId());
        } catch (RefreshTokenRejectedException exception) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new MobileSessionResponse(
                tokens.accessToken(), tokens.refreshToken(), Instant.now().plus(REFRESH_TOKEN_LIFETIME)));
    }
}

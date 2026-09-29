package cl.duoc.xyzbank.bffmobile.auth.infrastructure.rest;

import cl.duoc.xyzbank.bffmobile.auth.infrastructure.adapters.AuthServerTokenClient;
import cl.duoc.xyzbank.sharedsecurity.callercontext.CallerIdentityException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Forwards a device revocation to the authorization server, authenticating as bff-mobile and
 * presenting the device's current access token. The authorization server performs no check that
 * the caller is that device, so this controller keeps the self-only rule: the path deviceId must
 * match the caller's own X-Device-Id (already proven to match the presented token by
 * CallerContextInterceptor).
 */
@RestController
public class DeviceRevocationController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthServerTokenClient tokenClient;

    public DeviceRevocationController(AuthServerTokenClient tokenClient) {
        this.tokenClient = tokenClient;
    }

    @PostMapping("/devices/{deviceId}/revocations")
    public ResponseEntity<Void> revoke(
            @PathVariable String deviceId,
            @RequestHeader("X-Device-Id") String callerDeviceId,
            @RequestHeader("Authorization") String authorization) {
        if (!deviceId.equals(callerDeviceId)) {
            throw CallerIdentityException.forbidden("A device may only revoke itself");
        }
        tokenClient.revokeDevice(deviceId, accessToken(authorization));
        return ResponseEntity.noContent().build();
    }

    private static String accessToken(String authorization) {
        return authorization.startsWith(BEARER_PREFIX)
                ? authorization.substring(BEARER_PREFIX.length())
                : authorization;
    }
}

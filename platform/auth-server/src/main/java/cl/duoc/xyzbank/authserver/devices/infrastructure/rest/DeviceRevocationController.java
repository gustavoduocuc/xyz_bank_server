package cl.duoc.xyzbank.authserver.devices.infrastructure.rest;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.application.usecases.RevokeDeviceUseCase;
import cl.duoc.xyzbank.authserver.devices.infrastructure.adapters.DeviceRevocationAuthenticator;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Revokes one device on behalf of the customer who owns it; called by bff-mobile only
 * (adopt-oauth2-tokens-between-services design.md Decision 3).
 */
@RestController
public class DeviceRevocationController {

    private final DeviceRevocationAuthenticator authenticator;
    private final RevokeDeviceUseCase revokeDevice;

    public DeviceRevocationController(DeviceRevocationAuthenticator authenticator, RevokeDeviceUseCase revokeDevice) {
        this.authenticator = authenticator;
        this.revokeDevice = revokeDevice;
    }

    @PostMapping("/devices/{deviceId}/revocations")
    public ResponseEntity<Void> revoke(
            @PathVariable String deviceId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "access_token", required = false) String accessToken) {
        CustomerId customer = authenticator.customerRevoking(deviceId, authorization, accessToken);
        try {
            revokeDevice.execute(deviceId, customer);
        } catch (DomainException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}

package cl.duoc.xyzbank.authserver.devices.application.usecases;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.devices.domain.repositories.DeviceRegistrationRepository;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;

/**
 * Revokes one of a customer's devices for good. A device of another customer is reported
 * exactly like an unknown one, so a caller cannot probe which device ids exist. Revoking an
 * already revoked device is harmless, so a retried revocation succeeds again.
 */
public class RevokeDeviceUseCase {

    private final DeviceRegistrationRepository devices;

    public RevokeDeviceUseCase(DeviceRegistrationRepository devices) {
        this.devices = devices;
    }

    public void execute(String deviceId, CustomerId customerId) {
        DeviceRegistration device = devices.findByDeviceId(deviceId)
                .filter(registration -> registration.isOwnedBy(customerId))
                .orElseThrow(() -> DomainException.notFound("Device " + deviceId + " not found"));
        device.revoke();
        devices.save(device);
    }
}

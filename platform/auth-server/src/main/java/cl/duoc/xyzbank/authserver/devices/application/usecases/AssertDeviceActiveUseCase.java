package cl.duoc.xyzbank.authserver.devices.application.usecases;

import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.devices.domain.repositories.DeviceRegistrationRepository;

/**
 * Refuses a device that was revoked; a device never seen before is fine (its first login
 * registers it).
 */
public class AssertDeviceActiveUseCase {

    private final DeviceRegistrationRepository devices;

    public AssertDeviceActiveUseCase(DeviceRegistrationRepository devices) {
        this.devices = devices;
    }

    public void execute(String deviceId) {
        devices.findByDeviceId(deviceId).ifPresent(DeviceRegistration::assertActive);
    }
}

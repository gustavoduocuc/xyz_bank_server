package cl.duoc.xyzbank.authserver.devices.application.usecases;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.devices.domain.repositories.DeviceRegistrationRepository;

/**
 * Registers the device a mobile login was completed on, the first time it is seen, and
 * refuses a revoked one.
 */
public class RegisterDeviceForLoginUseCase {

    private final DeviceRegistrationRepository devices;

    public RegisterDeviceForLoginUseCase(DeviceRegistrationRepository devices) {
        this.devices = devices;
    }

    public void execute(String deviceId, CustomerId customerId) {
        DeviceRegistration device = devices.findByDeviceId(deviceId)
                .orElseGet(() -> DeviceRegistration.register(deviceId, customerId));
        device.assertActive();
        devices.save(device);
    }
}

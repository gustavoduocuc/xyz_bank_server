package cl.duoc.xyzbank.authserver.devices.domain.repositories;

import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;

import java.util.Optional;

public interface DeviceRegistrationRepository {

    Optional<DeviceRegistration> findByDeviceId(String deviceId);

    void save(DeviceRegistration device);
}

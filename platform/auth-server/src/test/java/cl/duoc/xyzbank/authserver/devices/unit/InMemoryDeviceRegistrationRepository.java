package cl.duoc.xyzbank.authserver.devices.unit;

import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.devices.domain.repositories.DeviceRegistrationRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryDeviceRegistrationRepository implements DeviceRegistrationRepository {

    private final Map<String, DeviceRegistration> devices = new ConcurrentHashMap<>();

    @Override
    public Optional<DeviceRegistration> findByDeviceId(String deviceId) {
        return Optional.ofNullable(devices.get(deviceId));
    }

    @Override
    public void save(DeviceRegistration device) {
        devices.put(device.deviceId(), device);
    }
}

package cl.duoc.xyzbank.authserver.devices.infrastructure.persistence;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.devices.domain.repositories.DeviceRegistrationRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/**
 * Device registrations in auth-server's own database (table from Flyway V2). A save inserts a
 * new device or updates an existing one, so registering on every login is idempotent.
 */
public class JdbcDeviceRegistrationRepository implements DeviceRegistrationRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDeviceRegistrationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DeviceRegistration> findByDeviceId(String deviceId) {
        return jdbcTemplate.query(
                "SELECT device_id, customer_id, revoked, version FROM device_registrations WHERE device_id = ?",
                (row, index) -> DeviceRegistration.create(
                        row.getString("device_id"),
                        CustomerId.create(row.getString("customer_id")),
                        row.getBoolean("revoked"),
                        row.getLong("version")),
                deviceId).stream().findFirst();
    }

    @Override
    public void save(DeviceRegistration device) {
        jdbcTemplate.update("""
                INSERT INTO device_registrations (device_id, customer_id, revoked, version)
                VALUES (?, ?, ?, 0)
                ON CONFLICT (device_id) DO UPDATE
                SET revoked = EXCLUDED.revoked, version = device_registrations.version + 1
                """,
                device.deviceId(), device.customerId().toPrimitives(), device.isRevoked());
    }
}

package cl.duoc.xyzbank.authserver.devices.integration;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.devices.infrastructure.persistence.JdbcDeviceRegistrationRepository;
import cl.duoc.xyzbank.authserver.testsupport.AbstractAuthServerIT;
import cl.duoc.xyzbank.authserver.testsupport.IsolatedSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migrated from core-service's JpaDeviceRegistrationRepositoryIT (cases 1-3) when device
 * registrations moved to auth-server's database.
 */
@DisplayName("The JDBC device registration repository")
class JdbcDeviceRegistrationRepositoryIT extends AbstractAuthServerIT {

    /*
     * Cases:
     * 1. Saves a device registration and finds it by device id
     * 2. Returns empty when no registration matches the device id
     * 3. Revoking a device is reflected on the next find
     */

    private static final CustomerId CUSTOMER = CustomerId.create("11111111-1111-1111-1111-111111111111");

    private JdbcDeviceRegistrationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcDeviceRegistrationRepository(IsolatedSchema.freshlyMigrated(POSTGRES, "devices_it"));
    }

    @Test
    @DisplayName("saves a device registration and finds it by device id")
    void savesADeviceRegistrationAndFindsItByDeviceId() {
        repository.save(DeviceRegistration.register("D1", CUSTOMER));

        DeviceRegistration found = repository.findByDeviceId("D1").orElseThrow();

        assertTrue(found.isOwnedBy(CUSTOMER));
        assertFalse(found.isRevoked());
    }

    @Test
    @DisplayName("returns empty when no registration matches the device id")
    void returnsEmptyWhenNoRegistrationMatchesTheDeviceId() {
        Optional<DeviceRegistration> found = repository.findByDeviceId("unknown");

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("reflects a revocation on the next find")
    void reflectsARevocationOnTheNextFind() {
        repository.save(DeviceRegistration.register("D1", CUSTOMER));
        DeviceRegistration device = repository.findByDeviceId("D1").orElseThrow();
        device.revoke();

        repository.save(device);

        DeviceRegistration found = repository.findByDeviceId("D1").orElseThrow();
        assertTrue(found.isRevoked());
        assertEquals(CUSTOMER, found.customerId());
    }
}

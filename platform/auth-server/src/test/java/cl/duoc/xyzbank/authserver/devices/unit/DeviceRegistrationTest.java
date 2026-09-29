package cl.duoc.xyzbank.authserver.devices.unit;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migrated from core-domain's DeviceRegistrationTest (cases 1-3) when device registration
 * moved to auth-server (adopt-oauth2-tokens-between-services design.md Decision 6).
 */
@DisplayName("The DeviceRegistration")
class DeviceRegistrationTest {

    /*
     * Cases:
     * 1. Registering a new device is not revoked and does not reject use
     * 2. Revoking a device marks it revoked
     * 3. A revoked device rejects further use
     * 4. A device belongs to the customer it was registered for, and to no other
     * 5. Rejects a blank device id
     */

    private static final CustomerId CUSTOMER = CustomerId.create("11111111-1111-1111-1111-111111111111");
    private static final CustomerId OTHER_CUSTOMER = CustomerId.create("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("registering a new device is not revoked and does not reject use")
    void registeringNewDeviceIsNotRevoked() {
        DeviceRegistration device = DeviceRegistration.register("D1", CUSTOMER);

        assertFalse(device.isRevoked());
        assertDoesNotThrow(device::assertActive);
    }

    @Test
    @DisplayName("revoking a device marks it revoked")
    void revokingDeviceMarksItRevoked() {
        DeviceRegistration device = DeviceRegistration.register("D1", CUSTOMER);

        device.revoke();

        assertTrue(device.isRevoked());
    }

    @Test
    @DisplayName("a revoked device rejects further use")
    void revokedDeviceRejectsFurtherUse() {
        DeviceRegistration device = DeviceRegistration.register("D1", CUSTOMER);
        device.revoke();

        DomainException exception = assertThrows(DomainException.class, device::assertActive);

        assertEquals(DomainException.Type.CONFLICT, exception.getType());
    }

    @Test
    @DisplayName("belongs to the customer it was registered for, and to no other")
    void belongsToTheCustomerItWasRegisteredFor() {
        DeviceRegistration device = DeviceRegistration.register("D1", CUSTOMER);

        assertTrue(device.isOwnedBy(CUSTOMER));
        assertFalse(device.isOwnedBy(OTHER_CUSTOMER));
    }

    @Test
    @DisplayName("rejects a blank device id")
    void rejectsABlankDeviceId() {
        DomainException exception =
                assertThrows(DomainException.class, () -> DeviceRegistration.register(" ", CUSTOMER));

        assertEquals(DomainException.Type.VALIDATION, exception.getType());
    }
}

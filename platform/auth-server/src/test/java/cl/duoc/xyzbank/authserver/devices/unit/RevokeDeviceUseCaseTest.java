package cl.duoc.xyzbank.authserver.devices.unit;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.application.usecases.RevokeDeviceUseCase;
import cl.duoc.xyzbank.authserver.devices.domain.entities.DeviceRegistration;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cases 1-2 migrated from core-service's RevokeDeviceUseCaseTest (adopt-oauth2-tokens-
 * between-services design.md Decision 6).
 */
@DisplayName("The RevokeDeviceUseCase")
class RevokeDeviceUseCaseTest {

    /*
     * Cases:
     * 1. Revokes the targeted device
     * 2. Does not affect another device registered to the same customer
     * 3. Reports an unknown device as not found
     * 4. Reports a device of another customer as not found, and leaves it active
     * 5. Revoking an already revoked device again is harmless
     */

    private static final CustomerId CUSTOMER = CustomerId.create("11111111-1111-1111-1111-111111111111");
    private static final CustomerId OTHER_CUSTOMER = CustomerId.create("33333333-3333-3333-3333-333333333333");

    private InMemoryDeviceRegistrationRepository devices;
    private RevokeDeviceUseCase useCase;

    @BeforeEach
    void setUp() {
        devices = new InMemoryDeviceRegistrationRepository();
        devices.save(DeviceRegistration.register("D1", CUSTOMER));
        devices.save(DeviceRegistration.register("D2", CUSTOMER));
        devices.save(DeviceRegistration.register("X1", OTHER_CUSTOMER));
        useCase = new RevokeDeviceUseCase(devices);
    }

    @Test
    @DisplayName("revokes the targeted device")
    void revokesTheTargetedDevice() {
        useCase.execute("D1", CUSTOMER);

        assertTrue(devices.findByDeviceId("D1").orElseThrow().isRevoked());
    }

    @Test
    @DisplayName("does not affect another device registered to the same customer")
    void doesNotAffectAnotherDeviceRegisteredToTheSameCustomer() {
        useCase.execute("D1", CUSTOMER);

        assertFalse(devices.findByDeviceId("D2").orElseThrow().isRevoked());
    }

    @Test
    @DisplayName("reports an unknown device as not found")
    void reportsAnUnknownDeviceAsNotFound() {
        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute("nope", CUSTOMER));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
    }

    @Test
    @DisplayName("reports a device of another customer as not found, and leaves it active")
    void reportsADeviceOfAnotherCustomerAsNotFound() {
        DomainException exception = assertThrows(DomainException.class, () -> useCase.execute("X1", CUSTOMER));

        assertEquals(DomainException.Type.NOT_FOUND, exception.getType());
        assertFalse(devices.findByDeviceId("X1").orElseThrow().isRevoked());
    }

    @Test
    @DisplayName("revoking an already revoked device again is harmless")
    void revokingAnAlreadyRevokedDeviceAgainIsHarmless() {
        useCase.execute("D1", CUSTOMER);

        assertDoesNotThrow(() -> useCase.execute("D1", CUSTOMER));
        assertTrue(devices.findByDeviceId("D1").orElseThrow().isRevoked());
    }
}

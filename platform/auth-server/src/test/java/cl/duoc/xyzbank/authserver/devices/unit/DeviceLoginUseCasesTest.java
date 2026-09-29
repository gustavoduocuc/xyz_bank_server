package cl.duoc.xyzbank.authserver.devices.unit;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.devices.application.usecases.AssertDeviceActiveUseCase;
import cl.duoc.xyzbank.authserver.devices.application.usecases.RegisterDeviceForLoginUseCase;
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
 * Cases 1 and 4 cover what core-service's RotateMobileRefreshTokenUseCaseTest cases 1 and 4
 * checked at first issuance (adopt-oauth2-tokens-between-services design.md Decision 6).
 */
@DisplayName("The device login use cases")
class DeviceLoginUseCasesTest {

    /*
     * Cases:
     * 1. The first login from a device registers it for the customer
     * 2. Logging in again from the same device keeps its single registration
     * 3. A device that was never registered may start a login
     * 4. A revoked device cannot log in or refresh
     */

    private static final CustomerId CUSTOMER = CustomerId.create("11111111-1111-1111-1111-111111111111");

    private InMemoryDeviceRegistrationRepository devices;
    private RegisterDeviceForLoginUseCase registerDevice;
    private AssertDeviceActiveUseCase assertDeviceActive;

    @BeforeEach
    void setUp() {
        devices = new InMemoryDeviceRegistrationRepository();
        registerDevice = new RegisterDeviceForLoginUseCase(devices);
        assertDeviceActive = new AssertDeviceActiveUseCase(devices);
    }

    @Test
    @DisplayName("registers a device for the customer on its first login")
    void registersADeviceForTheCustomerOnItsFirstLogin() {
        registerDevice.execute("D1", CUSTOMER);

        DeviceRegistration device = devices.findByDeviceId("D1").orElseThrow();
        assertTrue(device.isOwnedBy(CUSTOMER));
        assertFalse(device.isRevoked());
    }

    @Test
    @DisplayName("keeps a single registration when the device logs in again")
    void keepsASingleRegistrationWhenTheDeviceLogsInAgain() {
        registerDevice.execute("D1", CUSTOMER);

        assertDoesNotThrow(() -> registerDevice.execute("D1", CUSTOMER));
        assertTrue(devices.findByDeviceId("D1").orElseThrow().isOwnedBy(CUSTOMER));
    }

    @Test
    @DisplayName("lets a device that was never registered start a login")
    void letsADeviceThatWasNeverRegisteredStartALogin() {
        assertDoesNotThrow(() -> assertDeviceActive.execute("new-device"));
    }

    @Test
    @DisplayName("keeps a revoked device from logging in or refreshing")
    void keepsARevokedDeviceFromLoggingInOrRefreshing() {
        DeviceRegistration revoked = DeviceRegistration.register("D1", CUSTOMER);
        revoked.revoke();
        devices.save(revoked);

        DomainException onCheck = assertThrows(DomainException.class, () -> assertDeviceActive.execute("D1"));
        DomainException onLogin = assertThrows(DomainException.class, () -> registerDevice.execute("D1", CUSTOMER));

        assertEquals(DomainException.Type.CONFLICT, onCheck.getType());
        assertEquals(DomainException.Type.CONFLICT, onLogin.getType());
    }
}

package cl.duoc.xyzbank.authserver.devices.domain.entities;

import cl.duoc.xyzbank.authserver.customers.domain.valueobjects.CustomerId;
import cl.duoc.xyzbank.authserver.shared.domain.DomainException;

/**
 * A mobile device a customer has logged in from. Once revoked it stays revoked: it can
 * neither refresh nor log in again (moved from core-domain, adopt-oauth2-tokens-between-
 * services design.md Decision 3).
 */
public final class DeviceRegistration {

    private final String deviceId;
    private final CustomerId customerId;
    private boolean revoked;
    private final long version;

    private DeviceRegistration(String deviceId, CustomerId customerId, boolean revoked, long version) {
        this.deviceId = deviceId;
        this.customerId = customerId;
        this.revoked = revoked;
        this.version = version;
    }

    public static DeviceRegistration create(String deviceId, CustomerId customerId, boolean revoked, long version) {
        if (deviceId == null || deviceId.isBlank()) {
            throw DomainException.validation("Device id cannot be blank");
        }
        if (customerId == null) {
            throw DomainException.validation("A device must belong to a customer");
        }
        return new DeviceRegistration(deviceId, customerId, revoked, version);
    }

    public static DeviceRegistration register(String deviceId, CustomerId customerId) {
        return create(deviceId, customerId, false, 0L);
    }

    public void revoke() {
        revoked = true;
    }

    public void assertActive() {
        if (revoked) {
            throw DomainException.conflict("Device " + deviceId + " is revoked");
        }
    }

    public boolean isOwnedBy(CustomerId candidate) {
        return customerId.equals(candidate);
    }

    public String deviceId() {
        return deviceId;
    }

    public CustomerId customerId() {
        return customerId;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public long version() {
        return version;
    }
}

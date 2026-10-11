package cl.duoc.xyzbank.customersservice.customers.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "customers")
public class CustomerJpaEntity {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    private String phone;

    private String address;

    @Version
    private Long version;

    @Column(name = "idempotency_key", updatable = false)
    private String idempotencyKey;

    protected CustomerJpaEntity() {
    }

    CustomerJpaEntity(
            UUID id, String fullName, String email, String phone, String address, Long version, String idempotencyKey) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.version = version;
        this.idempotencyKey = idempotencyKey;
    }

    UUID getId() {
        return id;
    }

    String getFullName() {
        return fullName;
    }

    String getEmail() {
        return email;
    }

    String getPhone() {
        return phone;
    }

    String getAddress() {
        return address;
    }

    Long getVersion() {
        return version;
    }
}

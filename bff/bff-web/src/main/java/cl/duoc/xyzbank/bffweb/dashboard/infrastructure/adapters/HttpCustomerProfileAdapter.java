package cl.duoc.xyzbank.bffweb.dashboard.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.CustomerProfile;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.CustomerProfilePort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCustomerProfileAdapter implements CustomerProfilePort {

    private final RestClient customersServiceClient;

    public HttpCustomerProfileAdapter(@Qualifier("customersServiceClient") RestClient customersServiceClient) {
        this.customersServiceClient = customersServiceClient;
    }

    /** The profile is owned by customers-service; a read, so it may be retried (bff-resilience spec). */
    @Override
    @CircuitBreaker(name = "customersService")
    @Retry(name = "customersServiceRead")
    public CustomerProfile fetchProfile(String customerId) {
        CustomerProfileWire wire = CoreServiceCalls.fetch(() -> customersServiceClient.get()
                .uri("/internal/customers/{customerId}", customerId)
                .retrieve()
                .body(CustomerProfileWire.class));
        return new CustomerProfile(wire.id(), wire.fullName(), wire.email());
    }

    private record CustomerProfileWire(String id, String fullName, String email) {
    }
}

package cl.duoc.xyzbank.bffweb.dashboard.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.dashboard.application.dto.CustomerProfile;
import cl.duoc.xyzbank.bffweb.dashboard.application.ports.CustomerProfilePort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCustomerProfileAdapter implements CustomerProfilePort {

    private final RestClient coreServiceClient;

    public HttpCustomerProfileAdapter(RestClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    /** A read, so it may be retried (bff-resilience spec); the breaker sees every attempt. */
    @Override
    @CircuitBreaker(name = "coreService")
    @Retry(name = "coreServiceRead")
    public CustomerProfile fetchProfile(String customerId) {
        CustomerProfileWire wire = CoreServiceCalls.fetch(() -> coreServiceClient.get()
                .uri("/internal/customers/{customerId}", customerId)
                .retrieve()
                .body(CustomerProfileWire.class));
        return new CustomerProfile(wire.id(), wire.fullName(), wire.email());
    }

    private record CustomerProfileWire(String id, String fullName, String email) {
    }
}

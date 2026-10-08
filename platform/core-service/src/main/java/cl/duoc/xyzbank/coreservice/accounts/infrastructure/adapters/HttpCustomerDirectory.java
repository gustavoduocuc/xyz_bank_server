package cl.duoc.xyzbank.coreservice.accounts.infrastructure.adapters;

import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectory;
import cl.duoc.xyzbank.coreservice.accounts.application.ports.CustomerDirectoryUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * A 404 is an answer (the customer does not exist); anything else that prevents an answer,
 * including an open circuit, makes the directory unavailable.
 */
@Component
public class HttpCustomerDirectory implements CustomerDirectory {

    private final RestClient customersServiceClient;

    public HttpCustomerDirectory(@Qualifier("customersServiceClient") RestClient customersServiceClient) {
        this.customersServiceClient = customersServiceClient;
    }

    @Override
    @CircuitBreaker(name = "customersService", fallbackMethod = "throwUnavailable")
    public boolean exists(String customerId) {
        try {
            customersServiceClient.get()
                    .uri("/internal/customers/{customerId}", customerId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound unknownCustomer) {
            return false;
        }
    }

    private boolean throwUnavailable(String customerId, Exception cause) {
        throw new CustomerDirectoryUnavailableException("customers-service could not confirm customer " + customerId, cause);
    }
}

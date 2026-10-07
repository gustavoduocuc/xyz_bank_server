package cl.duoc.xyzbank.customersservice.customers.infrastructure.rest;

import cl.duoc.xyzbank.customersservice.customers.application.CreateCustomerRequest;
import cl.duoc.xyzbank.customersservice.customers.application.CreateCustomerUseCase;
import cl.duoc.xyzbank.customersservice.customers.application.CustomerResponse;
import cl.duoc.xyzbank.customersservice.customers.application.GetCustomerUseCase;
import cl.duoc.xyzbank.customersservice.customers.application.UpdateCustomerContactRequest;
import cl.duoc.xyzbank.customersservice.customers.application.UpdateCustomerContactUseCase;
import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/customers")
public class CustomerController {

    private static final String READ_ANY_CUSTOMER = "SCOPE_customers:read";

    private final GetCustomerUseCase getCustomerUseCase;
    private final CreateCustomerUseCase createCustomerUseCase;
    private final UpdateCustomerContactUseCase updateCustomerContactUseCase;

    public CustomerController(
            GetCustomerUseCase getCustomerUseCase,
            CreateCustomerUseCase createCustomerUseCase,
            UpdateCustomerContactUseCase updateCustomerContactUseCase) {
        this.getCustomerUseCase = getCustomerUseCase;
        this.createCustomerUseCase = createCustomerUseCase;
        this.updateCustomerContactUseCase = updateCustomerContactUseCase;
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomer(@PathVariable String customerId, JwtAuthenticationToken caller) {
        requireOwnProfileForWebCallers(customerId, caller);
        return getCustomerUseCase.execute(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateCustomerRequest request) {
        return createCustomerUseCase.execute(idempotencyKey, request);
    }

    @PatchMapping("/{customerId}")
    public CustomerResponse updateContact(
            @PathVariable String customerId, @RequestBody UpdateCustomerContactRequest request) {
        return updateCustomerContactUseCase.execute(customerId, request);
    }

    /** A web token reads only its own customer; another customer looks exactly like an unknown one. */
    private static void requireOwnProfileForWebCallers(String customerId, JwtAuthenticationToken caller) {
        boolean readsAnyCustomer = caller.getAuthorities().stream()
                .anyMatch(authority -> READ_ANY_CUSTOMER.equals(authority.getAuthority()));
        if (!readsAnyCustomer && !customerId.equals(caller.getToken().getSubject())) {
            throw CustomerException.notFound("Customer " + customerId + " not found");
        }
    }
}

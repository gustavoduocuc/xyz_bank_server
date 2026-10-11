package cl.duoc.xyzbank.customersservice.notifications.infrastructure.rest;

import cl.duoc.xyzbank.customersservice.customers.domain.CustomerException;
import cl.duoc.xyzbank.customersservice.notifications.application.GetNotificationsUseCase;
import cl.duoc.xyzbank.customersservice.notifications.application.NotificationResponse;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class NotificationController {

    private static final String READ_ANY_CUSTOMER = "SCOPE_customers:read";

    private final GetNotificationsUseCase getNotificationsUseCase;

    public NotificationController(GetNotificationsUseCase getNotificationsUseCase) {
        this.getNotificationsUseCase = getNotificationsUseCase;
    }

    @GetMapping("/internal/customers/{customerId}/notifications")
    public List<NotificationResponse> getNotifications(
            @PathVariable String customerId, JwtAuthenticationToken caller) {
        requireOwnFeedForWebCallers(customerId, caller);
        return getNotificationsUseCase.execute(customerId);
    }

    /** A web token reads only its own feed; another customer looks exactly like an unknown one. */
    private static void requireOwnFeedForWebCallers(String customerId, JwtAuthenticationToken caller) {
        boolean readsAnyCustomer = caller.getAuthorities().stream()
                .anyMatch(authority -> READ_ANY_CUSTOMER.equals(authority.getAuthority()));
        if (!readsAnyCustomer && !customerId.equals(caller.getToken().getSubject())) {
            throw CustomerException.notFound(customerId);
        }
    }
}

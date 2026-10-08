package cl.duoc.xyzbank.bffweb.notifications.infrastructure.adapters;

import cl.duoc.xyzbank.bffweb.notifications.application.dto.NotificationView;
import cl.duoc.xyzbank.bffweb.notifications.application.ports.NotificationsPort;
import cl.duoc.xyzbank.bffweb.shared.infrastructure.adapters.CoreServiceCalls;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
public class HttpNotificationsAdapter implements NotificationsPort {

    private final RestClient customersServiceClient;

    public HttpNotificationsAdapter(@Qualifier("customersServiceClient") RestClient customersServiceClient) {
        this.customersServiceClient = customersServiceClient;
    }

    /** The feed is owned by customers-service; a read, so it may be retried (bff-resilience spec). */
    @Override
    @CircuitBreaker(name = "customersService")
    @Retry(name = "customersServiceRead")
    public List<NotificationView> fetchNotifications(String customerId) {
        return CoreServiceCalls.fetch(() -> customersServiceClient.get()
                .uri("/internal/customers/{customerId}/notifications", customerId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<NotificationView>>() { }));
    }
}

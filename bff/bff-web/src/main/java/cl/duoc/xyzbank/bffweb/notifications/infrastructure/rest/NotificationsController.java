package cl.duoc.xyzbank.bffweb.notifications.infrastructure.rest;

import cl.duoc.xyzbank.bffweb.notifications.application.dto.NotificationView;
import cl.duoc.xyzbank.bffweb.notifications.application.usecases.NotificationsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/customers")
public class NotificationsController {

    private final NotificationsUseCase notificationsUseCase;

    public NotificationsController(NotificationsUseCase notificationsUseCase) {
        this.notificationsUseCase = notificationsUseCase;
    }

    @GetMapping("/{customerId}/notifications")
    public List<NotificationView> getNotifications(@PathVariable String customerId) {
        return notificationsUseCase.execute(customerId);
    }
}

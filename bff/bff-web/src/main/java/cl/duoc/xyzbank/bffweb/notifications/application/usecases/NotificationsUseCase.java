package cl.duoc.xyzbank.bffweb.notifications.application.usecases;

import cl.duoc.xyzbank.bffweb.notifications.application.dto.NotificationView;
import cl.duoc.xyzbank.bffweb.notifications.application.ports.NotificationsPort;

import java.util.List;

public class NotificationsUseCase {

    private final NotificationsPort notificationsPort;

    public NotificationsUseCase(NotificationsPort notificationsPort) {
        this.notificationsPort = notificationsPort;
    }

    public List<NotificationView> execute(String customerId) {
        return notificationsPort.fetchNotifications(customerId);
    }
}

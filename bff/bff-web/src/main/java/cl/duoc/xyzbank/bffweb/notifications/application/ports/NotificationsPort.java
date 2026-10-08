package cl.duoc.xyzbank.bffweb.notifications.application.ports;

import cl.duoc.xyzbank.bffweb.notifications.application.dto.NotificationView;

import java.util.List;

public interface NotificationsPort {

    List<NotificationView> fetchNotifications(String customerId);
}

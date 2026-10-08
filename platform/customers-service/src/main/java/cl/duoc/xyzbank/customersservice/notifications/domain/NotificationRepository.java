package cl.duoc.xyzbank.customersservice.notifications.domain;

import java.util.List;

public interface NotificationRepository {

    /** Stores the notification unless one with the same event id already exists. */
    void saveIfAbsent(Notification notification);

    /** The customer's most recent notifications, newest first. */
    List<Notification> latestOf(String customerId, int limit);
}

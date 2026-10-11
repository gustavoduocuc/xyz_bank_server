package cl.duoc.xyzbank.customersservice.notifications.application;

import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationRepository;

import java.util.List;

public class GetNotificationsUseCase {

    static final int FEED_SIZE = 50;

    private final NotificationRepository notifications;

    public GetNotificationsUseCase(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public List<NotificationResponse> execute(String customerId) {
        return notifications.latestOf(customerId, FEED_SIZE).stream().map(NotificationResponse::of).toList();
    }
}

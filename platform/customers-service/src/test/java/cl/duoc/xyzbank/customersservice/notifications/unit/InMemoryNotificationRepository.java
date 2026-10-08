package cl.duoc.xyzbank.customersservice.notifications.unit;

import cl.duoc.xyzbank.customersservice.notifications.domain.Notification;
import cl.duoc.xyzbank.customersservice.notifications.domain.NotificationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class InMemoryNotificationRepository implements NotificationRepository {

    private final Map<String, Notification> byEventId = new LinkedHashMap<>();

    @Override
    public void saveIfAbsent(Notification notification) {
        byEventId.putIfAbsent(notification.eventId(), notification);
    }

    @Override
    public List<Notification> latestOf(String customerId, int limit) {
        return new ArrayList<>(byEventId.values()).stream()
                .filter(notification -> notification.customerId().equals(customerId))
                .sorted(Comparator.comparing(Notification::occurredAt).reversed())
                .limit(limit)
                .toList();
    }

    int size() {
        return byEventId.size();
    }
}

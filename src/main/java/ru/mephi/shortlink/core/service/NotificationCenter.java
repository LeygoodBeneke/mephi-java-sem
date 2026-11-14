package ru.mephi.shortlink.core.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.UserId;

public final class NotificationCenter {

    private static final int MAX_PER_USER = 50;

    private final Map<UserId, Deque<Notification>> queues = new HashMap<>();
    private final boolean enabled;

    public NotificationCenter(boolean enabled) {
        this.enabled = enabled;
    }

    public synchronized void publish(Notification notification) {
        if (!enabled) {
            return;
        }
        Deque<Notification> queue = queues.computeIfAbsent(notification.recipient(), user -> new ArrayDeque<>());
        queue.addLast(notification);
        while (queue.size() > MAX_PER_USER) {
            queue.removeFirst();
        }
        log("→ пользователю {}: {}", notification.recipient(), notification.message());
    }

    public synchronized List<Notification> pending(UserId user) {
        Deque<Notification> queue = queues.get(user);
        return queue == null ? List.of() : List.copyOf(queue);
    }

    public synchronized List<Notification> drain(UserId user) {
        Deque<Notification> queue = queues.get(user);
        if (queue == null || queue.isEmpty()) {
            return List.of();
        }
        List<Notification> drained = new ArrayList<>(queue);
        queue.clear();
        return drained;
    }

    public synchronized Set<UserId> recipientsWithPending() {
        return Set.copyOf(queues.keySet());
    }

    private static void log(String template, Object... args) {
        System.getLogger("notifications").log(System.Logger.Level.INFO, String.format(template, args));
    }
}

package ru.mephi.shortlink.core.model;

import java.time.Instant;
import java.util.Objects;

public record Notification(Instant at, NotificationKind kind, UserId recipient, ShortCode code, String message) {

    public Notification {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public enum NotificationKind {
        LIMIT_EXHAUSTED,
        TTL_EXPIRED
    }
}

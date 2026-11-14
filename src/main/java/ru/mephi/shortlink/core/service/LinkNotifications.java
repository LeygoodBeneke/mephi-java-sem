package ru.mephi.shortlink.core.service;

import java.time.Instant;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.model.Durations;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.Notification.NotificationKind;
import ru.mephi.shortlink.core.model.ShortLink;

final class LinkNotifications {

    private LinkNotifications() {
    }

    static Notification limitExhausted(AppConfig config, ShortLink link, Instant now) {
        String message = String.format(
            "Лимит переходов выбран: %s (%d из %d). Ссылка недоступна — создайте новую: new %s",
            config.renderShortLink(link.code().value()), link.redirectCount(), link.redirectLimit(),
            link.targetUrl());
        return new Notification(now, NotificationKind.LIMIT_EXHAUSTED, link.owner(), link.code(), message);
    }

    static Notification ttlExpired(AppConfig config, ShortLink link, Instant now) {
        String message = String.format(
            "Ссылка %s удалена: истёк срок жизни (%s). Засчитано переходов: %d. Создайте новую: new %s",
            config.renderShortLink(link.code().value()), Durations.humanize(config.linkTtl()),
            link.redirectCount(), link.targetUrl());
        return new Notification(now, NotificationKind.TTL_EXPIRED, link.owner(), link.code(), message);
    }
}

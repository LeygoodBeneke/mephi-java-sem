package ru.mephi.shortlink.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.repository.LinkRepository;

public final class ExpirationService {

    private static final System.Logger LOG = System.getLogger("ru.mephi.shortlink.cleanup");

    private final LinkRepository repository;
    private final NotificationCenter notifications;
    private final AppConfig config;
    private final Clock clock;

    public ExpirationService(LinkRepository repository, NotificationCenter notifications, AppConfig config,
                             Clock clock) {
        this.repository = repository;
        this.notifications = notifications;
        this.config = config;
        this.clock = clock;
    }

    public List<ShortLink> purgeExpired() {
        Instant now = clock.instant();
        List<ShortLink> removed = new ArrayList<>();
        for (ShortLink link : repository.findAll()) {
            if (!link.isExpiredAt(now) || !repository.delete(link.code(), link.owner())) {
                continue;
            }
            removed.add(link);
            if (link.claimTtlNotice()) {
                notifications.publish(LinkNotifications.ttlExpired(config, link, now));
            }
        }
        if (!removed.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "Автоочистка на {0}: удалено {1}, осталось {2}",
                new Object[] {now, removed.size(), repository.count()});
        }
        return List.copyOf(removed);
    }
}

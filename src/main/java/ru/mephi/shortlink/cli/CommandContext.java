package ru.mephi.shortlink.cli;

import java.io.PrintWriter;
import java.time.Clock;
import java.time.Instant;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.core.service.ExpirationService;
import ru.mephi.shortlink.core.service.LinkService;
import ru.mephi.shortlink.core.service.NotificationCenter;
import ru.mephi.shortlink.core.service.UserSession;

public record CommandContext(LinkService links,
                             ExpirationService expiration,
                             NotificationCenter notifications,
                             UserSession session,
                             AppConfig config,
                             Clock clock,
                             CommandRegistry registry,
                             String configSource,
                             PrintWriter out) {

    public Instant now() {
        return clock.instant();
    }

    public UserId requireUser() throws DomainException {
        return session.currentUser().orElseThrow(() -> new DomainException(
            "Пользователь ещё не определён: UUID выдаётся автоматически при первом new <url>, "
                + "либо переключитесь командой as <uuid>."));
    }
}

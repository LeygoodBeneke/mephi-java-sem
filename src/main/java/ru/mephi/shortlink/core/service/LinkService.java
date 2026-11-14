package ru.mephi.shortlink.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.exception.NotOwnerException;
import ru.mephi.shortlink.core.generation.ShortCodeGenerator;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.core.port.LinkOpener;
import ru.mephi.shortlink.core.repository.LinkRepository;
import ru.mephi.shortlink.core.validation.UrlValidator;

public final class LinkService {

    private static final int MAX_CODE_ATTEMPTS = 10;

    private static final System.Logger LOG = System.getLogger("ru.mephi.shortlink.links");

    private final LinkRepository repository;
    private final ShortCodeGenerator generator;
    private final UrlValidator urlValidator;
    private final NotificationCenter notifications;
    private final LinkOpener opener;
    private final AppConfig config;
    private final Clock clock;

    public LinkService(LinkRepository repository, ShortCodeGenerator generator, UrlValidator urlValidator,
                       NotificationCenter notifications, LinkOpener opener, AppConfig config, Clock clock) {
        this.repository = repository;
        this.generator = generator;
        this.urlValidator = urlValidator;
        this.notifications = notifications;
        this.opener = opener;
        this.config = config;
        this.clock = clock;
    }

    public ShortLink create(UserId owner, String rawUrl, OptionalInt requestedLimit) throws DomainException {
        String targetUrl = urlValidator.validate(rawUrl);
        int limit = resolveLimit(requestedLimit);
        Instant now = clock.instant();
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            ShortCode code = generator.generate();
            ShortLink candidate = new ShortLink(code, owner, targetUrl, now, config.expiresAt(now), limit);
            if (repository.save(candidate)) {
                LOG.log(System.Logger.Level.INFO, "Создана ссылка {0} для {1} (лимит {2})",
                    new Object[] {code, owner, limit});
                return candidate;
            }
        }
        throw new DomainException("Не удалось выдать свободный короткий код после " + MAX_CODE_ATTEMPTS
            + " попыток. Увеличьте link.code-length в конфигурации.");
    }

    public RedirectResult redirect(ShortCode code) {
        Instant now = clock.instant();
        Optional<ShortLink> found = repository.findByCode(code);
        if (found.isEmpty()) {
            return RedirectResult.notFound();
        }
        ShortLink link = found.get();
        if (link.isExpiredAt(now)) {
            repository.delete(code, link.owner());
            if (link.claimTtlNotice()) {
                notifications.publish(LinkNotifications.ttlExpired(config, link, now));
            }
            return RedirectResult.rejected(RedirectResult.Outcome.EXPIRED, link);
        }
        OptionalInt counted = link.registerRedirect(now);
        if (counted.isEmpty()) {
            return RedirectResult.rejected(RedirectResult.Outcome.LIMIT_REACHED, link);
        }
        LinkOpener.OpenResult opened = openQuietly(link);
        boolean becameUnavailable = link.isLimitReached();
        if (becameUnavailable && link.claimLimitNotice()) {
            notifications.publish(LinkNotifications.limitExhausted(config, link, now));
        }
        return RedirectResult.opened(link, link.targetUrl(), becameUnavailable, opened.message());
    }

    public List<ShortLink> ownedLinks(UserId owner) {
        return repository.findByOwner(owner);
    }

    public ShortLink ownedLink(UserId owner, ShortCode code) throws DomainException, NotOwnerException {
        ShortLink link = requireLink(code);
        if (!link.owner().equals(owner)) {
            throw new NotOwnerException("Ссылка " + config.renderShortLink(code.value())
                + " принадлежит другому пользователю. Смотреть, редактировать и удалять её может только создатель.");
        }
        return link;
    }

    public ShortLink updateLimit(UserId owner, ShortCode code, int newLimit)
        throws DomainException, NotOwnerException {
        ShortLink link = ownedLink(owner, code);
        if (newLimit < config.minRedirectLimit() || newLimit > config.maxRedirectLimit()) {
            throw new DomainException(limitRangeMessage(newLimit));
        }
        if (newLimit <= link.redirectCount()) {
            throw new DomainException("Новый лимит (" + newLimit + ") не больше уже засчитанных переходов ("
                + link.redirectCount() + "). Ссылка останется недоступной — создайте новую.");
        }
        link.changeRedirectLimit(newLimit);
        LOG.log(System.Logger.Level.INFO, "{0} изменил лимит {1}: {2}", new Object[] {owner, code, newLimit});
        return link;
    }

    public void delete(UserId owner, ShortCode code) throws DomainException, NotOwnerException {
        ShortLink link = ownedLink(owner, code);
        repository.delete(link.code(), owner);
        LOG.log(System.Logger.Level.INFO, "{0} удалил ссылку {1}", new Object[] {owner, code});
    }

    public int defaultRedirectLimit() {
        return config.defaultRedirectLimit();
    }

    public int maxRedirectLimit() {
        return config.maxRedirectLimit();
    }

    public String limitRangeMessage(int received) {
        return "Лимит переходов должен быть в диапазоне " + config.minRedirectLimit() + ".."
            + config.maxRedirectLimit() + ", получено: " + received + ". Без аргумента используется "
            + config.defaultRedirectLimit();
    }

    private ShortLink requireLink(ShortCode code) throws DomainException {
        return repository.findByCode(code).orElseThrow(() -> new DomainException(
            "Ссылка " + config.renderShortLink(code.value()) + " не найдена: кода не существовало"
                + " либо срок жизни истёк и ссылка удалена."));
    }

    private int resolveLimit(OptionalInt requestedLimit) throws DomainException {
        if (requestedLimit.isEmpty()) {
            return config.defaultRedirectLimit();
        }
        int limit = requestedLimit.getAsInt();
        if (limit < config.minRedirectLimit() || limit > config.maxRedirectLimit()) {
            throw new DomainException(limitRangeMessage(limit));
        }
        return limit;
    }

    private LinkOpener.OpenResult openQuietly(ShortLink link) {
        try {
            return opener.open(link.targetUrl());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Браузер не открылся для " + link.code(), e);
            return new LinkOpener.OpenResult(false, "Браузер не открылся: " + e.getMessage()
                + ". Откройте адрес вручную: " + link.targetUrl());
        }
    }
}

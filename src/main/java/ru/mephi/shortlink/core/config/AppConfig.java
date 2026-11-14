package ru.mephi.shortlink.core.config;

import java.time.Duration;
import java.time.Instant;
import ru.mephi.shortlink.core.exception.ConfigException;

public record AppConfig(Duration linkTtl,
                        int defaultRedirectLimit,
                        int minRedirectLimit,
                        int maxRedirectLimit,
                        int codeLength,
                        String baseUrl,
                        int urlMaxLength,
                        boolean autoCleanupEnabled,
                        Duration cleanupInterval,
                        boolean notificationsEnabled,
                        boolean openBrowser) {

    private static final Duration MAX_TTL = Duration.ofDays(365);

    public static AppConfig defaults() {
        return new AppConfig(Duration.ofHours(24), 50, 1, 1000, 6, "https://clck.ru/", 2048,
            true, Duration.ofMinutes(1), true, true);
    }

    public AppConfig {
        if (linkTtl == null || cleanupInterval == null || baseUrl == null) {
            throw new ConfigException("link.ttl, cleanup.interval и service.base-url не могут быть null");
        }
        String problem = check(linkTtl, defaultRedirectLimit, minRedirectLimit, maxRedirectLimit, codeLength,
            baseUrl, urlMaxLength, autoCleanupEnabled, cleanupInterval);
        if (problem != null) {
            throw new ConfigException(problem);
        }
    }

    public Instant expiresAt(Instant createdAt) {
        return createdAt.plus(linkTtl);
    }

    public String renderShortLink(String code) {
        return baseUrl.endsWith("/") ? baseUrl + code : baseUrl + "/" + code;
    }

    private static String check(Duration linkTtl, int defaultRedirectLimit, int minRedirectLimit,
                                int maxRedirectLimit, int codeLength, String baseUrl, int urlMaxLength,
                                boolean cleanupEnabled, Duration cleanupInterval) {
        if (linkTtl.isZero() || linkTtl.isNegative()) {
            return "link.ttl должен быть больше нуля, получен: " + linkTtl;
        }
        if (linkTtl.compareTo(MAX_TTL) > 0) {
            return "link.ttl не может превышать 365 дней, получен: " + linkTtl;
        }
        if (minRedirectLimit < 1) {
            return "link.redirect-limit.min должен быть не меньше 1, получен: " + minRedirectLimit;
        }
        if (maxRedirectLimit < minRedirectLimit) {
            return "link.redirect-limit.max (" + maxRedirectLimit + ") меньше link.redirect-limit.min ("
                + minRedirectLimit + ")";
        }
        if (defaultRedirectLimit < minRedirectLimit || defaultRedirectLimit > maxRedirectLimit) {
            return "link.redirect-limit.default (" + defaultRedirectLimit + ") вне диапазона "
                + minRedirectLimit + ".." + maxRedirectLimit;
        }
        if (codeLength < 4 || codeLength > 12) {
            return "link.code-length должен быть в диапазоне 4..12, получен: " + codeLength;
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return "service.base-url должен начинаться с http:// или https://, получен: " + baseUrl;
        }
        if (urlMaxLength < 64 || urlMaxLength > 8192) {
            return "link.url.max-length должен быть в диапазоне 64..8192, получен: " + urlMaxLength;
        }
        if (cleanupEnabled && (cleanupInterval.isZero() || cleanupInterval.isNegative())) {
            return "cleanup.interval должен быть больше нуля, получен: " + cleanupInterval;
        }
        return null;
    }
}

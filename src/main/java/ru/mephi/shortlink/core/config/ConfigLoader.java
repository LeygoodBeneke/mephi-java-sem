package ru.mephi.shortlink.core.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Properties;
import ru.mephi.shortlink.core.exception.ConfigException;

public final class ConfigLoader {

    public static final String CONFIG_PATH_PROPERTY = "shortlink.config";

    public static final Path DEFAULT_PATH = Path.of("config", "application.properties");

    private static final System.Logger LOG = System.getLogger(ConfigLoader.class.getName());

    private final Properties fileValues;
    private final Path source;

    private ConfigLoader(Path source, Properties values) {
        this.source = source;
        this.fileValues = values;
    }

    public static AppConfig load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            LOG.log(System.Logger.Level.WARNING,
                "Конфигурационный файл не найден: {0} — применяются значения по умолчанию", String.valueOf(path));
            return AppConfig.defaults();
        }
        Properties values = new Properties();
        try (Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            values.load(input);
        } catch (IOException e) {
            throw new ConfigException("Не удалось прочитать конфигурационный файл " + path + ": " + e.getMessage(), e);
        }
        LOG.log(System.Logger.Level.INFO, "Конфигурация загружена из {0}", path);
        return new ConfigLoader(path, values).toConfig();
    }

    public static Path resolvePath(String explicitPath) {
        if (explicitPath != null && !explicitPath.isBlank()) {
            return Path.of(explicitPath.trim());
        }
        String fromProperty = System.getProperty(CONFIG_PATH_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Path.of(fromProperty.trim());
        }
        return DEFAULT_PATH;
    }

    private AppConfig toConfig() {
        AppConfig d = AppConfig.defaults();
        return new AppConfig(
            duration("link.ttl", d.linkTtl()),
            integer("link.redirect-limit.default", d.defaultRedirectLimit()),
            integer("link.redirect-limit.min", d.minRedirectLimit()),
            integer("link.redirect-limit.max", d.maxRedirectLimit()),
            integer("link.code-length", d.codeLength()),
            string("service.base-url", d.baseUrl()),
            integer("link.url.max-length", d.urlMaxLength()),
            bool("cleanup.enabled", d.autoCleanupEnabled()),
            duration("cleanup.interval", d.cleanupInterval()),
            bool("notifications.enabled", d.notificationsEnabled()),
            bool("browser.open", d.openBrowser()));
    }

    private String raw(String key) {
        String fromProperty = System.getProperty(key);
        return fromProperty != null ? fromProperty : fileValues.getProperty(key);
    }

    private String string(String key, String fallback) {
        String value = raw(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean bool(String key, boolean fallback) {
        String value = raw(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new ConfigException("Ключ " + key + " должен быть true/false, получено: " + value
                + " (файл " + source + ")");
        };
    }

    private int integer(String key, int fallback) {
        String value = raw(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("Ключ " + key + " должен быть целым числом, получено: " + value
                + " (файл " + source + ")", e);
        }
    }

    private Duration duration(String key, Duration fallback) {
        String value = raw(key);
        return value == null || value.isBlank() ? fallback : parseDuration(key, value.trim());
    }

    public static Duration parseDuration(String key, String value) {
        if (value.regionMatches(true, 0, "P", 0, 1)) {
            try {
                return Duration.parse(value.toUpperCase(Locale.ROOT));
            } catch (DateTimeParseException e) {
                throw new ConfigException("Ключ " + key + ": некорректная длительность ISO-8601 \"" + value
                    + "\". Пример: PT24H", e);
            }
        }
        if (value.length() < 2) {
            throw new ConfigException("Ключ " + key + ": длительность \"" + value
                + "\" без единицы измерения. Примеры: 45s, 30m, 24h, PT24H");
        }
        String unit = value.substring(value.length() - 1).toLowerCase(Locale.ROOT);
        long amount;
        try {
            amount = Long.parseLong(value.substring(0, value.length() - 1).trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("Ключ " + key + ": не число в длительности \"" + value
                + "\". Примеры: 45s, 30m, 24h", e);
        }
        return switch (unit) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            default -> throw new ConfigException("Ключ " + key + ": неизвестная единица длительности \"" + unit
                + "\". Доступны s, m, h, d либо ISO-8601");
        };
    }
}

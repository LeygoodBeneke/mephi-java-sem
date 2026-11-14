package ru.mephi.shortlink.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.config.ConfigLoader;
import ru.mephi.shortlink.core.exception.ConfigException;

class ConfigTest {

    @TempDir
    Path directory;

    @AfterEach
    void clearOverrides() {
        System.clearProperty("link.ttl");
        System.clearProperty("browser.open");
        System.clearProperty(ConfigLoader.CONFIG_PATH_PROPERTY);
    }

    private Path write(String content) throws IOException {
        Path file = directory.resolve("application.properties");
        Files.writeString(file, content);
        return file;
    }

    @Test
    @DisplayName("Значения по умолчанию согласованы и совпадают с поставляемым файлом")
    void defaults() {
        AppConfig config = AppConfig.defaults();
        assertEquals(Duration.ofHours(24), config.linkTtl());
        assertEquals(6, config.codeLength());
        assertEquals(50, config.defaultRedirectLimit());
        assertTrue(config.minRedirectLimit() <= config.defaultRedirectLimit());
        assertTrue(config.defaultRedirectLimit() <= config.maxRedirectLimit());
        assertEquals(config, ConfigLoader.load(directory.resolve("absent.properties")));
    }

    @Test
    @DisplayName("Срок жизни и полная короткая ссылка вычисляются из конфигурации")
    void derivedValues() {
        AppConfig config = AppConfig.defaults();
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        assertEquals(created.plus(Duration.ofHours(24)), config.expiresAt(created));
        assertEquals("https://clck.ru/3DZHeG", config.renderShortLink("3DZHeG"));
        AppConfig noSlash = new AppConfig(Duration.ofHours(1), 10, 1, 100, 6, "https://clck.ru", 2048,
            true, Duration.ofMinutes(1), true, true);
        assertEquals("https://clck.ru/3DZHeG", noSlash.renderShortLink("3DZHeG"));
    }

    @Test
    @DisplayName("Все одиннадцать ключей читаются из файла")
    void readsEveryKey() throws IOException {
        AppConfig config = ConfigLoader.load(write("""
            link.ttl=2h
            link.redirect-limit.default=7
            link.redirect-limit.min=2
            link.redirect-limit.max=20
            link.code-length=8
            service.base-url=https://short.example/
            link.url.max-length=512
            cleanup.enabled=false
            cleanup.interval=30s
            notifications.enabled=false
            browser.open=false
            """));
        assertEquals(Duration.ofHours(2), config.linkTtl());
        assertEquals(7, config.defaultRedirectLimit());
        assertEquals(2, config.minRedirectLimit());
        assertEquals(20, config.maxRedirectLimit());
        assertEquals(8, config.codeLength());
        assertEquals("https://short.example/", config.baseUrl());
        assertEquals(512, config.urlMaxLength());
        assertFalse(config.autoCleanupEnabled());
        assertEquals(Duration.ofSeconds(30), config.cleanupInterval());
        assertFalse(config.notificationsEnabled());
        assertFalse(config.openBrowser());
    }

    @Test
    @DisplayName("JVM-свойство перебивает файл — удобно для разового запуска")
    void systemPropertyOverridesFile() throws IOException {
        System.setProperty("link.ttl", "10m");
        System.setProperty("browser.open", "false");
        AppConfig config = ConfigLoader.load(write("link.ttl=24h\nbrowser.open=true\n"));
        assertEquals(Duration.ofMinutes(10), config.linkTtl());
        assertFalse(config.openBrowser());
    }

    @ParameterizedTest
    @CsvSource({"45s, 45", "30m, 1800", "24h, 86400", "7d, 604800", "PT1H30M, 5400", "pt45s, 45"})
    @DisplayName("Длительность понимается в суффиксном и ISO-8601 форматах")
    void parsesDurations(String value, long expectedSeconds) {
        assertEquals(Duration.ofSeconds(expectedSeconds), ConfigLoader.parseDuration("link.ttl", value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"24", "10x", "PT", "m"})
    @DisplayName("Некорректная длительность отклоняется с именем ключа")
    void rejectsBadDuration(String value) {
        assertTrue(assertThrows(ConfigException.class, () -> ConfigLoader.parseDuration("link.ttl", value))
            .getMessage().contains("link.ttl"));
    }

    @ParameterizedTest
    @CsvSource({
        "PT0S, 50, 1, 1000, 6, https://clck.ru/, 2048, true, PT1M, link.ttl",
        "PT9000H, 50, 1, 1000, 6, https://clck.ru/, 2048, true, PT1M, link.ttl",
        "PT1H, 50, 0, 1000, 6, https://clck.ru/, 2048, true, PT1M, redirect-limit.min",
        "PT1H, 50, 10, 5, 6, https://clck.ru/, 2048, true, PT1M, redirect-limit.max",
        "PT1H, 5, 10, 20, 6, https://clck.ru/, 2048, true, PT1M, redirect-limit.default",
        "PT1H, 50, 1, 1000, 3, https://clck.ru/, 2048, true, PT1M, code-length",
        "PT1H, 50, 1, 1000, 6, clck.ru, 2048, true, PT1M, base-url",
        "PT1H, 50, 1, 1000, 6, https://clck.ru/, 32, true, PT1M, url.max-length",
        "PT1H, 50, 1, 1000, 6, https://clck.ru/, 2048, true, PT0S, cleanup.interval"
    })
    @DisplayName("Несогласованная конфигурация отклоняется с указанием проблемного ключа")
    void rejectsInvalidConfig(String ttl, int defaultLimit, int minLimit, int maxLimit, int codeLength,
                              String baseUrl, int urlMaxLength, boolean cleanupEnabled, String cleanup,
                              String expectedKey) {
        ConfigException error = assertThrows(ConfigException.class, () -> new AppConfig(
            Duration.parse(ttl), defaultLimit, minLimit, maxLimit, codeLength, baseUrl, urlMaxLength,
            cleanupEnabled, Duration.parse(cleanup), true, true));
        assertTrue(error.getMessage().contains(expectedKey), error.getMessage());
    }

    @Test
    @DisplayName("Битое число или флаг в файле — ошибка с ключом и путём файла")
    void rejectsBadValues() throws IOException {
        ConfigException number = assertThrows(ConfigException.class,
            () -> ConfigLoader.load(write("link.code-length=abc\n")));
        assertTrue(number.getMessage().contains("link.code-length"), number.getMessage());
        assertTrue(number.getMessage().contains("целым числом"), number.getMessage());

        ConfigException flag = assertThrows(ConfigException.class,
            () -> ConfigLoader.load(write("browser.open=может_быть\n")));
        assertTrue(flag.getMessage().contains("browser.open"), flag.getMessage());
    }

    @Test
    @DisplayName("Путь к конфигу: аргумент важнее JVM-свойства, свойство — пути по умолчанию")
    void resolvesPath() {
        assertEquals(Path.of("custom.properties"), ConfigLoader.resolvePath("custom.properties"));
        System.setProperty(ConfigLoader.CONFIG_PATH_PROPERTY, "from-property.properties");
        assertEquals(Path.of("from-property.properties"), ConfigLoader.resolvePath(null));
        System.clearProperty(ConfigLoader.CONFIG_PATH_PROPERTY);
        assertEquals(ConfigLoader.DEFAULT_PATH, ConfigLoader.resolvePath(null));
    }
}

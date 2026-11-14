package ru.mephi.shortlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.mephi.shortlink.ShortLinkApplication.Options;
import ru.mephi.shortlink.core.exception.ConfigException;

class ShortLinkApplicationTest {

    @TempDir
    Path directory;

    private int lastCode;

    @AfterEach
    void clearConfigProperty() {
        System.clearProperty("shortlink.config");
    }

    private StringWriter run(String... args) {
        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);
        lastCode = ShortLinkApplication.run(args, out);
        out.flush();
        return buffer;
    }

    @Test
    @DisplayName("Флаги процесса отделяются от команды и её опций")
    void parsesArguments() {
        Options options = Options.parse(new String[] {"--no-browser", "new", "https://e.com", "--limit=10"});
        assertTrue(options.noBrowser());
        assertEquals(List.of("new", "https://e.com", "--limit=10"), options.command());

        assertEquals("a.properties", Options.parse(new String[] {"--config", "a.properties"}).configPath());
        assertEquals("b.properties", Options.parse(new String[] {"--config=b.properties"}).configPath());
        assertTrue(Options.parse(new String[] {"-h"}).helpRequested());

        assertThrows(ConfigException.class, () -> Options.parse(new String[] {"--bogus"}));
        assertThrows(ConfigException.class, () -> Options.parse(new String[] {"--config"}));
    }

    @Test
    @DisplayName("Справка печатается и завершается кодом 0")
    void printsUsage() {
        StringWriter output = run("--help");
        assertEquals(0, lastCode);
        assertTrue(output.toString().contains("Запуск:"), output.toString());
        assertTrue(output.toString().contains("--no-browser"), output.toString());
    }

    @Test
    @DisplayName("Настройки из файла применяются к короткой ссылке и сроку жизни")
    void appliesConfigFile() throws Exception {
        Path file = directory.resolve("test.properties");
        Files.writeString(file, "link.ttl=90m\nservice.base-url=https://tld.example/\n");
        StringWriter output = run("--config", file.toString(), "--no-browser", "new", "https://example.com");
        assertEquals(0, lastCode);
        String text = output.toString();
        assertTrue(text.contains("https://tld.example/"), text);
        assertTrue(text.contains("через 1 ч 30 мин"), text);
    }

    @Test
    @DisplayName("Однократный запуск использует лимит по умолчанию из конфигурации")
    void createsLinkInSingleShot() throws Exception {
        Path file = directory.resolve("one.properties");
        Files.writeString(file, "link.redirect-limit.default=4\n");
        StringWriter output = run("--config", file.toString(), "--no-browser", "new",
            "https://example.com");
        assertEquals(0, lastCode);
        assertTrue(output.toString().contains("Лимит переходов: 4"), output.toString());
        assertTrue(output.toString().contains("https://clck.ru/"), output.toString());
    }

    @Test
    @DisplayName("Битый конфигурационный файл — код 2 и объяснение без стектрейса")
    void reportsBrokenConfig() throws Exception {
        Path file = directory.resolve("broken.properties");
        Files.writeString(file, "link.ttl=вчерашний\n");
        StringWriter output = run("--config", file.toString(), "list");
        assertEquals(2, lastCode);
        assertTrue(output.toString().contains("Конфигурация не принята"), output.toString());
        assertFalse(output.toString().contains("Exception"), output.toString());
    }

    @Test
    @DisplayName("Отсутствующий файл конфигурации не фатален: ссылки создаются на значениях по умолчанию")
    void missingConfigIsNotFatal() {
        StringWriter output = run("--config", directory.resolve("absent.properties").toString(), "new",
            "https://example.com");
        assertEquals(0, lastCode);
        assertTrue(output.toString().contains("через 1 д"), output.toString());
    }

    @Test
    @DisplayName("Путь к конфигу можно задать JVM-свойством shortlink.config")
    void configPathFromSystemProperty() throws Exception {
        Path file = directory.resolve("from-property.properties");
        Files.writeString(file, "link.ttl=45m\n");
        System.setProperty("shortlink.config", file.toString());
        StringWriter output = run("--no-browser", "new", "https://example.com");
        assertEquals(0, lastCode);
        assertTrue(output.toString().contains("45 мин"), output.toString());
    }
}

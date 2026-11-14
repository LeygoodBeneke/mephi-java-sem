package ru.mephi.shortlink.support;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import ru.mephi.shortlink.app.AppContainer;
import ru.mephi.shortlink.cli.CommandContext;
import ru.mephi.shortlink.cli.CommandRegistry;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.service.ExpirationService;
import ru.mephi.shortlink.core.service.LinkService;
import ru.mephi.shortlink.core.service.NotificationCenter;
import ru.mephi.shortlink.core.service.UserSession;
import ru.mephi.shortlink.core.validation.UrlValidator;
import ru.mephi.shortlink.infrastructure.memory.InMemoryLinkRepository;

public final class Fixtures {

    public static final String SAMPLE_URL = "https://www.baeldung.com/java-9-http-client";

    private Fixtures() {
    }

    public record Harness(AppConfig config,
                          InMemoryLinkRepository repository,
                          ScriptedCodeGenerator generator,
                          RecordingLinkOpener opener,
                          MutableClock clock,
                          NotificationCenter notifications,
                          UserSession session,
                          LinkService links,
                          ExpirationService expiration) {
    }

    public static AppConfig config() {
        return config(Duration.ofHours(24));
    }

    public static AppConfig config(Duration ttl) {
        return new AppConfig(ttl, 50, 1, 1000, 6, "https://clck.ru/", 2048, true, Duration.ofMinutes(1),
            true, false);
    }

    public static Harness harness() {
        return harness(config());
    }

    public static Harness harness(AppConfig config) {
        MutableClock clock = MutableClock.frozen();
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        ScriptedCodeGenerator generator = new ScriptedCodeGenerator();
        RecordingLinkOpener opener = new RecordingLinkOpener();
        NotificationCenter notifications = new NotificationCenter(config.notificationsEnabled());
        UserSession session = new UserSession();
        LinkService links = new LinkService(repository, generator,
            new UrlValidator(config.urlMaxLength(), config.baseUrl()), notifications, opener, config, clock);
        ExpirationService expiration = new ExpirationService(repository, notifications, config, clock);
        return new Harness(config, repository, generator, opener, clock, notifications, session, links, expiration);
    }

    public record Cli(Harness harness, CommandRegistry registry, CommandContext context, PrintWriter out,
                      StringWriter buffer) {

        public String text() {
            out.flush();
            return buffer.toString();
        }

        public void clear() {
            out.flush();
            buffer.getBuffer().setLength(0);
        }
    }

    public static Cli cli(AppConfig config) {
        Harness harness = harness(config);
        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(new BufferedWriter(buffer), true);
        CommandRegistry registry = new CommandRegistry(AppContainer.commands());
        CommandContext context = new CommandContext(harness.links(), harness.expiration(), harness.notifications(),
            harness.session(), config, harness.clock(), registry, "тестовая конфигурация", out);
        return new Cli(harness, registry, context, out, buffer);
    }

    public static Cli cli() {
        return cli(config());
    }
}

package ru.mephi.shortlink.app;

import java.awt.GraphicsEnvironment;
import java.io.PrintWriter;
import java.time.Clock;
import java.util.List;
import ru.mephi.shortlink.cli.Command;
import ru.mephi.shortlink.cli.CommandContext;
import ru.mephi.shortlink.cli.CommandRegistry;
import ru.mephi.shortlink.cli.commands.LinkCommands;
import ru.mephi.shortlink.cli.commands.SessionCommands;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.generation.Base62CodeGenerator;
import ru.mephi.shortlink.core.port.LinkOpener;
import ru.mephi.shortlink.core.service.ExpirationService;
import ru.mephi.shortlink.core.service.LinkService;
import ru.mephi.shortlink.core.service.NotificationCenter;
import ru.mephi.shortlink.core.service.UserSession;
import ru.mephi.shortlink.core.validation.UrlValidator;
import ru.mephi.shortlink.infrastructure.browser.ConsoleLinkOpener;
import ru.mephi.shortlink.infrastructure.browser.DesktopLinkOpener;
import ru.mephi.shortlink.infrastructure.memory.InMemoryLinkRepository;
import ru.mephi.shortlink.infrastructure.scheduler.ExpirationScheduler;

public final class AppContainer implements AutoCloseable {

    private final CommandRegistry registry;
    private final CommandContext context;
    private final ExpirationScheduler scheduler;

    public AppContainer(AppConfig config, String configSource, PrintWriter out, boolean forceManualBrowser) {
        Clock clock = Clock.systemUTC();
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        NotificationCenter notifications = new NotificationCenter(config.notificationsEnabled());
        UserSession session = new UserSession();
        LinkService links = new LinkService(repository, new Base62CodeGenerator(config.codeLength()),
            new UrlValidator(config.urlMaxLength(), config.baseUrl()), notifications,
            chooseOpener(config, out, forceManualBrowser), config, clock);
        ExpirationService expiration = new ExpirationService(repository, notifications, config, clock);
        this.registry = new CommandRegistry(commands());
        this.context = new CommandContext(links, expiration, notifications, session, config, clock, registry,
            configSource, out);
        this.scheduler = config.autoCleanupEnabled()
            ? new ExpirationScheduler(expiration, config.cleanupInterval().toMillis())
            : null;
        if (scheduler != null) {
            scheduler.start();
        }
    }

    public static List<Command> commands() {
        return List.of(new LinkCommands.New(), new LinkCommands.Open(), new LinkCommands.ListLinks(),
            new LinkCommands.Show(), new LinkCommands.Edit(), new LinkCommands.Delete(),
            new SessionCommands.WhoAmI(), new SessionCommands.As(), new SessionCommands.Purge(),
            new SessionCommands.Help(), new SessionCommands.Exit());
    }

    private static LinkOpener chooseOpener(AppConfig config, PrintWriter out, boolean forceManualBrowser) {
        if (forceManualBrowser || !config.openBrowser() || GraphicsEnvironment.isHeadless()) {
            return new ConsoleLinkOpener(out);
        }
        return new DesktopLinkOpener();
    }

    public CommandContext context() {
        return context;
    }

    public CommandRegistry registry() {
        return registry;
    }

    @Override
    public void close() {
        if (scheduler != null) {
            scheduler.close();
        }
    }
}

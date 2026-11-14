package ru.mephi.shortlink.cli.commands;

import java.io.PrintWriter;
import java.util.List;
import ru.mephi.shortlink.cli.Command;
import ru.mephi.shortlink.cli.CommandContext;
import ru.mephi.shortlink.cli.CommandLine;
import ru.mephi.shortlink.cli.CommandRegistry;
import ru.mephi.shortlink.cli.OutputFormatter;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.Durations;
import ru.mephi.shortlink.core.model.LinkStatus;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;

public final class SessionCommands {

    private SessionCommands() {
    }

    public static final class WhoAmI implements Command {

        @Override
        public String name() {
            return "whoami";
        }

        @Override
        public String summary() {
            return "показать мой UUID и число ссылок";
        }

        @Override
        public String usage() {
            return "whoami";
        }

        @Override
        public String details() {
            return "UUID выдаётся при первом new <url> и используется вместо авторизации: по нему "
                + "определяются ваши ссылки и доставляются уведомления.";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            PrintWriter out = context.out();
            var current = context.session().currentUser();
            if (current.isEmpty()) {
                out.println("  Пользователь ещё не определён: UUID появится при первом new <url>.");
                return true;
            }
            List<ShortLink> links = context.links().ownedLinks(current.get());
            long active = links.stream().filter(l -> l.statusAt(context.now()) == LinkStatus.ACTIVE).count();
            out.println("  Ваш UUID: " + current.get());
            out.println("  Ссылок: " + links.size() + ", активных: " + active);
            return true;
        }
    }

    public static final class As implements Command {

        @Override
        public String name() {
            return "as";
        }

        @Override
        public List<String> aliases() {
            return List.of("switch");
        }

        @Override
        public String summary() {
            return "переключиться на другой UUID: as new | as <uuid>";
        }

        @Override
        public String usage() {
            return "as new   или   as <uuid>";
        }

        @Override
        public String details() {
            return """
                Примеры:
                  as new
                  as 0f6c2c2a-3b1e-4d5a-9e7f-1a2b3c4d5e6f

                Прав на чужие ссылки переключение не даёт: show/edit/delete чужой ссылки отклоняются
                проверкой владельца.""";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            String argument = line.requireArg(0, usage());
            PrintWriter out = context.out();
            if ("new".equalsIgnoreCase(argument)) {
                out.println("  Активен новый пользователь: " + context.session().becomeNewUser());
                return true;
            }
            UserId target = UserId.parse(argument);
            context.session().use(target);
            out.println("  Консоль работает от пользователя " + target);
            out.println("  Его ссылок в хранилище: " + context.links().ownedLinks(target).size());
            OutputFormatter.printNotifications(out, context.notifications().drain(target));
            return true;
        }
    }

    public static final class Purge implements Command {

        @Override
        public String name() {
            return "purge";
        }

        @Override
        public String summary() {
            return "немедленно удалить ссылки с истёкшим сроком жизни";
        }

        @Override
        public String usage() {
            return "purge";
        }

        @Override
        public String details() {
            return """
                Автоудаление работает само по cleanup.interval; команда запускает тот же проход вручную
                — удобно для проверки другого TTL без ожидания. Пример запуска:
                  java -jar short-link-service.jar --config config/application.fast-ttl.properties""";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            List<ShortLink> removed = context.expiration().purgeExpired();
            PrintWriter out = context.out();
            out.println("  Проверка на момент " + context.now() + ", удалено ссылок: " + removed.size());
            for (ShortLink link : removed) {
                out.println("    - " + context.config().renderShortLink(link.code().value()) + " (владелец "
                    + link.owner() + ", переходов " + link.redirectCount() + "/" + link.redirectLimit() + ")");
            }
            if (removed.isEmpty()) {
                out.println("  Устаревших ссылок нет: срок жизни "
                    + Durations.humanize(context.config().linkTtl()) + ", автоочистка "
                    + (context.config().autoCleanupEnabled()
                        ? "каждые " + Durations.humanize(context.config().cleanupInterval()) : "выключена"));
            }
            return true;
        }
    }

    public static final class Help implements Command {

        @Override
        public String name() {
            return "help";
        }

        @Override
        public List<String> aliases() {
            return List.of("?");
        }

        @Override
        public String summary() {
            return "список команд; help <команда> — подробности";
        }

        @Override
        public String usage() {
            return "help [команда]";
        }

        @Override
        public String details() {
            return "Примеры:\n  help\n  help new\n  open --help";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            if (line.args().isEmpty()) {
                context.out().print(context.registry().helpOverview());
                return true;
            }
            String requested = line.args().get(0);
            Command command = context.registry().find(requested)
                .orElseThrow(() -> new DomainException("Нет такой команды: " + requested
                    + ". Наберите help — полный список."));
            CommandRegistry.printHelpFor(command, context.out());
            return true;
        }
    }

    public static final class Exit implements Command {

        @Override
        public String name() {
            return "exit";
        }

        @Override
        public List<String> aliases() {
            return List.of("quit", "q");
        }

        @Override
        public String summary() {
            return "выйти из сервиса";
        }

        @Override
        public String usage() {
            return "exit";
        }

        @Override
        public String details() {
            return "Останавливает цикл консоли и фоновую автоочистку. Хранилище in-memory, поэтому "
                + "ссылки после выхода исчезают.";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) {
            return false;
        }
    }
}

package ru.mephi.shortlink.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.List;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.Durations;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.core.service.NotificationCenter;
import ru.mephi.shortlink.core.service.UserSession;

public final class ConsoleApplication {

    private static final String PROMPT = "shortlink> ";
    private static final System.Logger LOG = System.getLogger(ConsoleApplication.class.getName());

    private enum Status { OK, FAILED, EXIT }

    private final CommandRegistry registry;
    private final CommandContext context;
    private final BufferedReader input;
    private final PrintWriter out;
    private final NotificationCenter notifications;
    private final UserSession session;

    public ConsoleApplication(CommandRegistry registry, CommandContext context, Reader input, PrintWriter out) {
        this.registry = registry;
        this.context = context;
        this.input = new BufferedReader(input);
        this.out = out;
        this.notifications = context.notifications();
        this.session = context.session();
    }

    public int run() {
        printBanner();
        deliverPendingNotifications();
        while (true) {
            out.print(PROMPT);
            out.flush();
            String rawLine;
            try {
                rawLine = input.readLine();
            } catch (IOException e) {
                out.println("  Не удалось прочитать ввод: " + e.getMessage());
                return 1;
            }
            if (rawLine == null) {
                out.println();
                out.println("  Ввод закрыт — завершение работы.");
                return 0;
            }
            if (rawLine.isBlank()) {
                continue;
            }
            if (handle(rawLine) == Status.EXIT) {
                out.println("  До свидания. Хранилище in-memory: ссылки живут до выхода из сервиса.");
                return 0;
            }
            deliverPendingNotifications();
        }
    }

    public int runOnce(List<String> arguments) {
        if (arguments.isEmpty()) {
            out.println("  Пустая команда. Использование: java -jar short-link-service.jar <команда> [аргументы]");
            return 2;
        }
        return switch (handle(String.join(" ", arguments))) {
            case OK, EXIT -> 0;
            case FAILED -> 1;
        };
    }

    private Status handle(String rawLine) {
        CommandLine line;
        try {
            line = CommandParser.parse(interpret(rawLine));
        } catch (DomainException e) {
            printError(e.getMessage());
            return Status.FAILED;
        }
        try {
            return registry.dispatch(line, context) ? Status.OK : Status.EXIT;
        } catch (DomainException e) {
            printError(e.getMessage());
            return Status.FAILED;
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Сбой при выполнении команды: " + rawLine, e);
            out.println("  Внутренняя ошибка сервиса: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            out.println("  Сессия продолжена — набор команд не потерян.");
            return Status.FAILED;
        } finally {
            out.flush();
        }
    }

    String interpret(String rawLine) {
        String trimmed = rawLine.trim();
        ShortCode fromLink = ShortCode.fromLink(trimmed, context.config().baseUrl());
        if (fromLink != null) {
            return "open " + fromLink.value();
        }
        boolean singleWord = !trimmed.contains(" ") && !trimmed.contains("\t");
        if (singleWord && ShortCode.looksLikeCode(trimmed) && registry.find(trimmed).isEmpty()) {
            return "open " + trimmed;
        }
        return trimmed;
    }

    private void deliverPendingNotifications() {
        UserId user = session.currentUser().orElse(null);
        if (user != null) {
            OutputFormatter.printNotifications(out, notifications.drain(user));
            out.flush();
        }
    }

    private void printBanner() {
        out.println("Сервис коротких ссылок (PROMO IT, учебный проект)");
        out.println("Срок жизни ссылки: " + Durations.humanize(context.config().linkTtl())
            + ", лимит по умолчанию: " + context.config().defaultRedirectLimit() + ", база: "
            + context.config().baseUrl());
        out.println("Настройки: " + context.configSource());
        out.println("Наберите help — список команд, help <команда> — подробности, exit — выход.");
        out.println();
    }

    private void printError(String message) {
        out.println("  Ошибка: " + message);
        out.println("  Наберите help — список команд.");
    }
}

package ru.mephi.shortlink.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import ru.mephi.shortlink.core.exception.DomainException;

public final class CommandRegistry {

    private final Map<String, Command> index = new LinkedHashMap<>();
    private final List<Command> commands;

    public CommandRegistry(List<Command> commands) {
        this.commands = List.copyOf(commands);
        for (Command command : commands) {
            index.put(command.name(), command);
            for (String alias : command.aliases()) {
                index.putIfAbsent(alias, command);
            }
        }
    }

    public Optional<Command> find(String verb) {
        return Optional.ofNullable(index.get(verb == null ? "" : verb.toLowerCase(Locale.ROOT)));
    }

    public List<Command> sortedCommands() {
        List<Command> sorted = new ArrayList<>(commands);
        sorted.sort(Comparator.comparing(Command::name));
        return List.copyOf(sorted);
    }

    public boolean dispatch(CommandLine line, CommandContext context) throws Exception {
        Command command = find(line.verb()).orElseThrow(() -> new DomainException(
            "Неизвестная команда \"" + line.verb() + "\". Наберите help — список команд, "
                + "help <команда> — подробности."));
        if (line.hasFlag("help")) {
            printHelpFor(command, context.out());
            return true;
        }
        return command.execute(context, line);
    }

    public String helpOverview() {
        StringBuilder text = new StringBuilder("Команды сервиса коротких ссылок:").append(newline());
        for (Command command : sortedCommands()) {
            String names = command.aliases().isEmpty()
                ? command.name()
                : command.name() + ", " + String.join(", ", command.aliases());
            text.append(String.format("  %-22s %s%n", names, command.summary()));
        }
        return text.append(newline())
            .append("Подсказки:").append(newline())
            .append("  · Короткую ссылку или её код можно ввести просто строкой — это тот же open.")
            .append(newline())
            .append("  · Любая команда принимает --help, например: new --help").append(newline())
            .append("  · Аргументы с пробелами и & берите в кавычки.").append(newline())
            .append("  · Пример: new https://www.baeldung.com/java-9-http-client 5").append(newline())
            .toString();
    }

    public static void printHelpFor(Command command, PrintWriter out) {
        out.println("Использование: " + command.usage());
        out.println();
        out.print(command.details().stripTrailing() + newline());
        if (!command.aliases().isEmpty()) {
            out.println("Алиасы: " + String.join(", ", command.aliases()));
        }
    }

    private static String newline() {
        return System.lineSeparator();
    }
}

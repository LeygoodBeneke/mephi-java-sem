package ru.mephi.shortlink.cli;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import ru.mephi.shortlink.core.exception.DomainException;

public record CommandLine(String verb, List<String> args, Map<String, String> options, Set<String> flags,
                          String raw) {

    public CommandLine {
        args = List.copyOf(args);
        options = Map.copyOf(options);
        flags = Set.copyOf(flags);
    }

    public static CommandLine of(String verb, String... args) {
        return new CommandLine(verb, List.of(args), Map.of(), Set.of(), verb + " " + String.join(" ", args));
    }

    public Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name));
    }

    public boolean hasFlag(String name) {
        return flags.contains(name);
    }

    public String requireArg(int index, String usage) throws DomainException {
        if (args.size() <= index) {
            throw new DomainException("Не задан аргумент. Использование: " + usage);
        }
        return args.get(index);
    }

    public OptionalInt optionAsInt(String name) throws DomainException {
        String value = options.get(name);
        if (value == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(parseInt(value, "Опция --" + name));
    }

    public static int parseInt(String raw, String what) throws DomainException {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new DomainException(what + " должен быть целым числом, получено: \"" + raw + "\"", e);
        }
    }
}

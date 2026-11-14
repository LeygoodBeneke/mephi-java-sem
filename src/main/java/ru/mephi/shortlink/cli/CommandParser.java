package ru.mephi.shortlink.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import ru.mephi.shortlink.core.exception.DomainException;

public final class CommandParser {

    private static final String OPTION_PREFIX = "--";

    private CommandParser() {
    }

    public static CommandLine parse(String line) throws DomainException {
        List<String> tokens = tokenize(line);
        if (tokens.isEmpty()) {
            throw new DomainException("Пустая команда. Напишите help, чтобы увидеть список команд.");
        }
        String verb = tokens.get(0).toLowerCase(Locale.ROOT);
        Map<String, String> options = new LinkedHashMap<>();
        Set<String> flags = new LinkedHashSet<>();
        List<String> args = new ArrayList<>();
        for (String token : tokens.subList(1, tokens.size())) {
            if (!token.startsWith(OPTION_PREFIX) || token.length() <= OPTION_PREFIX.length()) {
                args.add(token);
                continue;
            }
            String body = token.substring(OPTION_PREFIX.length());
            int equals = body.indexOf('=');
            if (equals < 0) {
                flags.add(body.toLowerCase(Locale.ROOT));
            } else {
                options.put(body.substring(0, equals).toLowerCase(Locale.ROOT),
                    body.substring(equals + 1));
            }
        }
        return new CommandLine(verb, args, options, flags, line);
    }

    static List<String> tokenize(String line) throws DomainException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (int i = 0; i < line.length(); i++) {
            char symbol = line.charAt(i);
            if (quote != 0) {
                if (symbol == quote) {
                    quote = 0;
                } else {
                    current.append(symbol);
                }
                continue;
            }
            if (symbol == '"' || symbol == '\'') {
                quote = symbol;
                inToken = true;
                continue;
            }
            if (Character.isWhitespace(symbol)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
                continue;
            }
            current.append(symbol);
            inToken = true;
        }
        if (quote != 0) {
            throw new DomainException("Незакрытая кавычка в команде: " + line);
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}

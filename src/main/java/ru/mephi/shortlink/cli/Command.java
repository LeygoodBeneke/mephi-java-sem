package ru.mephi.shortlink.cli;

import java.util.List;

public interface Command {

    String name();

    default List<String> aliases() {
        return List.of();
    }

    String summary();

    String usage();

    String details();

    boolean execute(CommandContext context, CommandLine line) throws Exception;
}

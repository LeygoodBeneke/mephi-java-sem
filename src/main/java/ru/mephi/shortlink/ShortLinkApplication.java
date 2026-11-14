package ru.mephi.shortlink;

import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import ru.mephi.shortlink.app.AppContainer;
import ru.mephi.shortlink.cli.ConsoleApplication;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.config.ConfigLoader;
import ru.mephi.shortlink.core.exception.ConfigException;

public final class ShortLinkApplication {

    private ShortLinkApplication() {
    }

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(new BufferedWriter(
            new OutputStreamWriter(System.out, StandardCharsets.UTF_8)), true);
        int exitCode;
        try {
            exitCode = run(args, out);
        } catch (RuntimeException e) {
            out.println("Фатальная ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            exitCode = 3;
        } finally {
            out.flush();
        }
        System.exit(exitCode);
    }

    static int run(String[] args, PrintWriter out) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (ConfigException e) {
            out.println(e.getMessage());
            out.println("Наберите --help, чтобы увидеть список опций.");
            return 2;
        }
        if (options.helpRequested()) {
            out.println(usage());
            return 0;
        }
        Path configPath = ConfigLoader.resolvePath(options.configPath());
        AppConfig config;
        try {
            config = ConfigLoader.load(configPath);
        } catch (ConfigException e) {
            out.println("Конфигурация не принята: " + e.getMessage());
            return 2;
        }
        String source = Files.isRegularFile(configPath)
            ? configPath.toString()
            : "значения по умолчанию (файл " + configPath + " не найден)";
        try (AppContainer container = new AppContainer(config, source, out, options.noBrowser())) {
            ConsoleApplication application = new ConsoleApplication(container.registry(), container.context(),
                new InputStreamReader(System.in, StandardCharsets.UTF_8), out);
            return options.command().isEmpty() ? application.run() : application.runOnce(options.command());
        } finally {
            out.flush();
        }
    }

    static String usage() {
        return """
            Сервис коротких ссылок (консольный).

            Запуск:
              mvn -q package
              java -jar target/short-link-service-1.0.0.jar [опции] [команда ...]

            Опции:
              --config <файл>   конфигурация (по умолчанию config/application.properties,
                                также -Dshortlink.config=<файл>)
              --no-browser      не открывать браузер, печатать исходный адрес в консоль
              -h, --help        эта справка

            Примеры:
              java -jar target/short-link-service-1.0.0.jar
              java -jar target/short-link-service-1.0.0.jar --no-browser list
              java -jar target/short-link-service-1.0.0.jar --config config/application.fast-ttl.properties

            Команды консоли: help, new, open, list, show, edit, delete, whoami, as, purge, exit.""";
    }

    record Options(String configPath, boolean noBrowser, boolean helpRequested, List<String> command) {

        static Options parse(String[] args) {
            String configPath = null;
            boolean noBrowser = false;
            boolean help = false;
            List<String> command = new ArrayList<>();
            int index = 0;
            while (index < args.length) {
                String arg = args[index];
                if ("--config".equals(arg)) {
                    if (index + 1 >= args.length) {
                        throw new ConfigException("--config требует путь к файлу");
                    }
                    configPath = args[index + 1];
                    index += 2;
                } else if (arg.startsWith("--config=")) {
                    configPath = arg.substring("--config=".length());
                    index++;
                } else if ("--no-browser".equals(arg)) {
                    noBrowser = true;
                    index++;
                } else if ("--help".equals(arg) || "-h".equals(arg)) {
                    help = true;
                    index++;
                } else if (arg.startsWith("-")) {
                    throw new ConfigException("Неизвестная опция запуска: " + arg
                        + ". Опции должны стоять перед командой.");
                } else {
                    command.addAll(List.of(args).subList(index, args.length));
                    break;
                }
            }
            return new Options(configPath, noBrowser, help, List.copyOf(command));
        }
    }
}

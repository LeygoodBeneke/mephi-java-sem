package ru.mephi.shortlink.cli.commands;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import ru.mephi.shortlink.cli.Command;
import ru.mephi.shortlink.cli.CommandContext;
import ru.mephi.shortlink.cli.CommandLine;
import ru.mephi.shortlink.cli.OutputFormatter;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.Durations;
import ru.mephi.shortlink.core.model.LinkStatus;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;

public final class LinkCommands {

    private LinkCommands() {
    }

    public static final class New implements Command {

        @Override
        public String name() {
            return "new";
        }

        @Override
        public String summary() {
            return "сократить URL и получить короткую ссылку";
        }

        @Override
        public String usage() {
            return "new <url> [лимит]   или   new <url> --limit=<N>";
        }

        @Override
        public String details() {
            return """
                Выдаёт короткую ссылку на длинный URL. Срок жизни задаёт система (link.ttl), выбрать
                его нельзя. При первом вызове сервис выдаёт вам UUID — по нему он узнаёт ваши ссылки.

                Примеры:
                  new https://www.baeldung.com/java-9-http-client
                  new https://www.baeldung.com/java-9-http-client 5
                  new "https://example.com/?a=1&b=2" --limit=10""";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            String rawUrl = line.requireArg(0, usage());
            OptionalInt limit = line.optionAsInt("limit");
            if (limit.isEmpty() && line.args().size() > 1) {
                limit = OptionalInt.of(CommandLine.parseInt(line.args().get(1), "Лимит переходов"));
            }
            Optional<UserId> existing = context.session().currentUser();
            UserId owner = context.session().identify();
            if (existing.isEmpty()) {
                context.out().println("  Вы зарегистрированы по UUID: " + owner
                    + " — сохраните его, по нему сервис узнаёт ваши ссылки.");
            }

            ShortLink link = context.links().create(owner, rawUrl, limit);
            context.out().println("  Готово: " + context.config().renderShortLink(link.code().value()));
            context.out().println("  Исходный URL:    " + link.targetUrl());
            context.out().println("  Лимит переходов: " + link.redirectLimit());
            context.out().println("  Автоудаление:    " + link.expiresAt() + " (через "
                + Durations.humanize(context.config().linkTtl()) + ")");
            context.out().println("  Ваш UUID:        " + owner);
            context.out().println("  Переход: open " + link.code().value() + " — или вставьте короткую ссылку");
            return true;
        }
    }

    public static final class Open implements Command {

        @Override
        public String name() {
            return "open";
        }

        @Override
        public List<String> aliases() {
            return List.of("go");
        }

        @Override
        public String summary() {
            return "перейти по короткой ссылке: открывает браузер и засчитывает переход";
        }

        @Override
        public String usage() {
            return "open <код|короткая ссылка>";
        }

        @Override
        public String details() {
            return """
                Засчитывает переход и открывает адрес так, как требует ТЗ:
                Desktop.getDesktop().browse(new URI(url)). В headless-среде или при browser.open=false
                адрес печатается в консоль.

                Примеры:
                  open 3DZHeG
                  open https://clck.ru/3DZHeG""";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            ShortCode code = CommandSupport.readCode(line.requireArg(0, usage()), context.config());
            OutputFormatter.printRedirectResult(context.out(), context.config(),
                context.links().redirect(code), code);
            return true;
        }
    }

    public static final class ListLinks implements Command {

        @Override
        public String name() {
            return "list";
        }

        @Override
        public String summary() {
            return "показать мои ссылки и их состояние";
        }

        @Override
        public String usage() {
            return "list";
        }

        @Override
        public String details() {
            return "Ссылки пользователя, чей UUID активен: переходы, лимит, статус и время до "
                + "автоудаления. Чужие ссылки не выводятся.";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            UserId owner = context.requireUser();
            OutputFormatter.printLinksTable(context.out(), context.config(),
                context.links().ownedLinks(owner), context.now());
            return true;
        }
    }

    public static final class Show implements Command {

        @Override
        public String name() {
            return "show";
        }

        @Override
        public String summary() {
            return "подробности по моей короткой ссылке";
        }

        @Override
        public String usage() {
            return "show <код|короткая ссылка>";
        }

        @Override
        public String details() {
            return "Исходный URL, владелец, лимит, счётчик и время последнего перехода, момент "
                + "автоудаления. Только для владельца ссылки.";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            UserId owner = context.requireUser();
            ShortCode code = CommandSupport.readCode(line.requireArg(0, usage()), context.config());
            OutputFormatter.printLinkDetails(context.out(), context.config(),
                context.links().ownedLink(owner, code), context.now());
            return true;
        }
    }

    public static final class Edit implements Command {

        @Override
        public String name() {
            return "edit";
        }

        @Override
        public String summary() {
            return "изменить лимит переходов моей ссылки";
        }

        @Override
        public String usage() {
            return "edit <код> --limit=<N>";
        }

        @Override
        public String details() {
            return """
                Новый лимит берётся из диапазона конфигурации и обязан быть больше уже засчитанных
                переходов. Если ссылку заблокировал исчерпанный лимит, повышение лимита открывает её.
                Срок жизни менять нельзя — его задаёт система (link.ttl).

                Пример:
                  edit 3DZHeG --limit=20""";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            UserId owner = context.requireUser();
            ShortCode code = CommandSupport.readCode(line.requireArg(0, usage()), context.config());
            OptionalInt requested = line.optionAsInt("limit");
            if (requested.isEmpty()) {
                throw new DomainException("Не задан новый лимит. Использование: " + usage());
            }
            Instant now = context.now();
            boolean wasBlocked = context.links().ownedLink(owner, code).statusAt(now) == LinkStatus.LIMIT_REACHED;
            ShortLink link = context.links().updateLimit(owner, code, requested.getAsInt());
            context.out().println("  Лимит обновлён: " + context.config().renderShortLink(code.value()) + " → "
                + link.redirectLimit() + " переходов");
            if (wasBlocked) {
                context.out().println("  Ссылка снова доступна: засчитано " + link.redirectCount() + " из "
                    + link.redirectLimit());
            }
            return true;
        }
    }

    public static final class Delete implements Command {

        @Override
        public String name() {
            return "delete";
        }

        @Override
        public List<String> aliases() {
            return List.of("rm");
        }

        @Override
        public String summary() {
            return "удалить мою короткую ссылку";
        }

        @Override
        public String usage() {
            return "delete <код|короткая ссылка>";
        }

        @Override
        public String details() {
            return "Удаляет ссылку сразу, не дожидаясь истечения срока. Чужую удалить нельзя.";
        }

        @Override
        public boolean execute(CommandContext context, CommandLine line) throws Exception {
            UserId owner = context.requireUser();
            ShortCode code = CommandSupport.readCode(line.requireArg(0, usage()), context.config());
            context.links().delete(owner, code);
            context.out().println("  Удалена ссылка " + context.config().renderShortLink(code.value()));
            return true;
        }
    }
}

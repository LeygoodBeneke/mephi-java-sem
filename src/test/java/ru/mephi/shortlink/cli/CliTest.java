package ru.mephi.shortlink.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.support.Fixtures;

class CliTest {

    private Fixtures.Cli cli;

    @BeforeEach
    void setUp() {
        cli = Fixtures.cli();
    }

    private int run(String... lines) {
        StringBuilder script = new StringBuilder();
        for (String line : lines) {
            script.append(line).append('\n');
        }
        return new ConsoleApplication(cli.registry(), cli.context(), new StringReader(script.toString()),
            cli.out()).run();
    }

    private String text() {
        return cli.text();
    }

    @Test
    @DisplayName("Парсер: глагол, аргументы, кавычки, опции и флаги")
    void parsesCommandLine() throws Exception {
        CommandLine line = CommandParser.parse("NEW \"https://example.com/a b&c=1\" --limit=7 --dry-run");
        assertEquals("new", line.verb());
        assertEquals(List.of("https://example.com/a b&c=1"), line.args());
        assertEquals(OptionalInt.of(7), line.optionAsInt("limit"));
        assertTrue(line.hasFlag("dry-run"));

        assertTrue(CommandParser.parse("new https://example.com 5").args().size() == 2);
    }

    @Test
    @DisplayName("Ошибки разбора объясняются по-человечески")
    void reportsParseErrors() throws Exception {
        assertTrue(assertThrows(DomainException.class, () -> CommandParser.parse("  ")).getMessage()
            .contains("help"));
        assertTrue(assertThrows(DomainException.class, () -> CommandParser.parse("new \"https://e.com"))
            .getMessage().contains("Незакрытая кавычка"));
        CommandLine line = CommandParser.parse("new https://e.com --limit=много");
        assertTrue(assertThrows(DomainException.class, () -> line.optionAsInt("limit")).getMessage()
            .contains("--limit"));
        assertTrue(assertThrows(DomainException.class, () -> CommandLine.of("new").requireArg(0, "new <url>"))
            .getMessage().contains("new <url>"));
    }

    @Test
    @DisplayName("new выдаёт UUID и короткую ссылку, open засчитывает переход")
    void createsAndFollowsLink() {
        run("new " + Fixtures.SAMPLE_URL + " 2", "open SEQ001");
        String output = text();
        assertTrue(output.contains("Вы зарегистрированы по UUID"), output);
        assertTrue(output.contains("https://clck.ru/SEQ001"), output);
        assertTrue(output.contains("Переход выполнен"), output);
        assertTrue(output.contains("Засчитано переходов: 1 из 2, осталось 1"), output);
    }

    @Test
    @DisplayName("Вставка полной короткой ссылки и одинокого кода работает как open")
    void acceptsLinkAndBareCode() {
        run("new " + Fixtures.SAMPLE_URL + " 3", "https://clck.ru/SEQ001", "SEQ001");
        String output = text();
        assertTrue(output.contains("Переход выполнен"), output);
        assertEquals(2, cli.harness().opener().openCount());
    }

    @Test
    @DisplayName("Лимит выбран: переход заблокирован, уведомление владельцу печатается сразу")
    void blocksAndNotifiesInConsole() {
        run("new " + Fixtures.SAMPLE_URL + " 1", "open SEQ001", "open SEQ001");
        String output = text();
        assertTrue(output.contains("лимит выбран (1 из 1)"), output);
        assertTrue(output.contains("Создайте новую"), output);
        assertTrue(output.contains("Лимит переходов выбран: https://clck.ru/SEQ001 (1 из 1)"), output);
        assertFalse(output.contains("%s"), "в уведомлении не осталось плейсхолдеров: " + output);
        assertEquals(1, cli.harness().opener().openCount());
    }

    @Test
    @DisplayName("help, help <команда> и --help у команды")
    void printsHelp() {
        run("help", "help new", "open --help", "help nosuch");
        String output = text();
        assertTrue(output.contains("Команды сервиса коротких ссылок"), output);
        assertTrue(output.contains("Использование: new <url> [лимит]"), output);
        assertTrue(output.contains("Desktop.getDesktop().browse"), output);
        assertTrue(output.contains("Нет такой команды: nosuch"), output);
    }

    @Test
    @DisplayName("Ошибки пользователя: неизвестная команда, невалидный URL, отсутствие аргумента")
    void reportsUserErrors() {
        run("frobnicate now", "new javascript:alert(1)", "new", "new https://e.com 1001");
        String output = text();
        assertTrue(output.contains("Неизвестная команда \"frobnicate\""), output);
        assertTrue(output.contains("Схема \"javascript\" запрещена"), output);
        assertTrue(output.contains("Не задан аргумент"), output);
        assertTrue(output.contains("Лимит переходов должен быть в диапазоне 1..1000"), output);
        assertEquals(0, cli.harness().repository().count());
    }

    @Test
    @DisplayName("Команды доступны только владельцу ссылки (ТЗ 2.4)")
    void deniesForeignOwner() throws Exception {
        UserId owner = cli.harness().session().identify();
        cli.harness().links().create(owner, Fixtures.SAMPLE_URL, OptionalInt.of(5));
        run("as new", "show SEQ001", "edit SEQ001 --limit=9", "delete SEQ001");
        assertTrue(text().contains("принадлежит другому пользователю"), text());
        assertEquals(1, cli.harness().repository().count());
    }

    @Test
    @DisplayName("edit повышает лимит и разблокирует ссылку")
    void editReactivatesLink() {
        run("new " + Fixtures.SAMPLE_URL + " 1", "open SEQ001", "edit SEQ001 --limit=3", "open SEQ001");
        String output = text();
        assertTrue(output.contains("Лимит обновлён: https://clck.ru/SEQ001 → 3 переходов"), output);
        assertTrue(output.contains("Ссылка снова доступна"), output);
        assertTrue(output.contains("Засчитано переходов: 2 из 3"), output);
    }

    @Test
    @DisplayName("Истечение срока в консоли: ссылка удалена, список пуст, есть уведомление")
    void expiryThroughConsole() {
        run("new " + Fixtures.SAMPLE_URL + " 5");
        cli.harness().clock().advance(Duration.ofHours(25));
        run("open SEQ001");
        String output = text();
        assertTrue(output.contains("истёк срок жизни, она удалена"), output);
        assertTrue(output.contains("Уведомления (1)"), output);
        assertEquals(0, cli.harness().repository().count());
        run("list");
        assertTrue(text().contains("ссылок пока нет"), text());
    }

    @Test
    @DisplayName("purge удаляет устаревшие и показывает отчёт")
    void purgeReports() {
        run("new " + Fixtures.SAMPLE_URL + " 5");
        cli.harness().clock().advance(Duration.ofHours(24));
        run("purge");
        String output = text();
        assertTrue(output.contains("удалено ссылок: 1"), output);
        assertTrue(output.contains("SEQ001"), output);
    }

    @Test
    @DisplayName("whoami до и после идентификации, as <uuid> — чужой список пуст")
    void showsIdentity() throws Exception {
        run("whoami", "list");
        assertTrue(text().contains("Пользователь ещё не определён"), text());

        cli = Fixtures.cli();
        UserId first = cli.harness().session().identify();
        cli.harness().links().create(first, Fixtures.SAMPLE_URL, OptionalInt.of(1));
        run("whoami", "as " + UserId.random(), "list");
        String output = text();
        assertTrue(output.contains("Ссылок: 1, активных: 1"), output);
        assertTrue(output.contains("Его ссылок в хранилище: 0"), output);
        assertTrue(output.contains("ссылок пока нет"), output);
    }

    @Test
    @DisplayName("exit и конец ввода завершают сессию кодом 0")
    void exitsCleanly() {
        assertEquals(0, run("exit", "new https://example.com"));
        assertTrue(text().contains("До свидания"), text());
        assertFalse(text().contains("Готово:"), "команды после exit не выполняются");
    }

    @Test
    @DisplayName("Однократный режим: коды возврата 0 / 1 / 2")
    void singleShotExitCodes() {
        ConsoleApplication application = new ConsoleApplication(cli.registry(), cli.context(),
            new StringReader(""), cli.out());
        assertEquals(0, application.runOnce(List.of("new", Fixtures.SAMPLE_URL, "4")));
        assertTrue(text().contains("Лимит переходов: 4"), text());

        cli.clear();
        assertEquals(1, application.runOnce(List.of("frobnicate", "now")));
        assertEquals(1, application.runOnce(List.of("new", "ftp://example.com")));
        assertEquals(2, application.runOnce(List.of()));
    }

    @Test
    @DisplayName("interpret: ссылка и код читаются как open, команды — как команды")
    void interpretsInput() {
        ConsoleApplication application = new ConsoleApplication(cli.registry(), cli.context(),
            new StringReader(""), cli.out());
        assertEquals("open SEQ001", application.interpret("https://clck.ru/SEQ001"));
        assertEquals("open SEQ001", application.interpret("  SEQ001  "));
        assertEquals("list", application.interpret("list"));
        assertEquals("new https://example.com", application.interpret("new https://example.com"));
    }

    @Test
    @DisplayName("Чтение кода из строки: код, полная ссылка и мусор")
    void readsCodeFromConsoleInput() {
        cli = Fixtures.cli();
        run("new " + Fixtures.SAMPLE_URL + " 2", "show SEQ001", "open https://clck.ru/SEQ001", "open ???");
        String output = text();
        assertTrue(output.contains("не короткий код и не короткая ссылка"), output);
        assertTrue(output.contains("Переход выполнен"), output);
    }
}

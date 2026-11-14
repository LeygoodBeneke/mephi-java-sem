package ru.mephi.shortlink.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.Durations;
import ru.mephi.shortlink.core.model.LinkStatus;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UnavailabilityReason;
import ru.mephi.shortlink.core.model.UserId;

class ModelTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T12:00:00Z");

    private static ShortLink link(int limit, Duration ttl) {
        return new ShortLink(new ShortCode("abcd12"), UserId.random(), "https://example.com", CREATED,
            CREATED.plus(ttl), limit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcd", "0123", "a1B2c3", "abcdefghijklm", "abc", "with space", "dot.code", ""})
    @DisplayName("Код — только base62 длиной 4..12")
    void codeShape(String candidate) {
        assertEquals(candidate.matches("[0-9A-Za-z]{4,12}"), ShortCode.looksLikeCode(candidate), candidate);
        if (!candidate.matches("[0-9A-Za-z]{4,12}")) {
            assertThrows(IllegalArgumentException.class, () -> new ShortCode(candidate));
        }
    }

    @Test
    @DisplayName("Регистр букв в коде значим: aB1 и ab1 — разные ссылки")
    void codeIsCaseSensitive() {
        assertNotEquals(new ShortCode("aB1xyz"), new ShortCode("ab1xyz"));
        assertEquals("aB1xyz", new ShortCode("aB1xyz").value());
    }

    @Test
    @DisplayName("Код извлекается из полной короткой ссылки, хвост пути и query отбрасываются")
    void codeFromLink() {
        assertEquals("3DZHeG", ShortCode.fromLink("https://clck.ru/3DZHeG", "https://clck.ru/").value());
        assertEquals("3DZHeG", ShortCode.fromLink("https://clck.ru/3DZHeG/extra", "https://clck.ru/").value());
        assertEquals("3DZHeG", ShortCode.fromLink("https://clck.ru/3DZHeG?utm=1", "https://clck.ru/").value());
        assertNull(ShortCode.fromLink("https://example.com/3DZHeG", "https://clck.ru/"));
        assertNull(ShortCode.fromLink("3DZHeG", "https://clck.ru/"));
    }

    @Test
    @DisplayName("Пустой код объясняется DomainException с примером")
    void blankCode() {
        assertTrue(assertThrows(DomainException.class, () -> ShortCode.of(" ")).getMessage()
            .contains("Пустой короткий код"));
    }

    @Test
    @DisplayName("Переходы считаются до лимита и не могут его превысить")
    void countsUntilLimit() {
        ShortLink link = link(3, Duration.ofHours(1));
        for (int expected = 1; expected <= 3; expected++) {
            assertEquals(expected, link.registerRedirect(CREATED).orElseThrow());
        }
        assertEquals(OptionalInt.empty(), link.registerRedirect(CREATED));
        assertTrue(link.isLimitReached());
        assertEquals(0, link.remainingRedirects());
        assertEquals(3, link.redirectCount(), "отклонённый переход не увеличивает счётчик");
        assertEquals(LinkStatus.LIMIT_REACHED, link.statusAt(CREATED));
    }

    @Test
    @DisplayName("Граница срока жизни: ровно в expiresAt ссылка уже устарела")
    void expiryBoundary() {
        ShortLink link = link(5, Duration.ofHours(24));
        assertFalse(link.isExpiredAt(CREATED.plus(Duration.ofHours(24)).minusMillis(1)));
        assertTrue(link.isExpiredAt(CREATED.plus(Duration.ofHours(24))));
        assertEquals(LinkStatus.EXPIRED, link.statusAt(CREATED.plus(Duration.ofHours(30))));
        assertEquals(Duration.ofHours(23), link.timeLeft(CREATED.plus(Duration.ofHours(1))));
    }

    @Test
    @DisplayName("Лимит нельзя уменьшить до уже засчитанных переходов, повышение снимает метку уведомления")
    void changeLimitAndNoticeClaims() {
        ShortLink link = link(1, Duration.ofHours(1));
        link.registerRedirect(CREATED);
        assertTrue(link.claimLimitNotice());
        assertFalse(link.claimLimitNotice(), "дубликат уведомления не отправляется");
        assertThrows(IllegalStateException.class, () -> link.changeRedirectLimit(1));

        link.changeRedirectLimit(5);
        assertTrue(link.claimLimitNotice(), "второе исчерпание должно уведомить снова");
        assertEquals(4, link.remainingRedirects());
    }

    @Test
    @DisplayName("Некорректные параметры ссылки отклоняются конструктором")
    void constructorValidates() {
        UserId owner = UserId.random();
        ShortCode code = new ShortCode("abcd12");
        assertThrows(IllegalArgumentException.class,
            () -> new ShortLink(code, owner, "https://e.com", CREATED, CREATED, 5));
        assertThrows(IllegalArgumentException.class,
            () -> new ShortLink(code, owner, "https://e.com", CREATED, CREATED.plus(Duration.ofHours(1)), 0));
        assertThrows(NullPointerException.class,
            () -> new ShortLink(code, owner, null, CREATED, CREATED.plus(Duration.ofHours(1)), 5));
    }

    @Test
    @DisplayName("Ссылки равны по коду — код и есть идентификатор")
    void equalityByCode() {
        ShortLink other = new ShortLink(new ShortCode("abcd12"), UserId.random(), "https://other.com", CREATED,
            CREATED.plus(Duration.ofHours(1)), 2);
        assertEquals(link(3, Duration.ofHours(1)), other);
        assertEquals(link(3, Duration.ofHours(1)).hashCode(), other.hashCode());
    }

    @ParameterizedTest
    @CsvSource({"PT24H, '1 д'", "P1DT12H, '1 д 12 ч'", "PT1H30M, '1 ч 30 мин'", "PT45S, '45 с'",
        "PT3M, '3 мин'", "PT0S, '0 с'", "-PT5S, '0 с'"})
    @DisplayName("Длительность для консоли читается по-человечески")
    void humanizesDuration(String iso, String expected) {
        assertEquals(expected, Durations.humanize(Duration.parse(iso)));
    }

    @Test
    @DisplayName("UUID уникален, разбирается из строки и объясняет ошибку ввода")
    void userId() throws DomainException {
        UserId first = UserId.random();
        assertNotEquals(first, UserId.random());
        assertEquals(first, UserId.parse(first.toString()));
        assertTrue(assertThrows(DomainException.class, () -> UserId.parse("не uuid")).getMessage()
            .contains("Некорректный UUID"));
        assertTrue(assertThrows(DomainException.class, () -> UserId.parse("")).getMessage()
            .contains("Пустой идентификатор"));
    }

    @Test
    @DisplayName("Каждая причина недоступности предлагает создать новую ссылку (ТЗ 1.5)")
    void reasonsSuggestNewLink() {
        for (UnavailabilityReason reason : UnavailabilityReason.values()) {
            assertTrue(reason.suggestion().contains("new"), reason.name());
        }
    }
}

package ru.mephi.shortlink.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.Notification.NotificationKind;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.support.Fixtures;

class ExpirationServiceTest {

    @ParameterizedTest
    @CsvSource({
        "PT1H, 59, 0", "PT1H, 61, 1",
        "PT24H, 1439, 0", "PT24H, 1441, 1",
        "PT45M, 46, 1"
    })
    @DisplayName("Удаление срабатывает на часовом, минутном и суточном TTL (ТЗ этап 3.3)")
    void removesExpiredAtDifferentIntervals(Duration ttl, long elapsedMinutes, int expectedRemoved)
        throws Exception {
        AppConfig config = new AppConfig(ttl, 50, 1, 1000, 6, "https://clck.ru/", 2048,
            true, Duration.ofMinutes(1), true, false);
        Fixtures.Harness harness = Fixtures.harness(config);
        UserId owner = UserId.random();
        ShortLink link = harness.links().create(owner, Fixtures.SAMPLE_URL, OptionalInt.of(5));

        harness.clock().advance(Duration.ofMinutes(elapsedMinutes));
        List<ShortLink> removed = harness.expiration().purgeExpired();

        assertEquals(expectedRemoved, removed.size(), () -> "ttl=" + ttl + ", прошло " + elapsedMinutes + " мин");
        assertEquals(expectedRemoved == 0, harness.repository().findByCode(link.code()).isPresent());
    }

    @Test
    @DisplayName("Удаляются только устаревшие ссылки, остальные остаются")
    void removesOnlyExpired() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId owner = UserId.random();
        ShortLink old = harness.links().create(owner, "https://example.com/old", OptionalInt.of(5));
        harness.clock().advance(Duration.ofHours(23));
        ShortLink fresh = harness.links().create(owner, "https://example.com/fresh", OptionalInt.of(5));

        harness.clock().advance(Duration.ofHours(2));
        assertEquals(List.of(old), harness.expiration().purgeExpired());
        assertTrue(harness.repository().findByCode(old.code()).isEmpty());
        assertTrue(harness.repository().findByCode(fresh.code()).isPresent());
        assertEquals(1, harness.links().ownedLinks(owner).size());
    }

    @Test
    @DisplayName("Каждый владелец получает своё уведомление с подсказкой восстановить ссылку")
    void notifiesEachOwner() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId first = UserId.random();
        UserId second = UserId.random();
        harness.links().create(first, "https://example.com/a", OptionalInt.of(5));
        harness.links().create(second, "https://example.com/b", OptionalInt.of(5));

        harness.clock().advance(Duration.ofHours(25));
        assertEquals(2, harness.expiration().purgeExpired().size());

        List<Notification> mine = harness.notifications().pending(first);
        assertEquals(1, mine.size());
        assertEquals(NotificationKind.TTL_EXPIRED, mine.get(0).kind());
        assertTrue(mine.get(0).message().contains("new https://example.com/a"), mine.get(0).message());
        assertTrue(harness.notifications().pending(second).get(0).message().contains("example.com/b"));
    }

    @Test
    @DisplayName("Второй проход ничего не удаляет и не уведомляет заново")
    void purgeIsIdempotent() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId owner = UserId.random();
        harness.links().create(owner, Fixtures.SAMPLE_URL, OptionalInt.of(5));
        harness.clock().advance(Duration.ofHours(25));

        assertEquals(1, harness.expiration().purgeExpired().size());
        harness.notifications().drain(owner);
        harness.clock().advance(Duration.ofHours(1));
        assertEquals(0, harness.expiration().purgeExpired().size());
        assertTrue(harness.notifications().pending(owner).isEmpty());
    }

    @Test
    @DisplayName("Обращение к устаревшей ссылке удаляет её раньше планировщика — уведомление одно")
    void accessRemovesAndNotifiesOnce() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId owner = UserId.random();
        ShortLink link = harness.links().create(owner, Fixtures.SAMPLE_URL, OptionalInt.of(5));

        harness.clock().advance(Duration.ofHours(25));
        assertEquals(RedirectResult.Outcome.EXPIRED, harness.links().redirect(link.code()).outcome());
        assertEquals(0, harness.expiration().purgeExpired().size(), "ссылка уже удалена при обращении");
        assertEquals(1, harness.notifications().pending(owner).size());
    }
}

package ru.mephi.shortlink.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mephi.shortlink.core.exception.NotOwnerException;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.Notification.NotificationKind;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.core.service.RedirectResult;
import ru.mephi.shortlink.support.Fixtures;

class SpecificationScenariosTest {

    @Test
    @DisplayName("ТЗ 3.1: одна ссылка у разных пользователей даёт уникальные коды")
    void uniqueCodesPerUser() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId alice = harness.session().identify();
        UserId bob = harness.session().becomeNewUser();
        ShortLink mine = harness.links().create(alice, Fixtures.SAMPLE_URL, OptionalInt.of(5));
        ShortLink theirs = harness.links().create(bob, Fixtures.SAMPLE_URL, OptionalInt.of(5));

        assertNotEquals(mine.code(), theirs.code());
        assertEquals(mine.targetUrl(), theirs.targetUrl());
        assertEquals(1, harness.links().ownedLinks(alice).size());
        assertEquals(1, harness.links().ownedLinks(bob).size());
    }

    @Test
    @DisplayName("ТЗ 3.2: при исчерпании лимита переход блокируется, в том числе для чужих")
    void limitExhaustionBlocksRedirect() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId owner = harness.session().identify();
        ShortLink link = harness.links().create(owner, Fixtures.SAMPLE_URL, OptionalInt.of(3));

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertEquals(RedirectResult.Outcome.REDIRECTED, harness.links().redirect(link.code()).outcome());
        }
        assertEquals(RedirectResult.Outcome.LIMIT_REACHED, harness.links().redirect(link.code()).outcome());
        assertEquals(3, link.redirectCount());
        assertEquals(3, harness.opener().openCount());

        harness.session().becomeNewUser();
        assertEquals(RedirectResult.Outcome.LIMIT_REACHED, harness.links().redirect(link.code()).outcome());
    }

    @Test
    @DisplayName("ТЗ 3.3: «протухшие» ссылки удаляются автоматически — час и сутки")
    void expiredLinksAreRemoved() throws Exception {
        for (Duration ttl : List.of(Duration.ofHours(1), Duration.ofHours(24))) {
            Fixtures.Harness harness = Fixtures.harness(Fixtures.config(ttl));
            ShortLink link = harness.links().create(UserId.random(), Fixtures.SAMPLE_URL, OptionalInt.of(5));

            harness.clock().advance(ttl.minusSeconds(1));
            assertEquals(1, harness.repository().count(), ttl + ": до истечения ссылка жива");

            harness.clock().advance(Duration.ofSeconds(2));
            assertEquals(RedirectResult.Outcome.EXPIRED, harness.links().redirect(link.code()).outcome());
            assertEquals(0, harness.repository().count(), ttl + ": ссылка удалена");
        }
    }

    @Test
    @DisplayName("ТЗ 3.4: пользователь уведомляется о недоступности ссылки")
    void userIsNotified() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId owner = harness.session().identify();
        ShortLink limited = harness.links().create(owner, "https://example.com/limited", OptionalInt.of(1));
        harness.links().create(owner, "https://example.com/expiring", OptionalInt.of(10));

        harness.links().redirect(limited.code());
        harness.clock().advance(Duration.ofHours(25));
        harness.expiration().purgeExpired();

        List<Notification> notifications = harness.notifications().drain(owner);
        assertEquals(3, notifications.size(), "лимит плюс автоудаление обеих ссылок");
        assertEquals(NotificationKind.LIMIT_EXHAUSTED, notifications.get(0).kind());
        assertTrue(notifications.get(0).message().contains("new https://example.com/limited"),
            notifications.get(0).message());
        assertTrue(notifications.stream().anyMatch(n -> n.kind() == NotificationKind.TTL_EXPIRED
            && n.message().contains("new https://example.com/expiring")));
        assertTrue(harness.notifications().pending(owner).isEmpty());
    }

    @Test
    @DisplayName("ТЗ 2.4: правка и удаление — только создателю")
    void onlyOwnerMayModify() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId creator = harness.session().identify();
        ShortLink link = harness.links().create(creator, Fixtures.SAMPLE_URL, OptionalInt.of(5));
        UserId intruder = harness.session().becomeNewUser();

        assertThrows(NotOwnerException.class, () -> harness.links().ownedLink(intruder, link.code()));
        assertThrows(NotOwnerException.class, () -> harness.links().updateLimit(intruder, link.code(), 9));
        assertThrows(NotOwnerException.class, () -> harness.links().delete(intruder, link.code()));
        assertEquals(1, harness.repository().count());

        harness.links().delete(creator, link.code());
        assertEquals(0, harness.repository().count());
    }

    @Test
    @DisplayName("ТЗ 1.6: UUID выдаётся один раз и используется для всех действий пользователя")
    void uuidTracksAllActions() throws Exception {
        Fixtures.Harness harness = Fixtures.harness();
        UserId identity = harness.session().identify();
        assertEquals(identity, harness.session().identify());

        harness.links().create(identity, "https://example.com/a", OptionalInt.of(2));
        harness.links().create(identity, "https://example.com/b", OptionalInt.of(2));
        assertEquals(2, harness.links().ownedLinks(identity).size());
        assertEquals(1, harness.session().knownUsers().size(), "повторные действия не выдают новый UUID");
        assertEquals("https://example.com/a", harness.links().ownedLinks(identity).get(0).targetUrl());
    }
}

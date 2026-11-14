package ru.mephi.shortlink.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.Notification.NotificationKind;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.support.Fixtures;

class NotificationCenterTest {

    private static final Instant WHEN = Instant.parse("2026-01-01T12:00:00Z");
    private static final ShortCode CODE = new ShortCode("abcd12");

    private static Notification note(UserId to, String message) {
        return new Notification(WHEN, NotificationKind.LIMIT_EXHAUSTED, to, CODE, message);
    }

    @Test
    @DisplayName("Очередь принадлежит пользователю: чужие уведомления не читаются")
    void queuesAreIsolated() {
        NotificationCenter center = new NotificationCenter(true);
        UserId alice = UserId.random();
        UserId bob = UserId.random();
        center.publish(note(alice, "у Алисы"));
        center.publish(note(bob, "у Боба"));
        assertEquals("у Алисы", center.pending(alice).get(0).message());
        assertEquals(1, center.pending(bob).size());
        assertTrue(center.pending(UserId.random()).isEmpty());
    }

    @Test
    @DisplayName("drain забирает очередь и очищает её")
    void drainClearsQueue() {
        NotificationCenter center = new NotificationCenter(true);
        UserId user = UserId.random();
        center.publish(note(user, "раз"));
        center.publish(note(user, "два"));
        assertEquals(2, center.drain(user).size());
        assertTrue(center.pending(user).isEmpty());
        assertTrue(center.drain(user).isEmpty());
    }

    @Test
    @DisplayName("Очередь ограничена: старые сообщения вытесняются новыми")
    void queueIsBounded() {
        NotificationCenter center = new NotificationCenter(true);
        UserId user = UserId.random();
        for (int index = 0; index < 60; index++) {
            center.publish(note(user, "сообщение " + index));
        }
        List<Notification> pending = center.pending(user);
        assertEquals(50, pending.size());
        assertEquals("сообщение 10", pending.get(0).message());
        assertEquals("сообщение 59", pending.get(pending.size() - 1).message());
    }

    @Test
    @DisplayName("notifications.enabled=false — очередь пуста")
    void disabledCenterDropsEverything() {
        NotificationCenter center = new NotificationCenter(false);
        UserId user = UserId.random();
        center.publish(note(user, "не должно появиться"));
        assertTrue(center.pending(user).isEmpty());
        assertFalse(center.recipientsWithPending().contains(user));
    }

    @Test
    @DisplayName("UUID выдаётся один раз при первом обращении; сессия помнит всех пользователей")
    void identityIsStableAndSwitchable() throws DomainException {
        UserSession session = new UserSession();
        assertTrue(session.currentUser().isEmpty());
        UserId first = session.identify();
        assertEquals(first, session.identify());
        assertNotEquals(first, session.becomeNewUser());

        UserId target = UserId.parse("0f6c2c2a-3b1e-4d5a-9e7f-1a2b3c4d5e6f");
        session.use(target);
        assertEquals(target, session.currentUser().orElseThrow());
        assertEquals(3, session.knownUsers().size());
    }

    @Test
    @DisplayName("Без идентифицированного пользователя контекст консоли объясняет, что делать")
    void requireUserBeforeIdentity() throws Exception {
        Fixtures.Cli cli = Fixtures.cli();
        assertTrue(assertThrows(DomainException.class, () -> cli.context().requireUser()).getMessage()
            .contains("new <url>"));
        cli.context().session().use(UserId.random());
        assertTrue(cli.context().requireUser() != null);
    }
}

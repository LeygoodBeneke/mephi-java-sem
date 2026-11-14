package ru.mephi.shortlink.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.exception.NotOwnerException;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.Notification.NotificationKind;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UnavailabilityReason;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.support.Fixtures;

class LinkServiceTest {

    private Fixtures.Harness harness;
    private LinkService links;
    private UserId alice;
    private UserId bob;
    private ShortLink link;

    @BeforeEach
    void setUp() throws Exception {
        harness = Fixtures.harness();
        links = harness.links();
        alice = UserId.random();
        bob = UserId.random();
        link = links.create(alice, Fixtures.SAMPLE_URL, OptionalInt.of(3));
    }

    @Test
    @DisplayName("Один URL от разных пользователей даёт разные коды (ТЗ 1.2, этап 3.1)")
    void differentUsersGetDifferentCodes() throws Exception {
        ShortLink other = links.create(bob, Fixtures.SAMPLE_URL, OptionalInt.of(3));
        assertNotEquals(link.code(), other.code());
        assertEquals(link.targetUrl(), other.targetUrl());
        assertEquals(1, links.ownedLinks(alice).size());
        assertEquals(1, links.ownedLinks(bob).size());

        for (int attempt = 0; attempt < 2; attempt++) {
            links.redirect(link.code());
        }
        assertEquals(2, links.redirect(other.code()).remainingRedirects(), "счётчики независимы");
        assertEquals(2, link.redirectCount());
        assertEquals(1, other.redirectCount());
    }

    @Test
    @DisplayName("Несколько ссылок у одного пользователя, порядок по созданию (ТЗ крит. 7)")
    void userCanHaveManyLinks() throws Exception {
        ShortLink second = links.create(alice, "https://example.com/other", OptionalInt.empty());
        assertEquals(List.of(link, second), links.ownedLinks(alice));
        assertTrue(links.ownedLinks(bob).isEmpty());
        assertEquals(harness.clock().instant().plus(Duration.ofHours(24)), second.expiresAt());
        assertEquals(harness.config().defaultRedirectLimit(), second.redirectLimit());
        assertEquals(links.defaultRedirectLimit(), second.redirectLimit());
    }

    @Test
    @DisplayName("Лимит вне диапазона и невалидный URL отклоняются, в хранилище ничего не попадает")
    void rejectsInvalidRequests() {
        assertTrue(assertThrows(DomainException.class,
            () -> links.create(alice, Fixtures.SAMPLE_URL, OptionalInt.of(1001))).getMessage()
            .contains("1..1000"));
        assertThrows(DomainException.class, () -> links.create(alice, Fixtures.SAMPLE_URL, OptionalInt.of(0)));
        assertThrows(DomainException.class, () -> links.create(alice, "javascript:alert(1)", OptionalInt.of(3)));
        assertEquals(1, harness.repository().count(), "отклонённые ссылки не сохраняются");
    }

    @Test
    @DisplayName("Коллизия кода разрешается перевыпуском, а серия коллизий — сообщением о проблеме")
    void retriesAndReportsExhaustedAttempts() throws Exception {
        harness.generator().enqueue("SEQ001");
        ShortLink second = links.create(bob, Fixtures.SAMPLE_URL, OptionalInt.of(3));
        assertEquals("SEQ002", second.code().value());

        harness.generator().enqueue("SEQ002", "SEQ002", "SEQ002", "SEQ002", "SEQ002",
            "SEQ002", "SEQ002", "SEQ002", "SEQ002", "SEQ002");
        DomainException error = assertThrows(DomainException.class,
            () -> links.create(alice, "https://example.com/x", OptionalInt.of(3)));
        assertTrue(error.getMessage().contains("code-length"), error.getMessage());
        assertEquals(2, harness.repository().count());
    }

    @Test
    @DisplayName("Лимит блокирует ровно на N+1: переход не засчитывается и браузер не открывается")
    void blocksAfterLimit() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertEquals(RedirectResult.Outcome.REDIRECTED, links.redirect(link.code()).outcome());
        }
        RedirectResult fourth = links.redirect(link.code());
        assertEquals(RedirectResult.Outcome.LIMIT_REACHED, fourth.outcome());
        assertEquals(UnavailabilityReason.LIMIT_REACHED, fourth.outcome().reason().orElseThrow());
        assertFalse(fourth.redirected());
        assertEquals(3, link.redirectCount());
        assertEquals(3, harness.opener().openCount(), "заблокированный переход адрес не открывает");
    }

    @Test
    @DisplayName("Уведомление об исчерпании лимита приходит владельцу и ровно один раз")
    void notifiesOwnerOnce() {
        UserId visitor = bob;
        for (int attempt = 0; attempt < 4; attempt++) {
            links.redirect(link.code());
        }
        List<Notification> notifications = harness.notifications().pending(alice);
        assertEquals(1, notifications.size());
        assertEquals(NotificationKind.LIMIT_EXHAUSTED, notifications.get(0).kind());
        assertTrue(notifications.get(0).message().contains(link.code().value()));
        assertTrue(notifications.get(0).message().contains("new " + Fixtures.SAMPLE_URL));
        assertFalse(notifications.get(0).message().contains("%"), "не переформатированный текст");
        assertTrue(harness.notifications().pending(visitor).isEmpty());
    }

    @Test
    @DisplayName("Неизвестный код и истёкший срок: отказ с причиной, устаревшая ссылка удалена")
    void notFoundAndExpired() {
        assertEquals(RedirectResult.Outcome.NOT_FOUND, links.redirect(new ShortCode("zzzzzz")).outcome());

        harness.clock().advance(Duration.ofHours(24));
        RedirectResult expired = links.redirect(link.code());
        assertEquals(RedirectResult.Outcome.EXPIRED, expired.outcome());
        assertEquals(0, harness.repository().count());
        List<Notification> notifications = harness.notifications().pending(alice);
        assertEquals(1, notifications.size());
        assertEquals(NotificationKind.TTL_EXPIRED, notifications.get(0).kind());
    }

    @Test
    @DisplayName("За миллисекунду до истечения ссылка ещё работает")
    void usableJustBeforeExpiry() {
        harness.clock().advance(Duration.ofHours(24).minusMillis(1));
        assertTrue(links.redirect(link.code()).redirected());
    }

    @Test
    @DisplayName("Сбой браузера не роняет переход: адрес показывается для ручного открытия")
    void survivesOpenerFailure() {
        harness.opener().failNextOpen();
        RedirectResult result = links.redirect(link.code());
        assertEquals(RedirectResult.Outcome.REDIRECTED, result.outcome());
        assertEquals(1, link.redirectCount());
        assertTrue(result.openMessage().contains("Браузер не открылся"), result.openMessage());
    }

    @Test
    @DisplayName("16 одновременных переходов при лимите 5: ровно 5 успешных, обхода нет")
    void concurrentRedirectsRespectLimit() throws Exception {
        ShortLink limited = links.create(alice, "https://example.com/concurrent", OptionalInt.of(5));
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        try {
            for (int index = 0; index < threads; index++) {
                pool.submit(() -> {
                    start.await();
                    if (links.redirect(limited.code()).redirected()) {
                        succeeded.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(5, succeeded.get());
        assertEquals(5, limited.redirectCount());
        assertEquals(1, harness.notifications().pending(alice).size());
    }

    @Test
    @DisplayName("Чужую ссылку нельзя ни показать, ни изменить, ни удалить (ТЗ 2.4)")
    void onlyOwnerMayModify() throws Exception {
        assertTrue(assertThrows(NotOwnerException.class, () -> links.ownedLink(bob, link.code())).getMessage()
            .contains("принадлежит другому пользователю"));
        assertThrows(NotOwnerException.class, () -> links.updateLimit(bob, link.code(), 9));
        assertThrows(NotOwnerException.class, () -> links.delete(bob, link.code()));
        assertEquals(1, harness.repository().count(), "чужая ссылка уцелела");

        links.delete(alice, link.code());
        assertEquals(0, harness.repository().count());
    }

    @Test
    @DisplayName("Несуществующий код объясняется как отсутствие ссылки")
    void unknownCodeForOwnerOperations() {
        ShortCode absent = new ShortCode("nope12");
        assertTrue(assertThrows(DomainException.class, () -> links.ownedLink(alice, absent)).getMessage()
            .contains("не найдена"));
        assertThrows(DomainException.class, () -> links.delete(alice, absent));
        assertThrows(DomainException.class, () -> links.updateLimit(alice, absent, 9));
    }

    @Test
    @DisplayName("Лимит нельзя уменьшить до засчитанных переходов; повышение разблокирует ссылку")
    void editLimitReactivatesLink() throws Exception {
        links.redirect(link.code());
        links.redirect(link.code());
        links.redirect(link.code());
        assertTrue(assertThrows(DomainException.class, () -> links.updateLimit(alice, link.code(), 3))
            .getMessage().contains("не больше уже засчитанных"));

        ShortLink updated = links.updateLimit(alice, link.code(), 5);
        assertEquals(5, updated.redirectLimit());
        assertTrue(links.redirect(link.code()).redirected());
        assertEquals(4, updated.redirectCount());
        assertEquals(1, updated.remainingRedirects());
    }

    @Test
    @DisplayName("Повторное исчерпание лимита уведомляет снова")
    void notifiesAgainAfterSecondExhaustion() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            links.redirect(link.code());
        }
        assertEquals(1, harness.notifications().pending(alice).size());

        links.updateLimit(alice, link.code(), 4);
        links.redirect(link.code());
        assertEquals(2, harness.notifications().pending(alice).size(), "выбран новый лимит — новое сообщение");
    }
}

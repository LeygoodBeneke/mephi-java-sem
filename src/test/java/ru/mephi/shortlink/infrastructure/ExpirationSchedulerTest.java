package ru.mephi.shortlink.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.infrastructure.scheduler.ExpirationScheduler;
import ru.mephi.shortlink.support.Fixtures;

class ExpirationSchedulerTest {

    private static final long TICK_MILLIS = 40;

    @Test
    @DisplayName("Планировщик сам удаляет устаревшую ссылку и уведомляет владельца")
    void removesExpiredInBackground() throws Exception {
        AppConfig config = new AppConfig(Duration.ofMinutes(5), 10, 1, 100, 6, "https://clck.ru/", 2048,
            true, Duration.ofMillis(TICK_MILLIS), true, false);
        Fixtures.Harness harness = Fixtures.harness(config);
        UserId owner = UserId.random();
        harness.links().create(owner, Fixtures.SAMPLE_URL, OptionalInt.of(3));

        try (ExpirationScheduler scheduler = new ExpirationScheduler(harness.expiration(), TICK_MILLIS)) {
            scheduler.start();
            harness.clock().advance(Duration.ofMinutes(6));
            waitUntil(() -> harness.repository().count() == 0, "ссылка не удалена фоном");
        }

        assertEquals(0, harness.repository().count());
        Notification notification = harness.notifications().pending(owner).get(0);
        assertTrue(notification.message().contains("SEQ001"), notification.message());
    }

    @Test
    @DisplayName("Свежие ссылки фон не трогает, а после close очистка останавливается")
    void keepsFreshLinksAndStopsAfterClose() throws Exception {
        AppConfig config = new AppConfig(Duration.ofMinutes(1), 10, 1, 100, 6, "https://clck.ru/", 2048,
            true, Duration.ofMillis(TICK_MILLIS), true, false);
        Fixtures.Harness harness = Fixtures.harness(config);
        harness.links().create(UserId.random(), Fixtures.SAMPLE_URL, OptionalInt.of(3));

        try (ExpirationScheduler scheduler = new ExpirationScheduler(harness.expiration(), TICK_MILLIS)) {
            scheduler.start();
            Thread.sleep(250);
            assertEquals(1, harness.repository().count(), "ссылка ещё не устарела");

            harness.clock().advance(Duration.ofMinutes(2));
            scheduler.close();
            Thread.sleep(250);
        }
        assertEquals(1, harness.repository().count(), "остановленный планировщик ничего не удаляет");
    }

    @Test
    @DisplayName("Некорректный период отклоняется")
    void rejectsBadPeriod() {
        Fixtures.Harness harness = Fixtures.harness();
        assertThrows(IllegalArgumentException.class,
            () -> new ExpirationScheduler(harness.expiration(), 0));
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, String message)
        throws InterruptedException {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(message);
    }
}

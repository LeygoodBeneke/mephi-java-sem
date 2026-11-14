package ru.mephi.shortlink.infrastructure.scheduler;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.service.ExpirationService;

public final class ExpirationScheduler implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger("ru.mephi.shortlink.cleanup.scheduler");

    private final ExpirationService expirationService;
    private final long periodMillis;
    private final ScheduledExecutorService executor;

    public ExpirationScheduler(ExpirationService expirationService, long periodMillis) {
        if (periodMillis <= 0) {
            throw new IllegalArgumentException("Период автоочистки должен быть положительным, получен: "
                + periodMillis);
        }
        this.expirationService = expirationService;
        this.periodMillis = periodMillis;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "link-cleanup");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::runQuietly, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    private void runQuietly() {
        try {
            List<ShortLink> removed = expirationService.purgeExpired();
            if (!removed.isEmpty()) {
                LOG.log(System.Logger.Level.INFO, "Фон удалил ссылок по сроку: {0}", removed.size());
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Итерация автоочистки завершилась ошибкой: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

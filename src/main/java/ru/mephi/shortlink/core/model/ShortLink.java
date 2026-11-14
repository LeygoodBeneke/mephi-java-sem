package ru.mephi.shortlink.core.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShortLink {

    private final ShortCode code;
    private final UserId owner;
    private final String targetUrl;
    private final Instant createdAt;
    private final Instant expiresAt;

    private final AtomicBoolean limitNoticeClaimed = new AtomicBoolean();
    private final AtomicBoolean ttlNoticeClaimed = new AtomicBoolean();

    private int redirectLimit;
    private int redirectCount;
    private Instant lastRedirectAt;

    public ShortLink(ShortCode code, UserId owner, String targetUrl, Instant createdAt,
                     Instant expiresAt, int redirectLimit) {
        this.code = Objects.requireNonNull(code, "code");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.targetUrl = Objects.requireNonNull(targetUrl, "targetUrl");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Срок жизни должен быть позже момента создания: expiresAt="
                + expiresAt + ", createdAt=" + createdAt);
        }
        setRedirectLimit(redirectLimit);
    }

    public ShortCode code() {
        return code;
    }

    public UserId owner() {
        return owner;
    }

    public String targetUrl() {
        return targetUrl;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public synchronized int redirectLimit() {
        return redirectLimit;
    }

    public synchronized int redirectCount() {
        return redirectCount;
    }

    public synchronized Optional<Instant> lastRedirectAt() {
        return Optional.ofNullable(lastRedirectAt);
    }

    public synchronized int remainingRedirects() {
        return Math.max(0, redirectLimit - redirectCount);
    }

    public synchronized boolean isLimitReached() {
        return redirectCount >= redirectLimit;
    }

    public synchronized boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public synchronized LinkStatus statusAt(Instant now) {
        if (isExpiredAt(now)) {
            return LinkStatus.EXPIRED;
        }
        return isLimitReached() ? LinkStatus.LIMIT_REACHED : LinkStatus.ACTIVE;
    }

    public Duration timeLeft(Instant now) {
        return Duration.between(now, expiresAt);
    }

    public synchronized OptionalInt registerRedirect(Instant now) {
        if (redirectCount >= redirectLimit) {
            return OptionalInt.empty();
        }
        redirectCount++;
        lastRedirectAt = now;
        return OptionalInt.of(redirectCount);
    }

    public synchronized void changeRedirectLimit(int newLimit) {
        if (newLimit <= redirectCount) {
            throw new IllegalStateException("Новый лимит (" + newLimit
                + ") не больше уже засчитанных переходов (" + redirectCount + ")");
        }
        setRedirectLimit(newLimit);
        limitNoticeClaimed.set(false);
    }

    public boolean claimLimitNotice() {
        return limitNoticeClaimed.compareAndSet(false, true);
    }

    public boolean claimTtlNotice() {
        return ttlNoticeClaimed.compareAndSet(false, true);
    }

    private void setRedirectLimit(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Лимит переходов должен быть положительным, получен: " + limit);
        }
        this.redirectLimit = limit;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShortLink link && code.equals(link.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return "ShortLink{" + code + " -> " + targetUrl + ", owner=" + owner + ", redirects=" + redirectCount
            + "/" + redirectLimit + ", expiresAt=" + expiresAt + "}";
    }
}

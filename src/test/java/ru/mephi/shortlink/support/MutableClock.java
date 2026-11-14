package ru.mephi.shortlink.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public final class MutableClock extends Clock {

    private Instant current;

    public MutableClock(Instant start) {
        this.current = start;
    }

    public static MutableClock frozen() {
        return new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return current;
    }

    public void advance(Duration duration) {
        current = current.plus(duration);
    }
}

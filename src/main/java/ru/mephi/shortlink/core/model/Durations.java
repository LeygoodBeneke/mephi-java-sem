package ru.mephi.shortlink.core.model;

import java.time.Duration;

public final class Durations {

    private Durations() {
    }

    public static String humanize(Duration duration) {
        if (duration == null) {
            return "не задана";
        }
        if (duration.isNegative() || duration.isZero()) {
            return "0 с";
        }
        if (duration.toDaysPart() > 0) {
            int hours = duration.toHoursPart();
            return hours == 0
                ? duration.toDaysPart() + " д"
                : duration.toDaysPart() + " д " + hours + " ч";
        }
        if (duration.toHoursPart() > 0) {
            int minutes = duration.toMinutesPart();
            return minutes == 0
                ? duration.toHoursPart() + " ч"
                : duration.toHoursPart() + " ч " + minutes + " мин";
        }
        if (duration.toMinutesPart() > 0) {
            return duration.toMinutesPart() + " мин";
        }
        return duration.toSecondsPart() + " с";
    }
}

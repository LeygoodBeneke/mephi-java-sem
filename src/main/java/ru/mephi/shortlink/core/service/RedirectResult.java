package ru.mephi.shortlink.core.service;

import java.util.Optional;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UnavailabilityReason;

public record RedirectResult(Outcome outcome, ShortLink link, String targetUrl, boolean becameUnavailable,
                             String openMessage) {

    public enum Outcome {
        REDIRECTED,
        NOT_FOUND,
        EXPIRED,
        LIMIT_REACHED;

        public Optional<UnavailabilityReason> reason() {
            return switch (this) {
                case NOT_FOUND -> Optional.of(UnavailabilityReason.NOT_FOUND);
                case EXPIRED -> Optional.of(UnavailabilityReason.TTL_EXPIRED);
                case LIMIT_REACHED -> Optional.of(UnavailabilityReason.LIMIT_REACHED);
                case REDIRECTED -> Optional.empty();
            };
        }
    }

    public boolean redirected() {
        return outcome == Outcome.REDIRECTED;
    }

    public int remainingRedirects() {
        return link == null ? 0 : link.remainingRedirects();
    }

    static RedirectResult notFound() {
        return new RedirectResult(Outcome.NOT_FOUND, null, null, false, null);
    }

    static RedirectResult rejected(Outcome outcome, ShortLink link) {
        return new RedirectResult(outcome, link, null, false, null);
    }

    static RedirectResult opened(ShortLink link, String targetUrl, boolean becameUnavailable, String openMessage) {
        return new RedirectResult(Outcome.REDIRECTED, link, targetUrl, becameUnavailable, openMessage);
    }
}

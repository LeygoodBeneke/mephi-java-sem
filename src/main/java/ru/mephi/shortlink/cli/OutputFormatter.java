package ru.mephi.shortlink.cli;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.List;
import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.model.Durations;
import ru.mephi.shortlink.core.model.LinkStatus;
import ru.mephi.shortlink.core.model.Notification;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UnavailabilityReason;
import ru.mephi.shortlink.core.service.RedirectResult;

public final class OutputFormatter {

    private static final int URL_COLUMN_WIDTH = 44;
    private static final String ROW_FORMAT = "  %-10s %-9s %-13s %-16s %-6s %s%n";

    private OutputFormatter() {
    }

    public static void printLinksTable(PrintWriter out, AppConfig config, List<ShortLink> links, Instant now) {
        if (links.isEmpty()) {
            out.println("  ссылок пока нет — создайте первую: new <url> [лимит]");
            return;
        }
        out.printf(ROW_FORMAT, "Код", "Переходы", "Статус", "До удаления", "Лимит", "Исходный URL");
        out.println("  " + "-".repeat(URL_COLUMN_WIDTH + 52));
        for (ShortLink link : links) {
            out.printf(ROW_FORMAT, link.code().value(),
                link.redirectCount() + "/" + link.redirectLimit(),
                statusText(link.statusAt(now)),
                timeLeft(link, now),
                link.redirectLimit(),
                truncate(link.targetUrl(), URL_COLUMN_WIDTH));
        }
        out.printf("  ссылок в списке: %d; время жизни по конфигурации: %s%n", links.size(),
            Durations.humanize(config.linkTtl()));
    }

    public static void printLinkDetails(PrintWriter out, AppConfig config, ShortLink link, Instant now) {
        out.println("  Короткая ссылка: " + config.renderShortLink(link.code().value()));
        out.println("  Исходный URL:    " + link.targetUrl());
        out.println("  Владелец:        " + link.owner());
        out.println("  Создана:         " + link.createdAt());
        out.println("  Автоудаление:    " + link.expiresAt() + " (через "
            + Durations.humanize(config.linkTtl()) + " после создания)");
        out.println("  Статус:          " + statusText(link.statusAt(now)));
        out.println("  Переходы:        " + link.redirectCount() + " из " + link.redirectLimit()
            + ", осталось " + link.remainingRedirects());
        out.println("  Последний:       " + link.lastRedirectAt().map(Instant::toString).orElse("не было"));
    }

    public static void printRedirectResult(PrintWriter out, AppConfig config, RedirectResult result,
                                            ShortCode code) {
        String shortLink = config.renderShortLink(code.value());
        switch (result.outcome()) {
            case REDIRECTED -> {
                out.println("  Переход выполнен: " + shortLink + " → " + result.targetUrl());
                out.println("  " + result.openMessage());
                out.println("  Засчитано переходов: " + result.link().redirectCount() + " из "
                    + result.link().redirectLimit() + ", осталось " + result.remainingRedirects());
                if (result.becameUnavailable()) {
                    printUnavailable(out, shortLink, UnavailabilityReason.LIMIT_REACHED,
                        "лимит выбран (" + result.link().redirectCount() + " из "
                            + result.link().redirectLimit() + ")");
                }
            }
            case NOT_FOUND -> printUnavailable(out, shortLink, UnavailabilityReason.NOT_FOUND,
                "код не найден в сервисе");
            case EXPIRED -> printUnavailable(out, shortLink, UnavailabilityReason.TTL_EXPIRED,
                "истёк срок жизни, она удалена");
            case LIMIT_REACHED -> printUnavailable(out, shortLink, UnavailabilityReason.LIMIT_REACHED,
                "лимит переходов выбран (" + result.link().redirectCount() + " из "
                    + result.link().redirectLimit() + ")");
            default -> out.println("  Неизвестный итог перехода");
        }
    }

    public static void printNotifications(PrintWriter out, List<Notification> notifications) {
        if (notifications.isEmpty()) {
            return;
        }
        out.println();
        out.println("  Уведомления (" + notifications.size() + "):");
        for (Notification notification : notifications) {
            out.println("    [" + notification.at() + "] " + notification.message());
        }
    }

    private static void printUnavailable(PrintWriter out, String shortLink, UnavailabilityReason reason,
                                         String detail) {
        out.println("  Ссылка " + shortLink + " недоступна: " + detail + ".");
        out.println("  " + reason.suggestion());
    }

    private static String statusText(LinkStatus status) {
        return switch (status) {
            case ACTIVE -> "активна";
            case LIMIT_REACHED -> "лимит выбран";
            case EXPIRED -> "срок истёк";
        };
    }

    private static String timeLeft(ShortLink link, Instant now) {
        return link.isExpiredAt(now) ? "истёк" : "через " + Durations.humanize(link.timeLeft(now));
    }

    private static String truncate(String value, int width) {
        return value.length() <= width ? value : value.substring(0, width - 1) + "…";
    }
}

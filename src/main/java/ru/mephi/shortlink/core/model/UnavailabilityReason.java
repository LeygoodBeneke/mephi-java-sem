package ru.mephi.shortlink.core.model;

public enum UnavailabilityReason {

    NOT_FOUND,

    TTL_EXPIRED,

    LIMIT_REACHED;

    public String suggestion() {
        return switch (this) {
            case NOT_FOUND -> "Проверьте код (регистр букв важен) или создайте новую ссылку: new <url>";
            case TTL_EXPIRED -> "Срок жизни истёк и ссылка удалена. Создайте новую: new <url> [лимит]";
            case LIMIT_REACHED -> "Лимит переходов выбран. Создайте новую: new <url> [лимит]";
        };
    }
}

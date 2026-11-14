package ru.mephi.shortlink.core.model;

import java.util.Objects;
import java.util.UUID;
import ru.mephi.shortlink.core.exception.DomainException;

public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "value");
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId parse(String raw) throws DomainException {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("Пустой идентификатор пользователя: ожидался UUID вида "
                + "0f6c2c2a-3b1e-4d5a-9e7f-1a2b3c4d5e6f");
        }
        try {
            return new UserId(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Некорректный UUID: \"" + raw.trim() + "\". Пример: "
                + "0f6c2c2a-3b1e-4d5a-9e7f-1a2b3c4d5e6f", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}

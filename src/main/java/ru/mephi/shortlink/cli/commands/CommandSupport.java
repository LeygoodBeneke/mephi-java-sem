package ru.mephi.shortlink.cli.commands;

import ru.mephi.shortlink.core.config.AppConfig;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.ShortCode;

final class CommandSupport {

    private CommandSupport() {
    }

    static ShortCode readCode(String raw, AppConfig config) throws DomainException {
        String trimmed = raw.trim();
        ShortCode fromLink = ShortCode.fromLink(trimmed, config.baseUrl());
        if (fromLink != null) {
            return fromLink;
        }
        if (ShortCode.looksLikeCode(trimmed)) {
            return new ShortCode(trimmed);
        }
        throw new DomainException("\"" + trimmed + "\" — не короткий код и не короткая ссылка. "
            + "Пример кода: 3DZHeG, пример ссылки: " + config.renderShortLink("3DZHeG"));
    }
}

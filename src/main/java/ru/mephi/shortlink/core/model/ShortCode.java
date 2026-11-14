package ru.mephi.shortlink.core.model;

import java.util.Objects;
import java.util.regex.Pattern;
import ru.mephi.shortlink.core.exception.DomainException;

public record ShortCode(String value) {

    private static final Pattern CODE_PATTERN = Pattern.compile("[0-9A-Za-z]{4,12}");

    public ShortCode {
        Objects.requireNonNull(value, "value");
        if (!CODE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Некорректный короткий код: \"" + value
                + "\". Ожидается 4..12 символов base62 (0-9, A-Z, a-z).");
        }
    }

    public static ShortCode of(String raw) throws DomainException {
        if (raw == null || raw.isBlank()) {
            throw new DomainException("Пустой короткий код. Пример: 3DZHeG");
        }
        try {
            return new ShortCode(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage(), e);
        }
    }

    public static boolean looksLikeCode(String raw) {
        return raw != null && CODE_PATTERN.matcher(raw.trim()).matches();
    }

    public static ShortCode fromLink(String raw, String baseUrl) {
        if (raw == null || baseUrl == null) {
            return null;
        }
        String trimmed = raw.trim();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        if (!trimmed.regionMatches(true, 0, normalizedBase, 0, normalizedBase.length())) {
            return null;
        }
        String candidate = trimmed.substring(normalizedBase.length());
        int slash = candidate.indexOf('/');
        if (slash >= 0) {
            candidate = candidate.substring(0, slash);
        }
        int query = candidate.indexOf('?');
        if (query >= 0) {
            candidate = candidate.substring(0, query);
        }
        return looksLikeCode(candidate) ? new ShortCode(candidate.trim()) : null;
    }

    @Override
    public String toString() {
        return value;
    }
}

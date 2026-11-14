package ru.mephi.shortlink.core.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.model.ShortCode;

public final class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final int maxLength;
    private final String serviceHost;
    private final String servicePathPrefix;

    public UrlValidator(int maxLength, String baseUrl) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("maxLength должен быть положительным, получен: " + maxLength);
        }
        this.maxLength = maxLength;
        URI base = URI.create(baseUrl);
        this.serviceHost = base.getHost() == null ? "" : base.getHost().toLowerCase(Locale.ROOT);
        String path = base.getPath() == null ? "/" : base.getPath();
        this.servicePathPrefix = path.endsWith("/") ? path : path + "/";
    }

    public String validate(String rawUrl) throws DomainException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new DomainException("URL не задан. Пример: new https://www.baeldung.com/java-9-http-client");
        }
        String url = rawUrl.trim();
        if (url.length() > maxLength) {
            throw new DomainException("URL длиннее разрешённого: " + url.length() + " символов при лимите "
                + maxLength);
        }
        if (url.matches(".*\\s.*")) {
            throw new DomainException("URL содержит пробел — заключите его в кавычки: "
                + "new \"https://example.com/a b\"");
        }
        URI uri = parse(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.isEmpty()) {
            throw new DomainException("URL должен быть абсолютным: ожидается http:// или https://, получено: " + url);
        }
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            throw new DomainException("Схема \"" + scheme + "\" запрещена: сервис умеет только http и https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new DomainException("В URL нет хоста: " + url);
        }
        if (uri.getUserInfo() != null) {
            throw new DomainException("Логин и пароль в URL запрещены: " + url);
        }
        if (pointsAtService(uri)) {
            throw new DomainException("Это ссылка самого сервиса — сокращать её нельзя, получится цикл: " + url);
        }
        return url;
    }

    private URI parse(String url) throws DomainException {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new DomainException("URL не разобран: " + e.getReason() + " (позиция " + e.getIndex()
                + ") в строке: " + url);
        }
    }

    private boolean pointsAtService(URI uri) {
        if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(serviceHost)) {
            return false;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.startsWith(servicePathPrefix)) {
            return false;
        }
        return ShortCode.looksLikeCode(path.substring(servicePathPrefix.length()));
    }
}

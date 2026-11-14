package ru.mephi.shortlink.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mephi.shortlink.core.exception.DomainException;
import ru.mephi.shortlink.core.generation.Base62CodeGenerator;
import ru.mephi.shortlink.core.generation.ShortCodeGenerator;
import ru.mephi.shortlink.core.validation.UrlValidator;

class ValidationAndGenerationTest {

    private final UrlValidator validator = new UrlValidator(2048, "https://clck.ru/");

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.baeldung.com/java-9-http-client",
        "http://example.com",
        "https://example.com/path?query=1&other=2#fragment",
        "https://example.com:8443/a/b",
        "https://xn--80ak6aa92e.com"
    })
    @DisplayName("Корректные http/https-адреса принимаются без изменения")
    void acceptsValidUrls(String url) throws Exception {
        assertEquals(url, validator.validate(url));
    }

    @Test
    @DisplayName("Пробелы по краям срезаются")
    void trimsOnly() throws Exception {
        assertEquals("https://example.com/a", validator.validate("  https://example.com/a "));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)", "data:text/html;base64,PHN2Zz4=", "file:///etc/passwd",
        "ftp://example.com/file", "example.com/no-scheme", "/relative/path", "https:///path",
        "https://user:pass@example.com", "https://clck.ru/3DZHeG", "https://example.com/a b",
        "https://example.com/%zz"
    })
    @DisplayName("Чужие схемы, относительные адреса, userinfo и само-ссылка отклоняются")
    void rejectsInvalidUrls(String url) {
        assertThrows(DomainException.class, () -> validator.validate(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("Пустой URL и null отклоняются с примером, а не вызывают NPE")
    void rejectsBlank(String url) throws Exception {
        assertTrue(validator.validate("https://example.com") != null);
        assertTrue(assertThrows(DomainException.class, () -> validator.validate(url)).getMessage()
            .contains("URL не задан"));
        assertThrows(DomainException.class, () -> validator.validate(null));
    }

    @Test
    @DisplayName("Длинный URL отклоняется с указанием границы")
    void rejectsTooLong() {
        String longUrl = "https://example.com/" + "a".repeat(2100);
        assertTrue(assertThrows(DomainException.class, () -> validator.validate(longUrl)).getMessage()
            .contains("2048"));
    }

    @Test
    @DisplayName("Длина кода берётся из конфигурации, алфавит — base62")
    void generatesConfiguredLength() {
        for (int length : new int[] {4, 6, 12}) {
            String code = new Base62CodeGenerator(length).generate().value();
            assertEquals(length, code.length());
            assertTrue(code.matches("[0-9A-Za-z]+"), code);
        }
    }

    @Test
    @DisplayName("Коды случайны: на 3000 генерациях повторов практически нет")
    void generatesDistinctCodes() {
        ShortCodeGenerator generator = new Base62CodeGenerator(6);
        Set<String> distinct = new HashSet<>();
        for (int attempt = 0; attempt < 3000; attempt++) {
            distinct.add(generator.generate().value());
        }
        assertTrue(distinct.size() >= 2998, "повторов больше двух: " + distinct.size());
    }

    @Test
    @DisplayName("Длина вне 4..12 и некорректная граница maxLength отклоняются")
    void rejectsBadConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new Base62CodeGenerator(3));
        assertThrows(IllegalArgumentException.class, () -> new UrlValidator(0, "https://clck.ru/"));
    }
}

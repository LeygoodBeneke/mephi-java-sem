package ru.mephi.shortlink.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.infrastructure.memory.InMemoryLinkRepository;

class InMemoryLinkRepositoryTest {

    private static final Instant BASE = Instant.parse("2026-01-01T12:00:00Z");

    private static ShortLink link(String code, UserId owner, Instant createdAt) {
        return new ShortLink(new ShortCode(code), owner, "https://example.com/" + code, createdAt,
            createdAt.plus(Duration.ofHours(24)), 10);
    }

    @Test
    @DisplayName("Повторный код не перезаписывает существующую ссылку")
    void rejectsDuplicateCode() {
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        UserId alice = UserId.random();
        assertTrue(repository.save(link("abcd12", alice, BASE)));
        assertFalse(repository.save(link("abcd12", UserId.random(), BASE)));
        assertEquals(alice, repository.findByCode(new ShortCode("abcd12")).orElseThrow().owner());
        assertEquals(1, repository.count());
        assertTrue(repository.findByCode(new ShortCode("zzzz99")).isEmpty());
    }

    @Test
    @DisplayName("Ссылки изолированы по владельцу и упорядочены по созданию")
    void isolationAndOrder() {
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        UserId alice = UserId.random();
        UserId bob = UserId.random();
        repository.save(link("abcd03", alice, BASE.plus(Duration.ofMinutes(3))));
        repository.save(link("abcd01", bob, BASE));
        repository.save(link("abcd02", alice, BASE.plus(Duration.ofMinutes(1))));

        assertEquals(List.of("abcd01", "abcd02", "abcd03"),
            repository.findAll().stream().map(l -> l.code().value()).toList());
        assertEquals(List.of("abcd02", "abcd03"),
            repository.findByOwner(alice).stream().map(l -> l.code().value()).toList());
        assertEquals(1, repository.findByOwner(bob).size());
        assertTrue(repository.findByOwner(UserId.random()).isEmpty());
    }

    @Test
    @DisplayName("Удаление разрешено только владельцу")
    void deleteChecksOwner() {
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        UserId alice = UserId.random();
        repository.save(link("abcd12", alice, BASE));
        assertFalse(repository.delete(new ShortCode("abcd12"), UserId.random()));
        assertTrue(repository.delete(new ShortCode("abcd12"), alice));
        assertFalse(repository.delete(new ShortCode("abcd12"), alice));
        assertEquals(0, repository.count());
    }

    @Test
    @DisplayName("Ссылка в хранилище одна: изменение счётчика видно всем читателям")
    void sharesMutableEntity() {
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        ShortLink saved = link("abcd12", UserId.random(), BASE);
        repository.save(saved);
        saved.registerRedirect(BASE);
        assertEquals(1, repository.findByCode(new ShortCode("abcd12")).orElseThrow().redirectCount());
    }
}

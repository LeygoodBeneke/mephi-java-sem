package ru.mephi.shortlink.infrastructure.memory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import ru.mephi.shortlink.core.model.ShortCode;
import ru.mephi.shortlink.core.model.ShortLink;
import ru.mephi.shortlink.core.model.UserId;
import ru.mephi.shortlink.core.repository.LinkRepository;

public final class InMemoryLinkRepository implements LinkRepository {

    private final ConcurrentMap<ShortCode, ShortLink> links = new ConcurrentHashMap<>();

    @Override
    public boolean save(ShortLink link) {
        return links.putIfAbsent(link.code(), link) == null;
    }

    @Override
    public Optional<ShortLink> findByCode(ShortCode code) {
        return Optional.ofNullable(links.get(code));
    }

    @Override
    public List<ShortLink> findByOwner(UserId owner) {
        return links.values().stream()
            .filter(link -> link.owner().equals(owner))
            .sorted(byCreation())
            .toList();
    }

    @Override
    public List<ShortLink> findAll() {
        return links.values().stream().sorted(byCreation()).toList();
    }

    @Override
    public boolean delete(ShortCode code, UserId owner) {
        ShortLink stored = links.get(code);
        if (stored == null || !stored.owner().equals(owner)) {
            return false;
        }
        return links.remove(code, stored);
    }

    @Override
    public int count() {
        return links.size();
    }

    public List<ShortLink> snapshot() {
        return new ArrayList<>(findAll());
    }

    private static Comparator<ShortLink> byCreation() {
        return Comparator.comparing(ShortLink::createdAt).thenComparing(link -> link.code().value());
    }
}
